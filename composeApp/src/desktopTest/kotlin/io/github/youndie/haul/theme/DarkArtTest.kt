package io.github.youndie.haul.theme

import androidx.compose.ui.graphics.toArgb
import io.github.youndie.haul.PictureVersions
import io.github.youndie.haul.ui.vectorPicture
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * B-181: every seed picture the goldens draw has its dark version beside it (`resources/art`, which the server's
 * ArtFixturesTest holds to what it serves), and the dark version is drawn over the very colour the dark scheme draws
 * the picture's tile in — the server's table of dark tones and the client's are one table in two places, and this is
 * where they meet. A tone the dark page keeps as sent keeps its picture as it is.
 */
class DarkArtTest {
    private val art = File("src/desktopTest/resources/art")

    @Test
    fun `every picture has a dark version over its tile's dark colour`() {
        val light =
            art
                .listFiles()
                .orEmpty()
                .map { it.name }
                .filter { PictureVersions.light(it) == null }
        // Positive control: the pictures are there, so «every one» was looked for.
        assertTrue(light.size > 20, "${light.size} pictures in $art")
        light.forEach { name ->
            val dark = File(art, assertNotNull(PictureVersions.dark(name), name))
            assertTrue(dark.isFile, "$name has no dark version")
            assertNotNull(vectorPicture(dark.readBytes()), "${dark.name} is no picture Skia draws")
            val tone = "#" + name.substringBefore('.').substringAfterLast('_').uppercase()
            val drawn = ground(dark.readText())
            if (tone in KEPT) {
                assertEquals(File(art, name).readText(), dark.readText(), "$name: a kept tone's picture changed")
            } else {
                assertEquals(
                    "#%06X".format(HaulColorScheme.Dark.tone(tone).toArgb() and 0xFFFFFF),
                    drawn,
                    "$name's dark ground",
                )
                assertEquals(tone, ground(File(art, name).readText()), "$name's light ground")
            }
        }
    }

    /** The colour a picture's first shape — its ground, under the hatching — is filled with. */
    private fun ground(svg: String): String =
        assertNotNull(Regex("""<rect [^>]*fill="(#[0-9A-Fa-f]{6})"""").find(svg.substringAfter("</defs>")), svg)
            .groupValues[1]
            .uppercase()

    private companion object {
        /** The tones the dark page draws as sent («Decided in B-180»): Acid and Cobalt. */
        val KEPT = setOf("#DFFF3A", "#2F2BFF")
    }
}
