package io.github.youndie.haul.feature.product

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import io.github.youndie.haul.Body
import io.github.youndie.haul.Fixture
import io.github.youndie.haul.Page
import io.github.youndie.haul.decode
import io.github.youndie.haul.shell.ProductLoading
import io.github.youndie.haul.ui.Highlight
import io.github.youndie.haul.ui.ProductDescription
import io.github.youndie.kompot.standard.ColumnComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * B-104: the product page does not move or cut what it draws. The loading page's photo stands where the photo
 * arrives — the thumbnail column B-71 removed from the page had stayed in the skeleton, and the page jumped when
 * it came; and the crumbs' placeholder was a 12 px bar where the crumbs' line is 16.8, so the photo came 5 px
 * lower (B-143) — and a fact tile's title is never cut at the tile's edge: the seed's specification names
 * («Noise cancelling», «Connectivity») wrap between words and shrink to fit, while the canvas's «40 h» keeps its
 * display size. At 1440 and on a phone; the photo also at 1280, and at 1024, where the page and its placeholder draw
 * two columns, not three (B-153).
 *
 * B-169: on a phone every choice — the colour's swatches, the bundle's pills — stands above «Add to cart» and «Buy
 * now», so a shopper sees there is a choice before pressing; at 1440 the choices stay in the details' column, beside
 * the buy box.
 */
@OptIn(ExperimentalTestApi::class)
class ProductPageLayoutTest {
    private fun photoMovesOnArrival(width: Int) =
        runDesktopComposeUiTest(width, HEIGHT) {
            var loaded by mutableStateOf(false)
            setContent {
                Fixture(compact = width < WIDE, medium = width < WIDE_FROM) {
                    Page { if (loaded) Body("product_description.json") else ProductLoading() }
                }
            }
            val loading = onNodeWithTag(GALLERY_TAG).fetchSemanticsNode().boundsInRoot
            loaded = true
            waitForIdle()
            val arrived = onNodeWithTag(GALLERY_TAG).fetchSemanticsNode().boundsInRoot
            assertEquals(arrived.top, loading.top, "the photo moves down when the page arrives at $width")
            assertEquals(arrived.left, loading.left, "the photo moves sideways when the page arrives at $width")
            assertEquals(arrived.width, loading.width, "the photo changes width when the page arrives at $width")
        }

    @Test
    fun `the loading photo stands where the photo arrives`() = photoMovesOnArrival(DESKTOP)

    @Test
    fun `the loading photo stands where the photo arrives at 1280`() = photoMovesOnArrival(NARROW_DESKTOP)

    @Test
    fun `the loading photo stands where the photo arrives at 1024`() = photoMovesOnArrival(MEDIUM)

    @Test
    fun `the loading photo stands where the photo arrives on a phone`() = photoMovesOnArrival(PHONE)

    private fun ComposeUiTest.layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val node = onNodeWithText(text).fetchSemanticsNode()
        node.config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.single()
    }

    /** Every line of [layout] ends between words: no word was broken across lines or cut at the edge. */
    private fun assertWhole(layout: TextLayoutResult) {
        val text = layout.layoutInput.text.text
        assertFalse(layout.hasVisualOverflow, "«$text» is cut at the tile's edge")
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line, visibleEnd = true)
            assertTrue(end < text.length && text[end] == ' ', "«$text» is broken inside a word at $end")
        }
    }

    private fun factTitles(compact: Boolean) =
        runDesktopComposeUiTest(if (compact) PHONE else DESKTOP, HEIGHT) {
            val description = description().copy(facts = SEED_FACTS)
            setContent { Fixture(compact) { Page { ProductDescriptionView(description) } } }
            waitForIdle()
            for (fact in SEED_FACTS) assertWhole(layoutOf(fact.title))
        }

    @Test
    fun `a fact's title is never cut`() = factTitles(compact = false)

    @Test
    fun `a fact's title is never cut on a phone`() = factTitles(compact = true)

    /** The canvas's facts fit as they are and keep the tile's display size. */
    private fun canvasFacts(compact: Boolean) =
        runDesktopComposeUiTest(if (compact) PHONE else DESKTOP, HEIGHT) {
            val description = description()
            setContent { Fixture(compact) { Page { ProductDescriptionView(description) } } }
            waitForIdle()
            for (fact in description.facts) {
                val layout = layoutOf(fact.title)
                assertWhole(layout)
                assertEquals(if (compact) 44.sp else 56.sp, layout.layoutInput.style.fontSize, fact.title)
            }
        }

    @Test
    fun `the canvas's facts keep their display size`() = canvasFacts(compact = false)

    @Test
    fun `the canvas's facts keep their display size on a phone`() = canvasFacts(compact = true)

    /** Where the choices and the buy buttons of the server's headphones page stand at [width]. */
    private fun choicesAndButtons(
        width: Int,
        check: (choices: List<Rect>, buttons: List<Rect>) -> Unit,
    ) = runDesktopComposeUiTest(width, HEIGHT) {
        setContent {
            Fixture(
                compact = width < WIDE,
                medium = width in WIDE until WIDE_FROM,
            ) { Page { Body("product_description.json") } }
        }
        waitForIdle()
        val choices =
            listOf(
                onNodeWithContentDescription("Midnight Black"),
                onNodeWithContentDescription("Silver"),
                onNodeWithText("Headphones only"),
                onNodeWithText("+ Travel case"),
                onNodeWithText("+ 2-year care"),
            ).map { it.fetchSemanticsNode().boundsInRoot }
        val buttons = listOf("Add to cart", "Buy now").map { onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
        check(choices, buttons)
    }

    @Test
    fun `on a phone the choices stand above the buy buttons`() =
        choicesAndButtons(PHONE) { choices, buttons ->
            val firstButton = buttons.minOf { it.top }
            for (choice in choices) {
                assertTrue(
                    choice.bottom <= firstButton,
                    "a choice at $choice stands below the buy buttons at $firstButton",
                )
            }
        }

    @Test
    fun `at 1440 the choices stay in the details' column beside the buy box`() =
        choicesAndButtons(DESKTOP) { choices, buttons ->
            val buyBox = buttons.minOf { it.left }
            for (choice in choices) {
                assertTrue(choice.right < buyBox, "a choice at $choice is not left of the buy box at $buyBox")
            }
        }

    private fun description(): ProductDescription =
        (decode("product_description.json") as ColumnComponent).children.filterIsInstance<ProductDescription>().single()

    private companion object {
        const val DESKTOP = 1440
        const val NARROW_DESKTOP = 1280
        const val MEDIUM = 1024
        const val PHONE = 390

        /** Below it the phone's arrangement is drawn (`App.kt`'s `COMPACT_BELOW`). */
        const val WIDE = 1024

        /** Below it the desktop is rearranged: the photo and the details share the row (`App.kt`'s `WIDE_FROM`). */
        const val WIDE_FROM = 1280
        const val HEIGHT = 1600

        /** The headphones' first four specifications, which the server sends as the tiles (`ProductScreen`). */
        val SEED_FACTS =
            listOf(
                Highlight("Battery", "Up to 40 hours, 3 min charge = 3 h"),
                Highlight("Noise cancelling", "Adaptive, 12 microphones"),
                Highlight("Connectivity", "Bluetooth 5.4 · LC3 · multipoint"),
                Highlight("Weight", "250 g"),
            )
    }
}
