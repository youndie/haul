package io.github.youndie.haul

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.youndie.haul.feature.cart.ktorCartCommands
import io.github.youndie.haul.feature.checkout.ktorCheckoutCommands
import io.github.youndie.haul.feature.identity.Identity
import io.github.youndie.haul.feature.identity.IdentityApi
import io.github.youndie.haul.feature.identity.LoopbackSignInFlow
import io.github.youndie.haul.feature.identity.SessionStore
import io.github.youndie.haul.feature.identity.SignInFlow
import io.github.youndie.haul.shell.BrowserHistory
import io.github.youndie.haul.shell.BrowserTitle
import io.github.youndie.haul.shell.ktorCommands
import io.github.youndie.haul.shell.ktorRealtime
import io.github.youndie.haul.shell.ktorTransport
import io.github.youndie.haul.shell.ktorTreeCommands
import io.github.youndie.haul.theme.MemoryThemeMemory
import io.github.youndie.haul.theme.ThemeChoice
import io.github.youndie.haul.ui.LinkCopier
import io.github.youndie.haul.ui.Motion
import io.github.youndie.haul.ui.coilPhotoLoader
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.defaultRequest

/** The server the desktop app browses when it is told no other (B-176): the public stand. */
internal const val STAND: String = "https://haul.kotlin.website"

/**
 * The desktop app's one client to [origin] over [engine] (B-176). The browser's requests go to the page's own origin
 * by themselves; a window has none, so every request the store makes by a bare path — the guest, the sign-in's
 * settings, the merge's address the server writes — is sent to [origin] here, and the ones built whole are sent
 * there already.
 */
internal fun storeClient(
    origin: String,
    engine: HttpClientEngine,
): HttpClient = HttpClient(engine) { defaultRequest { url(origin.trimEnd('/') + "/") } }

/**
 * The storefront in a window (B-176): the same [App] the browser mounts, with the same renderers, over [http] to
 * [origin] — the trees, the commands, the photos and the order's stream, each through [Identity]'s headers, as in the
 * browser. What is the browser's there is the window's here: [history] the app's own entries, [title] the window's,
 * [links] the system clipboard and browser, [sessions] the guest and a sign-in kept between runs. [signIn] is the
 * provider's page in the system's browser, returning to a loopback port ([LoopbackSignInFlow], B-178); the fonts are
 * Compose's resources, read as the app starts. [theme] is the shopper's choice over the system's scheme, [systemDark]
 * (B-180).
 */
@Composable
internal fun DesktopStore(
    http: HttpClient,
    origin: String,
    history: BrowserHistory,
    sessions: SessionStore,
    title: BrowserTitle? = null,
    links: LinkCopier? = null,
    motion: Motion = Motion.Lively,
    signIn: SignInFlow = remember { LoopbackSignInFlow() },
    theme: ThemeChoice = remember { ThemeChoice(MemoryThemeMemory()) },
    systemDark: Boolean = false,
) {
    val identity = remember(http, sessions, signIn) { Identity(IdentityApi(http), signIn, sessions) }
    val photos = remember(http, origin) { coilPhotoLoader(http, origin) }
    val transport = remember(http, origin, identity) { ktorTransport(http, origin, identity::send) }
    val cart = remember(http, origin, identity) { ktorCartCommands(http, origin, identity::send) }
    val commands = remember(http, origin, identity) { ktorCommands(http, origin, identity::send) }
    val checkout = remember(http, origin, identity) { ktorCheckoutCommands(http, origin, identity::send) }
    val trees = remember(http, origin, identity) { ktorTreeCommands(http, origin, identity::send) }
    val realtime = remember(http, origin, identity) { ktorRealtime(http, origin, identity::send) }
    App(
        photos,
        transport,
        history,
        identity,
        cartCommands = cart,
        commands = commands,
        checkoutCommands = checkout,
        treeCommands = trees,
        realtime = realtime,
        links = links,
        motion = motion,
        title = title,
        theme = theme,
        systemDark = systemDark,
    )
}
