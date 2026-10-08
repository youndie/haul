package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.ErrorBody
import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.ProductDetails
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
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
            val colour = details.variants.single { it.name == "Colour" }
            assertFalse(colour.options.single { it.label == "Silver" }.available, "Silver offered as available")
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
