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
import io.github.youndie.haul.haulWireJson
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
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A campaign's prices end at its end (B-58), over HTTP against shildik and a seeded PostgreSQL of each test's
 * own. The Autumn mega sale runs to Oct 15, 2025 at midnight; the headphones (Midnight Black, headphones only)
 * are $349 in it and $449 regularly, and their deal of Oct 7 is long over by then.
 *
 * Before B-58 only a campaign's opening was read: an ended sale went on selling at its prices forever, the
 * card, the product page, the cart and placement all at $349 under a struck-through $449.
 */
class CampaignEndTest {
    private val maya by lazy {
        ShildikHarness.accessToken(ShildikHarness.person("Maya Kowalski", SampleCustomers.MAYA))
    }
    private val sam by lazy { ShildikHarness.accessToken(ShildikHarness.person("Sam Ortiz", SampleCustomers.SAM)) }

    /** The store's «now», which a test moves across the sale's end. */
    private class MovingClock(
        var at: OffsetDateTime,
    ) : StoreClock {
        override fun now(): ZonedDateTime = at.toZonedDateTime()
    }

    private fun store(block: suspend HttpClient.(clock: MovingClock, database: DataSource) -> Unit) =
        seededFreshDatabase().use { database ->
            val clock = MovingClock(LAST_MINUTE)
            haulTest(database, signIn = ShildikHarness.signIn, clock = clock) { block(clock, database) }
        }

    private suspend fun HttpClient.page(
        path: String,
        token: String,
    ): KompotComponent {
        val response = get(path) { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return haulWireJson.decodeKompotComponent(response.bodyAsText())
    }

    /** The headphones' buy box: the price and the struck-through one. */
    private suspend fun HttpClient.productPrice(token: String): Pair<String, String?> =
        page("/ui/p/$SONY?sku=$SONY_SKU", token).only<ProductDetails>().let { it.price to it.oldPrice }

    /** The headphones' card in their category: its price, the struck-through one and the badge. */
    private suspend fun HttpClient.card(token: String): List<String?> =
        page("/ui/c/headphones?brand=Sony", token)
            .all()
            .filterIsInstance<ProductCard>()
            .single { it.productId == SONY }
            .let { listOf(it.price, it.oldPrice, it.badge) }

    private suspend fun HttpClient.putLine(skuId: String) =
        put(CartPaths.line(skuId)) {
            bearerAuth(sam)
            contentType(ContentType.Application.Json)
            setBody(haulWireJson.encodeToString(LineChange.serializer(), LineChange(quantity = 1)))
        }.assertRefresh()

    private suspend fun HttpClient.cartLine(skuId: String): CartLine =
        page(CartPaths.SCREEN, sam).all().filterIsInstance<CartLine>().single { it.skuId == skuId }

    private suspend fun HttpClient.cartTotal(): String = page(CartPaths.SCREEN, sam).only<OrderSummary>().total

    private suspend fun HttpClient.checkout(): CheckoutSummary = page(CheckoutPaths.SCREEN, sam).only<CheckoutSummary>()

    /** Sam's way to receive: a pickup point, since only Maya has an address. */
    private suspend fun HttpClient.pickUp() =
        put(CheckoutPaths.CHOICE) {
            bearerAuth(sam)
            contentType(ContentType.Application.Json)
            setBody(
                haulWireJson.encodeToString(
                    CheckoutChoice.serializer(),
                    CheckoutChoice(method = DeliveryMethod.PickupPoint, pointId = SampleCheckout.BEDFORD),
                ),
            )
        }.assertRefresh()

    private suspend fun HttpClient.place(
        key: String,
        summary: CheckoutSummary,
    ): HttpResponse =
        post(assertNotNull(summary.placeUrl, "the summary carries no placeUrl")) {
            bearerAuth(sam)
            header(IDEMPOTENCY_KEY_HEADER, key)
            contentType(ContentType.Application.Json)
            setBody(haulWireJson.encodeToString(PlaceOrderRequest.serializer(), PlaceOrderRequest(summary.quote)))
        }

    /**
     * The sale's last minute and its end, as Sam and Maya draw the headphones: $349 under $449, −22 %, on the
     * card and the product page up to the end; from it $449 with nothing struck through — for Maya too, whose
     * membership opened the sale a day early and keeps it no longer.
     */
    @Test
    fun `at the sale's end its prices are gone from the card and the product page`() =
        store { clock, _ ->
            assertEquals(listOf("$349", "$449", "−22%"), card(sam))
            assertEquals("$349" to "$449", productPrice(sam))
            assertEquals("$349" to "$449", productPrice(maya))

            clock.at = AUTUMN_END
            assertEquals(listOf("$449", null, null), card(sam), "the ended sale still prices the card")
            assertEquals("$449" to null, productPrice(sam), "the ended sale still prices the product page")
            assertEquals("$449" to null, productPrice(maya), "a member keeps the sale past its end")
        }

    /**
     * Sam put the headphones and the mugs into his cart at the sale's $349 + $24 in its last minute and drew
     * his checkout. At the end the line is a changed price, «now $449», so the quote he drew is
     * `409 cart_changed`; accepted, the cart and the checkout are $473 and placement authorises 47,300.
     */
    @Test
    fun `at the sale's end the cart the checkout and placement charge the regular price`() =
        store { clock, database ->
            putLine(SONY_SKU)
            putLine(MUG_SKU)
            pickUp()
            assertEquals("$349", cartLine(SONY_SKU).price)
            val stale = checkout()
            assertEquals("$373", stale.total)

            clock.at = AUTUMN_END
            val line = cartLine(SONY_SKU)
            assertEquals("$449", line.price, "the cart charges the ended sale's price")
            assertEquals("Price changed: now $449", line.change)
            place("sam-stale", stale).assertError(HttpStatusCode.Conflict, ErrorCode.CartChanged)

            post(CartPaths.acknowledge(SONY_SKU)) { bearerAuth(sam) }.assertRefresh()
            assertNull(cartLine(SONY_SKU).change)
            assertEquals("$473", cartTotal())
            val summary = checkout()
            assertEquals("$473", summary.total)
            val response = place("sam-after", summary)
            assertEquals(HttpStatusCode.Accepted, response.status, response.bodyAsText())

            assertEquals(listOf(47_300), Ledger(database).authorisations().map { it.second })
        }

    private companion object {
        const val SONY = SampleCatalog.SONY_HEADPHONES
        const val SONY_SKU = "${SampleCatalog.SONY_HEADPHONES}-0"
        const val MUG_SKU = "${SampleCatalog.STONEWARE_MUG}-0"

        /** The Autumn mega sale's end (research §6): Oct 15, 2025, New York's midnight. */
        val AUTUMN_END: OffsetDateTime =
            SampleCatalog.campaigns.single { it.slug == SampleCatalog.AUTUMN_MEGA_SALE }.endsAt

        /** The sale's last minute. */
        val LAST_MINUTE: OffsetDateTime = AUTUMN_END.minusMinutes(1)

        init {
            check(AUTUMN_END == OffsetDateTime.parse("2025-10-15T00:00:00-04:00"))
        }
    }
}
