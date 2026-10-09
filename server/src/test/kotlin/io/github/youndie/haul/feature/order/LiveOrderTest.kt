package io.github.youndie.haul.feature.order

import io.github.youndie.haul.ErrorBody
import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.checkout.PlaceOrderRequest
import io.github.youndie.haul.feature.checkout.domain.CheckoutCommands
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentSimulator
import io.github.youndie.haul.feature.order.domain.Placement
import io.github.youndie.haul.feature.payment.data.ExposedPaymentSimulator
import io.github.youndie.haul.feature.payment.domain.Authorisation
import io.github.youndie.haul.feature.payment.domain.AuthorisationOutcome
import io.github.youndie.haul.feature.payment.domain.PaymentProcessor
import io.github.youndie.haul.haulModule
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.FulfilmentWorld
import io.github.youndie.haul.testing.Ledger
import io.github.youndie.haul.testing.MAYA
import io.github.youndie.haul.testing.ShildikHarness
import io.github.youndie.haul.testing.TestClock
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.ui.OrderBody
import io.github.youndie.haul.ui.OrderFactKind
import io.github.youndie.kompot.realtime.UpdateComponentMessage
import io.github.youndie.petich.SuspendedPetichSweeper
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.koin.core.Koin
import org.koin.dsl.module
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
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
     * B-56: placement moves the page too. Maya's page opens while her order is still placing — the card
     * processor holds its answer, and the order is open as `placing` — and is drawn again placed when the saga
     * confirms it: with the points it earns, which a placing order does not show yet (the control that the
     * frame heard is the placed one, not the placing one again). Before B-56 only the simulators told, and the
     * page stayed as it opened until something else moved the order.
     */
    @Test
    fun `a page that opened while the order was placing hears it placed`() =
        seededFreshDatabase().use { database ->
            val card = HeldCard(ExposedPaymentSimulator(Databases.connect(database), CANVAS_NOW))
            FulfilmentWorld(database, overrides = module { single<PaymentProcessor> { card } }).use { world ->
                val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                try {
                    val live = world.koin.get<LiveOrders>()
                    live.start(scope)
                    val placing = scope.async { world.koin.place(key = "maya-placing") }
                    val orderId = runBlocking { withTimeout(30.seconds) { card.asked.await() } }
                    val page = Channel<String>(Channel.UNLIMITED)
                    runBlocking { live.subscribe(SampleCustomers.MAYA, orderId, page) }
                    val opened = live.drawn(orderId)
                    assertTrue(opened.summary.facts.none { it.kind == OrderFactKind.Points }, "${opened.summary}")

                    card.answer()
                    assertEquals(orderId, runBlocking { withTimeout(30.seconds) { placing.await() } })

                    val heard = runBlocking { withTimeout(10.seconds) { page.receive() } }.body()
                    assertTrue(
                        heard.summary.facts.any { it.kind == OrderFactKind.Points },
                        "the page was not drawn placed: ${heard.summary.facts}",
                    )
                    assertEquals("Thanks, Maya — order #$orderId is placed", heard.title)
                } finally {
                    scope.cancel()
                }
            }
        }

    /**
     * B-56, the rollback's half: Maya pays with the test card ···· 0002, which the simulator declines, and her
     * page — opened while the order was placing — is drawn again cancelled, declined and nothing charged, when
     * the saga's rollback cancels it (`open-order`'s compensation, which every cancellation passes through).
     */
    @Test
    fun `a page that opened while the order was placing hears a declined card cancel it`() =
        seededFreshDatabase().use { database ->
            val card = HeldCard(ExposedPaymentSimulator(Databases.connect(database), CANVAS_NOW))
            FulfilmentWorld(database, overrides = module { single<PaymentProcessor> { card } }).use { world ->
                val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                try {
                    val live = world.koin.get<LiveOrders>()
                    live.start(scope)
                    val placing = scope.async { world.koin.place(DECLINED, key = "maya-declined") }
                    val orderId = runBlocking { withTimeout(30.seconds) { card.asked.await() } }
                    val page = Channel<String>(Channel.UNLIMITED)
                    runBlocking { live.subscribe(SampleCustomers.MAYA, orderId, page) }
                    assertEquals("Thanks, Maya — order #$orderId is placed", live.drawn(orderId).title)

                    card.answer()
                    assertEquals(orderId, runBlocking { withTimeout(30.seconds) { placing.await() } })

                    val heard = runBlocking { withTimeout(10.seconds) { page.receive() } }.body()
                    assertCancelledAsDeclined(heard)
                } finally {
                    scope.cancel()
                }
            }
        }

    /**
     * B-56, the sweeper's half: the process placing Maya's order with the declined card dies inside the card
     * processor's call (as in [PlacementRestartTest]), leaving the order `placing` and the saga `PROCESSING`. In
     * the next process her page is open on it when the sweeper carries the saga on — the card is declined this
     * time, the rollback cancels the order — and the page hears it, though no request of hers ran the saga.
     */
    @Test
    fun `a page open on an order a dead process left placing hears the sweeper cancel it`() =
        seededFreshDatabase().use { database ->
            val card = HeldCard(ExposedPaymentSimulator(Databases.connect(database), CANVAS_NOW))
            val orderId =
                FulfilmentWorld(database, overrides = module { single<PaymentProcessor> { card } }).use { first ->
                    runBlocking {
                        val placing = launch(Dispatchers.IO) { first.koin.place(DECLINED, key = "maya-swept") }
                        val asked = withTimeout(30.seconds) { card.asked.await() }
                        // The process dies here, inside the processor's call: petich writes nothing more.
                        placing.cancelAndJoin()
                        asked
                    }
                }
            assertEquals("PROCESSING", Ledger(database).sagaStatus())

            // The next process, later by the saga's clock than the dead one's last write.
            FulfilmentWorld(database, TestClock().at(5.minutes)).use { next ->
                val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                try {
                    val live = next.koin.get<LiveOrders>()
                    live.start(scope)
                    val page = Channel<String>(Channel.UNLIMITED)
                    runBlocking { live.subscribe(SampleCustomers.MAYA, orderId, page) }
                    assertEquals("Thanks, Maya — order #$orderId is placed", live.drawn(orderId).title)

                    val swept = runBlocking { next.koin.get<SuspendedPetichSweeper>().sweepStuck() }
                    assertEquals(1, swept, "the sweeper did not carry the saga on")

                    val heard = runBlocking { withTimeout(10.seconds) { page.receive() } }.body()
                    assertCancelledAsDeclined(heard)
                    assertEquals("REJECTED", Ledger(database).sagaStatus())
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

    /**
     * The status and the code a stream that is refused is answered with. Read as a stream, so a stream that was
     * opened instead — which never ends — fails here rather than hanging the suite.
     */
    private fun refusal(
        origin: String,
        topic: String,
        token: String?,
    ): Pair<Int, ErrorCode> {
        val response = http.send(updates(origin, topic, token), HttpResponse.BodyHandlers.ofInputStream())
        response.body().use { body ->
            check(response.statusCode() != 200) { "the stream of $topic was opened" }
            val error = haulWireJson.decodeFromString(ErrorBody.serializer(), body.readAllBytes().decodeToString())
            return response.statusCode() to error.code
        }
    }

    /** Maya's cart placed after [choice] in the application's own graph, which the simulator in it then moves. */
    private suspend fun Koin.place(
        choice: CheckoutChoice = CheckoutChoice(slotId = SLOT),
        key: String = "maya-live",
    ): String {
        val checkout = get<CheckoutCommands>()
        checkout.choose(MAYA, choice)
        return get<Placement>().place(MAYA, key, PlaceOrderRequest(checkout.quote(MAYA).fingerprint))
    }

    /** [orderId]'s body as Maya's page is drawn it now — what a page that opens now starts from. */
    private fun LiveOrders.drawn(orderId: String): OrderBody =
        assertNotNull(runBlocking { frame(SampleCustomers.MAYA, "Maya", orderId) }, "not Maya's order").body()

    /** Cancelled because the card was declined, as the page says it: the title, the notice, nothing charged. */
    private fun assertCancelledAsDeclined(heard: OrderBody) {
        assertEquals("Order cancelled", heard.title, "the page was not drawn cancelled")
        val notice = assertNotNull(heard.notice, "a cancelled order says why")
        assertTrue("declined" in notice.title, notice.toString())
        assertTrue(heard.summary.voided, "${heard.summary}")
    }

    /**
     * The card processor, holding its answer: placement reaches it with the order open as `placing`, and waits
     * there until the test lets [real] answer — or, cancelled, dies there as a killed process would.
     */
    private class HeldCard(
        private val real: PaymentProcessor,
    ) : PaymentProcessor by real {
        /** The order the authorisation was asked for, once it is. */
        val asked = CompletableDeferred<String>()
        private val released = CompletableDeferred<Unit>()

        fun answer() {
            released.complete(Unit)
        }

        override suspend fun authorise(
            key: String,
            authorisation: Authorisation,
        ): AuthorisationOutcome {
            asked.complete(authorisation.orderId)
            released.await()
            return real.authorise(key, authorisation)
        }
    }

    private companion object {
        const val SLOT = "2025-10-08T15"

        /** The window, and the test card ···· 0002, which the simulator always declines. */
        val DECLINED = CheckoutChoice(slotId = SLOT, payment = "card-0002")
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
