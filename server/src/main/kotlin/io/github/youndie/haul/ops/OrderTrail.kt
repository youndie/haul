package io.github.youndie.haul.ops

import io.github.youndie.tracy.agent.TracyAgent
import io.github.youndie.tracy.agent.withSpan

/** The field an order's lines carry its id in, and the entity key tracy's `get_entity` finds them by. */
internal const val ORDER_ID: String = "orderId"

/**
 * Everything an order's trail says, in one place: the messages — constants, the templates tracy groups by —
 * and the names of the fields that carry the data.
 */
internal object OrderLines {
    const val STEP_DONE = "order saga step done"
    const val STEP_REFUSED = "order saga step refused"
    const val STEP_THREW = "order saga step threw"
    const val STEP_UNDONE = "order saga step undone"
    const val SHIPMENT_MOVED = "order shipment moved"
    const val SHIPMENT_CAPTURED = "order shipment captured"
    const val RETURN_REQUESTED = "order return requested"
    const val RETURN_MOVED = "order return moved"
    const val RETURN_REFUNDED = "order return refunded"

    const val STEP = "step"
    const val REASON = "reason"
    const val SHIPMENT = "shipmentId"
    const val STATUS = "status"
    const val AT = "at"
    const val CENTS = "cents"
}

/**
 * What happened to one order after the shopper pressed «Place order», written where tracy files it under
 * the order (B-109): each member of the saga, each move of a shipment and the capture that lets it leave,
 * the return asked for, collected and refunded — so `get_entity orderId=<id>` reads placement → delivery →
 * return in one list.
 *
 * Written through tracy's own API and not through SLF4J, because only a field written there can be an
 * entity key: the agent's Logback appender takes MDC as plain fields, never indexed, and haul attaches none.
 *
 * [record]'s message is a constant at its call site — tracy groups by it and hands it to an agent as
 * trusted text; everything that came from the order goes into the fields. Inside a request (placement runs
 * its saga in `POST /orders`) a line joins the request's trace and its tail decision: a trace that is not
 * kept still leaves the order's reference, without the body. Outside one — the simulators' passes, the
 * sweeper — a line is its own entry and is always kept.
 */
internal interface OrderTrail {
    /** One line about [orderId]: [message] is the template, [fields] are data. */
    suspend fun record(
        orderId: String,
        message: String,
        vararg fields: Pair<String, String>,
    )

    /** [block] as a span named [name] under the request it runs in; outside a request, only [block]. */
    suspend fun <T> span(
        name: String,
        block: suspend () -> T,
    ): T

    companion object {
        /** What the tests and a server without tracy run with: nothing written, every block just runs. */
        val NONE: OrderTrail =
            object : OrderTrail {
                override suspend fun record(
                    orderId: String,
                    message: String,
                    vararg fields: Pair<String, String>,
                ) = Unit

                override suspend fun <T> span(
                    name: String,
                    block: suspend () -> T,
                ): T = block()
            }
    }
}

/** The order's trail in tracy, through [agent]. */
internal class TracyOrderTrail(
    private val agent: TracyAgent,
) : OrderTrail {
    private val log = agent.logger(LOGGER)

    override suspend fun record(
        orderId: String,
        message: String,
        vararg fields: Pair<String, String>,
    ) {
        log.info(message) {
            field(ORDER_ID, orderId, indexed = true)
            fields.forEach { (name, value) -> field(name, value) }
        }
    }

    override suspend fun <T> span(
        name: String,
        block: suspend () -> T,
    ): T = withSpan(name, agent) { block() }

    private companion object {
        const val LOGGER = "io.github.youndie.haul.order"
    }
}
