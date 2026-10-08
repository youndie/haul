package io.github.youndie.haul

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.github.youndie.haul.feature.identity.BrowserSessionStore
import io.github.youndie.haul.feature.identity.Identity
import io.github.youndie.haul.feature.identity.IdentityApi
import io.github.youndie.haul.feature.identity.OidcSignInFlow
import io.github.youndie.haul.shell.WindowHistory
import io.github.youndie.haul.shell.ktorTransport
import io.github.youndie.haul.ui.coilPhotoLoader
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import kotlinx.browser.document
import kotlinx.browser.window

/**
 * The bundle's entry point: the whole page is the storefront. The server serves the page, so every
 * request — the photos, the screens' trees, sign-in's merge — goes over one client, the browser's
 * fetch, to this origin; the screens' through [Identity.send], which adds the bearer token or the
 * guest id. The sign-in returns to `signed-in.html` beside the bundle, the address the realm's client
 * registers.
 */
@OptIn(ExperimentalComposeUiApi::class)
public fun main() {
    val http = HttpClient(Js)
    val origin = window.location.origin
    val photos = coilPhotoLoader(http, origin)
    val identity =
        Identity(
            api = IdentityApi(http),
            flow = OidcSignInFlow(redirectUri = "$origin/signed-in.html"),
            store = BrowserSessionStore(),
        )
    val transport = ktorTransport(http, origin, identity::send)
    ComposeViewport(document.body!!) { App(photos, transport, WindowHistory, identity) }
}
