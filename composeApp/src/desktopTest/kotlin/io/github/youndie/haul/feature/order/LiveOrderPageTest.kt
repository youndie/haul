package io.github.youndie.haul.feature.order

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.CANVAS_NOW
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.decode
import io.github.youndie.haul.read
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.OrderBody
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.realtime.KompotRealtimeSource
import io.github.youndie.kompot.realtime.UpdateComponentMessage
import io.github.youndie.kompot.standard.ColumnComponent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The order page moves by itself (B-29): the storefront listens on the channel the order's tree names while the
 * page is shown, and an update the server pushes redraws the order where it now is — without loading the page
 * again. The server is played by a fake transport answering the server's own recorded trees
 * (`resources/bodies/order_*.json`), the stream by a fake source.
 */
@OptIn(ExperimentalTestApi::class)
class LiveOrderPageTest {
    private val requests = CopyOnWriteArrayList<String>()
    private val transport =
        HaulTransport { path ->
            requests += path
            when (path) {
                "/ui$ORDER" -> HaulResponse(200, """{"screen":${read("order_placed.json")},"realtimeTopic":"$TOPIC"}""")
                "/ui$ACCOUNT" -> HaulResponse(200, read("account_orders.json"))
                else -> error("nothing answers $path")
            }
        }

    private val pushed = MutableSharedFlow<UpdateComponentMessage>(extraBufferCapacity = 8)
    private val topics = CopyOnWriteArrayList<String>()
    private val listening = AtomicInteger()
    private val realtime =
        KompotRealtimeSource { topic ->
            pushed
                .onStart {
                    topics += topic
                    listening.incrementAndGet()
                }.onCompletion { listening.decrementAndGet() }
        }

    private val history = FakeHistory(ACCOUNT).apply { push(ORDER) }
    private val clock =
        object : Clock {
            override fun now(): Instant = CANVAS_NOW
        }

    private fun ComposeUiTest.storefront() =
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                Storefront(transport, history, signIn = {}, clock = clock, realtime = realtime)
            }
        }

    private fun ComposeUiTest.shows(text: String): Boolean =
        onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    /**
     * The acceptance of B-29: an order placed a moment ago is shown placed; the server says it is in transit,
     * and the page says so, while the only request it ever made is the first.
     */
    @Test
    fun `a pushed update redraws the order page in transit without loading it again`() =
        runDesktopComposeUiTest(WIDTH, 1_400) {
            storefront()
            waitUntil(timeoutMillis = 5_000) { shows(PLACED_TITLE) && listening.get() == 1 }
            assertEquals(listOf(TOPIC), topics.toList(), "the page listens on the channel its tree named")

            val inTransit = orderBody(decode("order_in_transit.json"))
            pushed.tryEmit(UpdateComponentMessage(inTransit.id, inTransit))

            waitUntil(timeoutMillis = 5_000) { shows(IN_TRANSIT_TITLE) }
            assertEquals(false, shows(PLACED_TITLE), "the placed order is still drawn beside the update")
            assertEquals(listOf("/ui$ORDER"), requests.toList(), "the page was loaded again")
        }

    /**
     * Leaving the order stops listening — a page nobody looks at holds no stream — and a page with no channel
     * opens none.
     */
    @Test
    fun `the order page stops listening when it is left`() =
        runDesktopComposeUiTest(WIDTH, 1_400) {
            storefront()
            waitUntil(timeoutMillis = 5_000) { listening.get() == 1 }
            history.back()
            waitUntil(timeoutMillis = 5_000) { requests.size == 2 && listening.get() == 0 }
            assertEquals(listOf(TOPIC), topics.toList(), "the account page, which names no channel, listened")
        }

    private fun orderBody(tree: KompotComponent): OrderBody =
        (tree as ColumnComponent).children.filterIsInstance<OrderBody>().single()

    private companion object {
        const val WIDTH = 1440
        const val ACCOUNT = "/account"
        const val ORDER = "/account/orders/HL-48302"
        const val TOPIC = "order:HL-48302"
        const val PLACED_TITLE = "is placed"
        const val IN_TRANSIT_TITLE = "Arriving tomorrow"
    }
}
