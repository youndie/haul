package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.shell.Parts
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.json
import io.github.youndie.haul.testing.loads
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.AppliedFilters
import io.github.youndie.haul.ui.EmptyState
import io.github.youndie.haul.ui.FacetPanel
import io.github.youndie.haul.ui.FilterChips
import io.github.youndie.haul.ui.PageTitle
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.commands.LoadAction
import io.github.youndie.kompot.standard.NavigateAction
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B-102: what a category page says is applied is what filters it. Every filter applied has a chip and is
 * counted once — «Filters 2» over one chip counted the delivery and the rating, which had none; «Clear all» is
 * there only when a chip is, and keeps the kind; a category with subcategories offers them rather than its
 * first leaf's kinds; and what is already chosen carries nothing, so no press loads the page it is on.
 */
class AppliedFiltersTest {
    @Test
    fun `every filter applied has a chip and is counted once`() =
        haulTest {
            val applied =
                tree(
                    "/ui$HEADPHONES?brand=Sony&price_min=80&feature=Noise%20cancelling&colour=Black" +
                        "&delivery=tomorrow&rating=4.5",
                ).only<AppliedFilters>()
            assertEquals(
                listOf("Sony", "From $80", "Noise cancelling", "Black", "Delivery: Tomorrow", "Rating: 4.5 and up"),
                applied.chips.map { it.label },
            )
            assertEquals(applied.chips.size, applied.filterCount)

            // Each of the two new chips removes its own filter and nothing else.
            val delivery = applied.chips.single { it.label == "Delivery: Tomorrow" }.action
            assertEquals(
                LoadAction(
                    "/ui/parts$HEADPHONES?brand=Sony&feature=Noise%20cancelling&colour=Black&price_min=80&rating=4.5",
                ),
                delivery,
            )
            val rating = applied.chips.single { it.label == "Rating: 4.5 and up" }.action
            assertEquals(
                LoadAction(
                    "/ui/parts$HEADPHONES?brand=Sony&feature=Noise%20cancelling&colour=Black&price_min=80" +
                        "&delivery=tomorrow",
                ),
                rating,
            )
            // The phone's count of the rating alone: one, as its one chip.
            val rated = tree("/ui$HEADPHONES?rating=4.0").only<AppliedFilters>()
            assertEquals(listOf("Rating: 4.0 and up") to 1, rated.chips.map { it.label } to rated.filterCount)
        }

    @Test
    fun `clear all is there only while a chip is and keeps the kind`() =
        haulTest {
            listOf(HEADPHONES, "$HEADPHONES?kind=Over-ear", "$HEADPHONES?sort=rating&page=2").forEach { address ->
                val applied = tree("/ui$address").only<AppliedFilters>()
                assertEquals(0 to null, applied.filterCount to applied.clearAction, address)
            }
            val applied = tree("/ui$HEADPHONES?brand=Sony&kind=Over-ear&sort=rating").only<AppliedFilters>()
            assertEquals(LoadAction("/ui/parts$HEADPHONES?kind=Over-ear&sort=rating"), applied.clearAction)

            // The empty state's «Clear all» is the same press; with nothing applied there is nothing to offer.
            val empty = tree("/ui$HEADPHONES?kind=Over-ear&brand=Sony&price_max=1").only<EmptyState>()
            assertEquals(
                "Clear all" to LoadAction("/ui/parts$HEADPHONES?kind=Over-ear"),
                empty.actionLabel to empty.action,
            )
        }

    @Test
    fun `a category with subcategories offers them and applies no kind`() =
        haulTest {
            assertEquals(
                listOf(
                    "Audio" to NavigateAction("/c/electronics/audio"),
                    "Video" to NavigateAction("/c/electronics/video"),
                ),
                tree("/ui/c/electronics").only<FilterChips>().chips.map { it.label to it.action },
            )
            assertEquals(
                listOf(
                    "Headphones" to NavigateAction("/c/electronics/audio/headphones"),
                    "Speakers" to NavigateAction("/c/electronics/audio/speakers"),
                ),
                tree("/ui/c/electronics/audio").only<FilterChips>().chips.map { it.label to it.action },
            )
            assertEquals(emptyList(), tree("/ui/c/electronics").only<FilterChips>().chips.filter { it.selected })
            // A kind in the address of such a category filters nothing, as no chip says it does.
            assertEquals(
                tree("/ui/c/electronics").only<PageTitle>().count,
                tree("/ui/c/electronics?kind=Gaming").only<PageTitle>().count,
            )
            // A leaf still has its kinds, «All» first.
            val kinds = tree("/ui$HEADPHONES").only<FilterChips>().chips.map { it.label }
            assertEquals("All", kinds.first())
            assertTrue("Over-ear" in kinds, "the headphones lost their kinds: $kinds")
        }

    @Test
    fun `what is chosen carries nothing`() =
        haulTest {
            val plain = tree("/ui$HEADPHONES")
            assertNull(
                plain
                    .only<FilterChips>()
                    .chips
                    .single { it.label == "All" }
                    .action,
                "«All» selected",
            )
            assertNull(
                plain
                    .only<AppliedFilters>()
                    .sorts
                    .single { it.label == "Popular" }
                    .action,
                "the sort shown",
            )
            assertNull(rating(plain, "Any rating"), "«Any rating» with no rating")

            val chosen = tree("/ui$HEADPHONES?kind=Over-ear&rating=4.5&sort=rating")
            val chips = chosen.only<FilterChips>().chips
            assertNull(chips.single { it.label == "Over-ear" }.action, "the kind selected")
            assertEquals(
                LoadAction("/ui/parts$HEADPHONES?rating=4.5&sort=rating"),
                chips.single { it.label == "All" }.action,
            )
            val sorts = chosen.only<AppliedFilters>().sorts
            assertNull(sorts.single { it.label == "Rating" }.action, "the sort shown")
            assertNotNull(sorts.single { it.label == "Popular" }.action, "another sort")
            assertNull(rating(chosen, "4.5 and up"), "the rating ticked")
            assertEquals(LoadAction("/ui/parts$HEADPHONES?kind=Over-ear&sort=rating"), rating(chosen, "Any rating"))
        }

    /** Every `load` a category page carries answers another page than the one it is pressed on. */
    @Test
    fun `no press on a category page loads the page it is on`() =
        haulTest {
            listOf(
                HEADPHONES,
                "$HEADPHONES?kind=Over-ear&rating=4.5&sort=rating",
                "$HEADPHONES?brand=Sony&delivery=tomorrow&expand=brand",
                "/c/electronics",
                "/c/electronics?rating=4.0&sort=price-asc&page=2",
            ).forEach { assertNoPressReloads(it) }
        }

    private suspend fun HttpClient.assertNoPressReloads(address: String) {
        val page = tree("/ui$address")
        page.loads().forEach { url ->
            assertNotEquals(page.json(), tree("/ui" + url.removePrefix(Parts.PREFIX)).json(), "$address: $url")
        }
    }

    private fun rating(
        page: KompotComponent,
        label: String,
    ) = page
        .only<FacetPanel>()
        .facets
        .single { it.key == "rating" }
        .options
        .single { it.label == label }
        .action

    private companion object {
        const val HEADPHONES = "/c/electronics/audio/headphones"
    }
}
