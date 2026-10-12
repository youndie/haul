package io.github.youndie.haul.feature.search

import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.testing.answer
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.testing.update
import io.github.youndie.haul.ui.Chip
import io.github.youndie.haul.ui.FilterChips
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.PageTitle
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.SearchScope
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.commands.LoadAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * B-119: what a search's page names is what its grid holds — the title counts the chosen chip's results, no two
 * chips read the same, and the header's picker names the category a chip chose — against the seeded catalog.
 * «classic» and «everyday» are the searches the v0.4.0 run found it on: the seed's generic leaves («Classics»,
 * «Limited», «New in») sit under every top-level category.
 */
class SearchSaysWhatIsShownTest {
    private val catalog = CatalogSeed.generate(CatalogSeed.CANVAS_DAY)

    /** The category a chip's press opens: the `category` of its `load`. */
    private fun Chip.category(): String? =
        (assertNotNull(action, "$label presses nothing") as LoadAction).url.substringAfter("category=", "").ifEmpty {
            null
        }

    private fun KompotComponent.chips(): List<Chip> = only<FilterChips>().chips

    /** «600 results» over the 15 of one «Classics» chip: the title counts the chip chosen, «All»'s every match. */
    @Test
    fun `the title counts the chosen chip's results`() =
        haulTest {
            val everywhere = tree("/ui/search?q=classic")
            val all = everywhere.chips().first()
            assertEquals("${all.count} results", everywhere.only<PageTitle>().count)
            val chip = everywhere.chips().drop(1).first()
            val chosen = tree("/ui/search?q=classic&category=${chip.category()}")
            val n = chip.count!!.replace(",", "").toInt()
            assertEquals("${chip.count} ${if (n == 1) "result" else "results"}", chosen.only<PageTitle>().count)
            assertEquals(chip.count, chosen.chips().single { it.selected }.count, "the chosen chip counts otherwise")
            assertTrue(
                chosen.only<ProductGrid>().cards.size <= n,
                "the grid holds more than the chip's ${chip.count}",
            )
        }

    /**
     * `classic` drew ten «Classics 15» chips, `everyday` two «Limited» and two «New in»: leaves of different
     * top-level categories. Each is still a chip — they hold different results — named with its top-level one.
     */
    @Test
    fun `no two chips on one search read the same`() =
        haulTest {
            for (path in listOf(
                "/ui/search?q=classic",
                "/ui/search?q=everyday",
                "/ui/search?q=classic&category=party",
            )) {
                val labels = tree(path).chips().map { it.label }
                assertEquals(labels, labels.distinct(), "$path repeats a chip")
            }
            val chips = tree("/ui/search?q=classic").chips().drop(1)
            val classics =
                chips.filter { chip ->
                    catalog.categories.single { it.slug == chip.category() }.name ==
                        "Classics"
                }
            assertTrue(classics.size > 1, "«classic» no longer finds the Classics of several categories")
            for (chip in classics) {
                val leaf = catalog.categories.single { it.slug == chip.category() }
                val top =
                    generateSequence(
                        leaf,
                    ) { c -> catalog.categories.singleOrNull { it.slug == c.parentSlug } }.last()
                assertEquals("${top.name} · Classics", chip.label)
            }
        }

    /**
     * A leaf's chip scoped the results while the picker read «All categories». The picker names the leaf — with
     * its top-level category, offered after it — on the page, on the parts a chip loads, and «All» leaves it.
     */
    @Test
    fun `the picker names the scope a chip chose`() =
        haulTest {
            val chip = tree("/ui/search?q=classic").chips().first { it.label.endsWith(" · Classics") }
            val leaf = catalog.categories.single { it.slug == chip.category() }
            val top = generateSequence(leaf) { c -> catalog.categories.singleOrNull { it.slug == c.parentSlug } }.last()
            val address = "/search?q=classic&category=${leaf.slug}"

            val header = tree("/ui$address").only<HaulHeader>()
            assertEquals(leaf.slug, header.scope, "the picker does not read the chip's category")
            val choice = SearchScope(chip.label, leaf.slug)
            assertEquals(choice, header.scopes.single { it.category == header.scope }, "the picker names it otherwise")
            assertEquals(top.slug, header.scopes[header.scopes.indexOf(choice) - 1].category, "not after its top level")

            val loaded =
                assertNotNull(
                    answer("/ui/parts$address").update(),
                ).updates.single { it.componentId == "header" }
            assertEquals(leaf.slug, (loaded.component as HaulHeader).scope, "a chip's load leaves the picker behind")

            val everywhere = tree("/ui/search?q=classic").only<HaulHeader>()
            assertEquals(null, everywhere.scope)
            assertTrue(everywhere.scopes.none { it.category == leaf.slug }, "the leaf stays a choice after «All»")
        }
}
