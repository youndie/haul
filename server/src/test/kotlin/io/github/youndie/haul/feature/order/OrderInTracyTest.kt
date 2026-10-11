package io.github.youndie.haul.feature.order

import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.checkout.PlaceOrderRequest
import io.github.youndie.haul.feature.checkout.domain.CheckoutCommands
import io.github.youndie.haul.feature.order.domain.Placement
import io.github.youndie.haul.feature.returns.ReturnEntry
import io.github.youndie.haul.feature.returns.domain.RequestReturn
import io.github.youndie.haul.ops.ORDER_ID
import io.github.youndie.haul.ops.OrderLines
import io.github.youndie.haul.ops.SERVICE
import io.github.youndie.haul.ops.TracyOrderTrail
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.testing.FulfilmentWorld
import io.github.youndie.haul.testing.MAYA
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.tracy.agent.AgentConfig
import io.github.youndie.tracy.agent.TracyAgent
import io.github.youndie.tracy.agent.TracyTraceContext
import io.github.youndie.tracy.wire.EntityRef
import io.github.youndie.tracy.wire.LogRecord
import io.github.youndie.tracy.wire.Span
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * An order's saga is visible in tracy (B-109): what the agent is handed — the batch it would send — when
 * Maya's order is placed, delivered and partly returned, read as tracy's `get_entity orderId=<id>` reads it:
 * every line carries the order's id as an indexed field, and in the order the order lived it.
 *
 * The agent is tracy's own, sampling nothing on its own account (`sampleRate = 0`), so what is kept is what
 * the agent's rules keep whatever the stand's rate is.
 */
class OrderInTracyTest {
    private val agent =
        TracyAgent(
            AgentConfig(
                SERVICE,
                apiKey = "test",
                endpoint = "http://tracy.invalid",
                instanceId = "test",
                sampleRate = 0.0,
            ),
            clock = { 0L },
        )

    private val slot = CheckoutChoice(slotId = "2025-10-08T15")

    /** Every record the agent would send now, in the order it was handed them. */
    private fun sent() = agent.drainBatch()

    /** The lines about [orderId], as `message step-or-status` — the shape `get_entity` lists them in. */
    private fun List<LogRecord>.about(orderId: String): List<String> =
        filter { it.fields?.get(ORDER_ID)?.content == orderId }.map { record ->
            val what = record.fields?.get(OrderLines.STEP) ?: record.fields?.get(OrderLines.STATUS)
            listOfNotNull(record.message, what?.content).joinToString(" ")
        }

