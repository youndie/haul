package io.github.youndie.haul.feature.live

import io.github.youndie.haul.feature.live.domain.FeedOrder
import io.github.youndie.haul.feature.live.domain.FeedRepository
import io.github.youndie.haul.feature.live.screen.LiveScreen
import io.github.youndie.haul.feature.order.domain.OrderMoves
import io.github.youndie.kompot.realtime.server.KompotUpdateBroadcaster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * B-164: the live channel of «Live orders» is public, so what it costs is the stand's moves, not its audience: one
 * drawing for every listener, at most one per pause however fast the orders move, none with nobody listening, and a
 * bounded number of listeners. Against the channel itself, over a feed that counts how often it is read.
 */
class LiveFeedTest {
    private val reads = AtomicInteger()
    private val feed =
        object : FeedRepository {
            override suspend fun latest(limit: Int): List<FeedOrder> {
                reads.incrementAndGet()
                return LiveFixturesTest.SAMPLE.take(limit)
            }
        }

    /**
     * Fifty listeners and twenty moves a twentieth of a second apart — a pass of the simulator moving a shipment after
     * another — are about one second of moves: with a pause of half a second the feed is read three times or fewer,
     * and every listener hears it. Drawn for each move it would be read twenty times; for each listener, a thousand.
     */
    @Test
    fun `moves are drawn once for every listener and at most once a pause`() =
        channel { live, moves ->
            val listeners = List(50) { Channel<String>(Channel.UNLIMITED).also { live.subscribe(it) } }
            repeat(20) {
                moves.moved("HL-$it")
                delay(50.milliseconds)
            }
            delay(700.milliseconds)
            listeners.forEach { withTimeout(5.seconds) { it.receive() } }
            assertTrue(reads.get() in 1..3, "the feed was read ${reads.get()} times for 20 moves")
        }

    /** With nobody listening a move draws nothing: the page is not read for nobody. */
    @Test
    fun `nobody listening draws nothing`() =
        channel { _, moves ->
            repeat(5) { moves.moved("HL-$it") }
            delay(600.milliseconds)
            assertEquals(0, reads.get())
        }

    /**
     * A listener that connects is sent the last drawing while no order has moved since — a burst of visitors opening
     * the page costs one read — and a drawing anew once one has.
     */
    @Test
    fun `a listener that connects while nothing moved is sent the last drawing`() =
        channel { live, moves ->
            val first = live.frame()
            assertEquals(first, live.frame())
            assertEquals(1, reads.get())
            moves.moved("HL-1")
            delay(100.milliseconds)
            live.frame()
            assertEquals(2, reads.get(), "a move since the last drawing is drawn again")
        }

    /** Past its capacity a listener is refused; a place frees when one leaves. */
    @Test
    fun `past its capacity a listener is refused`() {
        val live =
            LiveFeed(OrderMoves(), feed, LiveScreen { LiveFixturesTest.ZONE }, KompotUpdateBroadcaster(), capacity = 2)
        assertTrue(live.admit())
        assertTrue(live.admit())
        assertFalse(live.admit(), "a third listener was admitted past a capacity of two")
        live.leave()
        assertTrue(live.admit(), "the place a listener left was not freed")
    }

    private fun channel(block: suspend (LiveFeed, OrderMoves) -> Unit) =
        runBlocking {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            try {
                val moves = OrderMoves()
                val broadcaster = KompotUpdateBroadcaster().also { it.start(scope) }
                val live =
                    LiveFeed(moves, feed, LiveScreen { LiveFixturesTest.ZONE }, broadcaster, pause = 500.milliseconds)
                live.start(scope)
                block(live, moves)
            } finally {
                scope.cancel()
            }
        }
}
