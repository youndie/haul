package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.seed.SampleCatalog.SONY_HEADPHONES
import io.github.youndie.haul.seed.SampleCatalog.STONEWARE_MUG
import io.github.youndie.haul.testing.FulfilmentWorld
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductDetails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * A product's listing name (B-45): what a card, a cart line and an order line write, the title when the
 * catalogue names none. The cart's and the checkout's lines are held by the fixture tests, whose bodies
 * carry the headphones' listing name; these are the card, the product page that does not take it, and the
 * order line that keeps it.
 */
class ListingNameTest {
    /**
     * The canvas writes «Sony WH-1000XM6 …» on a card and «WH-1000XM6 …» under «Sony» on the product page.
     * Before B-45 the server sent the title to both, so every list of the headphones differed from its
     * artboard by one line; «brand + title» on the card would have written the duvet's seller in front of it.
     */
    @Test
    fun `a card writes the listing name where the product page writes brand above title`() =
        haulTest {
            val cards = tree("/ui/c/electronics/audio/headphones").all().filterIsInstance<ProductCard>()
            val card = cards.single { it.productId == SONY_HEADPHONES }
            assertEquals("Sony WH-1000XM6 Wireless Noise Cancelling Headphones", card.title)

            val details = tree("/ui/p/$SONY_HEADPHONES").only<ProductDetails>()
            assertEquals("Sony", details.brand)
            assertEquals(
                "WH-1000XM6 Wireless Noise Cancelling Headphones",
                details.title,
                "the product page took the listing name",
            )
        }

    /** A product the catalogue gives no listing name is listed by its title: `NULL` is the default, not a blank card. */
    @Test
    fun `a product with no listing name is listed by its title`() =
        haulTest {
            val card =
                tree("/ui/c/home-kitchen/kitchen/mugs").all().filterIsInstance<ProductCard>().single {
                    it.productId ==
                        STONEWARE_MUG
                }
            assertEquals("Stoneware Mug, 12 oz", card.title)
        }

    /**
     * An order line keeps the listing name it was bought under: placement copies it into the line, and the
     * order reads the line, not the product. Renamed after the order, the headphones keep their old name there.
     */
    @Test
    fun `an order line keeps the listing name it was bought under`() =
        seededFreshDatabase().use { database ->
            FulfilmentWorld(database).use { world ->
                val orderId = world.place()
                database.connection.use { connection ->
                    connection.createStatement().use {
                        it.executeUpdate(
                            "UPDATE products SET listing_name = 'Sony WH-1000XM6, renamed' WHERE id = '$SONY_HEADPHONES'",
                        )
                    }
                    connection.commit()
                }

                val order = assertNotNull(world.order(orderId))
                val line = order.placed.lines.single { it.skuId.startsWith(SONY_HEADPHONES) }
                assertEquals("Sony WH-1000XM6 Wireless Noise Cancelling Headphones", line.title)
            }
        }
}
