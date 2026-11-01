package io.github.youndie.haul.feature.live

import io.github.youndie.haul.feature.live.domain.FeedRepository
import io.github.youndie.haul.feature.live.screen.LiveScreen
import io.github.youndie.haul.feature.order.domain.OrderMoves
import io.github.youndie.haul.haulWireJson
import io.github.youndie.kompot.realtime.UpdateComponentMessage
import io.github.youndie.kompot.realtime.server.KompotUpdateBroadcaster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val log = LoggerFactory.getLogger("io.github.youndie.haul.live")

/**
 * The live channel of «Live orders» (B-164): the order page's transport (B-29) — kompot's realtime over the same
 * broadcaster and the same `GET /ui/updates` — with one channel, [TOPIC], that everybody hears. Every move of an order
 * the saga or the simulated world tells ([OrderMoves]) draws the feed's body again and hands it to every listener as
 * kompot's `UpdateComponentMessage`.
 *
 * **It is public, so what it costs is bounded by the stand, not by the audience:**
 * - **one drawing per move, whoever listens**, sent to all of them; none when nobody listens;
 * - **at most one drawing per [pause]**: the moves that come while one is drawn and the pause after it are drawn
 *   together, once — the shoppers' passes move many shipments in one go, and a visitor's eye needs no more;
 * - **a listener that connects is sent the last drawing** while no order has moved since, not a drawing of its own;
 * - **at most [capacity] listeners at once** ([admit]): past it the stream is refused `503` and the page keeps what it
 *   drew, its client asking again later.
 *
 * **On every replica** (B-174): the moves include the other replicas' ([OrderMoves.heard], through the replicas' bus),
 * so each replica draws for its own listeners, at most once per [pause], and a drawing it kept is dropped by a move
 * made anywhere. [capacity] is per replica: two replicas admit twice as many.
 */
internal class LiveFeed(
    private val moves: OrderMoves,
    private val feed: FeedRepository,
    private val screen: LiveScreen,
    private val broadcaster: KompotUpdateBroadcaster,
    private val pause: Duration = PAUSE,
    private val capacity: Int = CAPACITY,
) {
    private val listening = AtomicInteger()

    /** How many moves were heard: a drawing begun before the last of them is not kept as the feed as it is. */
    private val heard = AtomicLong()

    /** The last body drawn, as a frame, with the moves heard before it; `null` once an order moved since. */
    @Volatile
    private var drawn: Pair<Long, String>? = null

    /**
     * Draws for the listeners from now until [scope] ends. The broadcaster is the order page's, started by
     * [io.github.youndie.haul.feature.order.LiveOrders.start]: started twice it would deliver every frame twice.
     */
    fun start(scope: CoroutineScope): Job {
        val moved = Channel<Unit>(Channel.CONFLATED)
        return scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Listening to the moves before this returns: a move made right after would otherwise go unheard.
            launch(start = CoroutineStart.UNDISPATCHED) {
                moves.orders.collect {
                    heard.incrementAndGet()
                    moved.trySend(Unit)
                }
            }
            for (move in moved) {
                publish()
                delay(pause)
            }
        }
    }

    /** One more listener, unless [capacity] already listen; a listener admitted [leave]s when its stream ends. */
    fun admit(): Boolean {
        if (listening.incrementAndGet() <= capacity) return true
        listening.decrementAndGet()
        return false
    }

    fun leave() {
        listening.decrementAndGet()
    }

    /** [channel] hears every drawing from now on, until [unsubscribe]. */
    suspend fun subscribe(channel: Channel<String>) = broadcaster.subscribe(TOPIC, channel)

    suspend fun unsubscribe(channel: Channel<String>) = broadcaster.unsubscribe(TOPIC, channel)

    /** The feed as it is now, as an update frame: the last drawing while nothing has moved since. */
    suspend fun frame(): String = drawn?.takeIf { it.first == heard.get() }?.second ?: draw()

    private suspend fun draw(): String {
        val before = heard.get()
        val body = screen.body(feed.latest(LiveScreen.LIMIT))
        return haulWireJson
            .encodeToString(UpdateComponentMessage.serializer(), UpdateComponentMessage(body.id, body))
            .also { drawn = before to it }
    }

    /**
     * Draws the feed again and hands it to whoever listens; nobody listening draws nothing. A failure is said and
     * dropped: the listeners keep what they have until the next move, and a stream that connects draws anew.
     */
    private suspend fun publish() {
        try {
            if (broadcaster.localSubscriberCount(TOPIC) == 0) return
            broadcaster.broadcast(TOPIC, draw())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("the live orders were not drawn again: {}", e.message, e)
        }
    }

    companion object {
        /** The channel the page names in its tree (kompot's `realtimeTopic`), the same for everybody. */
        const val TOPIC = "live:orders"

        /** The least time between two drawings. */
        val PAUSE: Duration = 1.seconds

        /** The most listeners at once. */
        const val CAPACITY = 200
    }
}
