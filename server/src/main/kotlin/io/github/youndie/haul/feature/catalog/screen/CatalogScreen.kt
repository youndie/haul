package io.github.youndie.haul.feature.catalog.screen

import io.github.youndie.haul.feature.catalog.domain.Browse
import io.github.youndie.haul.feature.catalog.domain.CatalogError
import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.domain.Category
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.FacetKey
import io.github.youndie.haul.feature.catalog.domain.Filters
import io.github.youndie.haul.feature.catalog.domain.Listed
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.feature.catalog.domain.Sort
import io.github.youndie.haul.feature.catalog.domain.count
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.ui.AppliedFilters
import io.github.youndie.haul.ui.Breadcrumbs
import io.github.youndie.haul.ui.Chip
import io.github.youndie.haul.ui.Crumb
import io.github.youndie.haul.ui.EmptyState
import io.github.youndie.haul.ui.Facet
import io.github.youndie.haul.ui.FacetOption
import io.github.youndie.haul.ui.FacetPanel
import io.github.youndie.haul.ui.FilterChips
import io.github.youndie.haul.ui.FilteredResults
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.PageTitle
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.NavigateAction
import java.math.BigDecimal

/** What a category page was asked for, as the route parsed it. */
internal data class CatalogRequest(
    val path: String,
    val filters: Filters,
    val sort: Sort,
    val page: Int,
)

/**
 * `/ui/c/{categoryPath}` (screen-catalog): the category's products filtered, counted per facet, sorted
 * and paged. A filter set that matches nothing is `Catalog_Empty`, answered `200` with its facets —
 * feature-browse, «A filter set with no products».
 */
