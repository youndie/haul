package io.github.youndie.haul.theme

import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.paragraph.FontCollection
import org.jetbrains.skia.paragraph.ParagraphBuilder
import org.jetbrains.skia.paragraph.ParagraphStyle
import org.jetbrains.skia.paragraph.TextStyle
import org.jetbrains.skia.paragraph.TypefaceFontProvider
import java.io.File
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
            SOURCES
                .flatMap { root -> File(root).walk().filter { it.extension == "kt" }.toList() }
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

        /** Where the strings the page draws are written: the client, the contract, and the server's trees and seed. */
        val SOURCES = listOf("src/commonMain/kotlin", "../shared/src/commonMain/kotlin", "../server/src/main/kotlin")

        /** U+21B5, the canvas's «↵»: none of the four faces has it. */
        const val RETURN = 0x21B5

        val Int.char: String get() = Char(this).toString()

        /**
         * The string literals of a Kotlin [source], each with the line it starts on, escapes decoded and templates
         * left out; comments and character literals are skipped. Enough of Kotlin's lexer for strings, no more.
         */
        fun stringLiterals(source: String): List<Pair<Int, String>> = Lexer(source).strings()
    }

    private class Lexer(
        private val source: String,
    ) {
        private var i = 0
        private var line = 1
        private val found = mutableListOf<Pair<Int, String>>()

        fun strings(): List<Pair<Int, String>> {
            while (i < source.length) {
                when {
                    at("//") -> while (i < source.length && source[i] != '\n') advance()
                    at("/*") -> blockComment()
                    at(RAW) -> string(raw = true)
                    at("\"") -> string(raw = false)
                    at("'") -> charLiteral()
                    else -> advance()
                }
            }
            return found
        }

        private fun at(token: String) = source.startsWith(token, i)

        private fun advance(n: Int = 1) =
            repeat(n) {
                if (i < source.length && source[i] == '\n') line++
                i++
            }

        private fun blockComment() {
            var depth = 0
            do {
                when {
                    at("/*") -> depth++.also { advance(2) }
                    at("*/") -> depth--.also { advance(2) }
                    else -> advance()
                }
            } while (i < source.length && depth > 0)
        }

        private fun string(raw: Boolean) {
            val start = line
            val text = StringBuilder()
            val end = if (raw) RAW else "\""
            advance(end.length)
            while (i < source.length && !at(end)) {
                when {
                    at("\${") -> template()

                    !raw &&
                        at(
                            "\\u",
                        )
                    -> text.append(source.substring(i + 2, i + 6).toInt(16).toChar()).also { advance(6) }

                    !raw && at("\\") -> text.append(source[i + 1]).also { advance(2) }

                    else -> text.append(source[i]).also { advance() }
                }
            }
            advance(end.length)
            found += start to text.toString()
        }

        private fun template() {
            var depth = 1
            advance(2)
            while (i < source.length && depth > 0) {
                when (source[i]) {
                    '{' -> depth++
                    '}' -> depth--
                }
                advance()
            }
        }

        private fun charLiteral() {
            advance()
            while (i < source.length && source[i] != '\'') advance(if (source[i] == '\\') 2 else 1)
            advance()
        }

        private companion object {
            const val RAW = "\"\"\""
        }
    }
}
