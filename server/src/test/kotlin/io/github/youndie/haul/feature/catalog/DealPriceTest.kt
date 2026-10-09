package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.feature.cart.CartPaths
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.cart.LineCommand
import io.github.youndie.haul.feature.cart.domain.Totals
import io.github.youndie.haul.feature.catalog.domain.money
import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.checkout.CheckoutPaths
import io.github.youndie.haul.feature.checkout.DeliveryMethod
import io.github.youndie.haul.feature.checkout.IDEMPOTENCY_KEY_HEADER
import io.github.youndie.haul.feature.checkout.PlaceOrderRequest
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.seed.SampleCheckout
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.seed.SeedDeal
import io.github.youndie.haul.testing.Ledger
import io.github.youndie.haul.testing.ShildikHarness
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.assertRefresh
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.ui.CartLine
import io.github.youndie.haul.ui.CheckoutSummary
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductDetails
import io.github.youndie.haul.ui.ProductGrid
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
import kotlin.test.assertTrue

/**
 * A deal's price is what the cart charges (B-57), over HTTP against shildik and a seeded PostgreSQL of each
 * test's own. A deal is a price of its SKU from its start to its end; while it is live the card, the product
 * page, the cart, the quote and placement all read it through the one pricing rule, and once it has ended
 * the SKU sells at its campaign or regular price again.
 *
 * Before B-57 the home page drew the five generated deals at 70 % of the price the card's «+» then put into
 * the cart — the card's price came from `deals`, the cart's from `skus` — so a customer was charged more than
 * the card offered. Sam, who is not a Plus member, buys: delivery and points are the plain ones.
 */
class DealPriceTest {
    private val sam by lazy { ShildikHarness.accessToken(ShildikHarness.person("Sam Ortiz", SampleCustomers.SAM)) }
    private val seed = CatalogSeed.generate(CatalogSeed.CANVAS_DAY)

    /** The store's «now», which a test moves past the deals' end; the canvas's by default. */
    private class MovingClock(
        var at: OffsetDateTime,
    ) : StoreClock {
        override fun now(): ZonedDateTime = at.toZonedDateTime()
    }

    private fun store(block: suspend HttpClient.(clock: MovingClock, database: DataSource) -> Unit) =
        seededFreshDatabase().use { database ->
            val clock = MovingClock(CatalogSeed.NOW)
            haulTest(database, signIn = ShildikHarness.signIn, clock = clock) { block(clock, database) }
        }

