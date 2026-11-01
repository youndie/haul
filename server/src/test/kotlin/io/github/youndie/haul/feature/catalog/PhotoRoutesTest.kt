package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.PictureVersions
import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.account.AccountPaths
import io.github.youndie.haul.feature.cart.CartPaths
import io.github.youndie.haul.feature.catalog.screen.productLink
import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.checkout.CheckoutPaths
import io.github.youndie.haul.feature.order.OrderPaths
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.seed.SeedArt
import io.github.youndie.haul.seed.SeedPhotos
import io.github.youndie.haul.seed.Seeder
import io.github.youndie.haul.testing.FulfilmentWorld
import io.github.youndie.haul.testing.PostgresHarness
import io.github.youndie.haul.testing.SeaweedHarness
import io.github.youndie.haul.testing.ShildikHarness
import io.github.youndie.haul.testing.TestClock
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.liveTree
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.AccountBody
import io.github.youndie.haul.ui.CampaignHero
import io.github.youndie.haul.ui.CartLine
import io.github.youndie.haul.ui.CategoryTile
import io.github.youndie.haul.ui.CheckoutSummary
import io.github.youndie.haul.ui.OrderBody
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductDetails
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.ProductQuestions
import io.github.youndie.haul.ui.ProductReviews
import io.github.youndie.haul.ui.PromoBanner
import io.github.youndie.haul.ui.QuestionForm
import io.github.youndie.haul.ui.ReturnForm
import io.github.youndie.haul.ui.ReviewForm
import io.github.youndie.haul.ui.SearchSuggestPanel
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.standard.PresentAction
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
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * B-30's acceptance on the server: a product with a stored photo carries its address on its card and
 * on its page, and the address serves the stored bytes. A product without one carries its picture (B-78), the
 * seed's illustration of its category, which the server draws and serves itself.
 */
class PhotoRoutesTest {
    private val sonora = SampleCatalog.SONORA_HEADPHONES
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
    fun `a product with a stored photo carries its address and one without carries its picture`() =
        haulTest(Photographed.dataSource, Photographed.store) {
            val details = tree("/ui/p/$sonora").only<ProductDetails>()
            val url = assertNotNull(details.photo, "the product page has no photo")
            assertTrue(url.startsWith("/images/products/$sonora/"), url)

            val deals =
                tree("/ui/home")
                    .all()
                    .filterIsInstance<ProductGrid>()
                    .single { it.id == "deals" }
                    .cards
            assertEquals(url, deals.single { it.productId == sonora }.image, "the card and the page disagree")
            val generated = deals.filter { it.productId !in sampleIds }
            // Positive control: the row does hold products without photos, so their pictures were looked for.
            assertTrue(generated.isNotEmpty(), "no generated product among the deals")
            generated.forEach {
                assertTrue(it.image.orEmpty().startsWith("/images/art/p_"), "${it.productId}: ${it.image}")
            }
        }

