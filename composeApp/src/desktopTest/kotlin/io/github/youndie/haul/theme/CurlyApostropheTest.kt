package io.github.youndie.haul.theme

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-137: the store writes ’, as the canvas does — «Couldn’t copy the link», «can’t be sent back» — and a straight
 * apostrophe inside a word is a sentence typed past that. This reads every string literal the page can be sent (the
 * client's copy, the contract's, the server's trees) and fails on `'` between two letters.
 *
 * Two kinds of string are not the store's copy and keep what they have. The seed (`seed/`) is shoppers' and sellers'
 * words — their reviews, questions and answers, as typed — and the products' names. Text for the developer never
 * reaches the page: a suppression's reason, a `require` or `check` message, an exception's, a log line's, a date
 * pattern (`'T'`). Those are told by what the literal is written into ([SourceString.callee]).
 */
class CurlyApostropheTest {
    @Test
    fun `no string the store writes has a straight apostrophe inside a word`() {
        val straight =
            SourceStrings
                .files()
                .filterNot { SEED in it.invariantSeparatorsPath }
                .flatMap { file ->
                    SourceStrings
                        .literals(file.readText())
                        .filter(::isStraight)
                        .map { "${file.path}:${it.line}: ${it.text}" }
                }
        assertEquals(emptyList(), straight, "a straight apostrophe in a word the store writes; write ’ (U+2019)")
    }

    /** The control: the check finds a straight apostrophe in copy, a template's word included, and passes the rest. */
    @Test
    fun `the check tells the store's copy from the developer's text`() {
        val source =
            """
            val a = "You'll earn ${'$'}points"
            val b = "${'$'}{names.joined()}'s list"
            val c = "You’ll earn" + "«Sonora» '24" + "shoppers' words"
            @Suppress("ktlint:kapkan:wall-clock", "the store's clock")
            fun d() = require(ok) { "a plan's payments" }.also { check(it, "it's") }
            val e = IllegalStateException("the provider's page") ?: error("isn't") ?: log.warn("isn't")
            val f = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            val g = items.map { "can't" }
            """.trimIndent()
        val found = SourceStrings.literals(source).filter(::isStraight).map { it.line to it.text }
        assertEquals(listOf(1 to "You'll earn \$points", 2 to "'s list", 8 to "can't"), found)
    }

    private fun isStraight(literal: SourceString): Boolean =
        IN_A_WORD.containsMatchIn(literal.shape) &&
            literal.callee !in DEVELOPERS &&
            !literal.callee.endsWith("Exception") &&
            !literal.callee.endsWith("Error")

    private companion object {
        /** The seed's package: shoppers' and sellers' words and the products' names, as written. */
        const val SEED = "/io/github/youndie/haul/seed/"

        /** `'` with a letter on each side. */
        val IN_A_WORD = Regex("""\p{L}'\p{L}""")

        /** What a literal written straight into is for the developer, not the page. */
        val DEVELOPERS =
            setOf(
                "Suppress",
                "require",
                "requireNotNull",
                "check",
                "checkNotNull",
                "error",
                "ofPattern",
                "trace",
                "debug",
                "info",
                "warn",
            )
    }
}
