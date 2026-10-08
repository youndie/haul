package io.github.youndie.haul.feature.recommendations

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.catalog.data.ExposedCatalogRepository
import io.github.youndie.haul.feature.identity.GUEST_HEADER
import io.github.youndie.haul.feature.recommendations.screen.PickedSection
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.testing.SeededDatabase
import io.github.youndie.haul.testing.ShildikHarness
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.guest
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.SectionHeader
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotComponent
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * feature-recommendations' scenarios over HTTP, against a running shildik: the views are the product pages
 * a customer opened, and the block is in the home page's tree.
 */
class RecommendationsRoutesTest {
    private val catalog = ExposedCatalogRepository(Databases.connect(SeededDatabase.dataSource))
    private val headphones =
        runBlocking { catalog.listedIn(setOf("headphones")) }.map { it.product.id }.sorted()
    private val categoryOf: Map<String, String> =
        runBlocking { catalog.listedIn(setOf("headphones", "duvet-covers", "mugs")) }
            .associate { it.product.id to it.product.categorySlug }

    private suspend fun HttpClient.page(
        path: String,
        auth: HttpRequestBuilder.() -> Unit,
    ): KompotComponent {
        val response = get(path, auth)
        assertEquals(HttpStatusCode.OK, response.status, "$path: ${response.bodyAsText()}")
        return haulWireJson.decodeKompotComponent(response.bodyAsText())
    }

    private fun KompotComponent.picked(): Pair<SectionHeader, ProductGrid>? {
        val header = all().filterIsInstance<SectionHeader>().singleOrNull { it.id == PickedSection.TITLE_ID }
        val grid = all().filterIsInstance<ProductGrid>().singleOrNull { it.id == PickedSection.GRID_ID }
        assertEquals(header == null, grid == null, "half a block: $header, $grid")
        return header?.let { it to grid!! }
    }

    /** The scenario «From views»: three headphones viewed, at most two picked, none of the three. */
    @Test
    fun `a customer who viewed three headphones is picked at most two and none of them`() {
        val token = ShildikHarness.accessToken(ShildikHarness.person("Vera Viewer"))
        haulTest(signIn = ShildikHarness.signIn) {
            val viewed = headphones.take(3)
            viewed.forEach { page("/ui/p/$it") { bearerAuth(token) } }

            val (header, grid) = assertNotNull(page("/ui/home") { bearerAuth(token) }.picked(), "no «Picked for you»")
            assertEquals("Picked for you", header.title)
            assertEquals("Based on your recent views", header.subtitle)
            val picked = grid.cards.map { it.productId }
            assertEquals(6, picked.size)
            assertTrue(picked.none { it in viewed }, "a viewed headphone was picked back: $picked")
            assertEquals(2, picked.count { it in headphones }, "not two headphones: $picked")
        }
    }

    /**
     * A view is a product, not a fetch: the same product page opened on every tab is one view, so it is still
     * the popular row — «Popular right now» — and that row leaves the product out.
     */
    @Test
    fun `fewer than three products viewed is the popular row`() {
        val token = ShildikHarness.accessToken(ShildikHarness.person("Pia Popular"))
        haulTest(signIn = ShildikHarness.signIn) {
            val product = headphones.first()
            listOf("", "?tab=specifications", "?tab=reviews", "?tab=questions").forEach {
                page("/ui/p/$product$it") { bearerAuth(token) }
            }
            page("/ui/p/${headphones[1]}") { bearerAuth(token) }

            val (header, grid) = assertNotNull(page("/ui/home") { bearerAuth(token) }.picked())
            assertEquals("Popular right now", header.subtitle)
            assertEquals(6, grid.cards.size)
            assertTrue(grid.cards.none { it.productId == product || it.productId == headphones[1] })

            // Positive control: a third product makes it the views' row.
            page("/ui/p/${headphones[2]}") { bearerAuth(token) }
            assertEquals("Based on your recent views", page("/ui/home") { bearerAuth(token) }.picked()?.first?.subtitle)
        }
    }

    /** The scenario «Guest»: no bearer, no block — with a guest id or with none. */
    @Test
    fun `a guest's home has no picked block`() =
        haulTest(signIn = ShildikHarness.signIn) {
            val guest = guest()
            headphones.take(3).forEach { page("/ui/p/$it") { header(GUEST_HEADER, guest) } }
            listOf<HttpRequestBuilder.() -> Unit>({ header(GUEST_HEADER, guest) }, {}).forEach { auth ->
                val home = page("/ui/home", auth)
                assertEquals(null, home.picked(), "a guest was picked products")
                // Positive control: this is the home page, with its deals.
                assertTrue(home.all().filterIsInstance<ProductGrid>().any { it.id == "deals" })
            }
        }

    /** The seed's Maya (`Home_Content`): three headphones and her cart's two other products viewed. */
    @Test
    fun `the seeded Maya is picked two headphones two duvet covers and two mugs`() {
        val token = ShildikHarness.accessToken(ShildikHarness.person("Maya Kowalski", SampleCustomers.MAYA))
        seededFreshDatabase().use { database ->
            haulTest(database, signIn = ShildikHarness.signIn) {
                val (header, grid) = assertNotNull(page("/ui/home") { bearerAuth(token) }.picked())
                assertEquals("Based on your recent views", header.subtitle)
                assertEquals(
                    listOf("headphones", "headphones", "duvet-covers", "duvet-covers", "mugs", "mugs"),
                    grid.cards.map { categoryOf[it.productId] },
                )
            }
        }
    }
}
