package io.github.youndie.haul.feature.saved

import io.github.youndie.haul.feature.account.screen.AccountPage
import io.github.youndie.haul.feature.account.screen.AccountScreen
import io.github.youndie.haul.feature.cart.CartPaths
import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.catalog.domain.CatalogError
import io.github.youndie.haul.feature.catalog.domain.PriceList
import io.github.youndie.haul.feature.identity.Caller
import io.github.youndie.haul.feature.identity.Callers
import io.github.youndie.haul.feature.identity.domain.IdentityError
import io.github.youndie.haul.feature.saved.domain.SavedCommands
import io.github.youndie.haul.feature.saved.screen.SavedFilter
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Viewers
import io.github.youndie.kompot.ktor.respondKompotAction
import io.github.youndie.kompot.ktor.respondKompotComponent
import io.github.youndie.kompot.standard.RefreshAction
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import org.koin.ktor.ext.inject

/**
 * The Saved list (endpoint-saved, screen-saved, B-20), in the customer tier: a request without a verified
 * token never reaches here, and a list is only ever its caller's. The page is `/ui/account/saved`, the
 * account's body, filtered by `filter` and paged by `page`; the heart is `PUT` and `DELETE` on a product
 * (`/api/v1/me/saved/{productId}`), «Save for later» a `POST` on a cart line. Every command answers
 * `refresh`.
 */
internal fun Route.savedRouting() {
    val screen by inject<AccountScreen>()
    val commands by inject<SavedCommands>()
    val callers by inject<Callers>()
    val viewers by inject<Viewers>()

    suspend fun ApplicationCall.caller(): Caller.Customer =
        callers.of(this) as? Caller.Customer ?: throw IdentityError.Unauthenticated()

    suspend fun ApplicationCall.refresh() = respondKompotAction(haulWireJson, RefreshAction)

    get(SavedPaths.SCREEN) {
        val caller = call.caller()
        val parameters = call.request.queryParameters
        val page =
            parameters[SavedPaths.PAGE]?.let {
                it.toIntOrNull()?.takeIf { p -> p >= 1 }
                    ?: throw CatalogError.Invalid(SavedPaths.PAGE, "Pages start at 1, not «$it»")
            } ?: 1
        val filter = SavedFilter.of(parameters[SavedPaths.FILTER])
        call.respondKompotComponent(
            haulWireJson,
            screen.build(caller.customer, AccountPage.Saved(filter, page), viewers.of(caller)),
        )
    }

    put(SavedPaths.ITEM) {
        val customer = call.caller().customer
        commands.save(customer.id, call.parameters["productId"]!!, PriceList.of(customer.plus))
        call.refresh()
    }

    delete(SavedPaths.ITEM) {
        commands.remove(call.caller().customer.id, call.parameters["productId"]!!)
        call.refresh()
    }

    post(CartPaths.SAVE_FOR_LATER) {
        val customer = call.caller().customer
        commands.saveForLater(CartOwner.Customer(customer.id, customer.plus), call.parameters["skuId"]!!)
        call.refresh()
    }
}

/** The Saved list's paths: the server's strings (CLAUDE.md), handed to the client inside the trees. */
internal object SavedPaths {
    const val SCREEN = "/ui" + Frame.SAVED
    const val ITEMS = "/api/v1/me/saved"
    const val ITEM = "$ITEMS/{productId}"

    /** The list's filter and page in the query string: `/account/saved?filter=price-dropped&page=2`. */
    const val FILTER = "filter"
    const val PAGE = "page"

    /** Where the heart of [productId] is sent. */
    fun item(productId: String): String = "$ITEMS/$productId"

    /** The list under [filter] at [page]: the bare address for all of them on the first. */
    fun page(
        filter: SavedFilter,
        page: Int = 1,
    ): String {
        val query =
            listOfNotNull(
                filter.parameter?.let { "$FILTER=$it" },
                "$PAGE=$page".takeIf { page > 1 },
            )
        return Frame.SAVED + (if (query.isEmpty()) "" else query.joinToString("&", prefix = "?"))
    }
}
