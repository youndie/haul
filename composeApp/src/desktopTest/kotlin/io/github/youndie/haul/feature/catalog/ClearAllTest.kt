package io.github.youndie.haul.feature.catalog

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.AppliedFilters
import io.github.youndie.haul.ui.Chip
import io.github.youndie.haul.ui.FacetPanel
import io.github.youndie.haul.ui.FilteredResults
import io.github.youndie.kompot.commands.LoadAction
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-102: «Clear all» was drawn link-coloured on a page with nothing applied, and pressing it reloaded the
 * page. It is drawn only while a filter is — beside the chips at 1440 and on a phone, and in the phone's
 * filter sheet.
 */
@OptIn(ExperimentalTestApi::class)
class ClearAllTest {
    private fun applied(chips: List<Chip>) =
        AppliedFilters(
            id = "applied",
            chips = chips,
            clearLabel = CLEAR_ALL,
            sortLabel = "Popular",
            filterCount = chips.size,
            clearAction = if (chips.isEmpty()) null else LoadAction("/ui/parts/c/mugs"),
        )

    private fun results(chips: List<Chip>) =
        FilteredResults("results", FacetPanel("facets", emptyList()), applied(chips), "Show 12 items")

    private fun ComposeUiTest.clearAlls(): Int = onAllNodes(hasText(CLEAR_ALL)).fetchSemanticsNodes().size

    @Test
    fun `clear all is drawn beside the chips only while a filter is applied`() {
        listOf(false, true).forEach { compact ->
            listOf(emptyList<Chip>() to 0, listOf(OSTRA) to 1).forEach { (chips, drawn) ->
                runDesktopComposeUiTest(if (compact) 390 else 1_440, 600) {
                    setContent {
                        HaulTheme(
                            FixtureFonts.fonts,
                            compact = compact,
                        ) { FilteredResultsView(results(chips)) }
                    }
                    assertEquals(drawn, clearAlls(), "compact=$compact, ${chips.size} applied")
                }
            }
        }
    }

    @Test
    fun `the filter sheet says clear all only while a filter is applied`() {
        listOf(emptyList<Chip>() to 0, listOf(OSTRA) to 1).forEach { (chips, drawn) ->
            runDesktopComposeUiTest(390, 800) {
                setContent {
                    HaulTheme(FixtureFonts.fonts, compact = true) {
                        FiltersSheet(FacetPanel("facets", emptyList()), applied(chips), "Show 12 items")
                    }
                }
                assertEquals(drawn, clearAlls(), "${chips.size} applied")
                onAllNodes(hasText("${chips.size} applied")).fetchSemanticsNodes().single()
            }
        }
    }

    private companion object {
        const val CLEAR_ALL = "Clear all"
        val OSTRA = Chip("Ostra", true, LoadAction("/ui/parts/c/mugs"))
    }
}
