package io.github.youndie.haul.feature.recommendations

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.catalog.data.ExposedCatalogRepository
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.feature.recommendations.data.ExposedPickSources
import io.github.youndie.haul.feature.recommendations.domain.PickedForYou
import io.github.youndie.haul.feature.recommendations.domain.ProductView
import io.github.youndie.haul.feature.recommendations.domain.ProductViews
import io.github.youndie.haul.feature.recommendations.domain.RecordView
import io.github.youndie.haul.feature.recommendations.screen.PickedSection
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.SeededDatabase
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.SectionHeader
import io.github.youndie.kompot.encodeKompotComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The block on the home page and the write on the product page, around the rule: who gets them, and what a
 * failure in either costs the page — nothing but the block.
 */
class PickedSectionTest {
    private val database = Databases.connect(SeededDatabase.dataSource)
    private val catalog = ExposedCatalogRepository(database)
    private val customer = Viewer(firstName = "Rita", customerId = "c-rita")
    private val headphones =
        runBlocking { catalog.listedIn(setOf("headphones")) }
            .sortedBy { it.product.id }
            .take(3)
            .map { ProductView(it.product.id, it.product.categorySlug) }

    private fun section(views: ProductViews) =
        PickedSection(
            PickedForYou(views, ExposedPickSources(database), catalog),
            DeliveryCalendar(CANVAS_NOW::now),
            ProductPhotos(null),
        )

    /** The scenario «Guest»: no block, and nothing is asked about a guest's views. */
    @Test
    fun `a guest gets no block and nothing is read for them`() =
        runBlocking {
            val views = CountingViews(headphones)
            assertEquals(emptyList(), section(views).build(Viewer()))
            assertEquals(0, views.reads, "a guest's home asked for recommendations")
            // Positive control: the same section reads and draws for a customer.
            assertEquals(2, section(views).build(customer).size)
            assertEquals(1, views.reads)
        }

    /** endpoint-recommendations left it to B-25: a failure inside the block drops the block, not the page. */
    @Test
    fun `a block that fails is left out`() =
        runBlocking {
            assertEquals(emptyList(), section(CountingViews(headphones, failing = true)).build(customer))
        }

    /**
     * The home bodies draw the block the server builds: `Home_Content` (Maya, who has viewed) and
     * `Home_PlusTrialDialog` (Sam, who has viewed nothing) carry the server's header field for field, and a grid
     * with the server's id, columns and number of cards. The cards stay the canvas's — it draws products the seed
     * does not sell, as the empty cart's picks do (`CartFixturesTest`).
     */
    @Test
    fun `the home bodies draw the block the server builds`() =
        runBlocking {
            assertBodyDraws("home_content.json", CountingViews(headphones))
            assertBodyDraws("home_plus_trial.json", CountingViews(emptyList()))
        }

    private suspend fun assertBodyDraws(
        body: String,
        views: ProductViews,
    ) {
        val (header, grid) = section(views).build(customer)
        val bodies = File(System.getProperty("haul.clientBodies") ?: error("haul.clientBodies is not set"))
        val drawn =
            Json
                .parseToJsonElement(File(bodies, body).readText())
                .jsonObject
                .getValue("children")
                .jsonArray
                .map { it.jsonObject }

        fun drawn(id: String): JsonObject = drawn.single { it["id"] == JsonPrimitive(id) }
        assertEquals(
            drawn(PickedSection.TITLE_ID),
            Json.parseToJsonElement(haulWireJson.encodeKompotComponent(header as SectionHeader)),
            "$body: the header is not the server's",
        )
        val built = grid as ProductGrid
        assertEquals(JsonPrimitive(built.columns), drawn(PickedSection.GRID_ID)["columns"], body)
        assertEquals(built.cards.size, drawn(PickedSection.GRID_ID).getValue("cards").jsonArray.size, body)
    }

    /** The product page's write: a customer's view is stored, a guest's is not, and a failed write throws nothing. */
    @Test
    fun `a view is recorded for a customer only and a failed write is swallowed`() =
        runBlocking {
            val views = CountingViews(emptyList())
            RecordView(views, CANVAS_NOW)(Viewer(), "p-1")
            assertEquals(emptyList(), views.recorded, "a guest's view was recorded")
            RecordView(views, CANVAS_NOW)(customer, "p-1")
            assertEquals(listOf("c-rita" to "p-1"), views.recorded)

            // The check is that this returns: a failure that reached the caller would fail the product page.
            RecordView(CountingViews(emptyList(), failing = true), CANVAS_NOW)(customer, "p-1")
        }

    private class CountingViews(
        private val viewed: List<ProductView>,
        private val failing: Boolean = false,
    ) : ProductViews {
        var reads = 0
        val recorded = mutableListOf<Pair<String, String>>()

        override suspend fun record(
            customerId: String,
            productId: String,
            at: OffsetDateTime,
        ) {
            check(!failing) { "the database refused the write" }
            recorded += customerId to productId
        }

        override suspend fun recent(customerId: String): List<ProductView> {
            reads++
            check(!failing) { "the database refused the read" }
            return viewed
        }
    }
}
