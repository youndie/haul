package io.github.youndie.haul.feature.about

import io.github.youndie.haul.feature.about.screen.AboutScreen
import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.screen.navigation
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.shell.Viewers
import io.github.youndie.kompot.ktor.respondKompotComponent
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject

/**
 * What the stand says about itself (B-160), in the public tier: the demo strip, which the storefront asks for once
 * its first page has drawn, and the About page, whose address (`/about`) the server serves the page at as it does
 * every storefront address (`StorefrontPage.About`, B-36).
 */
internal fun Route.aboutRouting() {
    val viewers by inject<Viewers>()
    val categories by inject<CatalogRepository>()

    get(DEMO_NOTE) { call.respondKompotComponent(haulWireJson, AboutScreen.note()) }

    get("/ui/about") {
        call.respondKompotComponent(
            haulWireJson,
            AboutScreen.page(viewers.of(call), navigation(categories.categories())),
        )
    }
}

/** Where the demo strip is: the client asks for it on its own, apart from any page (B-160). */
internal const val DEMO_NOTE = "/ui/demo"
