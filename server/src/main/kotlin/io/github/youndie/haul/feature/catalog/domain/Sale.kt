package io.github.youndie.haul.feature.catalog.domain

/**
 * What is on sale in [listed]: every product whose shown price is under its old one — a markdown of the sale or a
 * deal of the day (B-57) — the deepest discount first, then the most reviewed, then by id. The deals page lists
 * exactly these under «On sale» and counts them in its title («832 items on sale»), and the sale's figures
 * ([SaleFigures]) are taken from the same list, so the home's hero and the deals page cannot count apart (B-172).
 */
internal fun onSale(listed: List<Listed>): List<Listed> =
    listed
        .filter { markdown(it) > 0.0 }
        .sortedWith(
            compareByDescending<Listed> { markdown(it) }
                .thenByDescending { it.product.reviewsCount }
                .thenBy { it.product.id },
        )

/** How far the shown price is under the old one, as a fraction of the old; 0 when it is not. */
private fun markdown(item: Listed): Double {
    val old = item.shown.oldPriceCents ?: return 0.0
    return if (old > item.shown.priceCents) (old - item.shown.priceCents).toDouble() / old else 0.0
}

/**
 * The sale's figures as the catalogue holds them (B-172): how many products are on sale for the viewer, in how many
 * top-level categories, and the deepest discount among them, written as its card's badge writes it («−49%»).
 *
 * A campaign's copy names them rather than stating them — «Up to {deepest}», «{items} marked down across
 * {categories}» — and [fill] writes them in. The seed's hero said «Up to −70%» and «1.2 million items marked down
 * across 32 categories» beside a deals page of 832 at most −49 % off: words a visitor checks against the page they
 * open next.
 */
internal data class SaleFigures(
    val items: Int,
    val categories: Int,
    val deepest: String?,
) {
    /** [copy] with its figures written in: «832 items», «32 categories», «−49%». A copy without them is unchanged. */
    fun fill(copy: String): String =
        copy
            .replace(ITEMS, items(items))
            .replace(CATEGORIES, if (categories == 1) "1 category" else "$categories categories")
            .replace(DEEPEST, deepest ?: "")

    companion object {
        const val ITEMS = "{items}"
        const val CATEGORIES = "{categories}"
        const val DEEPEST = "{deepest}"

        /** The figures of [sale] — what [onSale] returns — over the store's [categories]. */
        fun of(
            sale: List<Listed>,
            categories: List<Category>,
        ): SaleFigures {
            val bySlug = categories.associateBy { it.slug }
            val tops =
                sale
                    .mapNotNull { bySlug[it.product.categorySlug] }
                    .map { categories.lineage(it).first().slug }
                    .toSet()
            val deepest = sale.firstOrNull()?.shown?.let { discount(it.priceCents, it.oldPriceCents) }
            return SaleFigures(sale.size, tops.size, deepest)
        }
    }
}
