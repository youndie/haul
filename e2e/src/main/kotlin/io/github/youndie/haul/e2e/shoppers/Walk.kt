package io.github.youndie.haul.e2e.shoppers

import io.github.youndie.haul.e2e.SignIn
import io.github.youndie.haul.e2e.Storefront
import io.github.youndie.haul.e2e.Tree
import io.github.youndie.haul.e2e.deeplink
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.checkout.AddressEntry
import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.checkout.DeliveryMethod
import io.github.youndie.haul.feature.checkout.IDEMPOTENCY_KEY_HEADER
import io.github.youndie.haul.feature.checkout.PlaceOrderRequest
import io.github.youndie.haul.feature.identity.GuestDto
import io.github.youndie.haul.feature.identity.SignInSettings
import io.github.youndie.haul.feature.returns.ReturnEntry
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.ui.AccountBody
import io.github.youndie.haul.ui.CartBody
import io.github.youndie.haul.ui.CategoryGrid
import io.github.youndie.haul.ui.CheckoutBody
import io.github.youndie.haul.ui.FilteredResults
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.OrderBody
import io.github.youndie.haul.ui.ProductDetails
import io.github.youndie.haul.ui.ReturnForm
import io.github.youndie.kompot.standard.PresentAction
import io.github.youndie.kompot.standard.RefreshAction
import java.util.UUID
import java.util.concurrent.locks.Lock
import kotlin.concurrent.withLock
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * One synthetic shopper's walk: the path the whole-path test walks (`WholePathTest`), taken for its
 * traffic rather than for its assertions. A guest arrives, opens the home, a category and a product picked
 * at random among those that can be bought, adds it to the cart from the product page, signs in as [login]
 * by the storefront's own PKCE flow and takes the cart along, checks out by courier with an address, a
 * window and a card the checkout offers, places the order — the saga — and then waits on the fulfilment
 * clock: delivered, the line returned, refunded, and the order read back from the history.
 *
 * Every address after `/` comes from a tree or an answer, as in the test, so a walk that still completes
 * is the storefront still working end to end. A step that fails throws [WalkFailed] naming the step.
 *
 * **One walk per person between the sign-in and the placement** ([cart]): the cart is the customer's, and
 * a second walk of the same person merging its line in between would have it placed by the first.
 */
