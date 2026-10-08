package io.github.youndie.haul.feature.order

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.cart.CartPaths
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.checkout.CheckoutPaths
import io.github.youndie.haul.feature.checkout.IDEMPOTENCY_KEY_HEADER
import io.github.youndie.haul.feature.checkout.PlaceOrderRequest
import io.github.youndie.haul.feature.checkout.domain.CheckoutError
import io.github.youndie.haul.feature.order.data.ExposedOrders
import io.github.youndie.haul.feature.order.domain.CancelReason
import io.github.youndie.haul.feature.order.domain.Order
import io.github.youndie.haul.feature.order.domain.OrderStatus
import io.github.youndie.haul.feature.order.domain.ShipmentStatus
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.SampleCatalog.BROOKLYN_HOME
import io.github.youndie.haul.seed.SampleCatalog.SONY_STORE
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.testing.Ledger
import io.github.youndie.haul.testing.MAYAS_SKUS
import io.github.youndie.haul.testing.SeededDatabase
import io.github.youndie.haul.testing.ShildikHarness
import io.github.youndie.haul.testing.assertError
import io.github.youndie.haul.testing.assertRefresh
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.ui.CheckoutNotice
import io.github.youndie.haul.ui.CheckoutSummary
import io.github.youndie.haul.ui.DeliverySlots
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotAction
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Placement over HTTP (feature-checkout, feature-orders), against shildik and a seeded PostgreSQL of
 * each test's own — placement takes stock and windows, which every customer shares — at the canvas's
 * «now». Maya signs in by her seeded id, with her cart (the headphones, the duvet, the mugs) and her
 * address; what placement did is read from the rows ([Ledger]), not from the answer alone.
 */
class PlacementRoutesTest {
    private val wednesday3pm = "2025-10-08T15"

    private fun asMaya(block: suspend HttpClient.(token: String, database: DataSource) -> Unit) =
        seededFreshDatabase().use { database ->
            val token = ShildikHarness.accessToken(ShildikHarness.person("Maya Kowalski", SampleCustomers.MAYA))
            haulTest(database, signIn = ShildikHarness.signIn) { block(token, database) }
        }

