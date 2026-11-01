package io.github.youndie.haul.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-180: the dark scheme reads as the light one does. Every pair of words and ground the store draws has at least the
 * light pair's contrast in the dark, or 7:1 (WCAG's AAA) where the light pair is above that — the research's table
 * (D7, «Decided in B-180»). And a colour the server sends — a tile's tone — has its dark counterpart, or is one of the
 * two the dark page keeps as they are.
 */
class HaulColorSchemeTest {
    @Test
    fun `every pair of words and ground reads in the dark at least as in the light, or at 7 to 1`() {
        val short =
            PAIRS.mapNotNull { (name, pair) ->
                val light = contrast(pair(HaulColorScheme.Light))
                val dark = contrast(pair(HaulColorScheme.Dark))
                "$name: light ${"%.2f".format(light)}, dark ${"%.2f".format(dark)}".takeIf {
                    dark < minOf(light, AAA) - EPSILON
                }
            }
        assertEquals(emptyList(), short)
    }

    @Test
    fun `every tone the server sends has its dark counterpart or is kept, and its words read on it`() {
        val tones = serverTones()
        assertTrue(CANVAS_TONES.all { it in tones }, "the scan found the canvas's tones: $tones")
        tones.forEach { tone ->
            val counterpart = HaulColorScheme.Dark.tone(tone)
            assertTrue(
                counterpart != HaulColorScheme.Light.tone(tone) || tone in KEPT,
                "$tone has no dark counterpart and is not one the dark page keeps",
            )
            listOf(HaulColorScheme.Light, HaulColorScheme.Dark).forEach { scheme ->
                val words = contrast(scheme.onTone(tone) to scheme.tone(tone))
                assertTrue(words >= AA, "words on $tone in ${if (scheme.isDark) "dark" else "light"}: $words")
            }
        }
    }

    @Test
    fun `a dark tile reads as far from the dark page as a light one from the light page, and no further than 1_5`() {
        CANVAS_TONES.forEach { tone ->
            val light = contrast(HaulColorScheme.Light.tone(tone) to HaulColorScheme.Light.background)
            val dark = contrast(HaulColorScheme.Dark.tone(tone) to HaulColorScheme.Dark.background)
            assertTrue(dark >= light - EPSILON && dark <= 1.5, "$tone: light ${"%.2f".format(light)}, dark $dark")
        }
    }

    @Test
    fun `the light scheme is the canvas's and the light page's, the one every view drawn on its own has`() {
        assertEquals(Color(0xFF2F2BFF), HaulColorScheme.Light.primary)
        assertEquals(Color(0xFFF5F3EE), HaulColorScheme.Light.background)
        // The light page draws a server's tone as sent, the canvas's stone where it is not a colour.
        assertEquals(Color(0xFFE6E4FF), HaulColorScheme.Light.tone("#E6E4FF"))
        assertEquals(Color(0xFFECE9E2), HaulColorScheme.Light.tone("not a tone"))
        assertEquals(false, HaulColorScheme.Light.isDark)
        assertEquals(true, HaulColorScheme.Dark.isDark)
    }

    /** The tones the server's sources write: the seed's tiles, the reviews', a campaign's and a banner's. */
    private fun serverTones(): Set<String> =
        File(SERVER)
            .walk()
            .filter { it.extension == "kt" }
            .flatMap { file -> TONE.findAll(file.readText()).flatMap { HEX.findAll(it.value).map { hex -> hex.value } } }
            .map { it.uppercase() }
            .toSet()

    private fun contrast(pair: Pair<Color, Color>): Double {
        val (a, b) = pair.first.luminance().toDouble() to pair.second.luminance().toDouble()
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private companion object {
        const val AAA = 7.0
        const val AA = 4.5
        const val EPSILON = 0.005

        const val SERVER = "../server/src/main/kotlin"

        /** A tone written to a name that says so: `tone = "#…"`, `TONES = listOf("#…", …)`, `BANNER_TONE = "#…"`. */
        val TONE = Regex("""(?i)\w*tones?\b[^\n=(]*=\s*(?:listOf\(\s*)?(?:"#[0-9A-Fa-f]{6}"\s*,?\s*)+""")
        val HEX = Regex("""#[0-9A-Fa-f]{6}""")

        /** The canvas's nine tile tones. */
        val CANVAS_TONES =
            setOf("#E6E4FF", "#F1EBDD", "#FFE5DD", "#F1E4F5", "#FFF1C9", "#E3F5D8", "#E9F0D2", "#E0EEF7", "#ECE9E2")

        /** The tones the dark page keeps: Acid (a Plus banner, a member's avatar) and Cobalt (the campaign). */
        val KEPT = setOf("#DFFF3A", "#2F2BFF")

        /** The pairs of words (first) and ground (second) the store draws, by the roles' names. */
        val PAIRS: List<Pair<String, (HaulColorScheme) -> Pair<Color, Color>>> =
            listOf(
                "onSurface on background" to { it.onSurface to it.background },
                "onSurface on surfaceContainerLowest" to { it.onSurface to it.surfaceContainerLowest },
                "onSurface on surfaceContainerHigh" to { it.onSurface to it.surfaceContainerHigh },
                "onSurfaceVariant on background" to { it.onSurfaceVariant to it.background },
                "onSurfaceVariant on surfaceContainerLowest" to { it.onSurfaceVariant to it.surfaceContainerLowest },
                "outline on background" to { it.outline to it.background },
                "outline on surfaceContainerLowest" to { it.outline to it.surfaceContainerLowest },
                "outline on surfaceContainerHigh" to { it.outline to it.surfaceContainerHigh },
                "outlineMuted on background" to { it.outlineMuted to it.background },
                "outlineMuted on surfaceContainerLowest" to { it.outlineMuted to it.surfaceContainerLowest },
                "primary on background" to { it.primary to it.background },
                "primary on surfaceContainerLowest" to { it.primary to it.surfaceContainerLowest },
                "onPrimary on primary" to { it.onPrimary to it.primary },
                "onPrimary on inverseSurface" to { it.onPrimary to it.inverseSurface },
                "inverseOnSurface on inverseSurface" to { it.inverseOnSurface to it.inverseSurface },
                "inverseAccent on inverseSurface" to { it.inverseAccent to it.inverseSurface },
                "onPrimaryFixed on primaryFixed" to { it.onPrimaryFixed to it.primaryFixed },
                "secondaryContainer on primaryFixed" to { it.secondaryContainer to it.primaryFixed },
                "onSecondaryContainer on secondaryContainer" to { it.onSecondaryContainer to it.secondaryContainer },
                "error on background" to { it.error to it.background },
                "error on surfaceContainerLowest" to { it.error to it.surfaceContainerLowest },
                "error on errorContainer" to { it.error to it.errorContainer },
                "onSurface on errorContainer" to { it.onSurface to it.errorContainer },
                "onBand on band" to { it.onBand to it.band },
                "onBandVariant on band" to { it.onBandVariant to it.band },
                "onBandStrong on band" to { it.onBandStrong to it.band },
                "secondaryContainer on band" to { it.secondaryContainer to it.band },
                "primary on band" to { it.primary to it.band },
            )
    }
}
