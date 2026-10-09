package io.github.youndie.haul.feature.search

import io.github.youndie.haul.ErrorBody
import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCatalog.SONY_HEADPHONES
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.CategoryGrid
import io.github.youndie.haul.ui.FilterChips
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.PageTitle
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.QuerySuggestion
import io.github.youndie.haul.ui.SearchNoResults
import io.github.youndie.haul.ui.SearchSuggestPanel
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** feature-search's scenarios, against the seeded catalog in PostgreSQL. */
class SearchRoutesTest {
    private val catalog = CatalogSeed.generate(CatalogSeed.CANVAS_DAY)

    /** Scenario «Typing suggests». */
    @Test
    fun `typing suggests the query and the category`() =
        haulTest {
            val panel = tree("/ui/search/suggest?q=running%20sh") as SearchSuggestPanel
            val first = panel.suggestions.first()
            assertEquals("running shoes", first.typed + first.completion)
            assertEquals("running sh", first.typed, "the typed part is drawn apart from the completion")
            assertTrue(
                panel.categories.any { it.label == "Sports › Running shoes" },
                "categories were ${panel.categories.map { it.label }}",
            )
        }

    /**
     * A suggestion whose term holds the query after its first word is drawn with the typed part regular
     * between two bold ones («**trail** running shoes», Search_Autocomplete). Treating it as a
     * misspelling made the whole term bold, which said the shopper had typed none of it.
     */
    @Test
    fun `a query typed inside a suggestion keeps what comes before it apart`() =
        haulTest {
            val panel = tree("/ui/search/suggest?q=shoes") as SearchSuggestPanel
            val inside = panel.suggestions.firstOrNull { it.prefix.isNotEmpty() }
            assertEquals(
                QuerySuggestion("shoes", "", inside?.action, prefix = "running "),
                inside,
                "suggestions were ${panel.suggestions}",
            )
        }

    /**
     * Full text, not a substring: the words in any order, across the title and the brand, the last one
     * still being typed. «headphones son» is in no title as written; the Sony headphones match because
     * `headphones` is in the title, `son` is the start of the brand, and every word is a prefix.
     */
    @Test
    fun `typed words match in any order across title and brand`() =
        haulTest {
            val panel = tree("/ui/search/suggest?q=headphones%20son") as SearchSuggestPanel
            assertTrue(
                panel.products.any { it.productId == "p-sony-wh-1000xm6" },
                "the Sony headphones are not among ${panel.products.map { it.title }}",
            )
        }

    /** The panel's limits (feature-search): five queries, three categories, three products. */
    @Test
    fun `the panel keeps to its limits and counts every result`() =
        haulTest {
            val panel = tree("/ui/search/suggest?q=sony") as SearchSuggestPanel
            assertTrue(panel.suggestions.size <= 5)
            assertTrue(panel.categories.size <= 3)
            assertEquals(3, panel.products.size, "sony matches more than three products")
            // A guest has no recent searches (feature-search); sign-in arrives with B-12.
            assertTrue(panel.recent.isEmpty())
            val sony = catalog.products.count { it.brand == "Sony" || it.title.contains("sony", ignoreCase = true) }
            assertEquals("All $sony results", panel.allResultsLabel)
        }

    /** Scenario «Too short», on both routes. */
    @Test
    fun `a one character query is 400 query_too_short`() =
        haulTest {
            val paths = listOf("/ui/search/suggest?q=r", "/ui/search?q=r", "/ui/search/suggest?q=%20r%20", "/ui/search")
            paths.forEach { path ->
                val response = get(path)
                assertEquals(HttpStatusCode.BadRequest, response.status, path)
                assertEquals(
                    ErrorCode.QueryTooShort,
                    haulWireJson.decodeFromString(ErrorBody.serializer(), response.bodyAsText()).code,
                    path,
                )
            }
        }

