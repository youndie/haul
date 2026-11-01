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
import io.github.youndie.haul.feature.reviews.domain.ReviewCommands
import io.github.youndie.haul.feature.reviews.domain.StoredReview
import io.github.youndie.haul.feature.reviews.screen.ReviewTabs
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCatalog.SONORA_HEADPHONES
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.seed.SeedArt
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.SeededDatabase
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.kompot.encodeKompotComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.Database
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The client draws Product_Reviews, Product_Questions and their two dialogs from wire bodies
 * (`composeApp/src/desktopTest/resources/bodies/product_reviews.json`, `product_questions.json`; a dialog
 * is the form inside its tab's `present`), and this holds what B-22 owns in them equal to what the server
 * builds for Maya looking at the seeded headphones at the canvas's «now»: the tab row and the tab — the
 * rating, the histogram, the reviews or the questions, and the dialog's form. `product_reviews_mine.json` is
 * the same tab once Maya has reviewed the headphones and found Aisha's review helpful (B-99): her review first,
 * no «Write a review», and a pill drawn voted. Compared as JSON, so a field
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
            val seeded = screen(Databases.connect(SeededDatabase.dataSource))
            val mismatches =
                listOfNotNull(
                    compare("product_reviews.json", seeded, ProductTab.Reviews),
                    compare("product_questions.json", seeded, ProductTab.Questions),
                    // B-99: Maya has reviewed the headphones and found Aisha's review helpful — among all 2,341.
                    seededFreshDatabase(generatedReviews = true).use { dataSource ->
                        val database = Databases.connect(dataSource)
                        val reviews = ExposedReviews(database)
                        reviews.write(
                            StoredReview(
                                id = "r-mayas",
                                productId = SONORA_HEADPHONES,
                                customerId = SampleCustomers.MAYA,
                                author = "Maya K.",
                                rating = 5,
                                title = "Worth it for the commute",
                                body =
                                    "Noise cancelling handles the subway and the open office. Comfortable for a full " +
                                        "workday, and the battery lasts all week.",
                                verified = true,
                                helpful = 0,
                                tone = ReviewCommands.avatarTone(SampleCustomers.MAYA),
                                photos = emptyList(),
                                createdAt = CatalogSeed.NOW,
                            ),
                        )
                        reviews.vote("r-sonora-aisha", SampleCustomers.MAYA, helpful = true, at = CatalogSeed.NOW)
                        compare("product_reviews_mine.json", screen(database), ProductTab.Reviews)
                    },
                )
            assertEquals(
                emptyList(),
                mismatches,
                "the bodies differ from the server's trees; see build/review-fixtures/",
            )
        }

    private fun screen(database: Database): ProductScreen =
        ProductScreen(
            ExposedCatalogRepository(database, CANVAS_NOW),
            DeliveryCalendar(CANVAS_NOW::now),
            ProductPhotos(null, SeedArt),
            ReviewTabs(ExposedReviews(database), CANVAS_NOW, ProductPhotos(null, SeedArt)),
            BoughtThisMonth(ExposedProductSales(database), CANVAS_NOW),
        )

    /**
     * Whether the body [name] holds what [screen] builds for Maya on [tab] of the headphones; the name when it
     * does not, with what the server built written to `build/review-fixtures/`.
     */
    private suspend fun compare(
        name: String,
        screen: ProductScreen,
        tab: ProductTab,
    ): String? {
        val maya = Viewer(firstName = "Maya", customerId = SampleCustomers.MAYA)
        val built =
            Json.parseToJsonElement(haulWireJson.encodeKompotComponent(screen.build(SONORA_HEADPHONES, null, tab, maya)))
        val drawn = Json.parseToJsonElement(File(bodies, name).readText())
        val owned =
            setOf(
                "haul_product_tabs",
                if (tab == ProductTab.Reviews) "haul_product_reviews" else "haul_product_questions",
            )
        if (built.sections(owned) == drawn.sections(owned)) return null
        File("build/review-fixtures").apply { mkdirs() }.resolve(name).writeText(
            pretty.encodeToString(JsonElement.serializer(), JsonObject(built.sections(owned))) + "\n",
        )
        return name
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
