package io.github.youndie.haul

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.CART_BUTTON_TAG
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.HaulHeaderView
import io.github.youndie.haul.ui.LocalHaulActions
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.standard.NavigateAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * B-75: the cart's count appearing moves nothing in the header. It used to: the count widened the cart button,
 * and the search field beside it gave the width up — seen on the stand at 1366 — while on a phone the heart and
 * the account moved left. The button keeps the count's place now, empty or not, up to two digits; only what
 * is inside it makes room.
 */
@OptIn(ExperimentalTestApi::class)
class HeaderCountTest {
    private var count by mutableIntStateOf(0)

    private fun ComposeUiTest.header(compact: Boolean) =
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = compact) { HaulHeaderView(HEADER.copy(cartCount = count)) }
        }

    /** Where [text] is drawn, and where the cart button is, for each of [counts] in turn. */
    private fun ComposeUiTest.drawnAt(
        text: String,
        counts: List<Int>,
    ): List<Pair<Rect, Rect>> =
        counts.map {
            count = it
            waitForIdle()
            onNodeWithText(text).fetchSemanticsNode().boundsInRoot to
                onNodeWithTag(CART_BUTTON_TAG).fetchSemanticsNode().boundsInRoot
        }

    @Test
    fun `at 1366 the search field keeps its width when the count appears`() =
        runDesktopComposeUiTest(1_366, 200) {
            header(compact = false)
            // «Orders» sits right of the search field: the field shrinking is «Orders» moving left.
            val drawn = drawnAt("Orders", COUNTS)
            assertEquals(List(COUNTS.size) { drawn.first() }, drawn, "the header moved as the count went $COUNTS")
        }

    @Test
    fun `on a phone the account keeps its place when the count appears`() =
        runDesktopComposeUiTest(390, 220) {
            header(compact = true)
            val drawn = drawnAt("Sign in", COUNTS)
            assertEquals(List(COUNTS.size) { drawn.first() }, drawn, "the header moved as the count went $COUNTS")
        }

    /**
     * B-136: the phone's cart button is its name alone — «Cart 3», «Cart» — with no words of its own beside it. It
     * carried the badge's «3» as its text too, and the browser's mirror kept that «3» after the cart was emptied,
     * though the name had gone back to «Cart».
     */
    @Test
    fun `on a phone the cart button reads its name and nothing else`() =
        runDesktopComposeUiTest(390, 220) {
            // Pressable, as the store draws it: a press merges what is inside the button into the button.
            setContent {
                HaulTheme(FixtureFonts.fonts, compact = true) {
                    CompositionLocalProvider(LocalHaulActions provides KompotActionHandler { }) {
                        HaulHeaderView(HEADER.copy(cartCount = count, cart = NavigateAction("/cart")))
                    }
                }
            }
            listOf(3 to "Cart 3", 0 to "Cart").forEach { (inCart, name) ->
                count = inCart
                waitForIdle()
                val config = onNodeWithTag(CART_BUTTON_TAG).fetchSemanticsNode().config
                assertEquals(listOf(name), config.getOrNull(SemanticsProperties.ContentDescription), "at $inCart")
                assertNull(config.getOrNull(SemanticsProperties.Text), "the button's own words at $inCart")
            }
        }

    private companion object {
        /** None, the first line, two digits, and the most two digits hold. */
        val COUNTS = listOf(0, 1, 12, 99)

        val HEADER =
            HaulHeader(
                id = "header",
                deliverTo = "Brooklyn, NY 11211",
                deliveryPromise = "Free delivery over $35",
                customerName = null,
                cartCount = 0,
                searchPlaceholder = "Search products",
                categories = listOf("Electronics"),
            )
    }
}
