package io.github.youndie.haul.feature.account

import io.github.youndie.haul.feature.account.screen.AccountPage
import io.github.youndie.haul.feature.account.screen.AccountScreen
import io.github.youndie.haul.feature.account.screen.HistoryFilterKey
import io.github.youndie.haul.feature.catalog.domain.CatalogError
import io.github.youndie.haul.feature.identity.Caller
import io.github.youndie.haul.feature.identity.Callers
import io.github.youndie.haul.feature.identity.domain.IdentityError
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Parts
import io.github.youndie.haul.shell.Viewers
import io.github.youndie.haul.shell.respondParts
import io.github.youndie.kompot.ktor.respondKompotComponent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject

/**
 * The account (endpoint-account, screen-account), in the customer tier: a request without a verified
 * token never reaches here (`401 unauthenticated`, feature-identity's «Account needs a sign-in»). The
 * overview is `/ui/account`; the orders' history `/ui/account/orders`, filtered by its `status`
 * parameter ([HistoryFilterKey]) — one the history does not have is all of them — at its `page` (B-159). Both
 * read only the caller's own orders.
 */
internal fun Route.accountRouting() {
    val screen by inject<AccountScreen>()
    val callers by inject<Callers>()
    val viewers by inject<Viewers>()

    suspend fun ApplicationCall.caller(): Caller.Customer =
        callers.of(this) as? Caller.Customer ?: throw IdentityError.Unauthenticated()

    /** The history the query string names: its filter at its page, as the Saved list reads its own. */
    fun ApplicationCall.history(): AccountPage.Orders {
        val parameters = request.queryParameters
        val page =
            parameters[AccountPaths.PAGE]?.let {
                it.toIntOrNull()?.takeIf { p -> p >= 1 }
                    ?: throw CatalogError.Invalid(AccountPaths.PAGE, "Pages start at 1, not «$it»")
            } ?: 1
        return AccountPage.Orders(HistoryFilterKey.of(parameters[AccountPaths.STATUS]), page)
    }

    get(AccountPaths.SCREEN) {
        val caller = call.caller()
        call.respondKompotComponent(
            haulWireJson,
            screen.build(caller.customer, AccountPage.Overview, viewers.of(caller)),
        )
    }

    get(AccountPaths.ORDERS) {
        val caller = call.caller()
        call.respondKompotComponent(
            haulWireJson,
            screen.build(caller.customer, call.history(), viewers.of(caller)),
        )
    }

    // A chip of the history loaded in place (B-63, kind `load`): the account's body, under the same header.
    get(Parts.PREFIX + Frame.ORDERS) {
        val caller = call.caller()
        call.respondParts(
            screen.build(caller.customer, call.history(), viewers.of(caller)),
            AccountScreen.PARTS,
        )
    }
}

/** The account's paths: the server's strings (CLAUDE.md), each tree at the page's address under `/ui`. */
internal object AccountPaths {
    const val SCREEN = "/ui" + Frame.ACCOUNT
    const val ORDERS = "/ui" + Frame.ORDERS

    /** The history's filter and page in the query string: `/account/orders?status=active&page=2`. */
    const val STATUS = "status"
    const val PAGE = "page"

    /** The history under [filter] at [page]: the bare address for all of them on the first. */
    fun orders(
        filter: HistoryFilterKey,
        page: Int = 1,
    ): String {
        val query =
            listOfNotNull(
                filter.parameter?.let { "$STATUS=$it" },
                "$PAGE=$page".takeIf { page > 1 },
            )
        return Frame.ORDERS + (if (query.isEmpty()) "" else query.joinToString("&", prefix = "?"))
    }
}
