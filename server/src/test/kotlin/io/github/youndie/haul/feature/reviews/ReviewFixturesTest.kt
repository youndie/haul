package io.github.youndie.haul.feature.reviews

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.catalog.data.ExposedCatalogRepository
import io.github.youndie.haul.feature.catalog.data.ExposedProductSales
import io.github.youndie.haul.feature.catalog.domain.BoughtThisMonth
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.feature.catalog.screen.ProductScreen
import io.github.youndie.haul.feature.catalog.screen.ProductTab
import io.github.youndie.haul.feature.reviews.data.ExposedReviews
import io.github.youndie.haul.feature.reviews.screen.ReviewTabs
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.SampleCatalog.SONY_HEADPHONES
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.SeededDatabase
import io.github.youndie.kompot.encodeKompotComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The client draws Product_Reviews, Product_Questions and their two dialogs from wire bodies
 * (`composeApp/src/desktopTest/resources/bodies/product_reviews.json`, `product_questions.json`; a dialog
 * is the form inside its tab's `present`), and this holds what B-22 owns in them equal to what the server
 * builds for Maya looking at the seeded headphones at the canvas's «now»: the tab row and the tab — the
 * rating, the histogram, the reviews or the questions, and the dialog's form. Compared as JSON, so a field
 * the server omits, renames or adds fails here; a mismatch writes what the server built to
 * `build/review-fixtures/`.
 *
 * The rest of each body — the header, the crumbs and the details above the tabs — is B-08's, written with
 * the canvas's copy, which the seed's product does not match («Color», the pickup line), and stays outside
 * this comparison as it was; «12K bought this month» there is held by `ProductRoutesTest` (B-52).
 */
class ReviewFixturesTest {
    private val bodies = File(System.getProperty("haul.clientBodies") ?: error("haul.clientBodies is not set"))
    private val pretty = Json(haulWireJson) { prettyPrint = true }

    @Test
    fun `the client's review and question bodies hold the trees the server builds`() =
        runBlocking {
            val database = Databases.connect(SeededDatabase.dataSource)
            val catalog = ExposedCatalogRepository(database)
            val screen =
                ProductScreen(
                    catalog,
                    DeliveryCalendar(CANVAS_NOW::now),
                    ProductPhotos(null),
                    ReviewTabs(ExposedReviews(database)),
                    BoughtThisMonth(ExposedProductSales(database), CANVAS_NOW),
                )
            val maya = Viewer(firstName = "Maya", customerId = SampleCustomers.MAYA)
            val mismatches = mutableListOf<String>()
            for ((name, tab) in listOf(
                "product_reviews.json" to ProductTab.Reviews,
                "product_questions.json" to ProductTab.Questions,
            )) {
                val built =
                    Json.parseToJsonElement(
                        haulWireJson.encodeKompotComponent(screen.build(SONY_HEADPHONES, null, tab, maya)),
                    )
                val drawn = Json.parseToJsonElement(File(bodies, name).readText())
                val owned =
                    setOf(
                        "haul_product_tabs",
                        if (tab ==
                            ProductTab.Reviews
                        ) {
                            "haul_product_reviews"
                        } else {
                            "haul_product_questions"
                        },
                    )
                if (built.sections(owned) != drawn.sections(owned)) {
                    File("build/review-fixtures").apply { mkdirs() }.resolve(name).writeText(
                        pretty.encodeToString(JsonElement.serializer(), JsonObject(built.sections(owned))) + "\n",
                    )
                    mismatches += name
                }
            }
            assertEquals(
                emptyList(),
                mismatches,
                "the bodies differ from the server's trees; see build/review-fixtures/",
            )
        }

    /** The page's sections of these [types], by type. */
    private fun JsonElement.sections(types: Set<String>): Map<String, JsonElement> =
        jsonObject
            .getValue("children")
            .jsonArray
            .map { it.jsonObject }
            .filter { it.getValue("type").jsonPrimitive.content in types }
            .associateBy { it.getValue("type").jsonPrimitive.content }
}