    /** Results group by category with counts, and the grid is the catalog's card. */
    @Test
    fun `results are grouped by category with counts`() =
        haulTest {
            val page = tree("/ui/search?q=running%20shoes")
            val chips = page.only<FilterChips>().chips
            assertEquals("All", chips.first().label)
            val total =
                chips
                    .first()
                    .count!!
                    .replace(",", "")
                    .toInt()
            assertEquals(
                total,
                chips.drop(1).sumOf { it.count!!.replace(",", "").toInt() },
                "the category counts do not add up",
            )
            assertTrue(
                chips.any { it.label == "Running shoes" },
                "no «Running shoes» chip in ${chips.map { it.label }}",
            )
            assertEquals("$total results", page.only<PageTitle>().count)
            assertTrue(page.only<PageTitle>().quoted, "a search's title is not drawn as the query")
            val cards = page.only<ProductGrid>().cards
            assertTrue(cards.isNotEmpty())
            assertEquals("running shoes", page.only<HaulHeader>().query, "the header lost the query")

            val narrowed = tree("/ui/search?q=running%20shoes&category=running-shoes").only<ProductGrid>().cards
            assertTrue(
                narrowed.all { card ->
                    catalog.products.single { it.id == card.productId }.categorySlug ==
                        "running-shoes"
                },
                "the category chip did not narrow the grid",
            )
        }

    /** «No results → the page says so and offers the suggestions for the query.» */
    @Test
    fun `a query that finds nothing answers the no results page`() =
        haulTest {
            val page = tree("/ui/search?q=xqzt")
            assertTrue(page.all().none { it is ProductGrid }, "a grid was drawn for nothing")
            val none = page.only<SearchNoResults>()
            assertEquals("Nothing found for “xqzt”", none.title)
            assertEquals("“xqzt”", none.accent, "the quoted query is the title's italic")
            assertEquals("0 results", none.count)
            assertEquals(3, none.tips.size)
            assertEquals(8, page.only<CategoryGrid>().tiles.size)
        }

    /** A misspelt query still finds its category name to suggest: the trigram half of the matching. */
    @Test
    fun `a misspelt query is offered the spelling the catalog has`() =
        haulTest {
            val page = tree("/ui/search?q=heaphones")
            val suggestions =
                (
                    page
                        .all()
                        .filterIsInstance<SearchNoResults>()
                        .singleOrNull()
                        ?.suggestions
                ).orEmpty()
            val panel = tree("/ui/search/suggest?q=heaphones") as SearchSuggestPanel
            assertTrue(
                (suggestions + panel.suggestions).any { it.typed + it.completion == "headphones" },
                "no «headphones» for «heaphones»",
            )
        }

    /**
     * Search reads what the cards write (B-45): the full text is over the listing name, so words only the
     * listing name holds find the product typed in any order and unfinished. The document over the title
     * found nothing here, and a shopper who types what a card says («Sony …») is answered by the brand alone.
     */
    @Test
    fun `words only the listing name holds find the product in any order`() =
        withListingName("Sony Zephyrine Quietude Headphones") { database ->
            haulTest(database) {
                val panel = tree("/ui/search/suggest?q=quietude%20zephyr") as SearchSuggestPanel
                assertTrue(
                    panel.products.any { it.productId == SONY_HEADPHONES },
                    "the listing name's words found ${panel.products.map { it.title }}",
                )
            }
        }

    /**
     * The substring half reads the listing name too (B-45): a piece cut out of the middle of its words is no
     * word to full text, and only `lower(listing_name) LIKE` finds it.
     */
    @Test
    fun `a piece of the listing name finds the product`() =
        withListingName("Sony Zephyrine Quietude Headphones") { database ->
            haulTest(database) {
                val panel = tree("/ui/search/suggest?q=phyrine%20qui") as SearchSuggestPanel
                assertTrue(
                    panel.products.any { it.productId == SONY_HEADPHONES },
                    "a piece of the listing name found ${panel.products.map { it.title }}",
                )
            }
        }

    /** The headphones under [listingName], in a database of their own. */
    private fun withListingName(
        listingName: String,
        block: (DataSource) -> Unit,
    ) = seededFreshDatabase().use { database ->
        database.connection.use { connection ->
            connection.prepareStatement("UPDATE products SET listing_name = ? WHERE id = ?").use {
                it.setString(1, listingName)
                it.setString(2, SONY_HEADPHONES)
                check(it.executeUpdate() == 1)
            }
            connection.commit()
        }
        block(database)
    }
}
