package io.github.youndie.haul.feature.search

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.SUGGEST_DELAY_MS
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.SEARCH_FIELD_TAG
import io.github.youndie.haul.ui.SearchSuggestPanel
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.ButtonComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.RefreshAction
import kotlinx.serialization.PolymorphicSerializer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * B-135, the search field's loose ends the v0.5.1 run found: one typed character asked for a panel the server
 * refuses (`400 query_too_short`, a console error each time); Escape left the panel open; and a press outside it only
 * closed it — the scrim swallowed the press, as a menu's does (B-126), though the panel is a suggestion over the page.
 */
@OptIn(ExperimentalTestApi::class)
class SuggestPanelClosingTest {
    private val requests = CopyOnWriteArrayList<String>()
    private val history = FakeHistory()

    private val transport =
        HaulTransport { path ->
            requests += path
            val tree =
                when (path) {
                    "/ui/home" -> HOME
                    SUGGEST -> PANEL
                    else -> error("nothing answers $path")
                }
            HaulResponse(200, haulWireJson.encodeToString(PolymorphicSerializer(KompotComponent::class), tree))
        }

    private fun ComposeUiTest.storefront() =
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                Storefront(transport, history, signIn = {})
            }
        }

    private fun ComposeUiTest.panelShown(): Boolean =
        onAllNodes(hasTestTag(SUGGEST_PANEL_TAG)).fetchSemanticsNodes().isNotEmpty()

    /** «ru» typed into the field, and the panel it asks for open under it. */
    private fun ComposeUiTest.openPanel() {
        val field = onNodeWithTag(SEARCH_FIELD_TAG)
        field.performClick()
        field.performTextInput("ru")
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText(ALL_RESULTS)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `one character asks for no suggestions and the second one does`() =
        runDesktopComposeUiTest(WIDTH, 1_000) {
            storefront()
            val field = onNodeWithTag(SEARCH_FIELD_TAG)
            field.performClick()
            mainClock.autoAdvance = false
            field.performTextInput("r")
            mainClock.advanceTimeBy(SUGGEST_DELAY_MS * 4)
            assertEquals(listOf("/ui/home"), requests, "one character asked for a panel the server refuses")
            field.performTextInput("u")
            mainClock.advanceTimeBy(SUGGEST_DELAY_MS * 2)
            mainClock.autoAdvance = true
            waitUntil(timeoutMillis = 5_000) { panelShown() }
            assertEquals(listOf("/ui/home", SUGGEST), requests)
        }

    @Test
    fun `Escape closes the panel and leaves the field to type in`() =
        runDesktopComposeUiTest(WIDTH, 1_000) {
            storefront()
            openPanel()
            val field = onNodeWithTag(SEARCH_FIELD_TAG)
            field.performKeyInput { pressKey(Key.Escape) }
            waitForIdle()
            assertFalse(panelShown(), "Escape left the panel open")
            field.assertIsFocused()
            assertEquals(listOf("/"), history.entries)
            assertEquals(listOf("/ui/home", SUGGEST), requests)
        }

    @Test
    fun `a press outside the panel closes it and reaches what it pressed`() =
        runDesktopComposeUiTest(WIDTH, 1_000) {
            storefront()
            openPanel()
            // The field is not outside: pressing it again keeps the panel.
            onNodeWithTag(SEARCH_FIELD_TAG).performClick()
            waitForIdle()
            assertEquals(true, panelShown(), "a press on the field closed the panel")

            val panel = onNodeWithTag(SUGGEST_PANEL_TAG).fetchSemanticsNode().boundsInRoot
            val reload = onNodeWithText(RELOAD).fetchSemanticsNode().boundsInRoot
            assertFalse(panel.overlaps(reload), "the button is under the panel: the press would be the panel's")
            onNodeWithText(RELOAD).performClick()
            waitUntil(timeoutMillis = 5_000) { requests.count { it == "/ui/home" } == 2 }
            waitForIdle()
            assertFalse(panelShown(), "the press outside left the panel open")
            assertEquals(listOf("/ui/home", SUGGEST, "/ui/home"), requests, "the press did not reach the page")
            assertEquals(listOf("/"), history.entries)
        }

    private companion object {
        const val WIDTH = 1440
        const val SUGGEST = "/ui/search/suggest?q=ru"
        const val ALL_RESULTS = "All 2 results"
        const val RELOAD = "Reload the page"

        val header =
            HaulHeader(
                id = "header",
                deliverTo = "Brooklyn, NY 11211",
                deliveryPromise = "Free delivery over $35",
                customerName = null,
                cartCount = 0,
                searchPlaceholder = "Search products",
                categories = listOf("Electronics"),
            )

        /** A page with a press under the scrim that changes no address: its `refresh` is one more `GET` of the page. */
        val HOME: KompotComponent =
            ColumnComponent(
                id = "page",
                children = listOf(header, ButtonComponent(id = "reload", text = RELOAD, action = RefreshAction)),
            )

        val PANEL =
            SearchSuggestPanel(
                id = "suggest",
                query = "ru",
                suggestions = emptyList(),
                categories = emptyList(),
                products = emptyList(),
                allResultsLabel = ALL_RESULTS,
                allResultsAction = NavigateAction("/search?q=ru"),
            )
    }
}
