package io.github.youndie.haul.feature.live

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
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
import io.github.youndie.haul.ui.LiveOrdersBody
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
 * «Live orders» in the storefront (B-164): the page at `/live` listens on the channel its tree names, a row opens in
 * place to its trail, and a body the server pushes redraws the rows without loading the page again — the row the
 * visitor opened staying open. The server is played by a fake transport answering the server's recorded body
 * (`resources/bodies/live_orders.json`), the stream by a fake source.
 */
@OptIn(ExperimentalTestApi::class)
class LiveOrdersPageTest {
    private val requests = CopyOnWriteArrayList<String>()
    private val transport =
        HaulTransport { path ->
            requests += path
            when (path) {
                "/ui$LIVE" -> HaulResponse(200, """{"screen":${read("live_orders.json")},"realtimeTopic":"$TOPIC"}""")
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

    private val history = FakeHistory(LIVE)
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

    private fun ComposeUiTest.has(tag: String): Boolean = onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    /**
     * The rows are drawn closed, the declined order's failed step heard as what it is; pressing «Show the trail» opens
     * the row to the saga's refusal and the compensations after it, and pressing again closes it.
     */
    @Test
    fun `a row opens in place to its trail and closes again`() =
        runDesktopComposeUiTest(WIDTH, 2_400) {
            storefront()
            waitUntil(timeoutMillis = 5_000) { has(liveOrderTag(DECLINED)) }
            assertEquals(listOf(TOPIC), topics.toList(), "the page listens on the channel its tree named")
            assertEquals(
                1,
                onAllNodes(hasContentDescription("Authorise the card, refused: card declined"))
                    .fetchSemanticsNodes()
                    .size,
            )
            assertEquals(false, has(liveOrderTrailTag(DECLINED)), "a row is drawn open before it is pressed")

            onNode(hasTestTag(liveOrderToggleTag(DECLINED))).performClick()
            waitUntil(timeoutMillis = 5_000) { has(liveOrderTrailTag(DECLINED)) }
            listOf("The card was declined", "Undone: the order cancelled", "Undone: the stock released")
                .forEach { assertEquals(true, shows(it), "the trail does not say «$it»") }
            assertEquals(false, has(liveOrderTrailTag(PLACED)), "another row opened with it")

            onNode(hasTestTag(liveOrderToggleTag(DECLINED))).performClick()
            waitUntil(timeoutMillis = 5_000) { !has(liveOrderTrailTag(DECLINED)) }
        }

    /**
     * The acceptance of B-164 in the client: the server pushes the body again with the placed order's sellers packing,
     * and the page draws it — the order the visitor opened still open — while the only request it made is the first.
     */
    @Test
    fun `a pushed body redraws the rows and keeps the opened one open`() =
        runDesktopComposeUiTest(WIDTH, 2_400) {
            storefront()
            waitUntil(timeoutMillis = 5_000) { has(liveOrderTag(DECLINED)) && listening.get() == 1 }
            onNode(hasTestTag(liveOrderToggleTag(DECLINED))).performClick()
            waitUntil(timeoutMillis = 5_000) { has(liveOrderTrailTag(DECLINED)) }
            assertEquals(false, shows(PACKED_STATUS))

            val body =
                (
                    decode(
                        "live_orders.json",
                    ) as ColumnComponent
                ).children.filterIsInstance<LiveOrdersBody>().single()
            val moved =
                body.copy(
                    orders =
                        body.orders.map { order ->
                            if (order.id == PLACED) order.copy(status = PACKED_STATUS) else order
                        },
                )
            pushed.tryEmit(UpdateComponentMessage(moved.id, moved))

            waitUntil(timeoutMillis = 5_000) { shows(PACKED_STATUS) }
            assertEquals(true, has(liveOrderTrailTag(DECLINED)), "the opened row closed when the body arrived again")
            assertEquals(listOf("/ui$LIVE"), requests.toList(), "the page was loaded again")
        }

    private companion object {
        const val WIDTH = 1440
        const val LIVE = "/live"
        const val TOPIC = "live:orders"
        const val DECLINED = "HL-48310"
        const val PLACED = "HL-48311"
        const val PACKED_STATUS = "Packed by both sellers"
    }
}
