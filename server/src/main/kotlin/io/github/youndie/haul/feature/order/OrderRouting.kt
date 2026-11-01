package io.github.youndie.haul.feature.order

import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.identity.Caller
import io.github.youndie.haul.feature.identity.Callers
import io.github.youndie.haul.feature.identity.domain.IdentityError
import io.github.youndie.haul.feature.order.domain.OrderError
import io.github.youndie.haul.feature.order.domain.Reorder
import io.github.youndie.haul.feature.order.screen.OrderScreen
import io.github.youndie.haul.feature.order.screen.ReorderAnswer
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.shell.Viewers
import io.github.youndie.haul.shell.respondUpdates
import io.github.youndie.kompot.ktor.respondKompotAction
import io.github.youndie.kompot.realtime.KompotScreenResponse
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
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
 * out of stock is left out, and the cart is drawn with the rest under a message naming it — or, when nothing
 * went in, the message alone over the order ([ReorderAnswer], B-101).
 *
 * The page is live (B-29): its tree comes in kompot's `KompotScreenResponse`, naming the order's channel, and
 * `GET /ui/updates` streams that channel's frames ([respondOrderUpdates]). That route is mounted in the public tier
 * since B-164, because «Live orders» streams there too; an order's topic is refused there as here, `401` without a
 * sign-in.
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
        call.respondText(
            haulWireJson.encodeToString(
                KompotScreenResponse.serializer(),
                KompotScreenResponse(tree, realtimeTopic = LiveOrders.topic(orderId)),
            ),
            ContentType.Application.Json,
        )
    }

    post(OrderPaths.REORDER) {
        val customer = call.caller().customer
        val reordered = reorder.reorder(CartOwner.Customer(customer.id, customer.plus), call.parameters["id"]!!)
        call.respondKompotAction(haulWireJson, ReorderAnswer.of(reordered))
    }
}

/**
 * The order page's stream (B-29), which `GET /ui/updates` hands an order's topic to: the customer's, refused the same
 * way as the page — no sign-in is `401`, a topic that is not an order's or an order that is not the caller's is
 * `404 order_not_found` before the stream opens. The stream's first frame is the order as it is then; every move after
 * is one more.
 */
internal suspend fun ApplicationCall.respondOrderUpdates(
    topic: String,
    callers: Callers,
    live: LiveOrders,
) {
    val customer = (callers.of(this) as? Caller.Customer ?: throw IdentityError.Unauthenticated()).customer
    val orderId = LiveOrders.orderOf(topic)?.takeIf { live.owns(customer.id, it) } ?: throw OrderError.NotFound(topic)
    respondUpdates(
        subscribe = { live.subscribe(customer.id, orderId, it) },
        unsubscribe = { live.unsubscribe(customer.id, orderId, it) },
        first = { live.frame(customer.id, customer.firstName, orderId) },
    )
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
