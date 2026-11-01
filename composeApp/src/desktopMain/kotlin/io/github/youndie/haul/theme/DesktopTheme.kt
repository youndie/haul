package io.github.youndie.haul.theme

import androidx.compose.ui.graphics.toArgb
import java.awt.Window
import java.util.prefs.Preferences
import javax.swing.RootPaneContainer

/**
 * The shopper's theme in the desktop's preferences (B-180), beside the guest and the sign-in (`PreferencesSessionStore`):
 * kept between runs of the app, on this machine's account.
 */
internal class PreferencesThemeMemory(
    private val node: Preferences = Preferences.userRoot().node(NODE),
) : ThemeMemory {
    override fun read(): ThemeMode? = ThemeMode.of(node.get(KEY, null))

    override fun write(mode: ThemeMode) = node.put(KEY, mode.key)

    private companion object {
        const val NODE = "io/github/youndie/haul"
        const val KEY = "theme"
    }
}

/**
 * The window drawn as the page is (B-180): its own ground in the scheme's paper, so a resize shows no white before the
 * page catches up, and, on a Mac, its title bar in the scheme's appearance. Windows and Linux draw the title bar
 * themselves, in the system's own scheme; asking Windows for a dark one is a native call (DWM) the app does not make.
 */
internal fun Window.drawIn(scheme: HaulColorScheme) {
    background = java.awt.Color(scheme.background.toArgb(), true)
    (this as? RootPaneContainer)?.rootPane?.putClientProperty(
        MAC_APPEARANCE,
        if (scheme.isDark) MAC_DARK else MAC_LIGHT,
    )
}

/**
 * Asked before the first window opens: on a Mac, the app's windows follow the system's appearance unless one is told
 * otherwise ([drawIn]); elsewhere the property means nothing.
 */
internal fun followSystemAppearance() {
    System.setProperty(MAC_APP_APPEARANCE, "system")
}

private const val MAC_APP_APPEARANCE = "apple.awt.application.appearance"
private const val MAC_APPEARANCE = "apple.awt.windowAppearance"
private const val MAC_DARK = "NSAppearanceNameDarkAqua"
private const val MAC_LIGHT = "NSAppearanceNameAqua"
