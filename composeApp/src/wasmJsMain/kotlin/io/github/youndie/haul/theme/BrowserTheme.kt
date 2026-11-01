package io.github.youndie.haul.theme

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window

/**
 * The shopper's theme in `localStorage` (B-180), kept per browser across visits, under the key `index.html` reads
 * before the bundle runs so the frame it paints is already in the scheme the store will draw. The choice is written to
 * the page's root as `data-theme` too, which the frame's and the page's own background follow from then on. Storage
 * that refuses — a private window, blocked site data — keeps the choice for the page alone.
 */
public class BrowserThemeMemory : ThemeMemory {
    @Suppress(
        "ktlint:kapkan:swallowed-failure",
        "Storage that refuses is a choice kept for this page alone (see the class): the system's scheme next time.",
    )
    override fun read(): ThemeMode? =
        try {
            ThemeMode.of(localStorage.getItem(THEME_KEY))
        } catch (_: Throwable) {
            null
        }

    @Suppress(
        "ktlint:kapkan:swallowed-failure",
        "Storage that refuses is a choice kept for this page alone (see the class): the system's scheme next time.",
    )
    override fun write(mode: ThemeMode) {
        mark(mode)
        try {
            localStorage.setItem(THEME_KEY, mode.key)
        } catch (_: Throwable) {
            // Not kept: the next visit follows the system's scheme.
        }
    }

    /** The page's root says which scheme was chosen, as `index.html`'s script marks it on a load. */
    private fun mark(mode: ThemeMode) {
        val root = document.documentElement ?: return
        if (mode == ThemeMode.System) root.removeAttribute(THEME_ATTRIBUTE) else root.setAttribute(THEME_ATTRIBUTE, mode.key)
    }
}

/**
 * Whether the browser asks for a dark page (`prefers-color-scheme: dark`), as a state that follows the system's
 * setting while the page is open: switched in the system, the page redraws in the other scheme at once.
 */
public fun browserPrefersDark(): MutableState<Boolean> {
    val query = window.matchMedia(PREFERS_DARK)
    val dark = mutableStateOf(query.matches)
    query.addEventListener("change", { dark.value = query.matches })
    return dark
}

private const val PREFERS_DARK = "(prefers-color-scheme: dark)"
