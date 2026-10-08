package io.github.youndie.haul

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.github.youndie.haul.ui.coilPhotoLoader
import io.github.youndie.haul.feature.identity.BrowserSessionStore
import io.github.youndie.haul.feature.identity.Identity
import io.github.youndie.haul.feature.identity.IdentityApi
import io.github.youndie.haul.feature.identity.OidcSignInFlow
import io.github.youndie.haul.shell.Trees
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import kotlinx.browser.document
import kotlinx.browser.window

/**
 * The bundle's entry point: the whole page is the storefront. The server serves the page, so every
 * request — the photos, the trees, sign-in's merge — is to this origin and needs no base URL; the
 * sign-in returns to `signed-in.html` beside it, the address the realm's client registers.
 */
@OptIn(ExperimentalComposeUiApi::class)
public fun main() {
    val http = HttpClient(Js)
    val photos = coilPhotoLoader(http, window.location.origin)
    val identity =
        Identity(
            api = IdentityApi(http),
            flow = OidcSignInFlow(redirectUri = "${window.location.origin}/signed-in.html"),
            store = BrowserSessionStore(),
        )
    val trees = Trees(http, identity)
    ComposeViewport(document.body!!) { App(photos, trees, identity) }
}
