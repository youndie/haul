package io.github.youndie.haul.feature.product

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
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
 * it came — and a fact tile's title is never cut at the tile's edge: the seed's specification names
 * («Noise cancelling», «Connectivity») wrap between words and shrink to fit, while the canvas's «40 h» keeps its
 * display size. At 1440 and on a phone.
 */
@OptIn(ExperimentalTestApi::class)
class ProductPageLayoutTest {
    private fun photoMovesOnArrival(compact: Boolean) =
        runDesktopComposeUiTest(if (compact) PHONE else DESKTOP, HEIGHT) {
            var loaded by mutableStateOf(false)
            setContent {
                Fixture(compact) { Page { if (loaded) Body("product_description.json") else ProductLoading() } }
            }
            val loading = onNodeWithTag(GALLERY_TAG).fetchSemanticsNode().boundsInRoot
            loaded = true
            waitForIdle()
            val arrived = onNodeWithTag(GALLERY_TAG).fetchSemanticsNode().boundsInRoot
            assertEquals(arrived.left, loading.left, "the photo moves sideways when the page arrives")
            assertEquals(arrived.width, loading.width, "the photo changes width when the page arrives")
        }

    @Test
    fun `the loading photo stands where the photo arrives`() = photoMovesOnArrival(compact = false)

    @Test
    fun `the loading photo stands where the photo arrives on a phone`() = photoMovesOnArrival(compact = true)

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

    private fun description(): ProductDescription =
        (decode("product_description.json") as ColumnComponent).children.filterIsInstance<ProductDescription>().single()

    private companion object {
        const val DESKTOP = 1440
        const val PHONE = 390
        const val HEIGHT = 1600

        /** The headphones' first four specifications, which the server sends as the tiles (`ProductScreen`). */
        val SEED_FACTS =
            listOf(
                Highlight("Battery", "Up to 40 hours, 3 min charge = 3 h"),
                Highlight("Noise cancelling", "Adaptive, 12 microphones"),
                Highlight("Connectivity", "Bluetooth 5.4 · LDAC · multipoint"),
                Highlight("Weight", "250 g"),
            )
    }
}