internal class Walk(
    val number: Int,
    private val origin: String,
    private val signIn: SignIn,
    val login: String,
    private val password: Password,
    private val cart: Lock,
    private val poll: Duration,
    private val wait: Duration,
    private val random: Random = Random.Default,
) {
    private val shop = Storefront(origin)
    private val started = TimeSource.Monotonic.markNow()

    fun take(): Walked {
        step("a guest arrives") {
            val answer = shop.send("POST", GUESTS)
            check(answer.status == 201) { "${answer.request} answered ${answer.status}" }
            shop.guest = haulWireJson.decodeFromString(GuestDto.serializer(), answer.body).id
        }
        val product = step("browse to a product and add it from its page") { browseAndAdd() }

        val placed =
            cart.withLock {
                step("sign in as $login and take the cart along") { signInAndMerge() }
                step("check out by courier and place the order") { checkOutAndPlace() }
            }
        val placedAfter = started.elapsedNow()

        val form = step("wait for the order to arrive") { delivered(placed) }
        val deliveredAfter = started.elapsedNow()
        step("return a line") { returnLine(form) }
        step("wait for the refund") {
            awaitOrder(placed, "refunded") { order -> order.summary.rows.any { it.label == "Refunded" } }
        }
        val refundedAfter = started.elapsedNow()
        step("find the order returned in the history") { history(placed) }
        return Walked(number, login, product, placed, placedAfter, deliveredAfter, refundedAfter)
    }

    /** A category tile and a buyable card on it, at random; the product's own page adds it. */
    private fun browseAndAdd(): String {
        val tiles = shop.page("/").one(CategoryGrid.serializer()).tiles
        check(tiles.isNotEmpty()) { "the home has no category tiles" }
        for (tile in tiles.shuffled(random)) {
            val grid = shop.page(tile.action.deeplink("the tile «${tile.name}»")).one(FilteredResults.serializer()).grid
            val card = grid?.cards?.filter { it.add != null }?.randomOrNull(random) ?: continue
            val page = shop.page(card.action.deeplink("the card «${card.title}»"))
            val details = page.one(ProductDetails.serializer())
            val add = details.add ?: continue
            val answer = shop.send("PUT", add.url, haulWireJson.encodeToString(LineChange.serializer(), add.change))
            check(answer.status == 200) { "${answer.request} answered ${answer.status}: ${answer.body.take(300)}" }
            return details.title
        }
        error("no category on the home has a product that can be added")
    }

    private fun signInAndMerge() {
        val answer = shop.send("GET", SIGN_IN_SETTINGS)
        check(answer.status == 200) { "${answer.request} answered ${answer.status}: ${answer.body.take(300)}" }
        val settings = haulWireJson.decodeFromString(SignInSettings.serializer(), answer.body)
        shop.token = signIn.accessToken(settings, login, password.value)
        check(shop.send("POST", settings.mergeUrl).action() == RefreshAction) { "the merge did not refresh" }
        // Merged: the cart is the customer's now, found by the token alone.
        shop.guest = null
    }

    /** The order's address, from placement's `navigate`. */
    private fun checkOutAndPlace(): String {
        val address =
            cartTree()
                .one(CartBody.serializer())
                .summary.checkoutAction
                .deeplink("«Checkout»")
        var body = shop.page(address).one(CheckoutBody.serializer())
        val courier = body.methods.options.single { it.method == DeliveryMethod.Courier }
        choose(body.methods.url, CheckoutChoice(method = courier.method))
        body = shop.page(address).one(CheckoutBody.serializer())

        val form = checkNotNull(body.address) { "a courier checkout has no address form" }
        // The same address every time: a form equal to one the customer has chooses it and stores nothing (B-40).
        val saved = shop.send("POST", form.url, haulWireJson.encodeToString(AddressEntry.serializer(), ADDRESS))
        check(saved.action() == RefreshAction) { "the address was not taken" }
        body = shop.page(address).one(CheckoutBody.serializer())

        val slots = checkNotNull(body.slots) { "a courier checkout offers no windows" }
        val window =
            checkNotNull(
                slots.days
                    .flatMap { it.slots }
                    .filter { it.available }
                    .randomOrNull(random),
            ) {
                "no window has room"
            }
        choose(slots.url, CheckoutChoice(slotId = window.id))
        val card = body.payment.options.first { it.label.startsWith("Card") }
        choose(body.payment.url, CheckoutChoice(payment = card.id))

        val summary = shop.page(address).one(CheckoutBody.serializer()).summary
        check(summary.placeEnabled) { "the checkout cannot be placed: ${summary.placeHint}" }
        val placeUrl = checkNotNull(summary.placeUrl) { "the checkout names nowhere to place" }
        val request = haulWireJson.encodeToString(PlaceOrderRequest.serializer(), PlaceOrderRequest(summary.quote))
        return shop
            .send("POST", placeUrl, request, mapOf(IDEMPOTENCY_KEY_HEADER to UUID.randomUUID().toString()))
            .action(202)
            .deeplink("placement's answer")
    }

    private fun delivered(order: String): ReturnForm {
        val delivered = awaitOrder(order, "delivered") { it.steps?.arrived == true && it.summary.returnAction != null }
        val present = delivered.summary.returnAction as? PresentAction ?: error("«Return items» presents nothing")
        return present.content as? ReturnForm ?: error("«Return items» presents ${present.content}")
    }

    private fun returnLine(form: ReturnForm) {
        val line = form.lines.first()
        val entry = ReturnEntry(lines = listOf(line.position), reason = form.reasons.random(random).id)
        shop.send("POST", form.url, haulWireJson.encodeToString(ReturnEntry.serializer(), entry)).action(201)
    }

    private fun history(order: String) {
        val orders = header().orders.deeplink("the header's «Orders»")
        val history = checkNotNull(shop.page(orders).one(AccountBody.serializer()).history) { "no history" }
        val row = history.rows.firstOrNull { it.action?.deeplink("a history row") == order }
        // A long history may page the order away; the page was read either way, which is the traffic.
        if (row != null) check(row.status == "Returned") { "the history reads «${row.status}»" }
    }

    private fun header(): HaulHeader = shop.page("/").one(HaulHeader.serializer())

    private fun cartTree(): Tree = shop.page(header().cart.deeplink("the header's cart"))

    private fun choose(
        url: String,
        choice: CheckoutChoice,
    ) = check(
        shop.send("PUT", url, haulWireJson.encodeToString(CheckoutChoice.serializer(), choice)).action() ==
            RefreshAction,
    ) { "the checkout did not take $choice" }

    /** The order's page, read every [poll] until [done] — within [wait], or a failure saying where it was. */
    private fun awaitOrder(
        address: String,
        what: String,
        done: (OrderBody) -> Boolean,
    ): OrderBody {
        val since = TimeSource.Monotonic.markNow()
        var last: OrderBody
        while (true) {
            last = shop.page(address).one(OrderBody.serializer())
            if (done(last)) return last
            if (since.elapsedNow() >= wait) break
            Thread.sleep(poll.inWholeMilliseconds)
        }
        error("not $what within $wait; the order reads «${last.title}»")
    }

    private fun <T> step(
        name: String,
        block: () -> T,
    ): T =
        try {
            block()
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            throw WalkFailed(name, e)
        }

    private companion object {
        /** The two paths a client calls before it has a tree to follow (the client's `IdentityApi`). */
        const val GUESTS = "/api/v1/guests"
        const val SIGN_IN_SETTINGS = "/api/v1/sign-in"

        val ADDRESS = AddressEntry(street = "1 Kent Avenue", city = "Brooklyn", zip = "11249")
    }
}

/** What a finished walk did, for its line in the log. */
internal data class Walked(
    val number: Int,
    val login: String,
    val product: String,
    val order: String,
    val placedAfter: Duration,
    val deliveredAfter: Duration,
    val refundedAfter: Duration,
)

/** A walk that stopped at [step]; the message names the step and what went wrong there. */
internal class WalkFailed(
    val step: String,
    cause: Exception,
) : RuntimeException("step «$step»: ${cause.message}", cause)
