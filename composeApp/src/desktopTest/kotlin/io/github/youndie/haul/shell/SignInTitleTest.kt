package io.github.youndie.haul.shell

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.ui.accented
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-121: «Checkout needs a sign-in» broke at its hyphen at 1440 — «sign-» on one line, «in» on the next. The
 * prompt's titles keep the word whole at every width, at both sizes the title is set in, and the accent is still
 * found in them (a title whose hyphen differs from the accent's loses its italic).
 */
@OptIn(ExperimentalTestApi::class)
class SignInTitleTest {
    @Test
    fun `a sign-in prompt's title never breaks inside sign-in`() {
        val broken = mutableListOf<String>()
        val accents = mutableListOf<String>()
        listOf(false, true).forEach { compact ->
            runDesktopComposeUiTest(400, 100) {
                setContent {
                    HaulTheme(FixtureFonts.fonts, compact = compact) {
                        val style = notFoundTitle(compact)
                        val measurer = rememberTextMeasurer()
                        // Narrower than the word itself, any line breaks it somewhere: the widths start where it fits.
                        val word = measurer.measure(SIGN_IN_WORD, style, softWrap = false).size.width
                        listOf(PageKind.Checkout, PageKind.Order, PageKind.Other).forEach { kind ->
                            val title = HaulType.legibleFigures(accented(kind.signInTitle, SIGN_IN_WORD), style)
                            title.spanStyles
                                .filter { it.item.fontStyle == FontStyle.Italic }
                                .forEach { accents += title.text.substring(it.start, it.end) }
                            for (width in (word + 1)..MAX_WIDTH step 3) {
                                val lines = measurer.measure(title, style, constraints = Constraints(maxWidth = width))
                                (0 until lines.lineCount - 1)
                                    .map { lines.getLineEnd(it) }
                                    .filter { end ->
                                        title.text.substring(0, end).trimEnd().endsWith(
                                            SIGN_IN_WORD.take(HYPHEN_AT + 1),
                                        )
                                    }.forEach { _ ->
                                        broken += "${kind.signInTitle} at $width px (compact $compact)"
                                    }
                            }
                        }
                    }
                }
                waitForIdle()
            }
        }
        assertEquals(emptyList(), broken.distinct().take(5), "the title broke after «sign» and its hyphen")
        assertTrue(accents.isNotEmpty() && accents.all { it == SIGN_IN_WORD }, "the accent was lost: $accents")
    }

    private companion object {
        /** Where the hyphen is in [SIGN_IN_WORD]. */
        const val HYPHEN_AT = 4

        /** Wider than any of the titles on one line. */
        const val MAX_WIDTH = 1_600
    }
}
