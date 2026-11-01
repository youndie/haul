package io.github.youndie.haul

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.youndie.haul.feature.identity.PreferencesSessionStore
import io.github.youndie.haul.shell.BackStackHistory
import io.github.youndie.haul.shell.DesktopLinks
import io.github.youndie.haul.theme.PreferencesThemeMemory
import io.github.youndie.haul.theme.THEME_TITLE
import io.github.youndie.haul.theme.ThemeChoice
import io.github.youndie.haul.theme.ThemeMode
import io.github.youndie.haul.theme.drawIn
import io.github.youndie.haul.theme.followSystemAppearance
import io.ktor.client.engine.cio.CIO
import java.awt.Dimension

/**
 * The desktop app (B-176): the storefront in a window, browsing the server [serverOrigin] names — the public stand
 * unless `--server=<origin>` or `HAUL_SERVER` says another, a local stack's `http://localhost:8080`. The window's
 * title is the page's, with the server's origin beside it when it is not the stand's; its Go menu goes back and
 * forward through the pages the app has shown, the browser's buttons it has none of. Its View menu holds the theme's
 * switch beside the footer's (B-180), and the window follows the scheme the page is drawn in.
 */
public fun main(args: Array<String>) {
    followSystemAppearance()
    val origin = serverOrigin(args.toList(), System.getenv(SERVER_VARIABLE))
    val http = storeClient(origin, CIO.create())
    val history = BackStackHistory()
    val sessions = PreferencesSessionStore(origin)
    val links = DesktopLinks(origin)
    // The system's scheme unless the shopper chose one, here or in the footer, kept between runs (B-180).
    val theme = ThemeChoice(PreferencesThemeMemory())
    // The keys a browser goes back and forward with: ⌘[ and ⌘] on a Mac, Alt and the arrows elsewhere.
    val mac = System.getProperty("os.name").orEmpty().startsWith("Mac")
    val back = if (mac) Key.LeftBracket else Key.DirectionLeft
    val backKey = KeyShortcut(back, meta = mac, alt = !mac)
    val forward = if (mac) Key.RightBracket else Key.DirectionRight
    val forwardKey = KeyShortcut(forward, meta = mac, alt = !mac)
    application {
        var page by remember { mutableStateOf(STORE_NAME) }
        val state = rememberWindowState(size = DpSize(1280.dp, 860.dp))
        val title = if (origin == STAND) page else "$page — $origin"
        Window(
            onCloseRequest = {
                http.close()
                exitApplication()
            },
            title = title,
            state = state,
        ) {
            // The phone's arrangement is drawn from 375 up; narrower, nothing is laid out for it.
            LaunchedEffect(Unit) { window.minimumSize = Dimension(MIN_WIDTH, MIN_HEIGHT) }
            val systemDark = isSystemInDarkTheme()
            val scheme = theme.scheme(systemDark)
            LaunchedEffect(scheme) { window.drawIn(scheme) }
            MenuBar {
                Menu("Go") {
                    Item(
                        "Back",
                        enabled = history.canGoBack,
                        shortcut = backKey,
                        onClick = history::back,
                    )
                    Item(
                        "Forward",
                        enabled = history.canGoForward,
                        shortcut = forwardKey,
                        onClick = history::forward,
                    )
                }
                Menu("View") {
                    Menu(THEME_TITLE) {
                        ThemeMode.entries.forEach { mode ->
                            RadioButtonItem(
                                mode.label,
                                selected = theme.mode == mode,
                                onClick = { theme.choose(mode) },
                            )
                        }
                    }
                }
            }
            DesktopStore(
                http,
                origin,
                history,
                sessions,
                title = { page = it },
                links = links,
                theme = theme,
                systemDark = systemDark,
            )
        }
    }
}

/**
 * The server the app browses: `--server=<origin>` among [args], else [variable] (`HAUL_SERVER`), else the [STAND];
 * a trailing slash is dropped, so the origin is written one way wherever it is compared and kept.
 */
internal fun serverOrigin(
    args: List<String>,
    variable: String?,
): String {
    val named = args.firstNotNullOfOrNull { it.removePrefix(SERVER_ARG).takeIf { _ -> it.startsWith(SERVER_ARG) } }
    return (named ?: variable)?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: STAND
}

private const val SERVER_ARG = "--server="
private const val SERVER_VARIABLE = "HAUL_SERVER"
private const val STORE_NAME = "Haul"
private const val MIN_WIDTH = 375
private const val MIN_HEIGHT = 560
