package io.github.youndie.haul.feature.account

import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.screen.navigation
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Viewers
import io.github.youndie.haul.ui.PageTitle
import io.github.youndie.kompot.ktor.respondKompotComponent
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject

/**
 * `GET /ui/account` (endpoint-account), in the customer tier: without a token it is `401
 * unauthenticated` (feature-identity, «Account needs a sign-in»). The tree is the frame and a title
 * naming who is signed in — where the header's account shortcut lands — until the Account screen
 * itself (overview, orders, Plus) is built by B-19.
 */
internal fun Route.accountRouting() {
    val viewers by inject<Viewers>()
    val catalog by inject<CatalogRepository>()

    get("/ui/account") {
        val viewer = viewers.of(call)
        val title = PageTitle("title", "Hi, ${viewer.firstName.orEmpty()}")
        call.respondKompotComponent(
            haulWireJson,
            Frame.page("account", viewer, navigation(catalog.categories()), listOf(title)),
        )
    }
}
