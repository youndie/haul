package io.github.youndie.haul.feature.search.screen

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.feature.catalog.domain.Browse
import io.github.youndie.haul.feature.catalog.domain.CatalogError
import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.domain.Category
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.Filters
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.feature.catalog.domain.Sort
import io.github.youndie.haul.feature.catalog.domain.count
import io.github.youndie.haul.feature.catalog.domain.money
import io.github.youndie.haul.feature.catalog.screen.card
import io.github.youndie.haul.feature.catalog.screen.categoryLink
import io.github.youndie.haul.feature.catalog.screen.navigation
import io.github.youndie.haul.feature.catalog.screen.pagination
import io.github.youndie.haul.feature.catalog.screen.productLink
import io.github.youndie.haul.feature.search.domain.Query
import io.github.youndie.haul.feature.search.domain.RecentSearches
import io.github.youndie.haul.feature.search.domain.SearchRepository
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.ui.CategoryGrid
import io.github.youndie.haul.ui.CategorySuggestion
import io.github.youndie.haul.ui.CategoryTile
import io.github.youndie.haul.ui.Chip
import io.github.youndie.haul.ui.FilterChips
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.PageTitle
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.ProductSuggestion
import io.github.youndie.haul.ui.QuerySuggestion
import io.github.youndie.haul.ui.SearchNoResults
import io.github.youndie.haul.ui.SearchSuggestPanel
import io.github.youndie.haul.ui.SectionHeader
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.NavigateAction
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** What a search page was asked for, as the route parsed it; [query] is still raw. */
internal data class SearchRequest(
    val query: String?,
    val category: String?,
    val sort: Sort,
    val page: Int,
)

/**
 * `/ui/search` and `/ui/search/suggest` (screen-search, feature-search). Matching is the database's
 * ([SearchRepository]); grouping by category, sorting and paging are the catalog's, on the matched
 * products. A signed-in customer's search is recorded in [RecentSearches]; a guest's is not.
 */
