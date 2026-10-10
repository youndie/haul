package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.feature.cart.CartPaths
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.checkout.CheckoutPaths
import io.github.youndie.haul.feature.checkout.DeliveryMethod
import io.github.youndie.haul.feature.checkout.IDEMPOTENCY_KEY_HEADER
import io.github.youndie.haul.feature.checkout.PlaceOrderRequest
import io.github.youndie.haul.feature.membership.MembershipPaths
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.seed.SampleCheckout
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.testing.Ledger
import io.github.youndie.haul.testing.ShildikHarness
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.assertError
import io.github.youndie.haul.testing.assertRefresh
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.ui.CartLine
import io.github.youndie.haul.ui.CheckoutSummary
import io.github.youndie.haul.ui.OrderSummary
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductDetails
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotComponent
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
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Plus early access to campaign prices over HTTP (B-53, feature-browse, feature-membership), against
 * shildik and a seeded PostgreSQL of each test's own. The Autumn mega sale opens to everybody on
 * Oct 7, 2025 and to Plus members on Oct 6; every markdown of the seed is its price. Inside that day a
 * member — Maya since 2023, Sam from his trial on — is drawn and charged the sale's price, a non-member
 * and a guest the regular one; from the start, everybody the sale's.
 *
 * The headphones (Midnight Black, headphones only) are $349 in the sale and $449 regularly; the mug
 * set, in no campaign, is $24 whoever looks.
 */
class PlusEarlyAccessTest {
    private val maya by lazy {
        ShildikHarness.accessToken(ShildikHarness.person("Maya Kowalski", SampleCustomers.MAYA))
    }
    private val sam by lazy { ShildikHarness.accessToken(ShildikHarness.person("Sam Ortiz", SampleCustomers.SAM)) }

    /** The store's «now», which a test moves: the early-access day first, then the sale's start. */
    private class MovingClock(
        var at: OffsetDateTime,
    ) : StoreClock {
        override fun now(): ZonedDateTime = at.toZonedDateTime()
    }

    private fun store(block: suspend HttpClient.(clock: MovingClock, database: DataSource) -> Unit) =
        seededFreshDatabase().use { database ->
            val clock = MovingClock(EARLY)
            haulTest(database, signIn = ShildikHarness.signIn, clock = clock) { block(clock, database) }
        }

    private suspend fun HttpClient.page(
        path: String,
        token: String?,
    ): KompotComponent {
        val response = get(path) { token?.let { bearerAuth(it) } }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return haulWireJson.decodeKompotComponent(response.bodyAsText())
    }

    /** The headphones' buy box: the price and the struck-through one. */
    private suspend fun HttpClient.productPrice(token: String?): Pair<String, String?> =
        page("/ui/p/$SONY", token).only<ProductDetails>().let { it.price to it.oldPrice }

    /** The headphones' card in their category. */
    private suspend fun HttpClient.card(token: String?): ProductCard =
        page("/ui/c/electronics/audio/headphones?brand=Sony", token)
            .all()
            .filterIsInstance<ProductCard>()
            .single { it.productId == SONY }

    private suspend fun HttpClient.putLine(
        token: String,
        skuId: String,
    ) = put(CartPaths.line(skuId)) {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(haulWireJson.encodeToString(LineChange.serializer(), LineChange(quantity = 1)))
    }.assertRefresh()

    private suspend fun HttpClient.cartLine(
        token: String,
        skuId: String,
    ): CartLine =
        page(CartPaths.SCREEN, token)
            .all()
            .filterIsInstance<CartLine>()
            .single { it.skuId == skuId }

    private suspend fun HttpClient.cartTotal(token: String): String =
        page(CartPaths.SCREEN, token).only<OrderSummary>().total

    private suspend fun HttpClient.checkout(token: String): CheckoutSummary =
        page(CheckoutPaths.SCREEN, token).only<CheckoutSummary>()

