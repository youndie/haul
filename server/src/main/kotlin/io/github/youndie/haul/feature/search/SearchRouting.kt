package io.github.youndie.haul.feature.search

import io.github.youndie.haul.feature.catalog.domain.CatalogError
import io.github.youndie.haul.feature.catalog.domain.Sort
import io.github.youndie.haul.feature.search.screen.SearchRequest
import io.github.youndie.haul.feature.search.screen.SearchScreen
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.kompot.ktor.respondKompotComponent
import io.ktor.http.Parameters
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject

/**
 * The search screens (endpoint-search), in the public tier: a guest searches as a customer does.
 * Refusals are `SearchError` (`query_too_short`) and `CatalogError` (a sort, page or category the
 * page does not have). `DELETE /api/v1/me/recent-searches` is the customer tier's, and arrives with
 * sign-in (B-12).
 */
internal fun Route.searchRouting() {
    val screen by inject<SearchScreen>()

    get("/ui/search") {
        call.respondKompotComponent(haulWireJson, screen.results(searchRequest(call.request.queryParameters), Viewer()))
    }

    get("/ui/search/suggest") {
        call.respondKompotComponent(haulWireJson, screen.suggest(call.request.queryParameters["q"], Viewer()))
    }
}

/** The search page's query; the query text itself is checked by the screen, the rest here. */
internal fun searchRequest(query: Parameters): SearchRequest {
    val sort = query["sort"]?.let { Sort.of(it) ?: throw CatalogError.Invalid("sort", "No sort «$it»") } ?: Sort.Popular
    val page =
        query["page"]?.let {
            it.toIntOrNull()?.takeIf { p -> p >= 1 }
                ?: throw CatalogError.Invalid("page", "Pages start at 1, not «$it»")
        } ?: 1
    return SearchRequest(query["q"], query["category"], sort, page)
}
