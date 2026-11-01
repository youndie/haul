package io.github.youndie.haul.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-180: every colour the store draws is a role of the scheme being drawn ([HaulColorScheme]), so a dark page is dark
 * everywhere. A colour written into a renderer — `Color(0xFF…)`, `Color.White`, `"#F5F3EE"` — is drawn the same in both
 * schemes, which is what left a light island on a dark page. This reads the client's sources (the code, its comments
 * aside) and fails on any colour written outside the theme, but for the few the allow-list keeps, each with why.
 */
class HardCodedColourTest {
    @Test
    fun `no colour is written outside the theme but the ones the allow-list keeps`() {
        val found =
            ROOTS
                .flatMap { root -> File(root).walk().filter { it.extension == "kt" }.toList() }
                .flatMap { file -> colours(file.invariantSeparatorsPath, file.readText()) }
        val kept = found.filterNot { finding -> ALLOWED.any { it.keeps(finding) } }
        assertEquals(
            emptyList(),
            kept.map { it.toString() },
            "a colour outside the theme: make it a role of both schemes",
        )
        val stale = ALLOWED.filter { allowed -> found.none { allowed.keeps(it) } }
        assertEquals(emptyList(), stale, "an allow-list entry that keeps nothing any more: take it out")
    }

    /** The control: each way of writing a colour is found in code and in a string, and none in a comment. */
    @Test
    fun `the scan finds a colour written in any of its ways, and none in a comment`() {
        val source =
            """
            val a = Color(0xFFF5F3EE) // Color(0xFF000000) in a comment
            val b = Color.White.copy(alpha = 0.5f)
            /* Color.Black, "#0F0F0F" */
            val c = Color(red = 1f, green = 0f, blue = 0f)
            val d = Color(0.5f, 0.5f, 0.5f)
            val e = "#2F2BFF"
            val f = Color.hsv(240f, 1f, 1f)
            val g = Color.Transparent.takeIf { Color.Unspecified != it }
            val h = 0xFF2F2BFF
            """.trimIndent()
        val found = colours("Sample.kt", source).map { it.line to it.text }
        assertEquals(
            listOf(
                1 to "Color(0x",
                2 to "Color.White",
                4 to "Color(red",
                5 to "Color(0.5f,",
                6 to "\"#2F2BFF\"",
                7 to "Color.hsv(",
                9 to "0xFF2F2BFF",
            ),
            found,
        )
    }

    /** A colour written at [line] of [file]: the [text] that writes it. */
    private data class Finding(
        val file: String,
        val line: Int,
        val text: String,
    ) {
        override fun toString(): String = "$file:$line: $text"
    }

    /** One colour the scan keeps: written in a file ending in [file], as [text], because of [reason]. */
    private data class Allowed(
        val file: String,
        val text: String,
        val reason: String,
    ) {
        fun keeps(finding: Finding): Boolean = finding.file.endsWith(file) && finding.text.startsWith(text)
    }

    private fun colours(
        file: String,
        source: String,
    ): List<Finding> {
        val code = withoutComments(source)
        val lines = code.lines()
        return lines.flatMapIndexed { index, line ->
            WRITTEN.findAll(line).map { Finding(file, index + 1, it.value) }.toList()
        }
    }

    /** [source] with its comments blanked out, strings kept: a `//` inside a string (an address) is not a comment. */
    private fun withoutComments(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        while (i < source.length) {
            when {
                source.startsWith("\"\"\"", i) -> {
                    val end = source.indexOf("\"\"\"", i + 3).let { if (it < 0) source.length else it + 3 }
                    out.append(source, i, end)
                    i = end
                }

                source[i] == '"' -> {
                    var j = i + 1
                    while (j < source.length && source[j] != '"' &&
                        source[j] != '\n'
                    ) {
                        j += if (source[j] == '\\') 2 else 1
                    }
                    val end = minOf(j + 1, source.length)
                    out.append(source, i, end)
                    i = end
                }

                source.startsWith("//", i) -> {
                    while (i < source.length && source[i] != '\n') i++
                }

                source.startsWith("/*", i) -> {
                    val end = source.indexOf("*/", i + 2).let { if (it < 0) source.length else it + 2 }
                    // The lines stay, so a finding's line is the source's.
                    out.append(source.substring(i, end).filter { it == '\n' })
                    i = end
                }

                else -> {
                    out.append(source[i])
                    i++
                }
            }
        }
        return out.toString()
    }

    private companion object {
        /** The client's code: the storefront, the desktop app's and the browser's own. */
        val ROOTS = listOf("src/commonMain/kotlin", "src/desktopMain/kotlin", "src/wasmJsMain/kotlin")

        /** How a colour is written: a hex or a components `Color(…)`, a named one, HSV or HSL, a hex string, an ARGB long. */
        val WRITTEN =
            Regex(
                listOf(
                    """\bColor\(\s*0x""",
                    """\bColor\(\s*red\b""",
                    """\bColor\(\s*[0-9.]+f?\s*,""",
                    """\bColor\.(?:White|Black|Red|Green|Blue|Yellow|Cyan|Magenta|Gray|LightGray|DarkGray)\b""",
                    """\bColor\.(?:hsv|hsl)\(""",
                    """"#[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?"""",
                    """\b0x[Ff]{2}[0-9A-Fa-f]{6}\b""",
                ).joinToString("|"),
            )

        val ALLOWED =
            listOf(
                Allowed(
                    "theme/HaulColors.kt",
                    "Color(0x",
                    "the schemes themselves — every role's value, light and dark, the dark tile tones — and hexColor's parse",
                ),
                Allowed(
                    "ui/HaulIcons.kt",
                    "Color.Black",
                    "the icons' paths' paint, which `Icon` tints with the role it is drawn in (ColorFilter.tint)",
                ),
                Allowed(
                    "feature/saved/SavedViews.kt",
                    "\"#E6E4FF\"",
                    "a canvas tile tone, drawn through toneColor, which draws it as the scheme's counterpart",
                ),
            )
    }
}
