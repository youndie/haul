package io.github.youndie.haul.feature.order

import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.identity.Caller
import io.github.youndie.haul.feature.identity.Callers
import io.github.youndie.haul.feature.identity.domain.IdentityError
import io.github.youndie.haul.feature.order.domain.OrderError
import io.github.youndie.haul.feature.order.domain.Reorder
import io.github.youndie.haul.feature.order.screen.OrderScreen
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Viewers
import io.github.youndie.kompot.ktor.respondKompotAction
import io.github.youndie.kompot.ktor.respondKompotComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.koin.ktor.ext.inject

/**
 * The order's page and its reorder (endpoint-orders), in the customer tier: a request without a verified
 * token never reaches here (`401 unauthenticated`). Another customer's order is `404 order_not_found`, the
 * same as one that does not exist ([OrderError.NotFound]).
 *
 * Reorder answers kompot's `navigate` to the cart, with the order's SKUs in it ([Reorder]); a SKU gone or
 * out of stock is left out, and the cart is drawn with the rest.
 */
internal fun Route.orderRouting() {
    val screen by inject<OrderScreen>()
    val reorder by inject<Reorder>()
    val callers by inject<Callers>()
    val viewers by inject<Viewers>()

    suspend fun ApplicationCall.caller(): Caller.Customer =
        callers.of(this) as? Caller.Customer ?: throw IdentityError.Unauthenticated()

    get(OrderPaths.SCREEN) {
        val caller = call.caller()
        val orderId = call.parameters["id"]!!
        val tree = screen.build(caller.customer.id, orderId, viewers.of(caller)) ?: throw OrderError.NotFound(orderId)
        call.respondKompotComponent(haulWireJson, tree)
    }

    post(OrderPaths.REORDER) {
        val customer = call.caller().customer
        reorder.reorder(CartOwner.Customer(customer.id, customer.plus), call.parameters["id"]!!)
        call.respondKompotAction(haulWireJson, NavigateAction(Frame.CART))
    }
}

/**
 * The order's paths: the server's strings (CLAUDE.md). [page] is the storefront's address of an order —
 * where placement lands (endpoint-checkout) — and its tree is the same address under `/ui` ([SCREEN]).
 */
internal object OrderPaths {
    const val SCREEN = "/ui/account/orders/{id}"
    const val REORDER = "/api/v1/me/orders/{id}/reorder"

    /** `/account/orders/HL-48302`: under the account, as the page's crumbs say (screen-order). */
    fun page(orderId: String): String = "/account/orders/$orderId"

    fun reorder(orderId: String): String = "/api/v1/me/orders/$orderId/reorder"
}