    private suspend fun HttpClient.page(path: String): KompotComponent {
        val response = get(path) { bearerAuth(sam) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return haulWireJson.decodeKompotComponent(response.bodyAsText())
    }

    /** The home page's deal cards; none when no deal is live and the section is not drawn (B-58). */
    private suspend fun HttpClient.dealCards(): List<ProductCard> =
        page("/ui/home")
            .all()
            .filterIsInstance<ProductGrid>()
            .singleOrNull { it.id == "deals" }
            ?.cards
            .orEmpty()

    private suspend fun HttpClient.dealCard(deal: SeedDeal): ProductCard =
        dealCards().single { it.add?.url == CartPaths.line(deal.skuId) }

    /** The product page of [skuId]'s product with that SKU chosen: its price and the struck-through one. */
    private suspend fun HttpClient.productPrice(skuId: String): Pair<String, String?> =
        page(
            "/ui/p/${skuId.substringBeforeLast('-')}?sku=$skuId",
        ).only<ProductDetails>().let { it.price to it.oldPrice }

    /** What a press on a card's «+» sends. */
    private suspend fun HttpClient.press(add: LineCommand) =
        put(add.url) {
            bearerAuth(sam)
            contentType(ContentType.Application.Json)
            setBody(haulWireJson.encodeToString(LineChange.serializer(), add.change))
        }.assertRefresh()

    private suspend fun HttpClient.cartLine(skuId: String): CartLine =
        page(CartPaths.SCREEN).all().filterIsInstance<CartLine>().single { it.skuId == skuId }

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

    private suspend fun HttpClient.checkout(): CheckoutSummary = page(CheckoutPaths.SCREEN).only<CheckoutSummary>()

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

    /** Checks out what is in Sam's cart and returns the amounts authorised. */
    private suspend fun HttpClient.checkOut(
        key: String,
        database: DataSource,
    ): List<Int> {
        pickUp()
        val response = place(key, checkout())
        assertEquals(HttpStatusCode.Accepted, response.status, response.bodyAsText())
        return Ledger(database).authorisations().map { it.second }
    }

    /**
     * A generated deal from its card to the authorisation: the card's price is the product page's with that
     * SKU chosen, the cart line's, the checkout's total and the amount placement authorises — the deal's, over
     * the price it beats, as the canvas draws the card.
     */
    @Test
    fun `a generated deal is charged the price its card shows`() =
        store { _, database ->
            val deal = generatedDeal
            val card = dealCard(deal)
            assertEquals(money(deal.priceCents), card.price, "the card is not at the deal's price")
            assertEquals(money(skuOf(deal).priceCents), card.oldPrice, "the card strikes through another price")
            assertEquals(
                card.price to card.oldPrice,
                productPrice(deal.skuId),
                "the product page and the card disagree",
            )

            press(assertNotNull(card.add, "the deal offers no «+»"))
            assertEquals(card.price, cartLine(deal.skuId).price, "the cart charges another price than the card")
            assertEquals(card.price, checkout().total, "the checkout's total is not the card's price")
            assertEquals(listOf(deal.priceCents), checkOut("sam-deal", database), "placement charged another price")
        }

    /**
     * Once the deals of the canvas's day have ended, at midnight, the card is gone, and the line Sam put into
     * his cart at the deal's price is the regular price again — marked as changed, as any price is — which
     * the checkout and placement charge.
     */
    @Test
    fun `after its window the deal card is gone and the cart charges the regular price`() =
        store { clock, database ->
            val deal = generatedDeal
            press(assertNotNull(dealCard(deal).add))

            clock.at = CatalogSeed.DEALS_END
            assertTrue(dealCards().none { it.add?.url == CartPaths.line(deal.skuId) }, "an ended deal is still drawn")
            val regular = money(skuOf(deal).priceCents)
            assertEquals(regular to null, productPrice(deal.skuId), "an ended deal still prices the product page")
            assertEquals("Price changed: now $regular", cartLine(deal.skuId).change)
            post(CartPaths.acknowledge(deal.skuId)) { bearerAuth(sam) }.assertRefresh()
            assertEquals(regular, checkout().total)
            assertEquals(listOf(skuOf(deal).priceCents), checkOut("sam-after", database))
        }

    /**
     * The Sony deal is the headphones at the Autumn mega sale's own $349: its card stays $349 under $449,
     * −22 %, and is charged $349; when the deal ends the sale still sells the headphones at $349.
     */
    @Test
    fun `the Sony deal is unchanged`() =
        store { clock, database ->
            val sony = dealCard(SampleCatalog.SONY_DEAL)
            assertEquals(listOf("$349", "$449", "−22%"), listOf(sony.price, sony.oldPrice, sony.badge))
            assertEquals("$349" to "$449", productPrice(SampleCatalog.SONY_DEAL.skuId))
            press(assertNotNull(sony.add))
            assertEquals(listOf(34_900), checkOut("sam-sony", database))

            clock.at = CatalogSeed.DEALS_END
            assertTrue(dealCards().isEmpty(), "a deal of the canvas's day is drawn the day after")
            assertEquals("$349" to "$449", productPrice(SampleCatalog.SONY_DEAL.skuId), "the sale ended with the deal")
        }

    /**
     * A deal and a campaign on one SKU sell at the lower of the two: the Sony deal moved to $399, above the
     * sale's $349, leaves the headphones at the sale's price under the regular one; moved to $299, below it,
     * makes them $299 under the sale's $349 — on the card, the product page and in the cart.
     */
    @Test
    fun `a deal and a campaign on one SKU take the lower price`() =
        store { _, database ->
            val skuId = SampleCatalog.SONY_DEAL.skuId
            database.sql("UPDATE deals SET price_cents = 39900 WHERE id = '${SampleCatalog.SONY_DEAL.id}'")
            assertEquals("$349" to "$449", dealCard(SampleCatalog.SONY_DEAL).let { it.price to it.oldPrice })
            assertEquals("$349" to "$449", productPrice(skuId))

            database.sql("UPDATE deals SET price_cents = 29900 WHERE id = '${SampleCatalog.SONY_DEAL.id}'")
            val card = dealCard(SampleCatalog.SONY_DEAL)
            assertEquals("$299" to "$349", card.price to card.oldPrice)
            assertEquals("$299" to "$349", productPrice(skuId))
            press(assertNotNull(card.add))
            assertEquals("$299", cartLine(skuId).price)
            assertNull(cartLine(skuId).change)
            assertEquals(listOf(29_900), checkOut("sam-lower", database))
        }

    /**
     * The generated deal the tests buy: one whose SKU is in no campaign, so «regular» is one price on either
     * side of the window, and whose deal price is past free delivery, so the authorisation is the price alone.
     */
    private val generatedDeal: SeedDeal
        get() =
            assertNotNull(
                seed.deals.firstOrNull { deal ->
                    deal != SampleCatalog.SONY_DEAL &&
                        skuOf(deal).campaignSlug == null &&
                        deal.priceCents >= Totals.FREE_DELIVERY_FROM_CENTS
                },
                "no generated deal outside a campaign at ${money(Totals.FREE_DELIVERY_FROM_CENTS)} or more",
            )

    private fun skuOf(deal: SeedDeal) = seed.skus.single { it.id == deal.skuId }

    private fun DataSource.sql(statement: String) {
        connection.use { c ->
            c.createStatement().use { it.executeUpdate(statement) }
            c.commit()
        }
    }

    private companion object {
        init {
            check(CatalogSeed.NOW.isBefore(CatalogSeed.DEALS_END))
        }
    }
}
