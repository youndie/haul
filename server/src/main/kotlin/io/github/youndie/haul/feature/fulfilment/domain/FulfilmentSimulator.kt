package io.github.youndie.haul.feature.fulfilment.domain

import io.github.youndie.haul.feature.checkout.domain.PaymentMethod
import io.github.youndie.haul.feature.order.domain.OrderRepository
import io.github.youndie.haul.feature.order.domain.ShipmentStatus
import io.github.youndie.haul.feature.payment.domain.Capture
import io.github.youndie.haul.feature.payment.domain.CaptureOutcome
import io.github.youndie.haul.feature.payment.domain.PaymentProcessor
import io.github.youndie.petich.PetichClock
import org.slf4j.LoggerFactory
import java.time.Instant
import kotlin.time.toJavaDuration

private val log = LoggerFactory.getLogger("io.github.youndie.haul.fulfilment")

/**
 * The outside world that moves an order (research D4): each seller's shipment along its way, at [pace], by
 * [clock] — and the card charged for a shipment when it leaves for the road («Your card is charged when the
 * order ships», the canvas).
 *
 * **One pass, [advance], does all of it**, and nothing else moves a shipment: a pass reads the shipments on
 * their way, and moves each one through every step that has come due by now, each stamped at the moment it
 * came due rather than at the pass — so a pass after a pause (a restart, a slow poll) catches up and leaves
 * the same history an unbroken run would. A shipment the seller has not seen yet is stamped `placed` now and
 * waits for the next pass.
 *
 * **The capture comes before the move to `in_transit`.** It is named by the shipment, so a process that died
 * between the two leaves a shipment still `packed` with its capture taken; the next pass asks again, is
 * answered with what was taken, and moves it. A capture the processor refuses holds the shipment where it
 * is, said at `error` — a shipment is never on the road unpaid. Pay on delivery has nothing to capture.
 *
 * Every move is conditional on the status it leaves, so two passes at once — two processes in a rolling
 * deploy — move each shipment once, and capture it once.
 *
 * [clock] is the saga's (`Application.kt`): the stamps are compared across processes, which only the wall
 * clock can do; the store's «now» is not it.
 */
internal class FulfilmentSimulator(
    private val shipments: FulfilmentRepository,
    private val orders: OrderRepository,
    private val payments: PaymentProcessor,
    private val clock: PetichClock,
    private val pace: FulfilmentPace,
) {
    /** One pass; the number of moves it made. */
    suspend fun advance(): Int {
        val now = Instant.ofEpochMilli(clock.nowEpochMs())
        return shipments.active().sumOf { carry(it, now) }
    }

    private suspend fun carry(
        shipment: ActiveShipment,
        now: Instant,
    ): Int {
        var since =
            shipment.since ?: run {
                shipments.receive(shipment.id, now)
                return 0
            }
        var status = shipment.status
        var moves = 0
        while (true) {
            val step = pace.next(status, shipment.pickup, shipment.dispatchDays) ?: break
            val due = since + step.after.toJavaDuration()
            if (due.isAfter(now)) break
            if (step.to == ShipmentStatus.IN_TRANSIT && !charged(shipment, due)) break
            val code =
                if (step.to ==
                    ShipmentStatus.READY_FOR_PICKUP
                ) {
                    PickupCodes.of(shipment.sagaId, shipment.id)
                } else {
                    null
                }
            if (!shipments.move(shipment.id, status, step.to, due, code)) break
            status = step.to
            since = due
            moves++
        }
        return moves
    }

    /** Whether [shipment]'s share of its order is captured — now, or by an earlier pass — or has nothing to capture. */
    private suspend fun charged(
        shipment: ActiveShipment,
        at: Instant,
    ): Boolean {
        val order = orders.order(shipment.orderId) ?: error("the shipment ${shipment.id} has no order")
        val method =
            PaymentMethod.byId(order.placed.payment)
                ?: error("the order ${order.id} pays with «${order.placed.payment}»")
        if (!method.card) return true
        val share = ShipmentShares.of(order).getValue(shipment.id)
        if (share == 0) return true
        return when (val outcome = payments.capture(shipment.id, Capture(order.id, share, at))) {
            is CaptureOutcome.Captured -> {
                true
            }

            else -> {
                log.error(
                    "shipment {} is held: capturing {} cents of {} was refused: {}",
                    shipment.id,
                    share,
                    order.id,
                    outcome,
                )
                false
            }
        }
    }
}
