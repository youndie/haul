package io.github.youndie.haul.feature.search

import io.github.youndie.haul.feature.catalog.domain.CatalogError
import io.github.youndie.haul.feature.catalog.domain.Sort
import io.github.youndie.haul.feature.identity.Caller
import io.github.youndie.haul.feature.identity.Callers
import io.github.youndie.haul.feature.identity.domain.IdentityError
import io.github.youndie.haul.feature.search.domain.RecentSearches
import io.github.youndie.haul.feature.search.screen.SearchRequest
import io.github.youndie.haul.feature.search.screen.SearchScreen
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.shell.Parts
import io.github.youndie.haul.shell.Viewers
import io.github.youndie.haul.shell.respondParts
import io.github.youndie.kompot.ktor.respondKompotAction
import io.github.youndie.kompot.ktor.respondKompotComponent
import io.github.youndie.kompot.standard.RefreshAction
import io.ktor.http.Parameters
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject

/**
 * The search screens (endpoint-search), in the public tier: a guest searches as a customer does,
 * and a signed-in customer's searches are recorded. Refusals are `SearchError` (`query_too_short`)
 * and `CatalogError` (a sort, page or category the page does not have). Clearing recent searches is
 * the customer tier's ([customerSearchRouting]).
 */
internal fun Route.searchRouting() {
    val screen by inject<SearchScreen>()
    val viewers by inject<Viewers>()

    get("/ui/search") {
        call.respondKompotComponent(
            haulWireJson,
            screen.results(searchRequest(call.request.queryParameters), viewers.of(call)),
        )
    }

    // A category chip or a page of the results loaded in place (B-63, kind `load`), the search not recorded
    // again. A query that now finds nothing, or a category no longer there, cannot be partial.
    get("${Parts.PREFIX}/search") {
        val page =
            try {
                screen.results(searchRequest(call.request.queryParameters), viewers.of(call), recorded = false)
            } catch (_: CatalogError.CategoryNotFound) {
                null
            }
        call.respondParts(page, SearchScreen.PARTS)
    }

    get("/ui/search/suggest") {
        call.respondKompotComponent(haulWireJson, screen.suggest(call.request.queryParameters["q"], viewers.of(call)))
    }
}

/**
 * «Clear» on recent searches (endpoint-search), in the customer tier: the signed-in customer's list is
 * emptied and the answer is `refresh`, which draws the suggest panel again without them. Without a
 * token it is `401 unauthenticated`, from the tier.
 */
internal fun Route.customerSearchRouting() {
    val callers by inject<Callers>()
    val recent by inject<RecentSearches>()

    delete(SearchScreen.RECENT_SEARCHES) {
        val customer = (callers.of(call) as? Caller.Customer)?.customer ?: throw IdentityError.Unauthenticated()
        recent.clear(customer.id)
        call.respondKompotAction(haulWireJson, RefreshAction)
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
