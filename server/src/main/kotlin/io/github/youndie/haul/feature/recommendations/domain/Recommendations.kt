package io.github.youndie.haul.feature.recommendations.domain

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.domain.Listed
import io.github.youndie.haul.shell.Viewer
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import kotlin.coroutines.cancellation.CancellationException

/** A product a customer opened, with the category «Picked for you» counts it under. */
internal data class ProductView(
    val productId: String,
    val categorySlug: String,
)

/**
 * A customer's product views (research §5, `ProductView`): one per product, the newest [KEPT] of them.
 * Only theirs: the customer is the filter of every read and write.
 */
internal interface ProductViews {
    /**
     * [customerId] opened [productId] at [at]: the product's view is now the newest, and the views past
     * the newest [KEPT] are gone. A product the catalog does not have is not recorded.
     */
    suspend fun record(
        customerId: String,
        productId: String,
        at: OffsetDateTime,
    )

    /** [customerId]'s views, newest first (then by product id), at most [KEPT]. */
    suspend fun recent(customerId: String): List<ProductView>

    companion object {
        /** feature-recommendations: «the customer's last 20 product views». */
        const val KEPT = 20
    }
}

/** What else the rule reads: what a customer bought, and what everybody buys. */
internal interface PickSources {
    /** The products in [customerId]'s orders that were not cancelled. */
    suspend fun bought(customerId: String): Set<String>

    /**
     * The most reviewed products with a SKU in stock — the catalog's «Popular» order (reviews, then id) —
     * leaving out [excluding], at most [limit].
     */
    suspend fun popular(
        excluding: Set<String>,
        limit: Int,
    ): List<String>
}

/** Why the picks are what they are, which is what the block's subtitle says. */
internal enum class PickBasis(
    val subtitle: String,
) {
    RecentViews("Based on your recent views"),
    Popular("Popular right now"),
}

internal data class Picks(
    val basis: PickBasis,
    val items: List<Listed>,
)

/**
 * «Picked for you» (feature-recommendations): from the customer's last [ProductViews.KEPT] views take their
 * categories by frequency — a tie goes to the category viewed most recently, then to its slug — and from
 * each, in that order, the top-rated products in stock (rating, then reviews, then id) that were neither
 * viewed nor bought, at most [PER_CATEGORY] of a category and [TOTAL] in all.
 *
 * Fewer than [MIN_VIEWS] views is the popular row instead. When the viewed categories run out before
 * [TOTAL], the row is filled from the popular products under the same exclusions and the same cap per
 * category, so the six-column row the canvas draws is never left with holes; the basis stays the views
 * as long as one pick came from them.
 */
internal class PickedForYou(
    private val views: ProductViews,
    private val sources: PickSources,
    private val catalog: CatalogRepository,
) {
    suspend operator fun invoke(customerId: String): Picks {
        val viewed = views.recent(customerId)
        val excluded = viewed.map { it.productId }.toSet() + sources.bought(customerId)
        if (viewed.size < MIN_VIEWS) return Picks(PickBasis.Popular, popular(excluded, emptyList()))

        val picks = mutableListOf<Listed>()
        for (window in ranked(viewed).chunked(TOTAL / PER_CATEGORY)) {
            if (picks.size >= TOTAL) break
            val inWindow = catalog.listedIn(window.toSet()).groupBy { it.product.categorySlug }
            for (category in window) {
                picks +=
                    inWindow[category]
                        .orEmpty()
                        .filter { it.inStock && it.product.id !in excluded }
                        .sortedWith(TOP_RATED)
                        .take(minOf(PER_CATEGORY, TOTAL - picks.size))
            }
        }
        if (picks.isEmpty()) return Picks(PickBasis.Popular, popular(excluded, emptyList()))
        return Picks(PickBasis.RecentViews, picks + popular(excluded, picks))
    }

    /** The viewed categories, most viewed first; a tie goes to the one viewed last, then to its slug. */
    private fun ranked(viewed: List<ProductView>): List<String> =
        viewed
            .withIndex()
            .groupBy({ it.value.categorySlug }, { it.index })
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, List<Int>>> {
                    it.value.size
                }.thenBy { it.value.min() }.thenBy { it.key },
            ).map { it.key }

    /** The popular products that fill the row after [already], under the same exclusions and cap. */
    private suspend fun popular(
        excluded: Set<String>,
        already: List<Listed>,
    ): List<Listed> {
        val room = TOTAL - already.size
        if (room <= 0) return emptyList()
        val perCategory = already.groupingBy { it.product.categorySlug }.eachCount().toMutableMap()
        val candidates =
            catalog.listed(sources.popular(excluded + already.map { it.product.id }, POPULAR_CANDIDATES))
        return buildList {
            for (item in candidates) {
                if (size == room) break
                val category = item.product.categorySlug
                if (!item.inStock || (perCategory[category] ?: 0) >= PER_CATEGORY) continue
                perCategory[category] = (perCategory[category] ?: 0) + 1
                add(item)
            }
        }
    }

    companion object {
        const val TOTAL = 6
        const val PER_CATEGORY = 2
        const val MIN_VIEWS = 3

        /** Enough popular products to fill a row under the cap per category; past it the row is short. */
        private const val POPULAR_CANDIDATES = 60

        private val TOP_RATED =
            compareByDescending<Listed> { it.product.rating }
                .thenByDescending { it.product.reviewsCount }
                .thenBy { it.product.id }
    }
}

/**
 * Records that [Viewer] opened a product page: a customer's view is stored, a guest's is not (a guest gets
 * no block). It must not cost the page its answer, so a failed write is logged and swallowed; the route
 * runs it beside the page's own reads rather than after them.
 */
internal class RecordView(
    private val views: ProductViews,
    private val clock: StoreClock,
) {
    suspend operator fun invoke(
        viewer: Viewer,
        productId: String,
    ) {
        val customerId = viewer.customerId ?: return
        try {
            views.record(customerId, productId, clock.now().toOffsetDateTime())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("a product view was not recorded (product {})", productId, e)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(RecordView::class.java)
    }
}