    /**
     * B-108: a cart line, checkout's «Your order», an order's line and the suggest panel's «Top products» drew a
     * blank tile where the card and the product page draw the stored photo — their wire had no address. Each carries
     * the card's now, and so does a line of the order's return dialog (B-136). Maya's seeded cart holds the
     * headphones; the order is that cart, placed and delivered.
     */
    @Test
    fun `the cart, checkout, an order and the suggest panel carry the photo the card carries`() =
        seededFreshDatabase().use { database ->
            check(runBlocking { SeedPhotos.attach(Databases.connect(database), Photographed.store) } > 0)
            val maya = ShildikHarness.accessToken(ShildikHarness.person("Maya Kowalski", SampleCustomers.MAYA))
            var url: String? = null
            haulTest(database, Photographed.store, signIn = ShildikHarness.signIn) {
                url = assertNotNull(tree("/ui/p/$sonora").only<ProductDetails>().photo)
                val lines = tree(CartPaths.SCREEN, maya).all().filterIsInstance<CartLine>()
                val line =
                    assertNotNull(
                        lines.firstOrNull { it.productId == sonora },
                        "the cart: ${lines.map { it.productId }}",
                    )
                assertEquals(url, line.image, "the cart line")
                val items = tree(CheckoutPaths.SCREEN, maya).only<CheckoutSummary>().items
                val item = assertNotNull(items.firstOrNull { it.action == productLink(sonora) }, "checkout: $items")
                assertEquals(url, item.image, "checkout's line")
                // Each suggested product carries its own page's photo; the sample ones have one.
                val panel = tree("/ui/search/suggest?q=sonora") as SearchSuggestPanel
                panel.products.forEach {
                    assertEquals(tree("/ui/p/${it.productId}").only<ProductDetails>().photo, it.image, it.productId)
                }
                assertTrue(
                    panel.products.any { it.image.orEmpty().startsWith("/images/products/") },
                    "no suggested product has a photo",
                )
            }
            // Delivered, so the order also offers «Return items», whose lines drew the bare tone tile (B-136).
            val clock = TestClock()
            val orderId =
                FulfilmentWorld(database, clock).use { world ->
                    world.place(CheckoutChoice(slotId = "2025-10-08T15")).also {
                        world.advance(Duration.ZERO)
                        world.advance(72.hours)
                    }
                }
            haulTest(database, Photographed.store, signIn = ShildikHarness.signIn, sagaClock = clock) {
                val response = get(OrderPaths.SCREEN.replace("{id}", orderId)) { bearerAuth(maya) }
                val body = liveTree(response.bodyAsText(), "order:$orderId").only<OrderBody>()
                val items = body.shipments.flatMap { it.items }
                val item = assertNotNull(items.firstOrNull { it.action == productLink(sonora) }, "the order: $items")
                assertEquals(url, item.image, "the order's line")
                val form = assertNotNull((body.summary.returnAction as? PresentAction)?.content as? ReturnForm)
                val returned = form.lines.firstOrNull { it.title == item.title }
                assertNotNull(returned, "returns: ${form.lines}")
                assertEquals(url, returned.image, "the return dialog's line")
                // B-149: the account's history draws the order's lines with the photos its page draws.
                val history = tree(AccountPaths.ORDERS, maya).only<AccountBody>().history
                val row = assertNotNull(history?.rows?.firstOrNull { it.id == orderId }, "the history: $history")
                assertEquals(items.map { it.image.orEmpty() }.sorted(), row.images.map { it.orEmpty() }.sorted())
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
            val url = assertNotNull(tree("/ui/p/$sonora").only<ProductDetails>().photo)
            val response = get(url)
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Image.SVG, response.contentType()?.withoutParameters())
            assertEquals("public, max-age=31536000, immutable", response.headers[HttpHeaders.CacheControl])
            // A stored SVG is a document of the store's origin as a picture is: it may run and load nothing.
            assertEquals("default-src 'none'", response.headers["Content-Security-Policy"])
            val product = SampleCatalog.products.single { it.id == sonora }
            assertContentEquals(SeedPhotos.draw(product).encodeToByteArray(), response.bodyAsBytes())
            // B-181: its dark version beside it, as stored, served the same way.
            val dark = get(assertNotNull(PictureVersions.dark(url), url))
            assertEquals(HttpStatusCode.OK, dark.status)
            assertEquals(ContentType.Image.SVG, dark.contentType()?.withoutParameters())
            assertEquals("public, max-age=31536000, immutable", dark.headers[HttpHeaders.CacheControl])
            assertEquals("default-src 'none'", dark.headers["Content-Security-Policy"])
            assertContentEquals(SeedPhotos.draw(product, dark = true).encodeToByteArray(), dark.bodyAsBytes())
        }

