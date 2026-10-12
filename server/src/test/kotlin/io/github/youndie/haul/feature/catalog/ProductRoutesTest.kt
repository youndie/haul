package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.ErrorBody
import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.ProductDescription
import io.github.youndie.haul.ui.ProductDetails
import io.github.youndie.haul.ui.ProductTabs
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** feature-product's scenarios, on the hero headphones of the sample data. */
class ProductRoutesTest {
    private val sony = "p-sony-wh-1000xm6"

    /** With no `sku`, the cheapest SKU in stock, and the buy box the canvas draws for it. */
    @Test
    fun `the product page opens on the cheapest SKU in stock`() =
        haulTest {
            val details = tree("/ui/p/$sony").only<ProductDetails>()
            assertEquals("$sony-0", details.skuId)
            assertEquals("$349", details.price)
            assertEquals("$449", details.oldPrice)
            assertEquals("−22%", details.discount)
            assertEquals("or 4 payments of $87.25 with Haul Pay", details.haulPay)
            assertEquals("Order within 3 h 42 min", details.cutoff)
            assertEquals("Courier · Tomorrow", details.delivery.first().title)
            // The courier line reads the cut-off, as Product_Description draws it.
            assertEquals("Order within 3 h 42 min", details.delivery.first().detail)
            assertEquals("4 payments of $87.25", details.haulPayStrong)
        }

    /** Scenario «Variant changes the price»: «+ Travel case» is another SKU with its own price. */
    @Test
    fun `a variant changes the price`() =
        haulTest {
            val details = tree("/ui/p/$sony?sku=$sony-1").only<ProductDetails>()
            assertEquals("$sony-1", details.skuId)
            assertEquals("$379", details.price)
            val bundle = details.variants.single { it.name == "Bundle" }
            assertEquals("+ Travel case", bundle.selectedLabel)
            assertTrue(bundle.options.single { it.label == "+ Travel case" }.selected)
        }

    /**
     * The page half of scenario «Out of stock»: Silver has no stock, so the details say so and offer no
     * cut-off to race. The refusal to add it (`409 out_of_stock`) belongs to the cart route, B-11.
     */
    @Test
    fun `an out of stock SKU is drawn out of stock`() =
        haulTest {
            val details = tree("/ui/p/$sony?sku=$sony-3").only<ProductDetails>()
            assertFalse(details.inStock)
            assertEquals("Out of stock", details.stockNote)
            assertNull(details.cutoff)
            assertEquals("Silver is out of stock.", details.stockAdvice?.title)
            val colour = details.variants.single { it.name == "Colour" }
            assertFalse(colour.options.single { it.label == "Silver" }.available, "Silver offered as available")
        }

    /**
     * B-33: the description tab opens on the headline the seed gives the headphones, its accent the part
     * `Product_Description` sets in italic — and it is the headline the client's parity fixture draws, so
     * the screenshot that passed B-08 is what the running app shows. The fixture's body text and facts
     * are the canvas's long copy, which the seed does not carry; only those two fields are set aside.
     */
    @Test
    fun `the description tab carries the headline the canvas draws`() =
        haulTest {
            val description = tree("/ui/p/$sony?tab=description").only<ProductDescription>()
            assertEquals("Silence, tuned to you", description.title)
            assertEquals("to you", description.accent)

            val bodies = File(System.getProperty("haul.clientBodies") ?: error("haul.clientBodies is not set"))
            val drawn =
                haulWireJson
                    .decodeKompotComponent(File(bodies, "product_description.json").readText())
                    .only<ProductDescription>()
            assertEquals(drawn.copy(text = description.text, facts = description.facts), description)
        }

    /**
     * B-52: the headphones say what every Product_* artboard says under the rating, «12K bought this month»,
     * and it is what the client's parity fixture draws — the seed's 12,340 under whatever orders the suite
     * placed. A server that stopped sending it would leave the fixture drawing a line the running app does not.
     */
    @Test
    fun `the headphones carry the bought line the canvas draws`() =
        haulTest {
            val details = tree("/ui/p/$sony").only<ProductDetails>()
            val bodies = File(System.getProperty("haul.clientBodies") ?: error("haul.clientBodies is not set"))
            val drawn =
                haulWireJson
                    .decodeKompotComponent(File(bodies, "product_description.json").readText())
                    .only<ProductDetails>()
            assertEquals("12K bought this month", drawn.bought, "the fixture no longer draws the canvas's line")
            assertEquals(drawn.bought, details.bought)
        }

