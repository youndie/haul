package io.github.youndie.haul.feature.catalog.domain

import java.math.BigDecimal

/** The filters a category page is asked for; every set is «any of», and the sets combine with «and». */
internal data class Filters(
    val brands: Set<String> = emptySet(),
    val priceMinDollars: Int? = null,
    val priceMaxDollars: Int? = null,
    val features: Set<String> = emptySet(),
    val colours: Set<String> = emptySet(),
    val kind: String? = null,
    val deliveryTomorrow: Boolean = false,
    val ratingAtLeast: BigDecimal? = null,
) {
    val count: Int
        get() =
            brands.size + features.size + colours.size +
                (if (priceMinDollars != null || priceMaxDollars != null) 1 else 0) +
                (if (deliveryTomorrow) 1 else 0) + (if (ratingAtLeast != null) 1 else 0)
}

internal enum class Sort(
    val key: String,
    val label: String,
) {
    Popular("popular", "Popular"),
    PriceAscending("price-asc", "Price: low to high"),
    PriceDescending("price-desc", "Price: high to low"),
    Rating("rating", "Rating"),
    Newest("newest", "Newest"),
    ;

    companion object {
        fun of(key: String): Sort? = entries.firstOrNull { it.key == key }
    }
}

/** Which facet a count leaves out: each facet is counted over every filter but its own. */
internal enum class FacetKey { Brand, Price, Feature, Colour, Kind, Delivery, Rating }

internal data class Page(
    val items: List<Listed>,
    val total: Int,
    val page: Int,
    val pages: Int,
)

/**
 * Filtering, facet counts, sorting and paging of one category's products, in memory.
 *
 * In memory on purpose: a leaf category of the seed holds tens of products, and a facet count is «the
 * filters minus one», which is one pass per facet here and one query per facet in SQL. Research D3
 * says when this stops being true.
 */
internal class Browse(
    private val calendar: DeliveryCalendar,
) {
    fun matches(
        item: Listed,
        filters: Filters,
        except: FacetKey? = null,
    ): Boolean {
        val dollars = item.shown.priceCents / CENTS
        return (except == FacetKey.Brand || filters.brands.isEmpty() || item.product.brand in filters.brands) &&
            (except == FacetKey.Price || filters.priceMinDollars == null || dollars >= filters.priceMinDollars) &&
            (except == FacetKey.Price || filters.priceMaxDollars == null || dollars <= filters.priceMaxDollars) &&
            (except == FacetKey.Feature || item.product.features.containsAll(filters.features)) &&
            (except == FacetKey.Colour || filters.colours.isEmpty() || item.colours.any { it in filters.colours }) &&
            (except == FacetKey.Kind || filters.kind == null || item.product.kind == filters.kind) &&
            (except == FacetKey.Delivery || !filters.deliveryTomorrow || calendar.isTomorrow(item)) &&
            (except == FacetKey.Rating || filters.ratingAtLeast == null || item.product.rating >= filters.ratingAtLeast)
    }

    fun page(
        all: List<Listed>,
        filters: Filters,
        sort: Sort,
        page: Int,
    ): Page {
        val matching = all.filter { matches(it, filters) }.sortedWith(comparator(sort))
        val pages = maxOf(1, (matching.size + PAGE_SIZE - 1) / PAGE_SIZE)
        return Page(matching.drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE), matching.size, page, pages)
    }

    /**
     * How many products each value of [key] would leave, with the other filters as they are — for every
     * value the category has, so a value the other filters rule out is listed with 0 rather than
     * dropped: the facets of an empty result are what the shopper undoes it with (`Catalog_Empty`).
     */
    fun counts(
        all: List<Listed>,
        filters: Filters,
        key: FacetKey,
        valuesOf: (Listed) -> Collection<String>,
    ): Map<String, Int> {
        val matching =
            all
                .filter { matches(it, filters, except = key) }
                .flatMap { item -> valuesOf(item).distinct() }
                .groupingBy { it }
                .eachCount()
        return all.flatMap(valuesOf).distinct().associateWith { matching[it] ?: 0 }
    }

    private fun comparator(sort: Sort): Comparator<Listed> =
        when (sort) {
            Sort.Popular -> compareByDescending<Listed> { it.product.reviewsCount }
            Sort.PriceAscending -> compareBy { it.shown.priceCents }
            Sort.PriceDescending -> compareByDescending { it.shown.priceCents }
            Sort.Rating -> compareByDescending { it.product.rating }
            Sort.Newest -> compareByDescending { it.product.createdAt }
        }.thenBy { it.product.id }

    companion object {
        const val PAGE_SIZE = 24
        const val CENTS = 100
    }
}
