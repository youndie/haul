package io.github.youndie.haul.theme

import java.io.File

/**
 * A string literal of the code: the line it starts on, its [text] with escapes decoded and templates left out,
 * its [shape] with each template standing as one letter (so «${name}'s» still reads as a word with an apostrophe
 * in it), and the [callee] it is written into — the name before the innermost bracket around it: `Suppress`
 * for a suppression's reason, `require` for `require(x) { "…" }`, `IllegalStateException` for an exception's
 * message, `map` for a lambda's body.
 */
internal data class SourceString(
    val line: Int,
    val text: String,
    val shape: String,
    val callee: String,
)

/** The Kotlin sources whose strings the page draws, and enough of Kotlin's lexer to find their string literals. */
internal object SourceStrings {
    /** Where the strings the page draws are written: the client, the contract, and the server's trees and seed. */
    val ROOTS = listOf("src/commonMain/kotlin", "../shared/src/commonMain/kotlin", "../server/src/main/kotlin")

    /** Every `.kt` file under [ROOTS]. */
    fun files(): List<File> = ROOTS.flatMap { root -> File(root).walk().filter { it.extension == "kt" }.toList() }

    /** The string literals of a Kotlin [source]; comments and character literals are skipped. */
    fun literals(source: String): List<SourceString> = Lexer(source).strings()

    private class Lexer(
        private val source: String,
    ) {
        private var i = 0
        private var line = 1
        private val found = mutableListOf<SourceString>()

        /** The callee of each bracket that is open, innermost last. */
        private val open = ArrayDeque<String>()

        /** The identifier just before the cursor, whitespace aside; empty after anything else. */
        private var word = ""

        /** The callee of the parenthesis that closed just before the cursor, for a trailing lambda. */
        private var closed = ""

        fun strings(): List<SourceString> {
            while (i < source.length) {
                val c = source[i]
                when {
                    at("//") -> while (i < source.length && source[i] != '\n') advance()
                    at("/*") -> blockComment()
                    at(RAW) -> string(raw = true).also { forget() }
                    at("\"") -> string(raw = false).also { forget() }
                    at("'") -> charLiteral().also { forget() }
                    c.isLetterOrDigit() || c == '_' -> identifier()
                    c.isWhitespace() -> advance()
                    c == '(' -> open(word)
                    c == '{' -> open(closed.ifEmpty { word })
                    c == '[' -> open("")
                    c == ')' -> close().also { closed = it }
                    c == '}' || c == ']' -> close()
                    else -> advance().also { forget() }
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

        private fun forget() {
            word = ""
            closed = ""
        }

        private fun identifier() {
            val start = i
            while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_')) advance()
            word = source.substring(start, i)
            closed = ""
        }

        private fun open(callee: String) {
            open.addLast(callee)
            advance()
            forget()
        }

        private fun close(): String {
            advance()
            forget()
            return open.removeLastOrNull() ?: ""
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
            val shape = StringBuilder()
            val end = if (raw) RAW else "\""
            advance(end.length)
            while (i < source.length && !at(end)) {
                when {
                    at("\${") -> {
                        template()
                        shape.append(TEMPLATE)
                    }

                    !raw && at("\\u") -> {
                        val char = source.substring(i + 2, i + 6).toInt(16).toChar()
                        text.append(char)
                        shape.append(char)
                        advance(6)
                    }

                    !raw && at("\\") -> {
                        text.append(source[i + 1])
                        shape.append(source[i + 1])
                        advance(2)
                    }

                    else -> {
                        text.append(source[i])
                        shape.append(source[i])
                        advance()
                    }
                }
            }
            advance(end.length)
            found += SourceString(start, text.toString(), shape.toString(), open.lastOrNull() ?: "")
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

            /** What a template stands as in a literal's shape: a letter, since it is usually a word. */
            const val TEMPLATE = 'x'
        }
    }
}
