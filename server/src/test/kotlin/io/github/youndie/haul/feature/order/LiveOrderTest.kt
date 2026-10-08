package io.github.youndie.haul.feature.order

import io.github.youndie.haul.ErrorBody
import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.checkout.PlaceOrderRequest
import io.github.youndie.haul.feature.checkout.domain.CheckoutCommands
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentSimulator
import io.github.youndie.haul.feature.order.domain.Placement
import io.github.youndie.haul.haulModule
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.FulfilmentWorld
import io.github.youndie.haul.testing.MAYA
import io.github.youndie.haul.testing.ShildikHarness
import io.github.youndie.haul.testing.TestClock
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.ui.OrderBody
import io.github.youndie.kompot.realtime.UpdateComponentMessage
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.koin.core.Koin
import org.koin.ktor.ext.getKoin
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * The order page's live updates (B-29, feature-orders): a move the simulated world makes reaches the page of
 * that order's customer, and nobody else's, without the page asking again.
 */
class LiveOrderTest {
    private val maya by lazy {
        ShildikHarness.accessToken(
            ShildikHarness.person("Maya Kowalski", SampleCustomers.MAYA),
        )
    }
    private val sam by lazy { ShildikHarness.accessToken(ShildikHarness.person("Sam Ortiz", SampleCustomers.SAM)) }

    /**
     * A move is delivered by the order and by whose it is. Delivering by the order alone would hand Maya's
     * order to anybody subscribed under its number — the channel is the only thing between two customers'
     * updates (kompot's SPEC §10.4) — and a move that reached every listener would hand them every order.
     * Sam subscribed to Maya's number under his own name and Maya's other order hear nothing; Maya's page
     * hears the order in transit, which is also the control that the move was published at all.
     */
    @Test
    fun `a move reaches the subscribers of that order and of no other`() =
        seededFreshDatabase().use { database ->
            FulfilmentWorld(database).use { world ->
                val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                try {
                    val live = world.koin.get<LiveOrders>()
                    live.start(scope)
                    val orderId = world.place(CheckoutChoice(slotId = "2025-10-08T15"))
                    val mayas = Channel<String>(Channel.UNLIMITED)
                    val samsUnderHerNumber = Channel<String>(Channel.UNLIMITED)
                    val herOtherOrder = Channel<String>(Channel.UNLIMITED)
                    runBlocking {
                        live.subscribe(SampleCustomers.MAYA, orderId, mayas)
                        live.subscribe(SampleCustomers.SAM, orderId, samsUnderHerNumber)
                        live.subscribe(SampleCustomers.MAYA, "HL-00001", herOtherOrder)
                    }

                    world.advance(Duration.ZERO)
                    world.advance(48.hours)

                    val heard = runBlocking { withTimeout(10.seconds) { mayas.receive() }.body() }
                    assertEquals(2, heard.steps?.current, "the order is not in transit: ${heard.steps}")
                    assertEquals("#$orderId", heard.crumbs.last().label)
                    assertTrue(samsUnderHerNumber.tryReceive().isFailure, "Sam heard Maya's order")
                    assertTrue(herOtherOrder.tryReceive().isFailure, "another order of hers heard this one's move")
                } finally {
                    scope.cancel()
                }
            }
        }