    /** Sam's way to receive: a pickup point, since only Maya has an address. */
    private suspend fun HttpClient.pickUp(token: String) =
        put(CheckoutPaths.CHOICE) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(
                haulWireJson.encodeToString(
                    CheckoutChoice.serializer(),
                    CheckoutChoice(method = DeliveryMethod.PickupPoint, pointId = SampleCheckout.BEDFORD),
                ),
            )
        }.assertRefresh()

    private suspend fun HttpClient.place(
        token: String,
        key: String,
        summary: CheckoutSummary,
    ): HttpResponse =
        post(assertNotNull(summary.placeUrl, "the summary carries no placeUrl")) {
            bearerAuth(token)
            header(IDEMPOTENCY_KEY_HEADER, key)
            contentType(ContentType.Application.Json)
            setBody(haulWireJson.encodeToString(PlaceOrderRequest.serializer(), PlaceOrderRequest(summary.quote)))
        }

    /**
     * The early-access day, as each viewer draws it: Maya, a member, sees the sale's $349 under $449 on
     * the product page and on the card; a guest and Sam, who is not one, see $449 with nothing struck
     * through. Asked in that order on purpose — the member first — so a page or a price kept between
     * viewers would hand the guest Maya's; and no answer says a shared cache may keep it.
     */
    @Test
    fun `inside the early window a member sees the sale price and a non-member the regular one`() =
        store { _, _ ->
            assertEquals("$349" to "$449", productPrice(maya))
            assertEquals(listOf("$349", "$449", "−22%"), card(maya).let { listOf(it.price, it.oldPrice, it.badge) })

            assertEquals("$449" to null, productPrice(null), "a guest was drawn the member's price")
            assertEquals("$449" to null, productPrice(sam), "a non-member was drawn the member's price")
            assertEquals(listOf("$449", null, null), card(null).let { listOf(it.price, it.oldPrice, it.badge) })
            assertEquals(listOf("$449", null, null), card(sam).let { listOf(it.price, it.oldPrice, it.badge) })

            val answer = get("/ui/p/$SONY") { bearerAuth(maya) }
            val cacheControl = answer.headers[HttpHeaders.CacheControl].orEmpty()
            assertFalse("public" in cacheControl, "a member's page may be kept for others: «$cacheControl»")
        }

    /**
     * A member is charged the early price: Maya's seeded cart on the early-access day totals $512 in the
     * cart and at checkout, as on the canvas's day, and placement authorises $512.00 — the headphones at
     * $349 and the duvet set at $139, both the sale's — where a non-member would pay $652.
     */
    @Test
    fun `a member is charged the sale price on the early-access day`() =
        store { _, database ->
            assertEquals("$512", cartTotal(maya))
            val summary = checkout(maya)
            assertEquals("$512", summary.total)

            val response = place(maya, "maya-early", summary)
            assertEquals(HttpStatusCode.Accepted, response.status, response.bodyAsText())

            assertEquals(listOf(51_200), Ledger(database).authorisations().map { it.second })
        }

    /**
     * A non-member is charged the regular price on the early-access day: Sam's headphones and mugs are
     * $449 + $24 in the cart, at checkout and at placement.
     */
    @Test
    fun `a non-member is charged the regular price on the early-access day`() =
        store { _, database ->
            putLine(sam, SONY_SKU)
            putLine(sam, MUG_SKU)
            assertEquals("$449", cartLine(sam, SONY_SKU).price)
            assertEquals("$473", cartTotal(sam))
            pickUp(sam)
            val summary = checkout(sam)
            assertEquals("$473", summary.total)

            val response = place(sam, "sam-early", summary)
            assertEquals(HttpStatusCode.Accepted, response.status, response.bodyAsText())

            assertEquals(listOf(47_300), Ledger(database).authorisations().map { it.second })
        }

    /**
     * Sam starts the trial mid-checkout on the early-access day: the headphones drop to the member's $349
     * in his cart, marked as a changed price until he accepts it, so the page he had drawn before is
     * `409 cart_changed`; once accepted, his checkout and his order are at $349 + $24.
     */
    @Test
    fun `a trial started mid-checkout redraws the quote at the member price`() =
        store { _, database ->
            putLine(sam, SONY_SKU)
            putLine(sam, MUG_SKU)
            pickUp(sam)
            val stale = checkout(sam)
            assertEquals("$473", stale.total)

            assertEquals(HttpStatusCode.Created, post(MembershipPaths.TRIAL) { bearerAuth(sam) }.status)

            val changed = cartLine(sam, SONY_SKU)
            assertEquals("$349", changed.price)
            assertEquals("Price changed: now $349", changed.change)
            place(sam, "sam-stale", stale).assertError(HttpStatusCode.Conflict, ErrorCode.CartChanged)

            post(CartPaths.acknowledge(SONY_SKU)) { bearerAuth(sam) }.assertRefresh()
            val fresh = checkout(sam)
            assertEquals("$373", fresh.total)
            val response = place(sam, "sam-member", fresh)
            assertEquals(HttpStatusCode.Accepted, response.status, response.bodyAsText())

            assertEquals(listOf(37_300), Ledger(database).authorisations().map { it.second })
        }

    /**
     * From the sale's start, everybody: what a guest and Sam saw at $449 on the early-access day is $349
     * under $449 at the very instant the sale starts, and Sam's line put in at $449 is a changed price
     * that, accepted, is checked out and charged at $349.
     */
    @Test
    fun `from the start a non-member gets the sale price through to the order`() =
        store { clock, database ->
            putLine(sam, SONY_SKU)
            putLine(sam, MUG_SKU)
            pickUp(sam)
            assertEquals("$449" to null, productPrice(null))

            clock.at = AUTUMN_SALE_START
            assertEquals("$349" to "$449", productPrice(null))
            assertEquals("$349" to "$449", productPrice(sam))
            assertEquals("Price changed: now $349", cartLine(sam, SONY_SKU).change)
            post(CartPaths.acknowledge(SONY_SKU)) { bearerAuth(sam) }.assertRefresh()
            assertNull(cartLine(sam, SONY_SKU).change)
            assertEquals("$373", cartTotal(sam))
            val summary = checkout(sam)
            assertEquals("$373", summary.total)

            val response = place(sam, "sam-open", summary)
            assertEquals(HttpStatusCode.Accepted, response.status, response.bodyAsText())

            assertEquals(listOf(37_300), Ledger(database).authorisations().map { it.second })
        }

    private companion object {
        const val SONY = SampleCatalog.SONY_HEADPHONES
        const val SONY_SKU = "${SampleCatalog.SONY_HEADPHONES}-0"
        const val MUG_SKU = "${SampleCatalog.STONEWARE_MUG}-0"

        /** Noon on Monday, Oct 6, 2025: the sale is open to Plus members since midnight, to everybody at the next. */
        val EARLY: OffsetDateTime = OffsetDateTime.parse("2025-10-06T12:00:00-04:00")

        /** The Autumn mega sale's start (research §6), the canvas's day at midnight. */
        val AUTUMN_SALE_START: OffsetDateTime = OffsetDateTime.parse("2025-10-07T00:00:00-04:00")

        init {
            check(AUTUMN_SALE_START.isBefore(CatalogSeed.NOW))
        }
    }
}
