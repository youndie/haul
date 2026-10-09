package io.github.youndie.haul.feature.recommendations

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.catalog.data.ExposedCatalogRepository
import io.github.youndie.haul.feature.catalog.domain.Listed
import io.github.youndie.haul.feature.catalog.domain.PriceList
import io.github.youndie.haul.feature.recommendations.data.ExposedPickSources
import io.github.youndie.haul.feature.recommendations.domain.PickBasis
import io.github.youndie.haul.feature.recommendations.domain.PickSources
import io.github.youndie.haul.feature.recommendations.domain.PickedForYou
import io.github.youndie.haul.feature.recommendations.domain.ProductView
import io.github.youndie.haul.feature.recommendations.domain.ProductViews
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.SeededDatabase
import kotlinx.coroutines.runBlocking
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * feature-recommendations' rules over the seeded catalog: the views and the purchases are fakes, the
 * catalog and the popular order are the database's, so a pick is a product the store really has.
 */
class PickedForYouTest {
    private val database = Databases.connect(SeededDatabase.dataSource)
    private val catalog = ExposedCatalogRepository(database, CANVAS_NOW)
    private val popular = ExposedPickSources(database)

    private fun category(slug: String): List<Listed> =
        runBlocking { catalog.listedIn(setOf(slug), PriceList.Public) }.sortedBy { it.product.id }

    private fun views(vararg products: Listed) = products.map { ProductView(it.product.id, it.product.categorySlug) }

    private fun picker(
        viewed: List<ProductView>,
        bought: Set<String> = emptySet(),
    ) = PickedForYou(FixedViews(viewed), Bought(bought, popular), catalog)

    /** Every pick of [slug] beats every product of it that could have been picked and was not. */
    private fun assertTopRated(
        slug: String,
        picks: List<Listed>,
        excluded: Set<String>,
    ) {
        val picked = picks.filter { it.product.categorySlug == slug }
        val passedOver =
            category(slug).filter { it.inStock && it.product.id !in excluded && it !in picked }
        picked.forEach { pick ->
            passedOver.forEach { other ->
                assertTrue(
                    pick.product.rating > other.product.rating ||
                        (
                            pick.product.rating == other.product.rating &&
                                pick.product.reviewsCount >= other.product.reviewsCount
                        ),
                    "${pick.product.id} was picked over the better ${other.product.id}",
                )
            }
        }
    }

    /** The scenario «From views», at the rule: Maya viewed three headphones. */
    @Test
    fun `three headphones viewed give at most two headphones and none of the three`() =
        runBlocking {
            // The best three: had the views not been left out, they would be the first picks.
            val viewed = category("headphones").filter { it.inStock }.sortedWith(BEST).take(3)
            val picks = picker(views(*viewed.toTypedArray()))("c-maya", PriceList.Public)
            assertEquals(PickBasis.RecentViews, picks.basis)
            assertEquals(6, picks.items.size, "the row has holes")
            val headphones = picks.items.filter { it.product.categorySlug == "headphones" }
            assertEquals(2, headphones.size, "not two of the one category viewed")
            assertTrue(picks.items.none { it in viewed }, "a viewed product was picked back")
            assertTrue(picks.items.all { it.inStock }, "an out-of-stock product was picked")
            assertTopRated("headphones", picks.items, viewed.map { it.product.id }.toSet())
        }

    /**
     * Categories by frequency: the most viewed first, and a tie to the one viewed last — the views are
     * newest first, so the mug viewed after the duvet cover puts mugs before duvet covers.
     */
    @Test
    fun `the most viewed category comes first and a tie goes to the latest viewed`() =
        runBlocking {
            val headphones = category("headphones").take(3)
            val mug = category("mugs").first()
            val duvet = category("duvet-covers").first()
            val picks =
                picker(views(mug, headphones[0], duvet, headphones[1], headphones[2]))("c-ann", PriceList.Public)
            assertEquals(
                listOf("headphones", "headphones", "mugs", "mugs", "duvet-covers", "duvet-covers"),
                picks.items.map { it.product.categorySlug },
            )
        }