    /**
     * The acceptance of B-29 over HTTP: Maya's order page, listening on the channel its tree names, is told the
     * order is in transit when the simulator moves it — no reload. The stream opens with the order as it is (a
     * move between the page's load and its listening is not lost), and it is the customer tier's: Sam asking
     * for Maya's channel, and anybody asking for a channel that is no order's, is `404 order_not_found` like the
     * page; with no token it is `401`.
     *
     * Against the application on a real port, as `main` serves it: the test engine runs the server on virtual
     * time, where the stream's heartbeat never lets the response start, and a client that leaves never ends the
     * server's side of the stream.
     */
    @Test
    fun `the order page hears its order go in transit without loading again`() =
        seededFreshDatabase().use { database ->
            val clock = TestClock()
            lateinit var koin: Koin
            val server =
                embeddedServer(CIO, port = 0) {
                    haulModule(
                        database,
                        CANVAS_NOW,
                        commit = "test",
                        signIn = ShildikHarness.signIn,
                        sagaClock = clock,
                    )
                    koin = getKoin()
                }.start(wait = false)
            try {
                val port =
                    runBlocking {
                        server.engine
                            .resolvedConnectors()
                            .first()
                            .port
                    }
                val origin = "http://127.0.0.1:$port"
                val orderId = runBlocking { koin.place() }
                val topic = LiveOrders.topic(orderId)

                assertEquals(404 to ErrorCode.OrderNotFound, refusal(origin, topic, sam))
                assertEquals(404 to ErrorCode.OrderNotFound, refusal(origin, "cart:$orderId", maya))
                assertEquals(401 to ErrorCode.Unauthenticated, refusal(origin, topic, null))

                val response = http.send(updates(origin, topic, maya), HttpResponse.BodyHandlers.ofLines())
                assertEquals(200, response.statusCode())
                val type = response.headers().firstValue("Content-Type").orElse("")
                assertEquals("text/event-stream", type.substringBefore(';'))
                val lines = LinkedBlockingQueue<String>()
                val reader = thread(isDaemon = true) { response.body().use { it.forEach(lines::put) } }
                try {
                    val first = lines.nextFrame().body()
                    assertEquals("Thanks, Maya — order #$orderId is placed", first.title)

                    val simulator = koin.get<FulfilmentSimulator>()
                    runBlocking {
                        clock.at(Duration.ZERO)
                        simulator.advance()
                        clock.at(48.hours)
                        simulator.advance()
                    }
                    val moved = lines.nextFrame().body()
                    assertEquals(2, moved.steps?.current, "the order is not in transit: ${moved.steps}")
                    assertTrue("In transit" in moved.shipments.map { it.status }, "${moved.shipments}")
                } finally {
                    reader.interrupt()
                }
            } finally {
                server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
            }
        }

    private val http: HttpClient = HttpClient.newHttpClient()

    private fun updates(
        origin: String,
        topic: String,
        token: String?,
    ): HttpRequest =
        HttpRequest
            .newBuilder(URI("$origin${OrderPaths.UPDATES}?topic=${URLEncoder.encode(topic, Charsets.UTF_8)}"))
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .timeout(java.time.Duration.ofSeconds(10))
            .GET()
            .build()

    /** The status and the code a stream that is refused is answered with. */
    private fun refusal(
        origin: String,
        topic: String,
        token: String?,
    ): Pair<Int, ErrorCode> {
        val response = http.send(updates(origin, topic, token), HttpResponse.BodyHandlers.ofString())
        return response.statusCode() to haulWireJson.decodeFromString(ErrorBody.serializer(), response.body()).code
    }

    /** Maya's cart placed in the application's own graph, which the simulator in it then moves. */
    private suspend fun Koin.place(): String {
        val checkout = get<CheckoutCommands>()
        checkout.choose(MAYA, CheckoutChoice(slotId = "2025-10-08T15"))
        return get<Placement>().place(MAYA, "maya-live", PlaceOrderRequest(checkout.quote(MAYA).fingerprint))
    }

    /** The data of the next event that has any, `ping`s skipped; ten seconds at most for each line. */
    private fun LinkedBlockingQueue<String>.nextFrame(): String {
        var data: String? = null
        while (true) {
            val line = poll(10, TimeUnit.SECONDS) ?: error("no line in ten seconds")
            if (line.startsWith("data:")) {
                data = line.removePrefix("data:").trim()
            } else if (line.isEmpty() && data != null) {
                return data
            }
        }
    }

    private fun String.body(): OrderBody =
        haulWireJson.decodeFromString(UpdateComponentMessage.serializer(), this).let {
            assertEquals("order", it.componentId)
            it.component as OrderBody
        }
}
