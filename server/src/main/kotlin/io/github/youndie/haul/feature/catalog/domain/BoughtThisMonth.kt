package io.github.youndie.haul.feature.catalog.domain

import io.github.youndie.haul.StoreClock
import java.time.OffsetDateTime

/** The orders side of «bought this month»: one aggregate over a product's order lines. */
internal fun interface ProductSales {
    /**
     * The units of [productId], over all its SKUs, in the orders placed after [from] and up to [until] that
     * the saga placed: an order still `placing` may yet be declined, and a `cancelled` one bought nothing.
     * A returned order still counts — it was bought, and «bought this month» says no more than that.
     */
    suspend fun unitsPlaced(
        productId: String,
        from: OffsetDateTime,
        until: OffsetDateTime,
    ): Int
}

/**
 * «12K bought this month» under the product page's rating (B-52): the units of the product in the orders
 * placed in the last [WINDOW_DAYS] store days and not cancelled, plus the product's [Product.boughtBase],
 * abbreviated as the canvas writes it ([compactCount]); absent below [THRESHOLD], so a quiet product does
 * not advertise its quietness. Only the product page asks — the cards do not show it — so it costs one
 * query per page, never one per card.
 */
internal class BoughtThisMonth(
    private val sales: ProductSales,
    private val clock: StoreClock,
) {
    suspend fun label(product: Product): String? {
        val now = clock.now()
        val units =
            product.boughtBase +
                sales.unitsPlaced(product.id, now.minusDays(WINDOW_DAYS).toOffsetDateTime(), now.toOffsetDateTime())
        return if (units < THRESHOLD) null else "${compactCount(units)} bought this month"
    }

    companion object {
        /** Days by the store's clock, in its zone, so a daylight-saving change does not move the window by an hour. */
        const val WINDOW_DAYS = 30L
        const val THRESHOLD = 50
    }
}
