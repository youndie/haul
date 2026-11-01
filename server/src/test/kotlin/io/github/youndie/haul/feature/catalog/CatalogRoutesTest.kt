package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.ErrorBody
import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.EmptyState
import io.github.youndie.haul.ui.FacetPanel
import io.github.youndie.haul.ui.FilteredResults
import io.github.youndie.haul.ui.PageTitle
import io.github.youndie.haul.ui.ProductGrid
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** feature-browse's scenarios, against the seeded catalog. */
class CatalogRoutesTest {
    private val catalog = CatalogSeed.generate(CatalogSeed.CANVAS_DAY)

    /** Scenario «Facets narrow the list»: every product shown matches all four filters, and the count is theirs. */
    @Test
    fun `facets narrow the list`() =
        haulTest {
            val page =
                tree(
                    "/ui/c/electronics/audio/headphones?brand=Sonora&brand=Bellwave&price_min=80&price_max=400&feature=Noise%20cancelling",
                )
            val cards = page.only<ProductGrid>().cards
            assertTrue(cards.isNotEmpty(), "the filters matched nothing — the scenario would pass on an empty page")
            cards.forEach { card ->
                val product = catalog.products.single { it.id == card.productId }
                assertTrue(product.brand in setOf("Sonora", "Bellwave"), "${product.title} is not Sonora or Bellwave")
                assertTrue("Noise cancelling" in product.features, "${product.title} does not cancel noise")
                val dollars =
                    card.price
                        .removePrefix("$")
                        .replace(",", "")
                        .toInt()
                assertTrue(dollars in 80..400, "${product.title} costs ${card.price}")
            }
            assertEquals("${cards.size} items", page.only<PageTitle>().count)
        }

    /** Scenario «A filter set with no products»: 200, nothing listed, the facets still there. */
    @Test
    fun `a filter set with no products answers an empty page with its facets`() =
        haulTest {
            val page = tree("/ui/c/electronics/audio/headphones?brand=Mallard&colour=Pink")
            assertTrue(page.all().none { it is ProductGrid }, "a grid was drawn for an empty result")
            assertEquals("No items match these filters", page.only<EmptyState>().title)
            assertEquals("0 items", page.only<PageTitle>().count)
            val brands =
                page
                    .only<FacetPanel>()
                    .facets
                    .single { it.key == "brand" }
                    .options
            assertTrue(brands.any { it.label == "Mallard" && it.selected }, "the brand facet lost its values")
        }

    /**
     * B-133: a result of one product is «1 item», on the title and on the phone sheet's button, as the cart writes it;
     * it read «1 items». The brand is one the facet counts a single product of, so the result is exactly one.
     */
    @Test
    fun `a result of one product is one item`() =
        haulTest {
            val (path, brand) =
                ONE_BRAND_PATHS.firstNotNullOf { path ->
                    tree("/ui$path")
                        .only<FacetPanel>()
                        .facets
                        .single { it.key == "brand" }
                        .options
                        .firstOrNull { it.count == 1 }
                        ?.let { path to it.label }
                }
            val page = tree("/ui$path?brand=${URLEncoder.encode(brand, Charsets.UTF_8)}")
            assertEquals(1, page.only<ProductGrid>().cards.size, "$brand in $path is not one product")
            assertEquals("1 item", page.only<PageTitle>().count)
            assertEquals("Show 1 item", page.only<FilteredResults>().showLabel)
        }

    /**
     * B-75: an option another filter has left with nothing to show — a count of 0 — carries no action, so the
     * client draws it faded and pressing it opens nothing; ticked, it keeps its action, which unticks it. The
     * rating's ticked option is the exception (B-102): a radio has nothing to untick, so it carries nothing.
     */
    @Test
    fun `an option with nothing to show presses nothing unless it is ticked`() =
        haulTest {
            val options =
                tree("/ui/c/electronics/audio/headphones?brand=Mallard&colour=Pink")
                    .only<FacetPanel>()
                    .facets
                    .filter { it.kind != "range" }
                    .flatMap { facet -> facet.options.map { facet.key to it } }
            val empty = options.filter { (_, it) -> it.count == 0 && !it.selected }
            assertTrue(empty.isNotEmpty(), "no option was left empty — the check would pass on nothing")
            empty.forEach { (key, it) -> assertNull(it.action, "$key «${it.label}» shows nothing and still presses") }
            val tickedEmpty = options.filter { (_, it) -> it.count == 0 && it.selected }
            assertTrue(tickedEmpty.isNotEmpty(), "no ticked option was left empty — the exception is unchecked")
            val chosen = options.filter { (key, it) -> key == "rating" && it.selected }
            assertEquals(listOf("Any rating"), chosen.map { (_, it) -> it.label })
            chosen.forEach { (_, it) -> assertNull(it.action, "the rating ticked presses") }
            (options - empty.toSet() - chosen.toSet()).forEach { (key, it) ->
                assertNotNull(it.action, "$key «${it.label}» (${it.count}) presses nothing")
            }
        }

    /** Scenario «An unknown category». */
    @Test
    fun `an unknown category is 404 category_not_found`() =
        haulTest {
            val response = get("/ui/c/no-such-thing")
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals(
                ErrorCode.CategoryNotFound,
                haulWireJson.decodeFromString(ErrorBody.serializer(), response.bodyAsText()).code,
            )
        }

    /** A parameter the page does not understand is refused by name, not ignored into a different page. */
    @Test
    fun `an unknown sort is 400 validation_failed naming the field`() =
        haulTest {
            val response = get("/ui/c/electronics/audio/headphones?sort=cheapest-first")
            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = haulWireJson.decodeFromString(ErrorBody.serializer(), response.bodyAsText())
            assertEquals(ErrorCode.ValidationFailed, body.code)
            assertEquals("sort", body.field)
        }

    private companion object {
        /** Where a brand with a single product is looked for, in this order. */
        val ONE_BRAND_PATHS =
            listOf("/c/electronics/audio/headphones", "/c/electronics", "/c/home-kitchen", "/c/fashion", "/c/beauty")
    }
}
