package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.ProductDetails
import io.github.youndie.haul.ui.ProductGrid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B-120: nothing in stock, no delivery day. A card promises a day only for the SKU it shows, which is out of
 * stock only when every SKU is; the product page only for the SKU chosen. At the canvas's «now», before the
 * cut-off, a generated product (no dispatch days) and the headphones would both come tomorrow.
 */
class OutOfStockPromiseTest {
    private val seed = CatalogSeed.generate(CatalogSeed.CANVAS_DAY)

    /** A product with every SKU out of stock, in a category whose first page holds every product. */
    private val gone =
        seed.products.first { product ->
            seed.skus.filter { it.productId == product.id }.all { it.stock == 0 } &&
                seed.products.count { it.categorySlug == product.categorySlug } <= 24
        }

    @Test
    fun `a product with nothing in stock promises no day on its page`() =
        haulTest {
            val details = tree("/ui/p/${gone.id}").only<ProductDetails>()
            assertEquals(false, details.inStock, "${gone.id} is drawn in stock")
            assertEquals(emptyList(), details.delivery, "${gone.id} has nothing to sell and still promises a day")
            assertNull(details.cutoff)
            assertEquals("", details.stockAdvice?.text, "no other colour to turn to, so no day to name")
        }

    @Test
    fun `a product with nothing in stock promises no day on its card`() =
        haulTest {
            val cards = tree("/ui/c/${gone.categorySlug}").only<ProductGrid>().cards
            assertNull(cards.single { it.productId == gone.id }.delivery, "${gone.id}'s card promises a day")
            // The control: a product of the same page that can be bought keeps its day.
            assertEquals("Tomorrow", cards.first { it.add != null }.delivery)
        }

    @Test
    fun `the delivery tomorrow facet leaves out a product with nothing in stock`() =
        haulTest {
            val all = tree("/ui/c/${gone.categorySlug}").only<ProductGrid>().cards
            val tomorrow = tree("/ui/c/${gone.categorySlug}?delivery=tomorrow").only<ProductGrid>().cards
            assertTrue(all.any { it.productId == gone.id })
            assertTrue(tomorrow.none { it.productId == gone.id }, "${gone.id} is offered as arriving tomorrow")
            assertEquals(all.filter { it.delivery == "Tomorrow" }.map { it.productId }, tomorrow.map { it.productId })
        }

    /** The headphones: Midnight Black in stock, Silver not. The page follows the SKU chosen, a card the SKU shown. */
    @Test
    fun `the chosen SKU decides the page's day and the card keeps the day of the SKU in stock`() =
        haulTest {
            val midnight = tree("/ui/p/$SONORA?sku=$SONORA-0").only<ProductDetails>()
            assertEquals(
                listOf("Courier · Tomorrow", "Pickup point · Thu, Oct 9", "Returns"),
                midnight.delivery.map { it.title },
            )

            val silver = tree("/ui/p/$SONORA?sku=$SONORA-3").only<ProductDetails>()
            assertEquals(emptyList(), silver.delivery, "Silver is out of stock and still promises a day")
            assertEquals("Midnight Black is in stock and arrives tomorrow.", silver.stockAdvice?.text)

            val card =
                tree("/ui/c/electronics/audio/headphones")
                    .only<ProductGrid>()
                    .cards
                    .single { it.productId == SONORA }
            assertEquals("Tomorrow", card.delivery)
        }

    private companion object {
        const val SONORA = "p-sonora-sh-718n"
    }
}