internal class CatalogScreen(
    private val catalog: CatalogRepository,
    private val browse: Browse,
    private val calendar: DeliveryCalendar,
    private val photos: ProductPhotos,
) {
    suspend fun build(
        request: CatalogRequest,
        viewer: Viewer,
    ): KompotComponent {
        val categories = catalog.categories()
        val slug = request.path.trimEnd('/').substringAfterLast('/')
        val category = categories.firstOrNull { it.slug == slug } ?: throw CatalogError.CategoryNotFound(slug)
        val all = catalog.listedIn(descendants(category, categories))
        val page = browse.page(all, request.filters, request.sort, request.page)
        val url = CatalogUrl(category.slug, request.filters, request.sort)

        val sections = mutableListOf<KompotComponent>()
        sections +=
            Breadcrumbs(
                "breadcrumbs",
                path(category, categories).map { Crumb(it.name, categoryLink(it.slug)) }.let {
                    listOf(Crumb("Home", NavigateAction("/"))) +
                        it
                },
            )
        sections += PageTitle("title", category.name, "${count(page.total)} items")
        kinds(all, request, url)?.let { sections += it }
        val applied =
            AppliedFilters(
                id = "applied",
                chips = applied(request.filters, url),
                clearLabel = "Clear all",
                sortLabel = request.sort.label,
                filterCount = request.filters.count,
                clearAction = NavigateAction(url.cleared()),
                sorts = Sort.entries.map { Link(it.label, NavigateAction(url.sorted(it))) },
            )
        // One component for the facets and the results, because a wide page puts them side by side and
        // a phone moves the facets into a sheet (B-07).
        sections +=
            if (page.items.isEmpty()) {
                FilteredResults(
                    id = "results",
                    facets = facets(all, request.filters, url),
                    applied = applied,
                    showLabel = "Show 0 items",
                    empty =
                        EmptyState(
                            "empty",
                            "No items match these filters",
                            "Try removing a filter or two.",
                            "Clear all",
                            NavigateAction(url.cleared()),
                            accent = "filters",
                        ),
                )
            } else {
                FilteredResults(
                    id = "results",
                    facets = facets(all, request.filters, url),
                    applied = applied,
                    showLabel = "Show ${count(page.total)} items",
                    grid =
                        ProductGrid(
                            "grid",
                            page.items.map { card(it, calendar, photos, viewer.inCart) },
                            columns = GRID_COLUMNS,
                        ),
                    pagination = pagination(page, url::page),
                )
            }
        return Frame.page("catalog", viewer, navigation(categories), sections)
    }

    private fun descendants(
        category: Category,
        categories: List<Category>,
    ): Set<String> {
        val children = categories.filter { it.parentSlug == category.slug }
        return setOf(category.slug) + children.flatMap { descendants(it, categories) }
    }

    private fun path(
        category: Category,
        categories: List<Category>,
    ): List<Category> =
        generateSequence(category) { c ->
            categories.firstOrNull {
                it.slug == c.parentSlug
            }
        }.toList().reversed()

    private fun kinds(
        all: List<Listed>,
        request: CatalogRequest,
        url: CatalogUrl,
    ): FilterChips? {
        val kinds = all.mapNotNull { it.product.kind }.distinct().sorted()
        if (kinds.isEmpty()) return null
        return FilterChips(
            id = "kinds",
            chips =
                listOf(
                    Chip(
                        "All",
                        request.filters.kind == null,
                        NavigateAction(url.with(request.filters.copy(kind = null))),
                    ),
                ) +
                    kinds.map {
                        Chip(
                            it,
                            request.filters.kind == it,
                            NavigateAction(url.with(request.filters.copy(kind = it))),
                        )
                    },
        )
    }

    private fun facets(
        all: List<Listed>,
        filters: Filters,
        url: CatalogUrl,
    ): FacetPanel {
        val brands = browse.counts(all, filters, FacetKey.Brand) { listOf(it.product.brand) }
        val colours = browse.counts(all, filters, FacetKey.Colour) { it.colours }
        val features = browse.counts(all, filters, FacetKey.Feature) { it.product.features }
        val ceiling = priceCeiling(all)
        val tomorrow = all.count { browse.matches(it, filters, except = FacetKey.Delivery) && calendar.isTomorrow(it) }
        // The six brands with the most products, and any brand that is ticked, wherever it ranks.
        val shownBrands =
            brands.entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, Int>> {
                        it.key in filters.brands
                    }.thenByDescending { it.value }.thenBy { it.key },
                ).take(maxOf(BRANDS_SHOWN, filters.brands.size))
        return FacetPanel(
            id = "facets",
            facets =
                listOf(
                    Facet(
                        "price",
                        "Price",
                        "range",
                        min =
                            filters.priceMinDollars?.let {
                                "$$it"
                            },
                        max = filters.priceMaxDollars?.let { "$$it" },
                        rangeStart = track(filters.priceMinDollars, ceiling) ?: 0f,
                        rangeEnd = track(filters.priceMaxDollars, ceiling) ?: 1f,
                    ),
                    Facet(
                        key = "brand",
                        title = "Brand",
                        kind = "checkbox",
                        options =
                            shownBrands.map { (brand, n) ->
                                FacetOption(
                                    brand,
                                    n,
                                    brand in filters.brands,
                                    action =
                                        toggle(url, filters) {
                                            copy(brands = this.brands.toggle(brand))
                                        },
                                )
                            },
                        moreLabel = (brands.size - shownBrands.size).takeIf { it > 0 }?.let { "Show $it more" },
                    ),
                    Facet(
                        key = "delivery",
                        title = "Delivery",
                        kind = "toggle",
                        options =
                            listOf(
                                FacetOption(
                                    "Tomorrow",
                                    tomorrow,
                                    filters.deliveryTomorrow,
                                    action =
                                        toggle(
                                            url,
                                            filters,
                                        ) {
                                            copy(deliveryTomorrow = !deliveryTomorrow)
                                        },
                                ),
                            ),
                    ),
                    Facet(
                        key = "rating",
                        title = "Rating",
                        kind = "radio",
                        options =
                            RATINGS.map { (label, value) ->
                                FacetOption(
                                    label,
                                    selected = filters.ratingAtLeast == value,
                                    action =
                                        toggle(
                                            url,
                                            filters,
                                        ) {
                                            copy(ratingAtLeast = value)
                                        },
                                )
                            },
                    ),
                    Facet(
                        key = "colour",
                        title = "Color",
                        kind = "swatch",
                        options =
                            colours.keys.sorted().map {
                                FacetOption(
                                    it,
                                    colours[it],
                                    it in filters.colours,
                                    SWATCHES[it],
                                    toggle(url, filters) {
                                        copy(colours = this.colours.toggle(it))
                                    },
                                )
                            },
                    ),
                    Facet(
                        key = "feature",
                        title = "Features",
                        kind = "pills",
                        options =
                            features.keys.sorted().map {
                                FacetOption(
                                    it,
                                    features[it],
                                    it in filters.features,
                                    action =
                                        toggle(url, filters) {
                                            copy(features = this.features.toggle(it))
                                        },
                                )
                            },
                    ),
                ),
        )
    }

    /** The slider's track runs from $0 to the category's dearest price, rounded up to the next $100. */
    private fun priceCeiling(all: List<Listed>): Int {
        val dearest = (all.maxOfOrNull { it.shown.priceCents } ?: 0) / CENTS
        return (dearest / PRICE_STEP + 1) * PRICE_STEP
    }

    private fun track(
        dollars: Int?,
        ceiling: Int,
    ): Float? = dollars?.let { (it.toFloat() / ceiling).coerceIn(0f, 1f) }

    private fun applied(
        filters: Filters,
        url: CatalogUrl,
    ): List<Chip> =
        filters.brands.sorted().map { Chip(it, true, toggle(url, filters) { copy(brands = brands - it) }) } +
            listOfNotNull(
                if (filters.priceMinDollars != null || filters.priceMaxDollars != null) {
                    Chip(
                        "$${filters.priceMinDollars ?: 0} – $${filters.priceMaxDollars ?: "∞"}",
                        true,
                        toggle(
                            url,
                            filters,
                        ) {
                            copy(priceMinDollars = null, priceMaxDollars = null)
                        },
                    )
                } else {
                    null
                },
            ) +
            filters.features.sorted().map { Chip(it, true, toggle(url, filters) { copy(features = features - it) }) } +
            filters.colours.sorted().map { Chip(it, true, toggle(url, filters) { copy(colours = colours - it) }) }

    private fun toggle(
        url: CatalogUrl,
        filters: Filters,
        change: Filters.() -> Filters,
    ): NavigateAction = NavigateAction(url.with(filters.change()))

    private fun Set<String>.toggle(value: String): Set<String> = if (value in this) this - value else this + value

    companion object {
        private const val GRID_COLUMNS = 4
        private const val BRANDS_SHOWN = 6
        private const val CENTS = 100
        private const val PRICE_STEP = 100
        private val RATINGS =
            listOf(
                "4.5 and up" to BigDecimal("4.5"),
                "4.0 and up" to BigDecimal("4.0"),
                "Any rating" to null,
            )

        // The canvas's product swatches (`canvas.json`, `productSwatches`).
        private val SWATCHES =
            mapOf(
                "Midnight Black" to "#2A2A2E",
                "Silver" to "#D9D3C7",
                "Black" to "#0F0F0F",
                "White" to "#FFFFFF",
                "Beige" to "#B9B2A4",
                "Navy" to "#2E4A7A",
                "Pink" to "#E8C9C2",
                "Olive" to "#5B6B4E",
            )
    }
}

