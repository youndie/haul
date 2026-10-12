package io.github.youndie.haul.theme

import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.paragraph.FontCollection
import org.jetbrains.skia.paragraph.ParagraphBuilder
import org.jetbrains.skia.paragraph.ParagraphStyle
import org.jetbrains.skia.paragraph.TextStyle
import org.jetbrains.skia.paragraph.TypefaceFontProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-128: the storefront loads nothing from another origin. In the browser Compose sends every code point a
 * paragraph cannot draw with the faces it has to a downloader that fetches a Noto face from fonts.gstatic.com
 * (`installFallbackFontDownloader`, installed by every `ComposeViewport`, with no switch to turn it off). So
 * every character the code writes into a string — the client's copy, and the server's, which reaches the page
 * in the trees — must be drawn by one of the four bundled faces.
 *
 * The check is the browser's own: a Skia paragraph over the bundled files alone (no system font to fall back
 * to) reports the code points it left unresolved. That is also what decides the invisible ones — a zero-width
 * space or a word joiner no face has a glyph for is still drawn, as nothing.
 */
class BundledGlyphsTest {
    private val faces = listOf("archivo.ttf", "jetbrains_mono.ttf", "bodoni_moda.ttf", "bodoni_moda_italic.ttf")

    private val fonts: FontCollection by lazy {
        val provider = TypefaceFontProvider()
        faces.forEach { provider.registerTypeface(FontMgr.default.makeFromFile("$FONTS/$it"), it) }
        FontCollection().apply {
            setAssetFontManager(provider)
            setDefaultFontManager(TypefaceFontProvider())
            setEnableFallback(false)
        }
    }

    /** The code points of [text] no bundled face draws. */
    private fun unresolved(text: String): Set<Int> {
        val style = TextStyle().setFontFamilies(faces.toTypedArray()).setFontSize(16f)
        val paragraph = ParagraphBuilder(ParagraphStyle(), fonts).pushStyle(style).addText(text).build()
        paragraph.layout(Float.POSITIVE_INFINITY)
        return paragraph.unresolvedCodepoints.toSet()
    }

    @Test
    fun `every character a string in the code holds is in a bundled face`() {
        val missing =
            SourceStrings
                .files()
                .flatMap { file ->
                    stringLiterals(file.readText()).map { (line, text) -> "${file.path}:$line" to text }
                }.mapNotNull { (where, text) -> unresolved(text).takeIf { it.isNotEmpty() }?.let { where to it } }
                .map { (where, points) -> "$where: ${points.joinToString { "U+%04X".format(it) }}" }
        assertEquals(emptyList(), missing, "characters no bundled face draws; each would fetch a font from Google")
    }

    /** The control: the check sees a character none of the faces has, and passes the invisible ones the code uses. */
    @Test
    fun `the check tells a missing glyph from an invisible one`() {
        assertEquals(setOf(RETURN), unresolved("All 14,870 results ${RETURN.char}"))
        assertEquals(emptySet(), unresolved(listOf(0x200B, 0x2060, 0x00A0).joinToString("Thu") { it.char }))
        val source = "val a = \"x\" // \"${RETURN.char}\"\n/* \"${RETURN.char}\" */ val b = '${RETURN.char}'"
        assertEquals(listOf(1 to "x"), stringLiterals(source))
    }

    private companion object {
        const val FONTS = "src/commonMain/composeResources/font"

        /** U+21B5, the canvas's «↵»: none of the four faces has it. */
        const val RETURN = 0x21B5

        val Int.char: String get() = Char(this).toString()

        /** The string literals of a Kotlin [source], each with the line it starts on (see [SourceStrings]). */
        fun stringLiterals(source: String): List<Pair<Int, String>> =
            SourceStrings.literals(source).map { it.line to it.text }
    }
}
