package io.github.youndie.haul.theme

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Which scheme the shopper asked for (B-180): the system's — the browser's `prefers-color-scheme`, the desktop's own
 * setting — unless they chose light or dark. [key] is how it is kept, and how `index.html` reads it before the bundle.
 */
public enum class ThemeMode(
    public val key: String,
    public val label: String,
) {
    System("system", "System"),
    Light("light", "Light"),
    Dark("dark", "Dark"),
    ;

    public companion object {
        /** The mode kept as [key], or `null` for anything else — nothing kept, or a value of another version. */
        public fun of(key: String?): ThemeMode? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Where the shopper's choice is kept between visits: the browser's `localStorage` (`BrowserThemeMemory`), the desktop's
 * preferences (`PreferencesThemeMemory`), memory in a test.
 */
public interface ThemeMemory {
    /** The mode kept, or `null` when none is. */
    public fun read(): ThemeMode?

    public fun write(mode: ThemeMode)
}

/** A [ThemeMemory] that lasts as long as the object: a test's, and the root's when its entry point keeps none. */
public class MemoryThemeMemory(
    private var mode: ThemeMode? = null,
) : ThemeMemory {
    override fun read(): ThemeMode? = mode

    override fun write(mode: ThemeMode) {
        this.mode = mode
    }
}

/**
 * The switch's state: the [mode] the shopper chose — [ThemeMode.System] until they choose — read from and written to
 * [memory]. Whether the page is dark is [isDark] of the system's setting, which the root passes in as it changes.
 */
@Stable
public class ThemeChoice(
    private val memory: ThemeMemory,
) {
    public var mode: ThemeMode by mutableStateOf(memory.read() ?: ThemeMode.System)
        private set

    /** The switch pressed: the page is drawn so at once, and so on the next visit. */
    public fun choose(mode: ThemeMode) {
        this.mode = mode
        memory.write(mode)
    }

    /** Whether the page is dark, the system being [systemDark]: the system's choice unless the shopper made one. */
    public fun isDark(systemDark: Boolean): Boolean =
        when (mode) {
            ThemeMode.System -> systemDark
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }

    /** The scheme the page is drawn in. */
    public fun scheme(systemDark: Boolean): HaulColorScheme =
        if (isDark(systemDark)) HaulColorScheme.Dark else HaulColorScheme.Light
}

/** The storefront's switch; `null` — a view drawn on its own, a golden — draws none. */
public val LocalThemeChoice: ProvidableCompositionLocal<ThemeChoice?> = staticCompositionLocalOf { null }

/** What the footer's switch is titled, and the window menu's entry. */
public const val THEME_TITLE: String = "Theme"

/** Where a browser keeps the choice (`localStorage`), and what `index.html` reads before the bundle (`StaticFrameTest`). */
public const val THEME_KEY: String = "haul.theme"

/** The page root's attribute that names a chosen scheme in the browser, which `index.html`'s styles read. */
public const val THEME_ATTRIBUTE: String = "data-theme"