internal class SearchScreen(
    private val catalog: CatalogRepository,
    private val search: SearchRepository,
    private val recent: RecentSearches,
    private val browse: Browse,
    private val calendar: DeliveryCalendar,
    private val photos: ProductPhotos,
    private val clock: StoreClock,
) {
    /** The suggest panel: queries, categories with counts, recent searches, top products. */
    suspend fun suggest(
        raw: String?,
        viewer: Viewer,
    ): SearchSuggestPanel {
        val query = Query.of(raw)
        val matches = search.matching(query)
        val categories = catalog.categories()
        val products = catalog.listed(matches.take(TOP_PRODUCTS).map { it.productId })
        val recentSearches = viewer.customerId?.let { recent.list(it) }.orEmpty()
        return SearchSuggestPanel(
            id = "suggest",
            query = query.text,
            suggestions = search.terms(query, QUERIES).map { suggestion(it, query) },
            categories =
                matches
                    .groupingBy { it.categorySlug }
                    .eachCount()
                    .entries
                    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                    .take(CATEGORIES)
                    .mapNotNull { (slug, n) ->
                        val leaf = categories.firstOrNull { it.slug == slug } ?: return@mapNotNull null
                        CategorySuggestion(label(leaf, categories), count(n), categoryLink(slug))
                    },
            products =
                products.map {
                    ProductSuggestion(
                        productId = it.product.id,
                        title = it.product.listingName,
                        price = money(it.shown.priceCents),
                        tone = it.product.tone,
                        label = it.product.label,
                        action = productLink(it.product.id),
                    )
                },
            allResultsLabel = "All ${count(matches.size)} results",
            // Each recent search runs again from its row (B-49): the tree carries the address.
            recent = recentSearches.map { Link(it, searchLink(it)) },
            allResultsAction = searchLink(query.text),
            clearUrl = if (recentSearches.isEmpty()) null else RECENT_SEARCHES,
        )
    }

    /** The results page, grouped by category with counts; or the page for a query that found nothing. */
    suspend fun results(
        request: SearchRequest,
        viewer: Viewer,
    ): KompotComponent {
        val query = Query.of(request.query)
        viewer.customerId?.let { recent.record(it, query.text, clock.now().toOffsetDateTime()) }
        val categories = catalog.categories()
        val matches = search.matching(query)
        if (matches.isEmpty()) return noResults(query, categories, viewer)

        val all = catalog.listed(matches.map { it.productId })
        request.category?.let { slug ->
            if (categories.none { it.slug == slug }) throw CatalogError.CategoryNotFound(slug)
        }
        val shown = request.category?.let { slug -> all.filter { it.product.categorySlug == slug } } ?: all
        val page = browse.page(shown, Filters(), request.sort, request.page)
        val byLeaf =
            all
                .groupingBy { it.product.categorySlug }
                .eachCount()
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })

        val sections =
            listOf(
                PageTitle(
                    "title",
                    query.text.replaceFirstChar { it.uppercase() },
                    "${count(all.size)} results",
                    quoted = true,
                ),
                FilterChips(
                    id = "categories",
                    chips =
                        listOf(Chip("All", request.category == null, searchLink(query.text), count(all.size))) +
                            byLeaf.mapNotNull { (slug, n) ->
                                val leaf = categories.firstOrNull { it.slug == slug } ?: return@mapNotNull null
                                Chip(leaf.name, request.category == slug, searchLink(query.text, slug), count(n))
                            },
                ),
                ProductGrid(
                    "grid",
                    page.items.map { card(it, calendar, photos, viewer) },
                    columns = GRID_COLUMNS,
                ),
                pagination(page) { searchLink(query.text, request.category, request.sort, it).deeplink },
            )
        return Frame.page("search", viewer, navigation(categories), sections, query = query.text)
    }

    private suspend fun noResults(
        query: Query,
        categories: List<Category>,
        viewer: Viewer,
    ): KompotComponent {
        val topLevel = categories.filter { it.parentSlug == null }.sortedBy { it.position }
        val sections =
            listOf(
                SearchNoResults(
                    id = "no-results",
                    query = query.text,
                    title = "Nothing found for “${query.text}”",
                    suggestions = search.terms(query, QUERIES).map { suggestion(it, query) },
                    tips = TIPS,
                    count = "0 results",
                    accent = "“${query.text}”",
                ),
                SectionHeader("popular-title", "Popular categories", accent = "categories"),
                CategoryGrid(
                    id = "popular",
                    tiles =
                        topLevel.take(POPULAR_CATEGORIES).map {
                            CategoryTile("tile-${it.slug}", it.name, it.tone, it.label, categoryLink(it.slug))
                        },
                ),
            )
        return Frame.page("search", viewer, navigation(categories), sections, query = query.text)
    }

    /**
     * «running sh» typed, «oes» completed; «shoes» typed inside «running shoes» has «running » before it.
     * A term that does not contain the query (a corrected spelling) is all completion.
     */
    private fun suggestion(
        term: String,
        query: Query,
    ): QuerySuggestion {
        val at = term.indexOf(query.text)
        if (at < 0) return QuerySuggestion("", term, searchLink(term))
        return QuerySuggestion(
            typed = query.text,
            completion = term.substring(at + query.text.length),
            action = searchLink(term),
            prefix = term.substring(0, at),
        )
    }

    /** «Sports › Running shoes»: the top-level category and the leaf. */
    private fun label(
        leaf: Category,
        categories: List<Category>,
    ): String {
        val top =
            generateSequence(leaf) { c -> categories.firstOrNull { it.slug == c.parentSlug } }.last()
        return if (top.slug == leaf.slug) leaf.name else "${top.name} › ${leaf.name}"
    }

    private fun searchLink(
        query: String,
        category: String? = null,
        sort: Sort = Sort.Popular,
        page: Int = 1,
    ): NavigateAction {
        val q = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20")
        return NavigateAction(
            "/search?q=$q" +
                (category?.let { "&category=$it" } ?: "") +
                (if (sort != Sort.Popular) "&sort=${sort.key}" else "") +
                (if (page > 1) "&page=$page" else ""),
        )
    }

    companion object {
        /** Where «Clear» on recent searches sends its `DELETE` (endpoint-search, the customer tier). */
        const val RECENT_SEARCHES = "/api/v1/me/recent-searches"

        /** feature-search: up to five queries, three categories, three products. */
        const val QUERIES = 5
        const val CATEGORIES = 3
        const val TOP_PRODUCTS = 3
        private const val GRID_COLUMNS = 5
        private const val POPULAR_CATEGORIES = 8

        private val TIPS =
            listOf(
                "Check the spelling",
                "Use fewer or more general words",
                "Browse a category below",
            )
    }
}
