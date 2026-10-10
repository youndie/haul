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
import io.github.youndie.haul.feature.catalog.domain.descendants
import io.github.youndie.haul.feature.catalog.domain.money
import io.github.youndie.haul.feature.catalog.screen.CatalogUrl
import io.github.youndie.haul.feature.catalog.screen.card
import io.github.youndie.haul.feature.catalog.screen.categoryLink
import io.github.youndie.haul.feature.catalog.screen.navigation
import io.github.youndie.haul.feature.catalog.screen.pagination
import io.github.youndie.haul.feature.catalog.screen.productLink
import io.github.youndie.haul.feature.search.domain.Query
import io.github.youndie.haul.feature.search.domain.RecentSearches
import io.github.youndie.haul.feature.search.domain.SearchRepository
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Parts
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

/**
 * What a search page was asked for, as the route parsed it; [query] is still raw. The grid holds the pages
 * [from] to [page] («Show N more», B-77).
 */
internal data class SearchRequest(
    val query: String?,
    val category: String?,
    val sort: Sort,
    val page: Int,
    val from: Int = page,
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
        val products = catalog.listed(matches.take(TOP_PRODUCTS).map { it.productId }, viewer.prices)
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
                        CategorySuggestion(label(leaf, categories), count(n), categoryLink(leaf, categories))
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

    /**
     * The results page, grouped by category with counts; or the page for a query that found nothing. A
     * category chip, a sort and a page load their [PARTS] in place (B-63); the sort ends the chips' row (B-77),
     * and a chip keeps it, as a category's facets keep theirs. A customer's search is recorded unless
     * [recorded] is `false`: the parts a `load` asks for, which changes nothing.
     *
     * The `category` is a scope: the results in it and in every category under it — a leaf's chip, or a
     * top-level category the header's picker chose (B-72), drawn as a chip of its own, chosen, after «All».
     */
    suspend fun results(
        request: SearchRequest,
        viewer: Viewer,
        recorded: Boolean = true,
    ): KompotComponent {
        val query = Query.of(request.query)
        if (recorded) viewer.customerId?.let { recent.record(it, query.text, clock.now().toOffsetDateTime()) }
        val categories = catalog.categories()
        val matches = search.matching(query)
        if (matches.isEmpty()) return noResults(query, categories, viewer)

        val all = catalog.listed(matches.map { it.productId }, viewer.prices)
        val scope =
            request.category?.let { slug ->
                categories.firstOrNull { it.slug == slug } ?: throw CatalogError.CategoryNotFound(slug)
            }
        val shown =
            scope?.let { categories.descendants(it) }?.let { within ->
                all.filter { it.product.categorySlug in within }
            }
                ?: all
        val page = browse.page(shown, Filters(), request.sort, request.page, request.from)
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
                        listOf(
                            Chip(
                                "All",
                                request.category == null,
                                parts(query.text, sort = request.sort),
                                count(all.size),
                            ),
                        ) +
                            listOfNotNull(
                                scope?.takeIf { s -> byLeaf.none { it.key == s.slug } }?.let {
                                    Chip(it.name, true, parts(query.text, it.slug, request.sort), count(shown.size))
                                },
                            ) +
                            byLeaf.mapNotNull { (slug, n) ->
                                val leaf = categories.firstOrNull { it.slug == slug } ?: return@mapNotNull null
                                Chip(
                                    leaf.name,
                                    request.category == slug,
                                    parts(query.text, slug, request.sort),
                                    count(n),
                                )
                            },
                    sortLabel = request.sort.label,
                    sorts = Sort.entries.map { Link(it.label, parts(query.text, request.category, it)) },
                ),
                ProductGrid(
                    "grid",
                    page.items.map { card(it, calendar, photos, viewer, query = query.text, scope = request.category) },
                    columns = GRID_COLUMNS,
                ),
                pagination(
                    page,
                    { searchLink(query.text, request.category, request.sort, it).deeplink },
                ) { _, to -> Parts.load(to) },
            )
        return Frame.page(
            "search",
            viewer,
            navigation(categories),
            sections,
            query = query.text,
            scope = request.category,
        )
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
                            CategoryTile("tile-${it.slug}", it.name, it.tone, it.label, categoryLink(it, categories))
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

    /** A category chip or a sort: the parts of the results in it, from their first page, loaded in place (B-63). */
    private fun parts(
        query: String,
        category: String? = null,
        sort: Sort = Sort.Popular,
    ) = Parts.load(searchLink(query, category, sort).deeplink)

    /** The search's address with the grid holding [pages] — one page, or those «Show N more» appended (B-77). */
    private fun searchLink(
        query: String,
        category: String? = null,
        sort: Sort = Sort.Popular,
        pages: IntRange = 1..1,
    ): NavigateAction {
        val q = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20")
        return NavigateAction(
            "/search?q=$q" +
                (category?.let { "&category=$it" } ?: "") +
                (if (sort != Sort.Popular) "&sort=${sort.key}" else "") +
                (if (pages.last > 1) "&page=${pages.last}" else "") +
                (if (pages.first < pages.last) "&${CatalogUrl.FROM}=${pages.first}" else ""),
        )
    }

    companion object {
        /**
         * What a category chip, a sort or a page changes on the results (B-63): the chips with the sort, the grid
         * and the pages — and the header, whose search picker reads the page's scope (B-72): «All» or a leaf's
         * chip after a top-level scope leaves it.
         */
        val PARTS = listOf("header", "categories", "grid", "pagination")

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
