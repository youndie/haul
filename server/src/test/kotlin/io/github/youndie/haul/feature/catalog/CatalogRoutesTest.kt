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
import io.github.youndie.haul.ui.PageTitle
import io.github.youndie.haul.ui.ProductGrid
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
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
                    "/ui/c/electronics/audio/headphones?brand=Sony&brand=Bose&price_min=80&price_max=400&feature=Noise%20cancelling",
                )
            val cards = page.only<ProductGrid>().cards
            assertTrue(cards.isNotEmpty(), "the filters matched nothing — the scenario would pass on an empty page")
            cards.forEach { card ->
                val product = catalog.products.single { it.id == card.productId }
                assertTrue(product.brand in setOf("Sony", "Bose"), "${product.title} is not Sony or Bose")
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
            val page = tree("/ui/c/electronics/audio/headphones?brand=Marshall&colour=Pink")
            assertTrue(page.all().none { it is ProductGrid }, "a grid was drawn for an empty result")
            assertEquals("No items match these filters", page.only<EmptyState>().title)
            assertEquals("0 items", page.only<PageTitle>().count)
            val brands =
                page
                    .only<FacetPanel>()
                    .facets
                    .single { it.key == "brand" }
                    .options
            assertTrue(brands.any { it.label == "Marshall" && it.selected }, "the brand facet lost its values")
        }

    /**
     * B-75: an option another filter has left with nothing to show — a count of 0 — carries no action, so the
     * client draws it faded and pressing it opens nothing; ticked, it keeps its action, which unticks it.
     */
    @Test
    fun `an option with nothing to show presses nothing unless it is ticked`() =
        haulTest {
            val options =
                tree("/ui/c/electronics/audio/headphones?brand=Marshall&colour=Pink")
                    .only<FacetPanel>()
                    .facets
                    .filter { it.kind != "range" }
                    .flatMap { facet -> facet.options.map { facet.key to it } }
            val empty = options.filter { (_, it) -> it.count == 0 && !it.selected }
            assertTrue(empty.isNotEmpty(), "no option was left empty — the check would pass on nothing")
            empty.forEach { (key, it) -> assertNull(it.action, "$key «${it.label}» shows nothing and still presses") }
            val tickedEmpty = options.filter { (_, it) -> it.count == 0 && it.selected }
            assertTrue(tickedEmpty.isNotEmpty(), "no ticked option was left empty — the exception is unchecked")
            (options - empty.toSet()).forEach { (key, it) ->
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
}
