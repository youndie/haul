package io.github.youndie.haul.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.HaulThemeAtWidth
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.ui.MIN_CARD
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * B-136: a page's placeholders draw its cards as many across as the page itself will. At 1024 the category's
 * placeholder drew four cards beside the facets and the home page's six in a row, while the page's grid takes only
 * the cards of [MIN_CARD] that fit (B-82) — three and four — so a cold load jumped once the tree arrived. Every
 * card placeholder on a desktop is now at least [MIN_CARD] wide, the rule the page's own grid keeps.
 */
@OptIn(ExperimentalTestApi::class)
class LoadingCardsTest {
    @Test
    fun `no card placeholder is narrower than the page's own cards at any desktop width`() =
        WIDTHS.forEach { width ->
            PAGES.forEach { (name, page) ->
                runDesktopComposeUiTest(width, 3_000) {
                    setContent {
                        HaulThemeAtWidth(FixtureFonts.fonts, Modifier.fillMaxSize().background(HaulColors.background)) {
                            Column(Modifier.verticalScroll(rememberScrollState())) { page() }
                        }
                    }
                    val cards = onAllNodesWithTag(CARD_SKELETON_TAG).fetchSemanticsNodes()
                    assertTrue(cards.isNotEmpty(), "$name at $width draws no card placeholder")
                    val narrowest = cards.minOf { with(density) { it.size.width.toDp() } }
                    assertTrue(
                        narrowest >= MIN_CARD,
                        "$name at $width: a card placeholder ${narrowest.show()} wide, under ${MIN_CARD.show()}",
                    )
                }
            }
        }

    private fun Dp.show() = "${value.toInt()} px"

    private companion object {
        val WIDTHS = listOf(1_024, 1_280, 1_440)

        val PAGES: List<Pair<String, @Composable () -> Unit>> =
            listOf(
                "home" to { HomeLoading() },
                "category" to { CatalogLoading() },
                "deals" to { DealsLoading() },
                "search" to { SearchLoading("headphones") },
                "saved" to { SavedLoading() },
            )
    }
}
