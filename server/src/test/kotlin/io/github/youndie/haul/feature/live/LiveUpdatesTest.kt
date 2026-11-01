package io.github.youndie.haul.feature.live

import io.github.youndie.haul.ErrorBody
import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.checkout.PlaceOrderRequest
import io.github.youndie.haul.feature.checkout.domain.CheckoutCommands
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentSimulator
import io.github.youndie.haul.feature.order.LiveOrders
import io.github.youndie.haul.feature.order.domain.Placement
import io.github.youndie.haul.haulModule
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.shell.UPDATES
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.MAYA
import io.github.youndie.haul.testing.TestClock
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.ui.LiveOrdersBody
import io.github.youndie.haul.ui.LiveStepState
import io.github.youndie.kompot.realtime.UpdateComponentMessage
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.runBlocking
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * The acceptance of B-164 over HTTP: «Live orders», listening on the channel its tree names without a sign-in, hears an
 * order go in transit when the simulator moves it — no reload — over the order page's own stream (`GET /ui/updates`,
 * B-29). And the order page's channel on that stream is still the customer's: without a sign-in it is `401`.
 *
 * Against the application on a real port, as `main` serves it, for the reason `LiveOrderTest` gives: the test engine
 * runs the server on virtual time, where the stream's heartbeat never lets the response start.
 */
class LiveUpdatesTest {
    @Test
    fun `the live orders hear an order go in transit without loading again`() =
        seededFreshDatabase().use { database ->
            val clock = TestClock()
            lateinit var koin: Koin
            val server =
                embeddedServer(CIO, port = 0) {
                    haulModule(database, CANVAS_NOW, commit = "test", sagaClock = clock)
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

                val refused =
                    http.send(
                        updates(origin, LiveOrders.topic(orderId)),
                        HttpResponse.BodyHandlers.ofString(),
                    )
                assertEquals(401, refused.statusCode(), "an order's channel was opened without a sign-in")
                assertEquals(
                    ErrorCode.Unauthenticated,
                    haulWireJson.decodeFromString(ErrorBody.serializer(), refused.body()).code,
                )

                val response = http.send(updates(origin, LiveFeed.TOPIC), HttpResponse.BodyHandlers.ofLines())
                assertEquals(200, response.statusCode())
                assertEquals(
                    "text/event-stream",
                    response
                        .headers()
                        .firstValue("Content-Type")
                        .orElse("")
                        .substringBefore(';'),
                )
                val lines = LinkedBlockingQueue<String>()
                val reader = thread(isDaemon = true) { response.body().use { it.forEach(lines::put) } }
                try {
                    val first = lines.nextFrame().body()
                    assertEquals("Placed", first.orders.single { it.id == orderId }.status)

                    val simulator = koin.get<FulfilmentSimulator>()
                    runBlocking {
                        clock.at(Duration.ZERO)
                        simulator.advance()
                        clock.at(48.hours)
                        simulator.advance()
                    }
                    // The moves may be heard one drawing at a time: read until the order is in transit.
                    var row =
                        lines
                            .nextFrame()
                            .body()
                            .orders
                            .single { it.id == orderId }
                    while (row.status != "In transit") {
                        row =
                            lines
                                .nextFrame()
                                .body()
                                .orders
                                .single { it.id == orderId }
                    }
                    val sent =
                        row.lanes
                            .drop(1)
                            .flatMap { it.steps }
                            .filter { it.label == "On its way" }
                    assertEquals(listOf(LiveStepState.Done), sent.map { it.state }.distinct(), "${row.lanes}")
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
    ): HttpRequest =
        HttpRequest
            .newBuilder(URI("$origin$UPDATES?topic=${URLEncoder.encode(topic, Charsets.UTF_8)}"))
            .timeout(java.time.Duration.ofSeconds(10))
            .GET()
            .build()

    /** Maya's cart placed by courier in the application's own graph, which the simulator in it then moves. */
    private suspend fun Koin.place(): String {
        val checkout = get<CheckoutCommands>()
        checkout.choose(MAYA, CheckoutChoice(slotId = "2025-10-08T15"))
        return get<Placement>().place(MAYA, "maya-live-feed", PlaceOrderRequest(checkout.quote(MAYA).fingerprint))
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

    private fun String.body(): LiveOrdersBody =
        haulWireJson.decodeFromString(UpdateComponentMessage.serializer(), this).let {
            assertEquals("live-orders", it.componentId)
            it.component as LiveOrdersBody
        }
}