/**
 * A category page's address with its filters and sort, the form every facet's, sort's and page's action
 * navigates to. A change of filters or sort starts again from the first page.
 */
internal class CatalogUrl(
    private val slug: String,
    private val filters: Filters,
    private val sort: Sort,
) {
    fun with(next: Filters): String = render(next)

    /** No filters, the sort kept: «Clear all». */
    fun cleared(): String = render(Filters())

    fun sorted(next: Sort): String = render(filters, next)

    /** The same filters and sort at page [n]. */
    fun page(n: Int): String = render(filters, sort, n)

    private fun render(
        f: Filters,
        sort: Sort = this.sort,
        page: Int = 1,
    ): String {
        val params =
            f.brands.sorted().map { "brand=$it" } +
                f.features.sorted().map { "feature=$it" } +
                f.colours.sorted().map { "colour=$it" } +
                listOfNotNull(
                    f.kind?.let { "kind=$it" },
                    f.priceMinDollars?.let { "price_min=$it" },
                    f.priceMaxDollars?.let { "price_max=$it" },
                    if (f.deliveryTomorrow) "delivery=tomorrow" else null,
                    f.ratingAtLeast?.let { "rating=${it.toPlainString()}" },
                    if (sort != Sort.Popular) "sort=${sort.key}" else null,
                    if (page > 1) "page=$page" else null,
                )
        val query = params.joinToString("&") { it.replace(" ", "%20") }
        return "/c/$slug" + if (query.isEmpty()) "" else "?$query"
    }
}
