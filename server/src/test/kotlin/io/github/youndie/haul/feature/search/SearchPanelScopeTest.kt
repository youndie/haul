package io.github.youndie.haul.feature.search

import io.github.youndie.haul.feature.catalog.domain.Sort
import io.github.youndie.haul.feature.search.data.PostgresSearchRepository
import io.github.youndie.haul.feature.search.domain.Query
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.testing.SeededDatabase
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.FilterChips
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.SearchSuggestPanel
import io.github.youndie.kompot.commands.LoadAction
import io.github.youndie.kompot.standard.NavigateAction
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B-103: the suggest panel keeps the query and the picker's scope, the search is ordered by relevance unless a
 * sort is chosen, and what is already chosen on the results carries nothing — against the seeded catalog.
 */
class SearchPanelScopeTest {
    private val catalog = CatalogSeed.generate(CatalogSeed.CANVAS_DAY)

    /** Every category under [slug], itself included. */
    private fun under(slug: String): Set<String> =
        generateSequence(setOf(slug)) { slugs ->
            (slugs + catalog.categories.filter { it.parentSlug in slugs }.map { it.slug }).takeIf { it != slugs }
        }.last()

    private fun categoryOf(productId: String): String = catalog.products.single { it.id == productId }.categorySlug

    /**
     * «In categories · Sports › Running shoes 12» counted the query's matches and opened the whole category; it
     * opens the query in that category, whose chip counts the same.
     */
    @Test
    fun `a category suggestion opens the query in that category`() =
        haulTest {
            val panel = tree("/ui/search/suggest?q=running%20sh") as SearchSuggestPanel
            val shoes = panel.categories.single { it.label == "Sports › Running shoes" }
            assertEquals(NavigateAction("/search?q=running%20sh&category=running-shoes"), shoes.action)
            val page = tree("/ui${(shoes.action as NavigateAction).deeplink}")
            val chip = page.only<FilterChips>().chips.single { it.label == "Running shoes" }
            assertTrue(chip.selected, "the suggestion's category is not the chosen chip")
            assertEquals(shoes.count, chip.count, "the suggestion and the page it opens count differently")
        }

    /**
     * The picker's scope (B-72) reaches the panel: its products and categories are under the scope, «All N results»
     * counts the scope's matches — the count the scope's chip shows on the page it opens — and every search the
     * panel opens stays in the scope.
     */
    @Test
    fun `the panel follows the picker's scope`() =
        haulTest {
            val everywhere = tree("/ui/search/suggest?q=everyday") as SearchSuggestPanel
            val sports = tree("/ui/search/suggest?q=everyday&category=sports") as SearchSuggestPanel
            val inSports = under("sports")
            assertNotEquals(everywhere.allResultsLabel, sports.allResultsLabel, "«everyday» is all in Sports")
            assertTrue(sports.products.isNotEmpty(), "no product in Sports")
            assertTrue(sports.products.all { categoryOf(it.productId) in inSports }, "a product outside Sports")
            assertTrue(
                sports.categories.all {
                    (it.action as NavigateAction).deeplink.substringAfter(
                        "category=",
                    ) in inSports
                },
                "a category outside Sports: ${sports.categories.map { it.label }}",
            )
            assertEquals(NavigateAction("/search?q=everyday&category=sports"), sports.allResultsAction)
            assertTrue(
                sports.suggestions.all { (it.action as NavigateAction).deeplink.endsWith("&category=sports") },
                "a query suggestion left the scope: ${sports.suggestions.map { it.action }}",
            )
            val chip =
                tree("/ui/search?q=everyday&category=sports").only<FilterChips>().chips.single { it.label == "Sports" }
            assertEquals("All ${chip.count} results", sports.allResultsLabel)
            assertEquals(NavigateAction("/search?q=everyday"), everywhere.allResultsAction)
        }