    @Test
    fun `placement then delivery then a return are filed under the order as they happened`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource, trail = TracyOrderTrail(agent)).use { world ->
                val order = world.place(slot)
                world.advance(Duration.ZERO)
                world.advance(72.hours)
                runBlocking {
                    world.koin.get<RequestReturn>().request(
                        SampleCustomers.MAYA,
                        order,
                        ReturnEntry(listOf(0), "doesnt_fit"),
                    )
                }
                world.advanceReturns(72.hours + 2.days)

                val records = sent().filterIsInstance<LogRecord>()
                records.forEach { record ->
                    assertEquals(listOf(ORDER_ID), record.indexed, "${record.message} is not filed under its order")
                }
                val lines = records.about(order)
                assertEquals(records.size, lines.size)
                assertEquals(
                    listOf(
                        "reserve-stock",
                        "reserve-slot",
                        "redeem-points",
                        "open-order",
                        "authorise-payment",
                        "confirm",
                        "clear-cart",
                    ).map { "${OrderLines.STEP_DONE} $it" },
                    lines.take(7),
                )
                val delivery = lines.drop(7).dropLast(4)
                assertTrue(delivery.first() == "${OrderLines.SHIPMENT_MOVED} placed", "$delivery")
                assertTrue("${OrderLines.SHIPMENT_CAPTURED}" in delivery, "$delivery")
                assertTrue(
                    delivery.indexOf(OrderLines.SHIPMENT_CAPTURED) <
                        delivery.indexOf("${OrderLines.SHIPMENT_MOVED} in_transit"),
                )
                assertTrue("${OrderLines.SHIPMENT_MOVED} delivered" in delivery, "$delivery")
                assertEquals(
                    listOf(
                        OrderLines.RETURN_REQUESTED,
                        "${OrderLines.RETURN_MOVED} picked_up",
                        OrderLines.RETURN_REFUNDED,
                        "${OrderLines.RETURN_MOVED} refunded",
                    ),
                    lines.takeLast(4),
                )
            }
        }

    @Test
    fun `a declined card's refusal and every member it undid are filed under the order`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource, trail = TracyOrderTrail(agent)).use { world ->
                val order = world.place(slot.copy(payment = "card-0002"), key = "maya-declined")

                val records = sent().filterIsInstance<LogRecord>()
                val refusal = records.single { it.message == OrderLines.STEP_REFUSED }
                assertEquals("payment_declined", refusal.fields?.get(OrderLines.REASON)?.content)
                assertEquals(
                    listOf(
                        "reserve-stock",
                        "reserve-slot",
                        "redeem-points",
                        "open-order",
                    ).map { "${OrderLines.STEP_DONE} $it" } +
                        "${OrderLines.STEP_REFUSED} authorise-payment" +
                        // The member that refused has nothing of its own to undo: the rollback starts below it.
                        listOf("open-order", "redeem-points", "reserve-slot", "reserve-stock")
                            .map { "${OrderLines.STEP_UNDONE} $it" },
                    records.about(order),
                )
            }
        }

    /**
     * Placement runs its saga inside `POST /orders`: each member is a span of that request's trace, under its
     * span, and the order's lines join the trace.
     */
    @Test
    fun `inside the placement request each member is a span of the request's trace`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource, trail = TracyOrderTrail(agent)).use { world ->
                val trace = TracyTraceContext(traceId = "trace-kept", spanId = "request", sampledUpstream = false)
                val order = world.placeIn(trace)
                agent.finishRequest(trace, span = null, durationMs = 0, statusCode = 202, forced = true)

                val batch = sent()
                val spans = batch.filterIsInstance<Span>()
                assertEquals(
                    listOf(
                        "reserve-stock",
                        "reserve-slot",
                        "redeem-points",
                        "open-order",
                        "authorise-payment",
                        "confirm",
                        "clear-cart",
                    ).map { "saga $it" },
                    spans.map { it.name },
                )
                assertTrue(spans.all { it.traceId == "trace-kept" && it.parentSpanId == "request" }, "$spans")
                val lines = batch.filterIsInstance<LogRecord>()
                assertEquals(7, lines.size)
                assertTrue(
                    lines.all { it.traceId == "trace-kept" && it.fields?.get(ORDER_ID)?.content == order },
                    "$lines",
                )
            }
        }

    /**
     * A placement trace the tail decision drops — the agent's own 1 % on a stand that sets no rate — still
     * leaves the order's reference to it, so `get_entity` names the trace even then; only the bodies go.
     */
    @Test
    fun `a placement trace the agent drops still leaves the order's reference`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource, trail = TracyOrderTrail(agent)).use { world ->
                val trace = TracyTraceContext(traceId = "trace-dropped", spanId = "request", sampledUpstream = false)
                val order = world.placeIn(trace)
                agent.finishRequest(trace, span = null, durationMs = 0, statusCode = 202)

                assertEquals(
                    listOf(EntityRef(traceId = "trace-dropped", key = ORDER_ID, value = order, ts = 0L)),
                    sent().filter { it is EntityRef || it is LogRecord || it is Span },
                )
            }
        }

    /** Maya's cart placed inside [trace], as the request's handler runs it; the order's id. */
    private fun FulfilmentWorld.placeIn(trace: TracyTraceContext): String =
        runBlocking(trace) {
            val checkout = koin.get<CheckoutCommands>()
            checkout.choose(MAYA, slot)
            koin.get<Placement>().place(MAYA, "maya-in-a-request", PlaceOrderRequest(checkout.quote(MAYA).fingerprint))
        }
}