    /**
     * B-181: a dark page asks for every SVG's dark version. A stored SVG with none of its own — one the seed did not
     * draw — is answered with itself rather than a 404 that would leave the tile; a photo that is not an SVG has no
     * dark address, and a dark address under nothing stored is still a 404.
     */
    @Test
    fun `a stored SVG with no dark version of its own answers its dark address with itself`() =
        haulTest(Photographed.dataSource, Photographed.store) {
            val key = "products/somebody/upload-1.svg"
            val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"/>"""
            Photographed.store.put(key, svg.encodeToByteArray(), "image/svg+xml")
            val dark = assertNotNull(PictureVersions.dark("/images/$key"))
            assertEquals(svg, get(dark).bodyAsText())
            assertEquals(null, PictureVersions.dark("/images/products/somebody/upload-2.png"))
            assertEquals(HttpStatusCode.NotFound, get("/images/products/nobody/0000000000000000.dark.svg").status)
        }

    /**
     * B-149: the sample products' stored photos were one tinted box for all fifteen — the headphones, the earbuds,
     * the mug and the duvet alike. Each is its own picture now: what the server draws for its page without object
     * storage, its category's object in its first SKU's colour.
     */
    @Test
    fun `a sample product's stored photo is its own picture`() {
        val ids = SampleCatalog.products.map { it.id }
        val stored = mutableMapOf<String, String>()
        haulTest(Photographed.dataSource, Photographed.store) {
            ids.forEach { id ->
                val url = assertNotNull(tree("/ui/p/$id").only<ProductDetails>().photo, id)
                assertTrue(url.startsWith("/images/products/$id/"), "$id: $url")
                stored[id] = get(url).bodyAsText()
            }
        }
        haulTest(Photographed.dataSource, photoStore = null) {
            ids.forEach { id ->
                val url = assertNotNull(tree("/ui/p/$id").only<ProductDetails>().photo, id)
                assertTrue(url.startsWith("/images/art/"), "$id: $url")
                assertEquals(get(url).bodyAsText(), stored[id], "$id: the stored photo is not its picture")
            }
        }
        // Positive control: the pictures compared are not one picture either.
        val mug = stored.getValue(SampleCatalog.STONEWARE_MUG)
        val duvet = stored.getValue(SampleCatalog.DUVET_COVER)
        assertEquals(3, setOf(stored.getValue(sonora), mug, duvet).size, "the headphones, the mug and the duvet alike")
    }

    /**
     * B-149: the review and question dialogs drew their product as a plain tone and a review's photos were tone
     * tiles — their wire named no picture. The dialogs carry the photo the product's page draws, and each of a
     * review's photos a picture of the product of its own, served as the seed's pictures are.
     */
    @Test
    fun `the review and question dialogs and a review's photos carry pictures`() {
        val maya = ShildikHarness.accessToken(ShildikHarness.person("Maya Kowalski", SampleCustomers.MAYA))
        haulTest(Photographed.dataSource, Photographed.store, signIn = ShildikHarness.signIn) {
            val photo = assertNotNull(tree("/ui/p/$sonora").only<ProductDetails>().photo)
            val reviews = tree("/ui/p/$sonora?tab=reviews", maya).only<ProductReviews>()
            val review = assertNotNull((reviews.action as? PresentAction)?.content as? ReviewForm, "${reviews.action}")
            assertEquals(photo, review.product.image, "the review dialog's product")
            val questions = tree("/ui/p/$sonora?tab=questions", maya).only<ProductQuestions>()
            val question = assertNotNull((questions.action as? PresentAction)?.content as? QuestionForm)
            assertEquals(photo, question.product.image, "the question dialog's product")

            val shown = reviews.reviews.filter { it.photos.isNotEmpty() }
            // Positive control: a review with photos is listed (Daniel R.'s two, research §6).
            assertTrue(shown.isNotEmpty(), "no review with photos")
            shown.forEach { listed ->
                assertEquals(listed.photos.size, listed.images.size, "${listed.author}: ${listed.images}")
                assertEquals(listed.images.size, listed.images.toSet().size, "${listed.author}: one picture twice")
                listed.images.forEach { url ->
                    assertTrue(url.startsWith("/images/art/p_headphones_"), url)
                    val response = get(url)
                    assertEquals(HttpStatusCode.OK, response.status, url)
                    assertEquals(SeedArt.svg(url.substringAfterLast('/')), response.bodyAsText(), url)
                }
            }
        }
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
     * Object storage is optional: a server without it — the stand — shows no stored photo, even for a product whose
     * row still has a key — an address that cannot load would only delay the placeholder — and every product carries
     * its picture instead (B-78), which needs no storage: its address answers the SVG.
     */
    @Test
    fun `without object storage every product carries its picture`() =
        haulTest(Photographed.dataSource, photoStore = null) {
            val photo = assertNotNull(tree("/ui/p/$sonora").only<ProductDetails>().photo)
            assertTrue(photo.startsWith("/images/art/p_headphones_"), photo)
            val cards: List<ProductCard> = tree("/ui/home").all().filterIsInstance<ProductGrid>().flatMap { it.cards }
            assertTrue(cards.any { it.productId == sonora }, "the sample product is not on the home page")
            cards.forEach {
                assertTrue(
                    it.image.orEmpty().startsWith("/images/art/p_"),
                    "${it.productId}: ${it.image}",
                )
            }
            val response = get(photo)
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().startsWith("<svg "), "not a picture")
        }

