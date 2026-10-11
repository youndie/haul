package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.cart.CartPaths
import io.github.youndie.haul.feature.catalog.screen.productLink
import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.checkout.CheckoutPaths
import io.github.youndie.haul.feature.order.OrderPaths
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.seed.SeedPhotos
import io.github.youndie.haul.seed.Seeder
import io.github.youndie.haul.testing.FulfilmentWorld
import io.github.youndie.haul.testing.PostgresHarness
import io.github.youndie.haul.testing.SeaweedHarness
import io.github.youndie.haul.testing.ShildikHarness
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.liveTree
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.CartLine
import io.github.youndie.haul.ui.CheckoutSummary
import io.github.youndie.haul.ui.OrderBody
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductDetails
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.SearchSuggestPanel
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotComponent
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B-30's acceptance on the server: a product with a stored photo carries its address on its card and
 * on its page, a product without one carries none — the client's cue to draw the placeholder tile — and
 * the address serves the stored bytes.
 */
class PhotoRoutesTest {
    private val sony = SampleCatalog.SONY_HEADPHONES
    private val sampleIds = SampleCatalog.products.map { it.id }.toSet()

    /** A seeded database whose sample products have photos in a SeaweedFS bucket, as `main` seeds them. */
    private object Photographed {
        val store = SeaweedHarness.store()
        val dataSource: DataSource by lazy {
            PostgresHarness.freshDatabase().also {
                val database = Databases.connect(it)
                check(
                    Seeder.seedIfEmpty(
                        database,
                        CatalogSeed.generate(CatalogSeed.CANVAS_DAY),
                        generatedReviews = false,
                    ),
                )
                val stored = runBlocking { SeedPhotos.attach(database, store) }
                check(stored == SampleCatalog.products.size) { "stored $stored photos" }
            }
        }
    }

    @Test
    fun `a product with a stored photo carries its address and one without carries none`() =
        haulTest(Photographed.dataSource, Photographed.store) {
            val details = tree("/ui/p/$sony").only<ProductDetails>()
            val url = assertNotNull(details.photo, "the product page has no photo")
            assertTrue(url.startsWith("/images/products/$sony/"), url)

            val deals =
                tree("/ui/home")
                    .all()
                    .filterIsInstance<ProductGrid>()
                    .single { it.id == "deals" }
                    .cards
            assertEquals(url, deals.single { it.productId == sony }.image, "the card and the page disagree")
            val generated = deals.filter { it.productId !in sampleIds }
            // Positive control: the row does hold products without photos, so «none» was looked for.
            assertTrue(generated.isNotEmpty(), "no generated product among the deals")
            assertTrue(generated.all { it.image == null }, "a product without a photo carries an address")
        }

    /**
     * B-108: a cart line, checkout's «Your order», an order's line and the suggest panel's «Top products» drew a
     * blank tile where the card and the product page draw the stored photo — their wire had no address. Each carries
     * the card's now. Maya's seeded cart holds the headphones; the order is that cart, placed.
     */
    @Test
    fun `the cart, checkout, an order and the suggest panel carry the photo the card carries`() =
        seededFreshDatabase().use { database ->
            check(runBlocking { SeedPhotos.attach(Databases.connect(database), Photographed.store) } > 0)
            val maya = ShildikHarness.accessToken(ShildikHarness.person("Maya Kowalski", SampleCustomers.MAYA))
            var url: String? = null
            haulTest(database, Photographed.store, signIn = ShildikHarness.signIn) {
                url = assertNotNull(tree("/ui/p/$sony").only<ProductDetails>().photo)
                val lines = tree(CartPaths.SCREEN, maya).all().filterIsInstance<CartLine>()
                assertEquals(url, lines.single { it.productId == sony }.image, "the cart line")
                val items = tree(CheckoutPaths.SCREEN, maya).only<CheckoutSummary>().items
                assertEquals(url, items.single { it.action == productLink(sony) }.image, "checkout's line")
                val panel = tree("/ui/search/suggest?q=sony") as SearchSuggestPanel
                assertEquals(url, panel.products.single { it.productId == sony }.image, "the suggest panel")
            }
            val orderId = FulfilmentWorld(database).use { it.place(CheckoutChoice(slotId = "2025-10-08T15")) }
            haulTest(database, Photographed.store, signIn = ShildikHarness.signIn) {
                val response = get(OrderPaths.SCREEN.replace("{id}", orderId)) { bearerAuth(maya) }
                val body = liveTree(response.bodyAsText(), "order:$orderId").only<OrderBody>()
                val items = body.shipments.flatMap { it.items }
                assertEquals(url, items.single { it.action == productLink(sony) }.image, "the order's line")
            }
        }

    private suspend fun HttpClient.tree(
        path: String,
        token: String,
    ): KompotComponent {
        val response = get(path) { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return haulWireJson.decodeKompotComponent(response.bodyAsText())
    }

    @Test
    fun `the photo address serves the stored bytes for good`() =
        haulTest(Photographed.dataSource, Photographed.store) {
            val url = assertNotNull(tree("/ui/p/$sony").only<ProductDetails>().photo)
            val response = get(url)
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Image.PNG, response.contentType()?.withoutParameters())
            assertEquals("public, max-age=31536000, immutable", response.headers[HttpHeaders.CacheControl])
            val tone = SampleCatalog.products.single { it.id == sony }.tone
            assertContentEquals(SeedPhotos.draw(tone), response.bodyAsBytes())
        }

    /**
     * The route is public and the bucket is not: an object outside the product photos is not served,
     * though it is there — the store is asked for nothing but `products/`.
     */
    @Test
    fun `a photo that is not there or not a product photo is 404`() =
        haulTest(Photographed.dataSource, Photographed.store) {
            Photographed.store.put("elsewhere/secret.txt", "secret".toByteArray(), "text/plain")
            assertNotNull(Photographed.store.get("elsewhere/secret.txt"), "the control object was not stored")

            assertEquals(HttpStatusCode.NotFound, get("/images/elsewhere/secret.txt").status)
            assertEquals(HttpStatusCode.NotFound, get("/images/products/../elsewhere/secret.txt").status)
            assertEquals(HttpStatusCode.NotFound, get("/images/products/nobody/0000000000000000.png").status)
        }

    /**
     * Object storage is optional: a server without it shows no photo, even for a product whose row
     * still has a key — an address that cannot load would only delay the placeholder.
     */
    @Test
    fun `without object storage no product carries a photo`() =
        haulTest(Photographed.dataSource, photoStore = null) {
            assertNull(tree("/ui/p/$sony").only<ProductDetails>().photo)
            val cards: List<ProductCard> = tree("/ui/home").all().filterIsInstance<ProductGrid>().flatMap { it.cards }
            assertTrue(cards.any { it.productId == sony }, "the sample product is not on the home page")
            assertTrue(cards.all { it.image == null })
        }
}
