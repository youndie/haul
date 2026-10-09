package io.github.youndie.haul

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.github.youndie.haul.feature.cart.ktorCartCommands
import io.github.youndie.haul.feature.checkout.ktorCheckoutCommands
import io.github.youndie.haul.feature.identity.BrowserSessionStore
import io.github.youndie.haul.feature.identity.BrowserSignInPopup
import io.github.youndie.haul.feature.identity.Identity
import io.github.youndie.haul.feature.identity.IdentityApi
import io.github.youndie.haul.feature.identity.OidcSignInFlow
import io.github.youndie.haul.feature.identity.PopupSignInFlow
import io.github.youndie.haul.feature.identity.SIGN_IN_WINDOW
import io.github.youndie.haul.shell.WindowHistory
import io.github.youndie.haul.shell.ktorCommands
import io.github.youndie.haul.shell.ktorRealtime
import io.github.youndie.haul.shell.ktorTransport
import io.github.youndie.haul.shell.ktorTreeCommands
import io.github.youndie.haul.ui.coilPhotoLoader
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import kotlinx.browser.document
import kotlinx.browser.window

/**
 * The bundle's entry point: the whole page is the storefront. The server serves the page, so every
 * request — the photos, the screens' trees, the cart's, the checkout's and the dialogs' commands, sign-in's merge —
 * goes over one client, the browser's fetch, to this origin; the screens' and the commands' through [Identity.send],
 * which adds the bearer token or the guest id; so does the order page's stream of live updates (B-29). The
 * sign-in returns to `signed-in.html` beside the bundle, the address the realm's client registers, from a popup
 * the storefront watches itself (B-46).
 */
@OptIn(ExperimentalComposeUiApi::class)
public fun main() {
    val http = HttpClient(Js)
    val origin = window.location.origin
    val photos = coilPhotoLoader(http, origin)
    val identity =
        Identity(
            api = IdentityApi(http),
            flow =
                PopupSignInFlow(
                    open = BrowserSignInPopup::open,
                    delegate = OidcSignInFlow(redirectUri = "$origin/signed-in.html", windowTarget = SIGN_IN_WINDOW),
                ),
            store = BrowserSessionStore(),
        )
    val transport = ktorTransport(http, origin, identity::send)
    val cartCommands = ktorCartCommands(http, origin, identity::send)
    val commands = ktorCommands(http, origin, identity::send)
    val checkoutCommands = ktorCheckoutCommands(http, origin, identity::send)
    val treeCommands = ktorTreeCommands(http, origin, identity::send)
    val realtime = ktorRealtime(http, origin, identity::send)
    ComposeViewport(document.body!!) {
        App(
            photos,
            transport,
            WindowHistory,
            identity,
            cartCommands = cartCommands,
            commands = commands,
            checkoutCommands = checkoutCommands,
            treeCommands = treeCommands,
            realtime = realtime,
        )
    }
}