    /**
     * B-132: `running` under «Appliances · Classics» matches no product there, while its queries still suggest
     * «running shoes». The panel had an empty «Top products» and «All 0 results», which opened a page saying there
     * is nothing; a part with nothing in it is not sent.
     */
    @Test
    fun `a scope with no match offers no products and no all results`() =
        haulTest {
            val classics =
                catalog.categories.single { leaf ->
                    leaf.name == "Classics" &&
                        generateSequence(leaf) { c -> catalog.categories.firstOrNull { it.slug == c.parentSlug } }
                            .any { it.name == "Appliances" }
                }
            val matches = PostgresSearchRepository(SeededDatabase.dataSource).matching(Query.of("running"))
            assertTrue(matches.isNotEmpty(), "«running» matches nothing anywhere")
            assertTrue(matches.none { it.categorySlug in under(classics.slug) }, "«running» matches in Classics")

            val panel = tree("/ui/search/suggest?q=running&category=${classics.slug}") as SearchSuggestPanel
            assertTrue(panel.suggestions.isNotEmpty(), "the queries went with the products")
            assertEquals(emptyList(), panel.products)
            assertEquals("", panel.allResultsLabel)
            assertNull(panel.allResultsAction, "«All 0 results» offered")
        }

    @Test
    fun `a scope the catalog does not have is 404 on the panel too`() =
        haulTest {
            assertEquals(HttpStatusCode.NotFound, get("/ui/search/suggest?q=everyday&category=gone").status)
        }

    /**
     * The search's default is relevance, the order the database matched in, not «Popular»: the grid is the
     * matching's first page, «Relevance» is the first sort, and «Popular» is a sort of its own, in the address.
     */
    @Test
    fun `a search is ordered by relevance unless a sort is chosen`() =
        haulTest {
            val query = "pro"
            val relevant =
                PostgresSearchRepository(
                    SeededDatabase.dataSource,
                ).matching(Query.of(query)).map { it.productId }
            val page = tree("/ui/search?q=pro")
            val grid = page.only<ProductGrid>().cards.map { it.productId }
            assertEquals(relevant.take(grid.size), grid, "the grid is not in the order the database matched")
            assertEquals(
                grid,
                tree("/ui/search?q=pro&sort=relevance").only<ProductGrid>().cards.map { it.productId },
            )

            val chips = page.only<FilterChips>()
            assertEquals("Relevance", chips.sortLabel)
            assertEquals(listOf("Relevance") + Sort.entries.map { it.label }, chips.sorts.map { it.label })
            val popular = chips.sorts.single { it.label == Sort.Popular.label }.action
            assertEquals(LoadAction("/ui/parts/search?q=pro&sort=popular"), popular)
            val byReviews = tree("/ui/search?q=pro&sort=popular")
            assertNotEquals(
                grid,
                byReviews.only<ProductGrid>().cards.map {
                    it.productId
                },
                "«pro» reads the same by relevance and by reviews: nothing tells them apart",
            )
            assertEquals(
                LoadAction("/ui/parts/search?q=pro"),
                byReviews
                    .only<FilterChips>()
                    .sorts
                    .single { it.label == "Relevance" }
                    .action,
                "relevance is not the address without a sort",
            )
        }

    /**
     * B-102's rule on the search: the chip chosen — «All», a leaf, the picker's scope — and the sort shown are the
     * page already and carry nothing; every other chip and sort loads its page.
     */
    @Test
    fun `no press on the results loads the page it is on`() =
        haulTest {
            listOf(
                "/ui/search?q=everyday",
                "/ui/search?q=everyday&category=sports",
                "/ui/search?q=running%20shoes&category=running-shoes&sort=price-asc",
            ).forEach { path ->
                val chips = tree(path).only<FilterChips>()
                chips.chips.forEach { chip ->
                    if (chip.selected) {
                        assertNull(chip.action, "$path: the chosen «${chip.label}» loads the page it is on")
                    } else {
                        assertNotNull(chip.action, "$path: «${chip.label}» goes nowhere")
                    }
                }
                chips.sorts.forEach { sort ->
                    if (sort.label == chips.sortLabel) {
                        assertNull(sort.action, "$path: the sort shown loads the page it is on")
                    } else {
                        assertNotNull(sort.action, "$path: «${sort.label}» goes nowhere")
                    }
                }
                assertEquals(
                    1,
                    chips.chips.count { it.selected },
                    "$path: chosen chips ${chips.chips.filter { it.selected }}",
                )
            }
        }
}
