package io.github.youndie.haul.e2e

import io.github.youndie.haul.StorefrontPage
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.checkout.AddressEntry
import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.checkout.DeliveryMethod
import io.github.youndie.haul.feature.checkout.IDEMPOTENCY_KEY_HEADER
import io.github.youndie.haul.feature.checkout.PlaceOrderRequest
import io.github.youndie.haul.feature.identity.GuestDto
import io.github.youndie.haul.feature.identity.SignInSettings
import io.github.youndie.haul.feature.returns.ReturnEntry
import io.github.youndie.haul.feature.returns.exactDollars
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
import io.github.youndie.kompot.standard.CloseAction
import io.github.youndie.kompot.standard.PresentAction
import io.github.youndie.kompot.standard.RefreshAction
import io.github.youndie.kompot.standard.SequenceAction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The whole path a shopper takes, over HTTP, against the server's own image with PostgreSQL and shildik
 * beside it ([ComposedStack]): browse from `/` to a product, put it in a guest cart from its page, sign in and take
 * the cart along, check out by courier with an address, a window and a card the checkout offers, place
 * the order, wait for the fast clock to deliver it, return it, wait for the refund — read again, and pushed to the
 * order's page down the channel its tree names (B-29) — and find it in the history as returned.
 *
 * **What it guards** is the seams no route test crosses: the image (its AOT cache, its configuration
 * from the environment), the realm a stand signs in against, the trees handing each other their
 * addresses — every path here after `/` is one a tree or a command answer gave — and the simulators
 * running on their own clock in a real process. Each step checks what the shopper would see — the
 * product, the line, the money, the order's state — and a failure names its step and carries the
 * server's log.
 *
 * Not covered: load (B-31's traffic generator walks the same path for that).
 */
class WholePathTest {
    private val shop = Storefront(ComposedStack.origin)

    @Test
    fun `a shopper browses buys receives and returns an order over HTTP`() {
        step("a guest arrives") {
            val answer = shop.send("POST", GUESTS)
            check(answer.status == 201) { "${answer.request} answered ${answer.status}: ${answer.body}" }
            shop.guest = haulWireJson.decodeFromString(GuestDto.serializer(), answer.body).id
        }

        val (card, productAddress) =
            step("browse from the home to a category and a product") {
                val home = shop.page("/")
                val tile = home.one(CategoryGrid.serializer()).tiles.first()
                val category = shop.page(tile.action.deeplink("the home's tile «${tile.name}»"))
                val grid =
                    assertNotNull(category.one(FilteredResults.serializer()).grid, "«${tile.name}» shows no products")
                // A card that offers «+» is a product in stock: the one worth opening to buy.
                val card =
                    assertNotNull(grid.cards.firstOrNull { it.add != null }, "no card in «${tile.name}» can be added")

                val productAddress = card.action.deeplink("the card «${card.title}»")
                assertEquals(StorefrontPage.Product, pageOf(productAddress))
                val product = shop.page(productAddress).one(ProductDetails.serializer())
                assertEquals(card.productId, product.productId, "the card led to another product")
                assertTrue(product.inStock, "the product page says «${card.title}» is out of stock")
                assertEquals(card.price, product.price, "the card and the product page disagree on the price")
                card to productAddress
            }
        val price = cents(card.price)

        step("add it to the guest's cart with the product page's «Add to cart»") {
            val product = shop.page(productAddress).one(ProductDetails.serializer())
            val add = assertNotNull(product.add, "the product page offers no «Add to cart» for «${product.title}»")
            val answer = shop.send("PUT", add.url, haulWireJson.encodeToString(LineChange.serializer(), add.change))
            assertEquals(RefreshAction, answer.action())
            val line = cartLines().single()
            assertEquals(product.productId, line.productId)
            assertEquals(1, line.quantity)
            assertEquals(product.price, line.price, "the cart charges another price than the product page showed")
            // `refresh` draws the page again: the header counts the line, and «Add to cart» offers one more.
            val again = shop.page(productAddress)
            assertEquals(1, again.one(HaulHeader.serializer()).cartCount, "the header does not count the line")
            assertEquals(
                2,
                again
                    .one(ProductDetails.serializer())
                    .add
                    ?.change
                    ?.quantity,
                "a second press adds no more",
            )
        }

        val shopper = "e2e-${UUID.randomUUID()}"
        step("sign in through the realm the server names and take the guest's cart along") {
            val checkout = cartTree().one(CartBody.serializer()).summary.checkoutAction
            val signIn = checkout.deeplink("a guest's «Checkout»")
            assertEquals(
                StorefrontPage.SignIn,
                pageOf(signIn),
                "a guest's «Checkout» goes to «$signIn»",
            )

            val settingsAnswer = shop.send("GET", SIGN_IN_SETTINGS)
            check(settingsAnswer.status == 200) {
                "${settingsAnswer.request} answered ${settingsAnswer.status}: ${settingsAnswer.body}"
            }
            val settings = haulWireJson.decodeFromString(SignInSettings.serializer(), settingsAnswer.body)
            assertEquals(ComposedStack.issuer, settings.issuer, "the server names another realm than it was given")
            assertEquals(ComposedStack.CLIENT, settings.clientId)

            val login = ComposedStack.shildikAdmin.person(shopper, "Erin Example", PASSWORD)
            shop.token = ComposedStack.shildikAdmin.accessToken(settings, ComposedStack.REDIRECT, login, PASSWORD)
            assertEquals(RefreshAction, shop.send("POST", settings.mergeUrl).action())
            // Merged: the cart is the customer's now, and is found by the token alone.
            shop.guest = null

            assertEquals("Erin", header().customerName, "the header does not greet the signed-in customer")
            val line = cartLines().single()
            assertEquals(card.productId, line.productId, "the guest's line did not come along")
            assertEquals(1, line.quantity)
        }

        val placed =
            step("check out by courier with an address, a window and a card the checkout offers") {
                val toCheckout = cartTree().one(CartBody.serializer()).summary.checkoutAction
                val address = toCheckout.deeplink("a customer's «Checkout»")
                assertEquals(StorefrontPage.Checkout, pageOf(address))

                var body = shop.page(address).one(CheckoutBody.serializer())
                val courier = body.methods.options.single { it.method == DeliveryMethod.Courier }
                choose(body.methods.url, CheckoutChoice(method = courier.method))
                body = shop.page(address).one(CheckoutBody.serializer())

                val form = assertNotNull(body.address, "a courier checkout has no address form")
                val required =
                    form.form
                        .filter { it.required }
                        .map { it.name }
                        .toSet()
                assertTrue(
                    required.isNotEmpty() && required.all { it in ADDRESS_FIELDS },
                    "the form requires $required",
                )
                assertEquals(
                    RefreshAction,
                    shop
                        .send(
                            "POST",
                            form.url,
                            haulWireJson.encodeToString(AddressEntry.serializer(), ADDRESS),
                        ).action(),
                )
                body = shop.page(address).one(CheckoutBody.serializer())
                assertEquals(
                    ADDRESS.street,
                    body.address
                        ?.form
                        ?.single { it.name == "street" }
                        ?.value,
                    "the address was not kept",
                )

                val slots = assertNotNull(body.slots, "a courier checkout offers no windows")
                val window =
                    assertNotNull(
                        slots.days
                            .flatMap { it.slots }
                            .lastOrNull { it.available },
                        "no window has room",
                    )
                choose(slots.url, CheckoutChoice(slotId = window.id))
                val payWith = body.payment.options.first { it.label.startsWith("Card") }
                choose(body.payment.url, CheckoutChoice(payment = payWith.id))

                body = shop.page(address).one(CheckoutBody.serializer())
                assertTrue(
                    body.methods.options
                        .single { it.method == DeliveryMethod.Courier }
                        .selected,
                )
                assertTrue(
                    body.slots!!
                        .days
                        .flatMap { it.slots }
                        .single { it.id == window.id }
                        .selected,
                    "the window is not chosen",
                )
                assertTrue(
                    body.payment.options
                        .single { it.id == payWith.id }
                        .selected,
                    "the card is not chosen",
                )
                val summary = body.summary
                assertTrue(summary.placeEnabled, "the checkout cannot be placed: ${summary.placeHint}")
                assertEquals(1, summary.items.single().quantity)
                assertTrue(
                    cents(summary.total) >= price,
                    "the total ${summary.total} is less than the item ${card.price}",
                )

                val key = UUID.randomUUID().toString()
                val placeUrl = assertNotNull(summary.placeUrl)
                val request =
                    haulWireJson.encodeToString(
                        PlaceOrderRequest.serializer(),
                        PlaceOrderRequest(summary.quote),
                    )
                val order = shop.send("POST", placeUrl, request, mapOf(IDEMPOTENCY_KEY_HEADER to key)).action(202)
                val orderAddress = order.deeplink("placement's answer")
                assertEquals(
                    StorefrontPage.Order,
                    pageOf(orderAddress),
                    "placement did not lead to an order",
                )
                val again = shop.send("POST", placeUrl, request, mapOf(IDEMPOTENCY_KEY_HEADER to key)).action(202)
                assertEquals(
                    orderAddress,
                    again.deeplink("the retried placement"),
                    "a retry under the same key placed another order",
                )
                Placed(orderAddress, cents(summary.total))
            }

        // The order's page listens on the channel its tree names (B-29), from here to the refund: every move the
        // simulators make after this is pushed down it.
        val live =
            step("the order's page listens for its moves") {
                val topic = assertNotNull(shop.page(placed.address).realtimeTopic, "the order's page names no channel")
                shop.listen(topic)
            }

        val form =
            step("the fast clock delivers the order") {
                val delivered =
                    awaitOrder(placed.address, 2.minutes, "delivered") {
                        it.steps?.arrived == true &&
                            it.summary.returnAction != null
                    }
                assertEquals(
                    placed.totalCents,
                    cents(delivered.summary.total),
                    "the order's total is not the checkout's",
                )
                assertTrue(
                    delivered.shipments.isNotEmpty() &&
                        delivered.shipments.all {
                            it.status == "Delivered"
                        },
                    "${delivered.shipments}",
                )
                val present =
                    delivered.summary.returnAction as? PresentAction ?: error("«Return items» presents nothing")
                present.content as? ReturnForm ?: error("«Return items» presents ${present.content}")
            }

        val refund =
            step("return the line") {
                val line = form.lines.single()
                assertEquals(price, line.refundCents, "returning the line does not give back what it cost")
                val entry = ReturnEntry(lines = listOf(line.position), reason = form.reasons.first().id)
                val answer =
                    shop
                        .send(
                            "POST",
                            form.url,
                            haulWireJson.encodeToString(ReturnEntry.serializer(), entry),
                        ).action(201)
                val actions = (answer as? SequenceAction)?.actions ?: error("the return answered $answer")
                assertTrue(CloseAction in actions && RefreshAction in actions, "the return answered $actions")
                line.refundCents
            }

        step("the return is collected and refunded") {
            val refunded =
                awaitOrder(placed.address, 2.minutes, "refunded") { order ->
                    order.summary.rows.any {
                        it.label ==
                            "Refunded"
                    }
                }
            val row = refunded.summary.rows.single { it.label == "Refunded" }
            assertEquals("−" + exactDollars(refund), row.value, "the refund row")
            assertEquals(
                exactDollars(placed.totalCents - refund),
                refunded.summary.total,
                "what the order cost after the refund",
            )
            assertTrue(refunded.shipments.any { it.returned }, "no card of returned items: ${refunded.shipments}")
        }

        step("the refund was pushed to the order's page") {
            // The return was asked for after the page started listening, so a refunded order on the stream was
            // pushed by the simulator's move, not drawn when the stream opened.
            live.use {
                val pushed =
                    it.await(OrderBody.serializer(), 30.seconds) { order ->
                        order.summary.rows.any { row -> row.label == "Refunded" }
                    }
                assertEquals("Return refunded", pushed.title)
            }
        }

        step("the account's history shows the order returned") {
            val orders = header().orders.deeplink("the header's «Orders»")
            assertEquals(StorefrontPage.Orders, pageOf(orders))
            val history =
                assertNotNull(shop.page(orders).one(AccountBody.serializer()).history, "the orders page has no history")
            val row = history.rows.single { it.action?.deeplink("a history row") == placed.address }
            assertEquals("Returned", row.status)
            assertEquals(HistoryStatusKind.Returned, row.statusKind)
            assertEquals(exactDollars(placed.totalCents), row.total)
        }
    }

    private data class Placed(
        val address: String,
        val totalCents: Int,
    )

    /** The header of the home, as the shopper is now. */
    private fun header(): HaulHeader = shop.page("/").one(HaulHeader.serializer())

    /** The cart's tree, reached the way the header's cart button reaches it. */
    private fun cartTree(): Tree = shop.page(header().cart.deeplink("the header's cart"))

    private fun cartLines() = cartTree().one(CartBody.serializer()).groups.flatMap { it.lines }

    /** A checkout choice to the url its section carries, answered `refresh`. */
    private fun choose(
        url: String,
        choice: CheckoutChoice,
    ) = assertEquals(
        RefreshAction,
        shop.send("PUT", url, haulWireJson.encodeToString(CheckoutChoice.serializer(), choice)).action(),
    )

    /**
     * The order's page, read again every half second until [done] — within [within], or a failure that
     * says where the order was the last time it was read.
     */
    private fun awaitOrder(
        address: String,
        within: Duration,
        what: String,
        done: (OrderBody) -> Boolean,
    ): OrderBody {
        val started = TimeSource.Monotonic.markNow()
        var last: OrderBody
        do {
            last = shop.page(address).one(OrderBody.serializer())
            if (done(last)) return last
            Thread.sleep(0.5.seconds.inWholeMilliseconds)
        } while (started.elapsedNow() < within)
        error(
            "not $what within $within; the order reads «${last.title}» with shipments ${last.shipments.map {
                it.status
            }}",
        )
    }

    /** [block], with a failure that names the step and carries what the server printed. */
    private fun <T> step(
        name: String,
        block: () -> T,
    ): T =
        try {
            block()
        } catch (e: Throwable) {
            throw AssertionError(
                "step «$name» failed: ${e.message}\n--- the server's log:\n${ComposedStack.serverLog()}",
                e,
            )
        }

    private companion object {
        /**
         * The two paths a client calls before it has a tree to follow (the client's `IdentityApi`): where a
         * guest is made, and where the browser reads how to sign in.
         */
        const val GUESTS = "/api/v1/guests"
        const val SIGN_IN_SETTINGS = "/api/v1/sign-in"

        const val PASSWORD = "e2e-horse-battery-staple"
        val ADDRESS = AddressEntry(street = "1 Kent Avenue", city = "Brooklyn", zip = "11249")
        val ADDRESS_FIELDS = setOf("street", "city", "zip")
    }
}
