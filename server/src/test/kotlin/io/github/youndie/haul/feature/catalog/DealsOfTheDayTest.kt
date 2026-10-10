package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SeedProduct
import io.github.youndie.haul.testing.SeededDatabase
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.cart
import io.github.youndie.haul.testing.guest
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.EmptyState
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.SectionHeader
import io.github.youndie.kompot.KompotComponent
import io.ktor.client.HttpClient
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * «Deals of the day» while there are deals and once there are none (B-58), on the home page, the deals page
 * and the empty cart, drawn for a guest.
 *
 * Before B-58 a section with no live deal was still drawn — its header, its countdown and «View all deals»
 * over an empty grid, and the empty cart's «Picked for you» over nothing — which is what the stand showed
 * once its seeded deals had ended; and the countdown ran to the store's next midnight whatever the deals'
 * ends were.
 */
class DealsOfTheDayTest {
    /** The store's «now», which a test moves past the deals' ends. */
    private class MovingClock(
        var at: OffsetDateTime,
    ) : StoreClock {
        override fun now(): ZonedDateTime = at.toZonedDateTime()
    }

    private fun store(
        database: DataSource,
        block: suspend HttpClient.(clock: MovingClock) -> Unit,
    ) {
        val clock = MovingClock(CatalogSeed.NOW)
        haulTest(database, clock = clock) { block(clock) }
    }

    private fun KompotComponent.ids(): Set<String> =
        all()
            .mapNotNull {
                when (it) {
                    is SectionHeader -> it.id
                    is ProductGrid -> it.id
                    else -> null
                }
            }.toSet()

    private fun KompotComponent.countdown(): String? =
        all().filterIsInstance<SectionHeader>().single { it.id == "deals-title" }.countdownEndsAt

    private fun KompotComponent.dealCards() = all().filterIsInstance<ProductGrid>().single { it.id == "deals" }.cards

    /**
     * At the canvas's «now» the home page, the deals page and the empty cart draw the day's deals; from the
     * deals' end none of them draws the section at all — no header, no countdown, no empty grid — while the
     * rest of each page stays: home's categories, the deals page's «On sale», the empty cart's message.
     */
    @Test
    fun `with no live deal no deals section is drawn`() =
        store(SeededDatabase.dataSource) { clock ->
            val guest = guest()
            assertTrue(tree("/ui/home").ids().containsAll(DEALS), "the control: home draws its deals")
            assertTrue(tree("/ui/deals").ids().containsAll(DEALS), "the control: /deals draws them")
            assertTrue(cart(guest).ids().containsAll(PICKS), "the control: the empty cart picks")

            clock.at = CatalogSeed.DEALS_END
            val home = tree("/ui/home")
            assertTrue(home.ids().none { it in DEALS }, "home draws a deals section with no deal: ${home.ids()}")
            assertTrue("categories-title" in home.ids(), "home lost more than its deals")

            val deals = tree("/ui/deals")
            assertTrue(deals.ids().none { it in DEALS }, "/deals draws a deals section with no deal: ${deals.ids()}")
            assertTrue(deals.ids().containsAll(setOf("sale-title", "grid")), "/deals lost more than its deals")

            val empty = cart(guest)
            assertTrue(empty.ids().none { it in PICKS }, "the empty cart picks from no deal: ${empty.ids()}")
            assertEquals("Your cart is empty", empty.only<EmptyState>().title)
        }

    /**
     * With no deal live a guest's home would have no row of products at all — hero, tiles and the Plus block
     * over nothing (B-70) — so «Popular on Haul» takes the deals' place: the six most reviewed products in stock,
     * at the guest's prices. Never beside live deals.
     */
    @Test
    fun `with no live deal home draws the popular row`() =
        seededFreshDatabase().use { database ->
            store(database) { clock ->
                assertTrue(tree("/ui/home").ids().none { it in POPULAR }, "the popular row is drawn beside live deals")

                clock.at = CatalogSeed.DEALS_END
                val home = tree("/ui/home")
                assertTrue(home.ids().containsAll(POPULAR), "a guest's home has no row of products: ${home.ids()}")
                val title =
                    home
                        .all()
                        .filterIsInstance<SectionHeader>()
                        .single { it.id == "popular-title" }
                        .title
                assertEquals("Popular on Haul", title)
                val popular = home.all().filterIsInstance<ProductGrid>().single { it.id == "popular" }
                assertEquals(mostReviewed(), popular.cards.map { it.productId })
            }
        }

    /** The six most reviewed products of the seed with a SKU in stock, ties by id. */
    private fun mostReviewed(): List<String> {
        val seed = CatalogSeed.generate(CatalogSeed.CANVAS_DAY)
        val inStock =
            seed.skus
                .filter { it.stock > 0 }
                .map { it.productId }
                .toSet()
        return seed.products
            .filter { it.id in inStock }
            .sortedWith(compareByDescending<SeedProduct> { it.reviewsCount }.thenBy { it.id })
            .take(6)
            .map { it.id }
    }

    /**
     * A deal that ends before midnight is counted down to its own end: with the third deal ending at 21:00 on
     * the canvas's day, the countdown at 19:47 runs to 21:00 on home and on the deals page; at 21:00 that card
     * is gone and the countdown runs to the other five's end, midnight.
     */
    @Test
    fun `the countdown runs to the soonest live deal's end`() =
        seededFreshDatabase().use { database ->
            database.connection.use { c ->
                c.createStatement().use {
                    it.executeUpdate("UPDATE deals SET ends_at = '$NINE_PM' WHERE id = 'deal-3'")
                }
                c.commit()
            }
            store(database) { clock ->
                assertEquals("2025-10-07T21:00-04:00", tree("/ui/home").countdown())
                assertEquals("2025-10-07T21:00-04:00", tree("/ui/deals").countdown())
                assertEquals(6, tree("/ui/home").dealCards().size)

                clock.at = NINE_PM
                val home = tree("/ui/home")
                assertEquals(5, home.dealCards().size, "the ended deal is still drawn")
                assertEquals("2025-10-08T00:00-04:00", assertNotNull(home.countdown()))
            }
        }

    private companion object {
        val DEALS = setOf("deals-title", "deals")
        val PICKS = setOf("picked-title", "picked")
        val POPULAR = setOf("popular-title", "popular")
        val NINE_PM: OffsetDateTime = OffsetDateTime.parse("2025-10-07T21:00:00-04:00")

        init {
            check(CatalogSeed.NOW.isBefore(NINE_PM))
        }
    }
}
