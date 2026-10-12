package io.github.youndie.haul.feature.search

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.ProductSuggestion
import io.github.youndie.haul.ui.QuerySuggestion
import io.github.youndie.haul.ui.SearchSuggestPanel
import io.github.youndie.kompot.standard.NavigateAction
import kotlin.test.Test

/**
 * B-132: `running` under «Appliances · Classics» found no product there, and the panel drew an empty «Top products»
 * over «All 0 results» beside its one query. The server now sends neither (`SearchPanelScopeTest`); a part with
 * nothing in it is not drawn — at 1440, where the products have a column of their own, and on a phone.
 */
@OptIn(ExperimentalTestApi::class)
class EmptyScopePanelTest {
    @Test
    fun `a panel with no products and no all results draws neither part`() =
        listOf(false, true).forEach { compact ->
            runDesktopComposeUiTest(if (compact) 390 else 1_440, 900) {
                setContent { HaulTheme(FixtureFonts.fonts, compact = compact) { SearchSuggestPanelView(empty) } }
                onNodeWithText("running shoes", substring = true).assertExists()
                onNodeWithText(TOP_PRODUCTS.uppercase()).assertDoesNotExist()
                onNodeWithText("results", substring = true).assertDoesNotExist()
            }
        }

    /** The same query where it matches: the heading, the product and the way to every result are all there. */
    @Test
    fun `a panel with products draws them under their heading with all results`() =
        listOf(false, true).forEach { compact ->
            runDesktopComposeUiTest(if (compact) 390 else 1_440, 900) {
                setContent { HaulTheme(FixtureFonts.fonts, compact = compact) { SearchSuggestPanelView(found) } }
                onNodeWithText(TOP_PRODUCTS.uppercase()).assertExists()
                onNodeWithText("Pace Running Shoe").assertExists()
                onNodeWithText("All 12 results").assertExists()
            }
        }

    private companion object {
        val empty =
            SearchSuggestPanel(
                id = "suggest",
                query = "running",
                suggestions =
                    listOf(
                        QuerySuggestion(
                            typed = "running",
                            completion = " shoes",
                            action = NavigateAction("/search?q=running%20shoes&category=appliances-classics"),
                        ),
                    ),
                categories = emptyList(),
                products = emptyList(),
                allResultsLabel = "",
            )

        val found =
            empty.copy(
                products =
                    listOf(
                        ProductSuggestion(
                            productId = "p-1",
                            title = "Pace Running Shoe",
                            price = "$89",
                            tone = "#E6E4FF",
                            action = NavigateAction("/p/p-1"),
                        ),
                    ),
                allResultsLabel = "All 12 results",
                allResultsAction = NavigateAction("/search?q=running"),
            )
    }
}
