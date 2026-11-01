package io.github.youndie.haul.feature.search

import io.github.youndie.haul.feature.search.data.PostgresSearchRepository
import io.github.youndie.haul.feature.search.domain.Query
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCatalog.SONORA_HEADPHONES
import io.github.youndie.haul.seed.SeedProduct
import io.github.youndie.haul.testing.SeededDatabase
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.PageTitle
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.SearchSuggestPanel
import kotlinx.coroutines.runBlocking
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-171: search finds a product by what it is — its kind, its features, its category and the category above — as
 * well as by its name, and a name match comes first. The shopper searched «wireless headphones» and was shown three
 * products: the ones whose name holds both words, the catalogue's own «Sonora SH-C388N Wireless Noise Cancelling»
 * not among them.
 */
class SearchByKindTest {
    private val catalog = CatalogSeed.generate(CatalogSeed.CANVAS_DAY)

    private fun matching(
        query: String,
        database: DataSource = SeededDatabase.dataSource,
    ): List<String> = runBlocking { PostgresSearchRepository(database).matching(Query.of(query)) }.map { it.productId }

    private fun SeedProduct.name(): String = "${listingName ?: title} $brand".lowercase()

    /** Every category under [slug], itself included. */
    private fun under(slug: String): Set<String> =
        generateSequence(setOf(slug)) { slugs ->
            (slugs + catalog.categories.filter { it.parentSlug in slugs }.map { it.slug }).takeIf { it != slugs }
        }.last()

    @Test
    fun `wireless headphones finds the catalogue's wireless headphones first`() =
        haulTest {
            val wireless = catalog.products.filter { it.categorySlug == "headphones" && "Wireless" in it.features }
            val named = wireless.filter { "wireless" in it.name() && "headphones" in it.name() }
            assertTrue(named.size in 1 until wireless.size, "the seed no longer tells a name from a kind")

            val found = matching("wireless headphones")
            assertEquals(wireless.map { it.id }.toSet(), found.toSet(), "«wireless headphones» found")
            assertEquals(named.map { it.id }.toSet(), found.take(named.size).toSet(), "a name match is not first")
            assertTrue("p-sonora-sh-c388n" in found && SONORA_HEADPHONES in found)

            val page = tree("/ui/search?q=wireless%20headphones")
            val cards = page.only<ProductGrid>().cards.map { it.productId }
            assertEquals(found.take(cards.size), cards, "the grid is not in the order matched")
            assertEquals("${wireless.size} results", page.only<PageTitle>().count)
            val panel = tree("/ui/search/suggest?q=wireless%20headphones") as SearchSuggestPanel
            assertEquals(found.take(panel.products.size), panel.products.map { it.productId }, "the panel's products")
            assertEquals("All ${wireless.size} results", panel.allResultsLabel)
        }

    /** A product whose name holds the query outranks one found by its category alone, however reviewed. */
    @Test
    fun `a name match outranks a category match`() =
        haulTest {
            val headphones = catalog.products.filter { it.categorySlug == "headphones" }
            val named = headphones.filter { "headphones" in it.name() }
            assertTrue(named.size in 1 until headphones.size)
            assertTrue(
                (headphones - named.toSet()).maxOf { it.reviewsCount } > named.minOf { it.reviewsCount },
                "every name match is reviewed more: the reviews alone would put them first",
            )
            val found = matching("headphones")
            assertEquals(headphones.map { it.id }.toSet(), found.toSet())
            assertEquals(named.map { it.id }.toSet(), found.take(named.size).toSet())
        }

    /**
     * The order is by where the words are before it is by `ts_rank`: a product that says «wireless headphones» over
     * and over in its features ranks above every name match by `ts_rank` alone — each pair of the words near each
     * other adds to the rank, whatever their weight — and still comes after every product whose name holds the query.
     */
    @Test
    fun `a product saying its kind over and over still comes after a name match`() =
        seededFreshDatabase().use { database ->
            database.connection.use { connection ->
                connection
                    .prepareStatement("UPDATE products SET features = ?::jsonb WHERE id = 'p-sonora-sh-c388n'")
                    .use {
                        it.setString(1, List(4) { "\"Wireless headphones\"" }.joinToString(", ", "[", "]"))
                        check(it.executeUpdate() == 1)
                    }
                connection.commit()
            }
            val named =
                catalog.products
                    .filter { "wireless" in it.name() && "headphones" in it.name() }
                    .map { it.id }
                    .toSet()
            val found = matching("wireless headphones", database)
            assertEquals(named, found.take(named.size).toSet(), "found ${found.take(named.size + 1)}")
            assertEquals(
                "p-sonora-sh-c388n",
                found[named.size],
                "the stuffed features no longer rank first among the rest",
            )
        }

    /** «Audio» is no product's name: it finds the products under Audio, headphones and speakers, and nothing else. */
    @Test
    fun `a word only a category holds finds that category's products`() =
        haulTest {
            val audio = catalog.categories.single { it.name == "Audio" }
            assertTrue(catalog.products.none { "audio" in it.name() }, "a product is named «audio»")
            val expected =
                catalog.products
                    .filter { it.categorySlug in under(audio.slug) }
                    .map { it.id }
                    .toSet()
            assertEquals(expected, matching("audio").toSet())
            assertEquals(expected, matching("audio speakers").toSet() + matching("audio headphones"))
        }

    @Test
    fun `a query that matches nothing still finds nothing`() =
        haulTest {
            assertEquals(emptyList(), matching("xqzt"))
            assertEquals(emptyList(), matching("wireless xqzt"), "one word found was enough")
        }
}