    /**
     * B-71: what looks like a link on the details is one. The brand opens its products in the product's
     * category, the rating and «2,341 reviews» the reviews tab, «All specifications» the specifications tab,
     * each at the SKU shown; the share button copies the product at that SKU. Every one of them is the address
     * of a page that opens.
     */
    @Test
    fun `the brand, the rating and all specifications lead somewhere and share names the product`() =
        haulTest {
            val details = tree("/ui/p/$sony?sku=$sony-1").only<ProductDetails>()
            assertEquals(NavigateAction("/c/electronics/audio/headphones?brand=Sony"), details.brandAction)
            assertEquals(NavigateAction("/p/$sony?sku=$sony-1&tab=reviews"), details.ratingAction)
            assertEquals(NavigateAction("/p/$sony?sku=$sony-1&tab=specifications"), details.specificationsAction)
            assertEquals("/p/$sony?sku=$sony-1", details.share)
            for (action in listOf(details.brandAction, details.ratingAction, details.specificationsAction)) {
                tree("/ui" + (action as NavigateAction).deeplink)
            }
            tree("/ui" + details.share)
        }

    /**
     * B-132: on `?tab=reviews`, an address naming no SKU, «2,341 reviews» led to `?sku=…-0&tab=reviews` — the page
     * shown, loaded again (B-95), at an address rewritten (TC-PDP-03 step 5). The links the page writes for itself
     * name the SKU as its address does: none here, the one named above. A variant picked still names its SKU.
     */
    @Test
    fun `the page's own links name no sku when its address names none`() =
        haulTest {
            val page = tree("/ui/p/$sony?tab=reviews")
            val details = page.only<ProductDetails>()
            assertEquals(NavigateAction("/p/$sony?tab=reviews"), details.ratingAction)
            assertEquals(NavigateAction("/p/$sony?tab=specifications"), details.specificationsAction)
            assertEquals(
                listOf("description", "specifications", "reviews", "questions").map { NavigateAction("/p/$sony?tab=$it") },
                page.only<ProductTabs>().tabs.map { it.action },
            )
            for (option in details.variants.flatMap { it.options }) {
                assertEquals(NavigateAction("/p/$sony?sku=${option.skuId}&tab=reviews"), option.action, option.label)
            }
        }

    /**
     * B-104: a colour or a bundle picked on the specifications, reviews or questions tab opens that SKU on the
     * same tab, not on the description; picked on the description, the address names no tab, as before.
     */
    @Test
    fun `a variant picked on a tab keeps the tab`() =
        haulTest {
            for (tab in listOf("specifications", "reviews", "questions")) {
                val details = tree("/ui/p/$sony?sku=$sony-0&tab=$tab").only<ProductDetails>()
                val options = details.variants.flatMap { it.options }
                for (option in options) {
                    assertEquals(NavigateAction("/p/$sony?sku=${option.skuId}&tab=$tab"), option.action, option.label)
                }
                val travel = options.single { it.label == "+ Travel case" }.action as NavigateAction
                val opened = tree("/ui" + travel.deeplink)
                assertEquals("$sony-1", opened.only<ProductDetails>().skuId)
                assertEquals(
                    tab,
                    opened
                        .only<ProductTabs>()
                        .tabs
                        .single { it.selected }
                        .key,
                )
            }
            val described = tree("/ui/p/$sony?sku=$sony-0").only<ProductDetails>()
            for (option in described.variants.flatMap { it.options }) {
                assertEquals(NavigateAction("/p/$sony?sku=${option.skuId}"), option.action, option.label)
            }
        }

    /** Scenario «Unknown product». */
    @Test
    fun `an unknown product is 404 product_not_found`() =
        haulTest {
            val response = get("/ui/p/p-no-such-product")
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals(
                ErrorCode.ProductNotFound,
                haulWireJson.decodeFromString(ErrorBody.serializer(), response.bodyAsText()).code,
            )
        }
}
