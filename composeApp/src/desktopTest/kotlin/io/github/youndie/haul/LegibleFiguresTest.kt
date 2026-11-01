package io.github.youndie.haul

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.legibleOpticalSize
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-106: Bodoni Moda at the canvas's optical size draws 0.2 px hairlines — no hyphen or dash, a 4 that reads as a 1.
 * A display style draws its figures and punctuation at [legibleOpticalSize] and its letters as the canvas does, and
 * that moves no line: a figure's and a mark's advance is the same in both cuts.
 */
@OptIn(ExperimentalTestApi::class)
class LegibleFiguresTest {
    /** What [HaulType.legibleFigures] makes of [text] in the style [style] builds, with the display family at [size]. */
    private fun legible(
        text: AnnotatedString,
        size: Float = 30f,
        style: @Composable () -> TextStyle = { HaulType.display(size, 800) },
    ): Pair<AnnotatedString, FontFamily> {
        var result: Pair<AnnotatedString, FontFamily>? = null
        runDesktopComposeUiTest(400, 100) {
            setContent {
                HaulTheme(FixtureFonts.fonts, compact = false) {
                    result =
                        HaulType.legibleFigures(text, style()) to FixtureFonts.fonts.bodoni(legibleOpticalSize(size))
                }
            }
            waitForIdle()
        }
        return checkNotNull(result)
    }

    /** The indices of [text] drawn in [family] by a span. */
    private fun spanned(
        text: AnnotatedString,
        family: FontFamily,
    ): Set<Int> =
        text.spanStyles
            .filter { it.item.fontFamily == family }
            .flatMap { it.start until it.end }
            .toSet()

    @Test
    fun `a display style draws its figures and punctuation in the legible cut and its letters as the canvas does`() {
        val title = "Thanks, Sam — order #HL-48303, $31.49 · 4.8"
        val (shown, legibleCut) = legible(AnnotatedString(title))
        val figures = title.indices.filter { !title[it].isLetter() && !title[it].isWhitespace() }.toSet()
        assertEquals(figures, spanned(shown, legibleCut))
        assertEquals(title, shown.text)
    }

    @Test
    fun `a family a span already chose is kept`() {
        val text =
            buildAnnotatedString {
                append("SH-")
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append("1000") }
                append("XM6")
            }
        val (shown, legibleCut) = legible(text)
        assertEquals(setOf(2, 9), spanned(shown, legibleCut))
    }

    @Test
    fun `a body style is left as it is`() {
        val text = AnnotatedString("SH-718N — $31.49")
        val (shown, _) = legible(text, style = { HaulType.text(15f) })
        assertTrue(shown.spanStyles.isEmpty(), "Archivo got spans: ${shown.spanStyles}")
    }

    @Test
    fun `the legible cut moves no figure, dash or price at any size the canvas sets the face in`() {
        val text = AnnotatedString("$31.49 — 4.8 – 2,480 · −$140.00 15:00 – 18:00 -1000")
        listOf(20f, 22f, 24f, 26f, 28f, 30f, 32f, 34f, 36f, 40f, 44f, 48f, 56f, 72f, 96f, 112f, 128f).forEach { size ->
            var widths: Pair<Int, Int>? = null
            runDesktopComposeUiTest(2_000, 200) {
                setContent {
                    HaulTheme(FixtureFonts.fonts, compact = false) {
                        val style = HaulType.display(size, 800)
                        val measurer = rememberTextMeasurer()
                        val shown = HaulType.legibleFigures(text, style)
                        widths =
                            measurer.measure(text, style, softWrap = false).size.width to
                            measurer.measure(shown, style, softWrap = false).size.width
                    }
                }
                waitForIdle()
            }
            val (canvas, legibleCut) = checkNotNull(widths)
            assertTrue(
                abs(canvas - legibleCut) <= 1,
                "at $size px the line went from $canvas to $legibleCut px",
            )
        }
    }
}
