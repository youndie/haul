package io.github.youndie.haul.e2e.shoppers

import io.github.youndie.haul.e2e.SignIn
import io.github.youndie.haul.e2e.Storefront
import io.github.youndie.haul.e2e.Transport
import io.github.youndie.haul.e2e.Tree
import io.github.youndie.haul.e2e.deeplink
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.cart.LinesRemoval
import io.github.youndie.haul.feature.checkout.AddressEntry
import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.checkout.DeliveryMethod
import io.github.youndie.haul.feature.checkout.IDEMPOTENCY_KEY_HEADER
import io.github.youndie.haul.feature.checkout.PlaceOrderRequest
import io.github.youndie.haul.feature.checkout.TEST_CARD_KEY_HEADER
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
import io.github.youndie.haul.ui.HistoryStatusKind
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
 * **The sign-in is kept alive** while the walk waits: shildik's access token lives five minutes and a walk at the
 * stand's speed (288) waits about ten for its delivery and ten more for its refund, so a refused bearer is
 * renewed with the refresh token, as the storefront's client does ([Storefront.renew]). Before that, every walk
 * on the stand stopped at its first read of the order page past the token's life (B-31).
 *
 * **One walk per person between the sign-in and the placement** ([cart]): the cart is the customer's, and
 * a second walk of the same person merging its line in between would have it placed by the first.
 *
 * **A walk given the [testCardKey] declines** (B-89): it pays with the test card ···· 0002, which the payment
 * simulator always declines and which the storefront never lists — named by its id, with the stand's key in
 * `TEST_CARD_KEY_HEADER` — and sees the order refused: cancelled, the card named, its lines back in the cart. The
 * saga undid what it had done (the stock, the window); the walk then puts the listed card back and removes the
 * lines, still holding the [cart], so the next walk of the person finds the checkout as a visitor would and buys
 * nothing twice.
 *
 * **A request a replica's death cut is sent again when that is safe** ([resend], on unless a test says otherwise,
 * B-174): a read, a choice, and the placement under the key it was first sent with — which answers the one order, the
 * saga carried on wherever it stopped, rather than placing a second.
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
    transport: Transport = Transport.DIRECT,
    private val testCardKey: TestCardKey? = null,
    resend: Boolean = true,
) {
    private val shop = Storefront(origin, transport, resend)

    /** How many requests the walk sent again after a replica went away under them (B-174). */
    val resent: Int get() = shop.resent
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
                if (testCardKey == null) {
                    step("check out by courier and place the order") { checkOutAndPlace() }
                } else {
                    step("pay with the declining card and see the order refused") { checkOutAndDecline(testCardKey) }
                }
            }
        val placedAfter = started.elapsedNow()
        if (testCardKey != null) {
            return Walked(number, login, product, placed, placedAfter, null, null, shop.renewals, declined = true)
        }

        val form = step("wait for the order to arrive") { delivered(placed) }
        val deliveredAfter = started.elapsedNow()
        step("return a line") { returnLine(form) }
        step("wait for the refund") {
            awaitOrder(placed, "refunded") { order -> order.summary.rows.any { it.label == "Refunded" } }
        }
        val refundedAfter = started.elapsedNow()
        step("find the order returned in the history") { history(placed) }
        return Walked(number, login, product, placed, placedAfter, deliveredAfter, refundedAfter, shop.renewals)
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
        val tokens = signIn.tokens(settings, login, password.value)
        shop.token = tokens.access
        var refresh = checkNotNull(tokens.refresh) { "the provider issued no refresh token for «${settings.scope}»" }
        shop.renew = { signIn.refresh(settings, refresh).also { next -> next.refresh?.let { refresh = it } }.access }
        check(shop.send("POST", settings.mergeUrl).action() == RefreshAction) { "the merge did not refresh" }
        // Merged: the cart is the customer's now, found by the token alone.
        shop.guest = null
    }

    /** The order's address, from placement's `navigate`, paid with the card the checkout lists. */
    private fun checkOutAndPlace(): String {
        val (address, body) = checkOut()
        choose(body.payment.url, CheckoutChoice(payment = listedCard(body)))
        return place(address)
    }

    /**
     * The declining card chosen by its id with the stand's [key], the order placed and seen refused — cancelled, the
     * card named — and then the checkout and the cart left as a visitor finds them: the listed card chosen again
     * (while the declined lines are back in the cart, since a checkout with nothing to quote takes no choice) and
     * those lines removed. The order's address.
     */
    private fun checkOutAndDecline(key: TestCardKey): String {
        val (address, body) = checkOut()
        val listed = CheckoutChoice(payment = listedCard(body))
        choose(body.payment.url, CheckoutChoice(payment = DECLINING_CARD), mapOf(TEST_CARD_KEY_HEADER to key.value))
        val order =
            try {
                place(address).also { refused(it) }
            } catch (e: Exception) {
                // So the declining card is not left chosen for whoever signs in as this person next; a reset that fails
                // too travels with the walk's failure rather than replacing it.
                try {
                    choose(body.payment.url, listed)
                } catch (reset: Exception) {
                    e.addSuppressed(reset)
                }
                throw e
            }
        choose(body.payment.url, listed)
        val back = cartTree().one(CartBody.serializer())
        val lines =
            back.groups
                .flatMap { it.lines }
                .filter { it.selected }
                .map { it.skuId }
        check(lines.isNotEmpty()) { "the declined order's lines did not come back to the cart" }
        val removed =
            shop.send(
                "DELETE",
                back.selection.linesUrl,
                haulWireJson.encodeToString(LinesRemoval.serializer(), LinesRemoval(lines)),
            )
        check(removed.status == 200) { "${removed.request} answered ${removed.status}: ${removed.body.take(300)}" }
        return order
    }

    /** The order's page says the card was declined: cancelled, nothing charged, the way back to the cart. */
    private fun refused(order: String) {
        val page = shop.page(order).one(OrderBody.serializer())
        check(page.summary.voided && page.notice?.title?.contains("declined") == true) {
            "the order paid with the declining card reads «${page.title}» / «${page.notice?.title}», not refused"
        }
    }

    /** The card the checkout lists, the one that approves: the first «Card …» option. */
    private fun listedCard(body: CheckoutBody): String =
        body.payment.options
            .first { it.label.startsWith("Card") && it.id != DECLINING_CARD }
            .id

    /** Checkout by courier with the address and a window chosen: its address and the body to pay from. */
    private fun checkOut(): Pair<String, CheckoutBody> {
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
        return address to body
    }

    /** The checkout at [address] placed under a fresh key: the order's address, from placement's `navigate`. */
    private fun place(address: String): String {
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
        // Returned whole or in part (B-101's «Partly returned», should the cart have carried more than one line).
        if (row != null) check(row.statusKind == HistoryStatusKind.Returned) { "the history reads «${row.status}»" }
    }

    private fun header(): HaulHeader = shop.page("/").one(HaulHeader.serializer())

    private fun cartTree(): Tree = shop.page(header().cart.deeplink("the header's cart"))

    private fun choose(
        url: String,
        choice: CheckoutChoice,
        headers: Map<String, String> = emptyMap(),
    ) = check(
        shop.send("PUT", url, haulWireJson.encodeToString(CheckoutChoice.serializer(), choice), headers).action() ==
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

        /** The payment simulator's test card, which it always declines; not listed, named by its id (B-89). */
        const val DECLINING_CARD = "card-0002"
    }
}

/** What a finished walk did, for its line in the log. */
internal data class Walked(
    val number: Int,
    val login: String,
    val product: String,
    val order: String,
    val placedAfter: Duration,
    /** `null` for a [declined] walk, whose order never ships. */
    val deliveredAfter: Duration?,
    val refundedAfter: Duration?,
    /** How many times the walk's bearer was refused and renewed on the way. */
    val renewals: Int,
    /** Paid with the declining card and saw the order refused (B-89). */
    val declined: Boolean = false,
)

/** A walk that stopped at [step]; the message names the step and what went wrong there. */
internal class WalkFailed(
    val step: String,
    cause: Exception,
) : RuntimeException("step «$step»: ${cause.message}", cause)
