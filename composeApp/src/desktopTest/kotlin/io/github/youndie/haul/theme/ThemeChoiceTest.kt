package io.github.youndie.haul.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** B-180: the theme follows the system's setting until the shopper chooses, and the choice is kept for the next visit. */
class ThemeChoiceTest {
    @Test
    fun `nothing chosen, the page follows the system's setting either way`() {
        val choice = ThemeChoice(MemoryThemeMemory())
        assertEquals(ThemeMode.System, choice.mode)
        assertEquals(HaulColorScheme.Light, choice.scheme(systemDark = false))
        assertEquals(HaulColorScheme.Dark, choice.scheme(systemDark = true))
    }

    @Test
    fun `a choice overrides the system's setting, and is kept for the next visit`() {
        val memory = MemoryThemeMemory()
        ThemeChoice(memory).choose(ThemeMode.Dark)
        val next = ThemeChoice(memory)
        assertEquals(ThemeMode.Dark, next.mode)
        assertEquals(HaulColorScheme.Dark, next.scheme(systemDark = false))
        next.choose(ThemeMode.Light)
        assertEquals(HaulColorScheme.Light, ThemeChoice(memory).scheme(systemDark = true))
        next.choose(ThemeMode.System)
        assertEquals(HaulColorScheme.Dark, ThemeChoice(memory).scheme(systemDark = true))
    }

    @Test
    fun `a kept value is read by its key, and anything else is no choice`() {
        assertEquals(listOf("system", "light", "dark"), ThemeMode.entries.map { it.key })
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.of(it.key)) }
        assertNull(ThemeMode.of(null))
        assertNull(ThemeMode.of("sepia"))
    }
}
