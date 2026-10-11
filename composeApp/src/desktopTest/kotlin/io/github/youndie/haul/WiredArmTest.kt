package io.github.youndie.haul

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * B-90: B-28's `wired` measurement arm (`scripts/measure-first-load.sh`) patches the entry point, swapping the
 * storefront's root for a fixed Home body, and stopped building once `Main.kt` no longer read `{ App() }` — nothing
 * noticed until the arm was next run (B-80). The script names what it swaps, `WIRED_ROOT`; this holds `Main.kt` to
 * exactly one such call, so a change to the entry point that would break the arm fails here instead.
 */
class WiredArmTest {
    private val script = File("../scripts/measure-first-load.sh").readText()
    private val main = File("src/wasmJsMain/kotlin/io/github/youndie/haul/Main.kt").readText()

    @Test
    fun `the wired arm's patch finds the one storefront root in the entry point`() {
        val pattern = Regex("""WIRED_ROOT = re\.compile\(r"(.+)"\)""").find(script)?.groupValues?.get(1)
        assertNotNull(pattern, "measure-first-load.sh names no WIRED_ROOT")
        val calls = Regex(pattern).findAll(main).map { it.value.substringBefore('(') }.toList()
        assertEquals(listOf("App"), calls, "the wired arm's patch would not apply to Main.kt")
    }
}