    /**
     * B-78: a picture is drawn by the server and served from its own origin as SVG, for good — its name carries its
     * content's hash — and as a document that may run and load nothing, should it be opened on its own.
     */
    @Test
    fun `a picture address serves the drawn SVG for good`() =
        haulTest(Photographed.dataSource, photoStore = null) {
            val home = tree("/ui/home").all()
            val tile = home.filterIsInstance<CategoryTile>().first()
            val hero = assertNotNull(home.filterIsInstance<CampaignHero>().single().image, "the hero has no picture")
            val banners = home.filterIsInstance<PromoBanner>()
            assertEquals(
                listOf(true, false),
                banners.map { it.image != null },
                "Tech week's banner has a picture, Haul Plus's has no place for one (B-172): ${banners.map {
                    it.id
                }}",
            )
            for (url in listOfNotNull(tile.image, hero, banners.first().image)) {
                val response = get(url)
                assertEquals(HttpStatusCode.OK, response.status, url)
                assertEquals(ContentType.Image.SVG, response.contentType()?.withoutParameters(), url)
                assertEquals("public, max-age=31536000, immutable", response.headers[HttpHeaders.CacheControl])
                assertEquals("nosniff", response.headers["X-Content-Type-Options"])
                assertEquals("default-src 'none'", response.headers["Content-Security-Policy"])
                val name = url.substringAfterLast('/')
                assertEquals(SeedArt.svg(name), response.bodyAsText(), url)
                // B-181: and its dark version beside it, drawn and served the same way.
                val dark = get(assertNotNull(PictureVersions.dark(url), url))
                assertEquals(HttpStatusCode.OK, dark.status, url)
                assertEquals("public, max-age=31536000, immutable", dark.headers[HttpHeaders.CacheControl])
                assertEquals("default-src 'none'", dark.headers["Content-Security-Policy"])
                assertEquals(SeedArt.svg(PictureVersions.dark(name)!!), dark.bodyAsText(), url)
            }
        }

    /** A name the seed draws no picture for — a changed hash, an unknown object, a path out — is 404, as a photo is. */
    @Test
    fun `a picture that is not the seed's is 404`() =
        haulTest(Photographed.dataSource, photoStore = null) {
            val url = assertNotNull(tree("/ui/p/$sonora").only<ProductDetails>().photo)
            assertEquals(HttpStatusCode.OK, get(url).status, "the control picture is not served")
            val stale = url.substringBeforeLast('.').substringBeforeLast('.') + ".0000000000.svg"
            assertEquals(HttpStatusCode.NotFound, get(stale).status)
            assertEquals(
                HttpStatusCode.NotFound,
                get(PictureVersions.dark(stale)!!).status,
                "a stale name's dark version",
            )
            assertEquals(HttpStatusCode.NotFound, get("/images/art/p_nothing_0_plain_e6e4ff.0000000000.svg").status)
            assertEquals(HttpStatusCode.NotFound, get("/images/art/../products/nobody/0000000000000000.png").status)
        }
}
