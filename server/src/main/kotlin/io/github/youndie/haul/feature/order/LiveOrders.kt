package io.github.youndie.haul.feature.order

import io.github.youndie.haul.feature.fulfilment.domain.OrderTracking
import io.github.youndie.haul.feature.identity.domain.Customers
import io.github.youndie.haul.feature.order.domain.OrderMoves
import io.github.youndie.haul.feature.order.domain.OrderRepository
import io.github.youndie.haul.feature.order.screen.OrderScreen
import io.github.youndie.haul.haulWireJson
import io.github.youndie.kompot.realtime.UpdateComponentMessage
import io.github.youndie.kompot.realtime.server.KompotUpdateBroadcaster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException

private val log = LoggerFactory.getLogger("io.github.youndie.haul.order.live")

/**
 * The order page's live updates (B-29, research consequence 3), through kompot's realtime: the page's tree
 * names its channel ([topic]), the page listens on it (`GET /ui/updates`, [OrderPaths.UPDATES]), and every
 * time the simulated world moves the order ([OrderMoves]) the order's [io.github.youndie.haul.ui.OrderBody]
 * is drawn again and handed to whoever listens as kompot's `UpdateComponentMessage` — the same body the page
 * would get by loading again, by the same [OrderScreen].
 *
 * **A channel is one customer's.** The topic on the wire names the order alone (`order:HL-48302`); the
 * [broadcaster] delivers by a key that names its customer as well, and a listener is subscribed under the
 * customer who asked. So a page only ever hears its own customer's order, even one that asked for somebody
 * else's number — which the route refuses before that anyway, `404 order_not_found` like the page itself.
 *
 * **What a listener is sent first is the order as it is now** ([frame]): a move between the page's load and
 * its listening, or while it was reconnecting, is not lost — the next connection starts where the order is.
 *
 * The broadcaster's bus is kompot's in-memory one: a move is heard by the pages connected to the process
 * that made it. One process runs the simulated world and serves the pages (haul-server); a second replica
 * would need a bus between them (kompot's Redis one) and could not skip the drawing by its own count as
 * [publish] does.
 */
internal class LiveOrders(
    private val moves: OrderMoves,
    private val orders: OrderRepository,
    private val tracking: OrderTracking,
    private val screen: OrderScreen,
    private val customers: Customers,
    private val broadcaster: KompotUpdateBroadcaster,
) {
    /**
     * Delivers from the first moment the application serves until its [scope] ends. Listening to [moves]
     * starts before this returns: a move made right after would otherwise go unheard.
     */
    fun start(scope: CoroutineScope): Job {
        broadcaster.start(scope)
        return scope.launch(start = CoroutineStart.UNDISPATCHED) {
            moves.orders.collect { publish(it) }
        }
    }

    /** Whether [orderId] is [customerId]'s: another customer's and a missing one are the same «no». */
    suspend fun owns(
        customerId: String,
        orderId: String,
    ): Boolean = orders.order(orderId)?.placed?.customerId == customerId

    /** [channel] hears every frame of [customerId]'s [orderId] from now on, until [unsubscribe]. */
    suspend fun subscribe(
        customerId: String,
        orderId: String,
        channel: Channel<String>,
    ) = broadcaster.subscribe(key(customerId, orderId), channel)

    suspend fun unsubscribe(
        customerId: String,
        orderId: String,
        channel: Channel<String>,
    ) = broadcaster.unsubscribe(key(customerId, orderId), channel)

    /**
     * [orderId]'s body as [customerId] is shown it now, greeting them by [firstName] as the page does, as an
     * update frame; `null` when it is not theirs.
     */
    suspend fun frame(
        customerId: String,
        firstName: String?,
        orderId: String,
    ): String? {
        val tracked = tracking.track(customerId, orderId) ?: return null
        val body = OrderScreen.body(screen.view(customerId, tracked, firstName))
        return haulWireJson.encodeToString(UpdateComponentMessage.serializer(), UpdateComponentMessage(body.id, body))
    }

    /**
     * Draws [orderId] again for its customer and hands it to their listeners. Nobody listening — the order
     * moved while nobody looks, which is nearly always — draws nothing: the bus is in memory, so the
     * listeners here are all there are. A failure is said and dropped; the page catches up on its next load
     * or connection, and the next move is tried as usual.
     */
    private suspend fun publish(orderId: String) {
        try {
            val customerId = orders.order(orderId)?.placed?.customerId ?: return
            val key = key(customerId, orderId)
            if (broadcaster.localSubscriberCount(key) == 0) return
            val firstName = customers.customer(customerId)?.firstName
            frame(customerId, firstName, orderId)?.let { broadcaster.broadcast(key, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("the live page of {} was not drawn again: {}", orderId, e.message, e)
        }
    }

    companion object {
        private const val SCOPE = "order"

        /** The channel the page of [orderId] names in its tree (kompot's `realtimeTopic`). */
        fun topic(orderId: String): String = "$SCOPE:$orderId"

        /** The order a [topic] names, or `null` for a topic that is not an order's. */
        fun orderOf(topic: String): String? =
            topic
                .removePrefix("$SCOPE:")
                .takeIf { topic.startsWith("$SCOPE:") && it.isNotEmpty() && ':' !in it }

        /** What the broadcaster delivers by: the order and whose it is. */
        private fun key(
            customerId: String,
            orderId: String,
        ): String = "$SCOPE:$customerId:$orderId"
    }
}
