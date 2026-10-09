package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.seed.SeedPhotos
import io.github.youndie.haul.seed.Seeder
import io.github.youndie.haul.testing.PostgresHarness
import io.github.youndie.haul.testing.SeaweedHarness
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductDetails
import io.github.youndie.haul.ui.ProductGrid
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
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
                check(Seeder.seedIfEmpty(database, CatalogSeed.generate(CatalogSeed.CANVAS_DAY)))
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