    private suspend fun HttpClient.checkout(token: String): KompotComponent {
        val response = get(CheckoutPaths.SCREEN) { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return haulWireJson.decodeKompotComponent(response.bodyAsText())
    }

    private suspend fun HttpClient.choose(
        token: String,
        choice: CheckoutChoice,
    ) = put(CheckoutPaths.CHOICE) {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(haulWireJson.encodeToString(CheckoutChoice.serializer(), choice))
    }.assertRefresh()

    /** «Place order» as the client sends it: to the summary's own url, with its quote, under [key]. */
    private suspend fun HttpClient.place(
        token: String,
        key: String?,
        summary: CheckoutSummary,
        quote: String = summary.quote,
    ): HttpResponse =
        post(assertNotNull(summary.placeUrl, "the summary carries no placeUrl")) {
            bearerAuth(token)
            key?.let { header(IDEMPOTENCY_KEY_HEADER, it) }
            contentType(ContentType.Application.Json)
            setBody(haulWireJson.encodeToString(PlaceOrderRequest.serializer(), PlaceOrderRequest(quote)))
        }

    /** A placement answered `202` with `navigate` to an order's page; the order's id. */
    private suspend fun HttpResponse.assertPlaced(): String {
        assertEquals(HttpStatusCode.Accepted, status, bodyAsText())
        val action = haulWireJson.decodeKompotAction(bodyAsText())
        val deeplink = (action as NavigateAction).deeplink
        assertTrue(deeplink.startsWith("/orders/HL-"), deeplink)
        return deeplink.removePrefix("/orders/")
    }

    private fun DataSource.order(id: String): Order =
        assertNotNull(runBlocking { ExposedOrders(Databases.connect(this@order)).order(id) }, "no order $id")

    /**
     * Scenario «Place by courier» (feature-checkout) and the saga's happy path (feature-orders): Maya's
     * cart to 148 Wythe Avenue, Wed 8 15:00–18:00, card ···· 4821 — `202` to the order's page, an `HL-`
     * order placed for $512.00 with a shipment per seller, and every effect the saga owns: one unit of
     * each SKU off its stock, a place in the window, the total authorised, the bought lines out of the cart.
     */
    @Test
    fun `Maya places her cart by courier and the saga takes stock window and payment`() =
        asMaya { token, database ->
            val ledger = Ledger(database)
            val stockBefore = ledger.stock()
            choose(token, CheckoutChoice(slotId = wednesday3pm))
            val summary = checkout(token).only<CheckoutSummary>()
            assertEquals(CheckoutPaths.PLACE, summary.placeUrl)

            val orderId = place(token, "maya-1", summary).assertPlaced()

            assertEquals("HL-48302", orderId, "the first order of a fresh store is the canvas's")
            val order = database.order(orderId)
            assertEquals(OrderStatus.Placed, order.status)
            assertEquals(51_200, order.placed.totalCents)
            assertEquals(wednesday3pm, order.placed.slotId)
            assertEquals(MAYAS_SKUS, order.placed.lines.map { it.skuId })
            assertEquals(listOf(SONY_STORE, BROOKLYN_HOME), order.shipments.map { it.sellerId })
            assertEquals(listOf(ShipmentStatus.PLACED, ShipmentStatus.PLACED), order.shipments.map { it.status })
            assertEquals(stockBefore.mapValues { it.value - 1 }, ledger.stock(), "one unit of each line reserved")
            assertEquals(1, ledger.taken(wednesday3pm), "the window's place was not taken")
            assertEquals(listOf(Triple(orderId, 51_200, "authorised")), ledger.authorisations())
            assertEquals(emptyList(), ledger.cartLines(SampleCustomers.MAYA), "the bought lines stayed in the cart")
            get(CheckoutPaths.SCREEN) { bearerAuth(token) }.assertError(HttpStatusCode.Conflict, ErrorCode.CartEmpty)
        }

    /**
     * Scenario «Same key twice» (feature-checkout): the retry answers the same order and places nothing
     * — though the cart it was placed from is empty now — and the same key with another request is
     * refused (petich-idempotency), as is a placement with no key at all.
     */
    @Test
    fun `the same key places once and a different request under it is refused`() =
        asMaya { token, database ->
            val ledger = Ledger(database)
            val summary = checkout(token).only<CheckoutSummary>()
            place(token, null, summary).assertError(HttpStatusCode.BadRequest, ErrorCode.IdempotencyKeyMissing)
            place(token, " ", summary).assertError(HttpStatusCode.BadRequest, ErrorCode.IdempotencyKeyMissing)

            val first = place(token, "maya-twice", summary).assertPlaced()
            val stockAfterFirst = ledger.stock()
            val again = place(token, "maya-twice", summary).assertPlaced()

            assertEquals(first, again)
            assertEquals(1, ledger.orders(), "a second order was placed under the same key")
            assertEquals(stockAfterFirst, ledger.stock(), "the retry reserved stock again")
            assertEquals(1, ledger.authorisations().size, "the retry authorised again")
            place(token, "maya-twice", summary, quote = "0".repeat(32))
                .assertError(HttpStatusCode.Conflict, ErrorCode.IdempotencyKeyReused)
        }

    /**
     * Scenario «Declined card cancels and releases» (feature-orders): the test card ···· 0002 is declined
     * by the simulator, and the saga undoes what it did — the order exists and is cancelled with
     * `payment_declined`, its shipments too, the stock and the window are given back, and the cart is
     * as it was. The shopper is sent to the order, which says so (research §6, #HL-48303).
     */
    @Test
    fun `a declined card cancels the order and gives everything back`() =
        asMaya { token, database ->
            val ledger = Ledger(database)
            val stockBefore = ledger.stock()
            choose(token, CheckoutChoice(slotId = wednesday3pm, payment = "card-0002"))
            val summary = checkout(token).only<CheckoutSummary>()

            val orderId = place(token, "maya-declined", summary).assertPlaced()

            val order = database.order(orderId)
            assertEquals(OrderStatus.Cancelled, order.status)
            assertEquals(CancelReason.PAYMENT_DECLINED, order.cancelReason)
            assertEquals(listOf(ShipmentStatus.CANCELLED, ShipmentStatus.CANCELLED), order.shipments.map { it.status })
            assertEquals(stockBefore, ledger.stock(), "the stock was not given back")
            assertEquals(0, ledger.reservedStock())
            assertEquals(0, ledger.taken(wednesday3pm), "the window's place was not given back")
            assertEquals(listOf(Triple(orderId, 51_200, "declined")), ledger.authorisations())
            assertEquals(MAYAS_SKUS, ledger.cartLines(SampleCustomers.MAYA), "the cart was touched")
            assertEquals("REJECTED", ledger.sagaStatus())
        }

    /**
     * Scenario «Slot filled meanwhile» (feature-checkout), placement's half: the window Maya chose fills
     * after her page was drawn, and placing is `409 slot_unavailable` — no order, no stock taken — and
     * the checkout she is sent back to has the window cleared and says why (`Checkout_PlaceError`).
     */
    @Test
    fun `a window that filled since the page was drawn is refused and the checkout says so`() =
        asMaya { token, database ->
            val ledger = Ledger(database)
            val stockBefore = ledger.stock()
            choose(token, CheckoutChoice(slotId = wednesday3pm))
            val summary = checkout(token).only<CheckoutSummary>()
            ledger.fill(wednesday3pm)

            place(token, "maya-filled", summary).assertError(HttpStatusCode.Conflict, ErrorCode.SlotUnavailable)

            assertEquals(0, ledger.orders())
            assertEquals(stockBefore, ledger.stock())
            val tree = checkout(token)
            assertEquals(CheckoutError.SLOT_FILLED, tree.only<CheckoutNotice>().text)
            assertTrue(
                tree
                    .only<DeliverySlots>()
                    .days
                    .flatMap { it.slots }
                    .none { it.selected },
                "the filled window is still chosen",
            )
        }

    /**
     * Placement places only the quote the shopper saw: a line changed after the page was drawn makes
     * the old fingerprint `409 cart_changed`, and nothing is placed. The key was not spent on the
     * refusal — the same key with the new quote places it.
     */
    @Test
    fun `a quote that changed since the page was drawn is refused`() =
        asMaya { token, database ->
            val ledger = Ledger(database)
            val stale = checkout(token).only<CheckoutSummary>()
            put(CartPaths.line(MAYAS_SKUS.last())) {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(haulWireJson.encodeToString(LineChange.serializer(), LineChange(quantity = 2)))
            }.assertRefresh()

            place(token, "maya-stale", stale).assertError(HttpStatusCode.Conflict, ErrorCode.CartChanged)
            assertEquals(0, ledger.orders())

            val fresh = checkout(token).only<CheckoutSummary>()
            val orderId = place(token, "maya-stale", fresh).assertPlaced()
            assertEquals(
                2,
                database
                    .order(orderId)
                    .placed.lines
                    .last()
                    .quantity,
            )
        }

    /** «Shown when: signed in» (screen-checkout): placement is in the customer tier. */
    @Test
    fun `placement needs a sign-in`() =
        haulTest(SeededDatabase.dataSource, signIn = ShildikHarness.signIn) {
            val response =
                post(CheckoutPaths.PLACE) {
                    header(IDEMPOTENCY_KEY_HEADER, "nobody")
                    contentType(ContentType.Application.Json)
                    setBody(haulWireJson.encodeToString(PlaceOrderRequest.serializer(), PlaceOrderRequest("x")))
                }
            response.assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
        }
}
