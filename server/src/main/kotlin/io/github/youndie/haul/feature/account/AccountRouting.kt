package io.github.youndie.haul.feature.account

import io.github.youndie.haul.feature.account.screen.AccountPage
import io.github.youndie.haul.feature.account.screen.AccountScreen
import io.github.youndie.haul.feature.account.screen.HistoryFilterKey
import io.github.youndie.haul.feature.identity.Caller
import io.github.youndie.haul.feature.identity.Callers
import io.github.youndie.haul.feature.identity.domain.IdentityError
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Viewers
import io.github.youndie.kompot.ktor.respondKompotComponent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject

/**
 * The account (endpoint-account, screen-account), in the customer tier: a request without a verified
 * token never reaches here (`401 unauthenticated`, feature-identity's «Account needs a sign-in»). The
 * overview is `/ui/account`; the orders' history `/ui/account/orders`, filtered by its `status`
 * parameter ([HistoryFilterKey]) — one the history does not have is all of them. Both read only the
 * caller's own orders.
 */
internal fun Route.accountRouting() {
    val screen by inject<AccountScreen>()
    val callers by inject<Callers>()
    val viewers by inject<Viewers>()

    suspend fun ApplicationCall.caller(): Caller.Customer =
        callers.of(this) as? Caller.Customer ?: throw IdentityError.Unauthenticated()

    get(AccountPaths.SCREEN) {
        val caller = call.caller()
        call.respondKompotComponent(
            haulWireJson,
            screen.build(caller.customer, AccountPage.Overview, viewers.of(caller)),
        )
    }

    get(AccountPaths.ORDERS) {
        val caller = call.caller()
        val filter = HistoryFilterKey.of(call.request.queryParameters[AccountPaths.STATUS])
        call.respondKompotComponent(
            haulWireJson,
            screen.build(caller.customer, AccountPage.Orders(filter), viewers.of(caller)),
        )
    }
}

/** The account's paths: the server's strings (CLAUDE.md), each tree at the page's address under `/ui`. */
internal object AccountPaths {
    const val SCREEN = "/ui" + Frame.ACCOUNT
    const val ORDERS = "/ui" + Frame.ORDERS

    /** The history's filter in the query string: `/account/orders?status=active`. */
    const val STATUS = "status"

    /** The history under [filter]: the bare address for all of them. */
    fun orders(filter: HistoryFilterKey): String = Frame.ORDERS + (filter.parameter?.let { "?$STATUS=$it" }.orEmpty())
}