    /** Fewer than three views: the popular row, still without what was viewed. */
    @Test
    fun `fewer than three views give the popular row`() =
        runBlocking {
            val mostReviewed = popular.popular(emptySet(), 1).single()
            val viewed = catalog.listed(listOf(mostReviewed), PriceList.Public) + category("mugs").take(1)
            val picks = picker(views(*viewed.toTypedArray()))("c-bo", PriceList.Public)
            assertEquals(PickBasis.Popular, picks.basis)
            assertEquals(6, picks.items.size)
            assertTrue(picks.items.none { it in viewed }, "a viewed product was offered as popular")
            assertEquals(
                popular.popular(viewed.map { it.product.id }.toSet(), 1).single(),
                picks.items
                    .first()
                    .product.id,
                "the row does not start with the most reviewed product left",
            )
            assertTrue(
                picks.items
                    .groupingBy { it.product.categorySlug }
                    .eachCount()
                    .values
                    .all { it <= 2 },
            )
        }

    /** «Neither viewed nor bought»: the two best headphones were bought, so the next two are picked. */
    @Test
    fun `bought products are never picked`() =
        runBlocking {
            val viewed = category("headphones").take(3)
            val viewedIds = viewed.map { it.product.id }.toSet()
            val before =
                picker(views(*viewed.toTypedArray()))("c-cy", PriceList.Public).items.filter {
                    it.product.categorySlug ==
                        "headphones"
                }
            val bought = before.map { it.product.id }.toSet()
            val after = picker(views(*viewed.toTypedArray()), bought)("c-cy", PriceList.Public).items
            assertTrue(after.none { it.product.id in bought }, "a bought product was picked")
            assertEquals(
                2,
                after.count { it.product.categorySlug == "headphones" },
                "the next headphones were not picked",
            )
            assertTopRated("headphones", after, viewedIds + bought)
        }

    /**
     * A viewed category with one product left gives one pick, and the row is filled from the popular row
     * under the same cap: never a third product of a category, never a hole.
     */
    @Test
    fun `a category run dry is filled from the popular row`() =
        runBlocking {
            val headphones = category("headphones")
            val viewed = headphones.take(3)
            val left = headphones.drop(3).first { it.inStock }
            val bought =
                headphones.map { it.product.id }.toSet() - viewed.map { it.product.id }.toSet() - left.product.id
            val picks = picker(views(*viewed.toTypedArray()), bought)("c-di", PriceList.Public)
            assertEquals(PickBasis.RecentViews, picks.basis)
            assertEquals(left, picks.items.first(), "the one headphone left is not the first pick")
            assertEquals(6, picks.items.size)
            assertTrue(
                picks.items
                    .groupingBy { it.product.categorySlug }
                    .eachCount()
                    .values
                    .all { it <= 2 },
            )
            assertTrue(picks.items.none { it.product.id in bought })
        }

    /** Fixtures and goldens are drawn from these rows, so the same views give the same picks. */
    @Test
    fun `the same views give the same picks`() =
        runBlocking {
            val viewed = views(*category("headphones").take(3).toTypedArray(), category("mugs").first())
            assertEquals(picker(viewed)("c-ed", PriceList.Public), picker(viewed.toList())("c-ed", PriceList.Public))
        }

    private companion object {
        val BEST =
            compareByDescending<Listed> { it.product.rating }
                .thenByDescending { it.product.reviewsCount }
                .thenBy { it.product.id }
    }

    private class FixedViews(
        private val viewed: List<ProductView>,
    ) : ProductViews {
        override suspend fun record(
            customerId: String,
            productId: String,
            at: OffsetDateTime,
        ) = error("the rule records nothing")

        override suspend fun recent(customerId: String): List<ProductView> = viewed
    }

    private class Bought(
        private val products: Set<String>,
        popular: PickSources,
    ) : PickSources by popular {
        override suspend fun bought(customerId: String): Set<String> = products
    }
}
