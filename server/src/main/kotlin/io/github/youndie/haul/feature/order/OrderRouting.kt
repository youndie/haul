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
import io.github.youndie.kompot.realtime.KompotScreenResponse
import io.github.youndie.kompot.standard.NavigateAction
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sse.SSEServerContent
import io.ktor.server.sse.heartbeat
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import org.koin.ktor.ext.inject
import kotlin.time.Duration.Companion.seconds

/**
 * The order's page and its reorder (endpoint-orders), in the customer tier: a request without a verified
 * token never reaches here (`401 unauthenticated`). Another customer's order is `404 order_not_found`, the
 * same as one that does not exist ([OrderError.NotFound]).
 *
 * Reorder answers kompot's `navigate` to the cart, with the order's SKUs in it ([Reorder]); a SKU gone or
 * out of stock is left out, and the cart is drawn with the rest.
 *
 * The page is live (B-29): its tree comes in kompot's `KompotScreenResponse`, naming the order's channel, and
 * [OrderPaths.UPDATES] streams that channel's frames ([LiveOrders]) — in the same tier and refused the same way:
 * a topic that is not an order's, or an order that is not the caller's, is `404 order_not_found` before the
 * stream opens. The stream's first frame is the order as it is then; every move after is one more; a `ping`
 * every [HEARTBEAT] keeps an idle stream through a proxy (kompot's SPEC §16.6).
 */
internal fun Route.orderRouting() {
    val screen by inject<OrderScreen>()
    val reorder by inject<Reorder>()
    val callers by inject<Callers>()
    val viewers by inject<Viewers>()
    val live by inject<LiveOrders>()

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

    get(OrderPaths.UPDATES) {
        val customer = call.caller().customer
        val topic = call.request.queryParameters["topic"].orEmpty()
        val orderId =
            LiveOrders.orderOf(topic)?.takeIf { live.owns(customer.id, it) } ?: throw OrderError.NotFound(topic)
        call.response.header(HttpHeaders.CacheControl, "no-store")
        // A proxy that buffers would hold every frame back until the stream ends.
        call.response.header("X-Accel-Buffering", "no")
        call.respond(
            SSEServerContent(call) {
                heartbeat {
                    period = HEARTBEAT
                    event = ServerSentEvent(event = "ping")
                }
                // Conflated: every frame is the whole body, so a page that fell behind needs only the last.
                val frames = Channel<String>(Channel.CONFLATED)
                live.subscribe(customer.id, orderId, frames)
                try {
                    // Listening before drawing the first: a move in between is heard rather than lost.
                    live.frame(customer.id, customer.firstName, orderId)?.let { send(ServerSentEvent(data = it)) }
                    for (frame in frames) send(ServerSentEvent(data = frame))
                } finally {
                    withContext(NonCancellable) { live.unsubscribe(customer.id, orderId, frames) }
                }
            },
        )
    }

    post(OrderPaths.REORDER) {
        val customer = call.caller().customer
        reorder.reorder(CartOwner.Customer(customer.id, customer.plus), call.parameters["id"]!!)
        call.respondKompotAction(haulWireJson, NavigateAction(Frame.CART))
    }
}

/** How often an idle stream says it is alive: kompot's reference interval (SPEC §16.6). */
private val HEARTBEAT = 20.seconds

/**
 * The order's paths: the server's strings (CLAUDE.md). [page] is the storefront's address of an order —
 * where placement lands (endpoint-checkout) — and its tree is the same address under `/ui` ([SCREEN]).
 */
internal object OrderPaths {
    const val SCREEN = "/ui/account/orders/{id}"
    const val REORDER = "/api/v1/me/orders/{id}/reorder"

    /** The live page's stream (B-29): `?topic=` is the channel its tree names, as kompot's SPEC §16.6 has it. */
    const val UPDATES = "/ui/updates"

    /** `/account/orders/HL-48302`: under the account, as the page's crumbs say (screen-order). */
    fun page(orderId: String): String = "/account/orders/$orderId"

    fun reorder(orderId: String): String = "/api/v1/me/orders/$orderId/reorder"
}
