package io.github.youndie.haul

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.github.youndie.haul.ui.coilPhotoLoader
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import kotlinx.browser.document
import kotlinx.browser.window

/** The bundle's entry point: the whole page is the storefront; photos load from the page's own origin. */
@OptIn(ExperimentalComposeUiApi::class)
public fun main() {
    val photos = coilPhotoLoader(HttpClient(Js), window.location.origin)
    ComposeViewport(document.body!!) { App(photos) }
}
