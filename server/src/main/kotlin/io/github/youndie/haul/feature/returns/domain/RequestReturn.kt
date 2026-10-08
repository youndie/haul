package io.github.youndie.haul.feature.returns.domain

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.FieldError
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentRepository
import io.github.youndie.haul.feature.order.domain.OrderError
import io.github.youndie.haul.feature.order.domain.OrderRepository
import io.github.youndie.haul.feature.order.domain.ShipmentStatus
import io.github.youndie.haul.feature.returns.ReturnEntry
import io.github.youndie.haul.feature.returns.returnProblems
import io.github.youndie.petich.PetichClock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * «Request return» (feature-orders, endpoint-orders `POST /api/v1/me/orders/{id}/returns`): the lines the
 * shopper ticked go back, whole, and the return waits for the simulated courier ([ReturnSimulator]).
 *
 * Refused, in this order: a form that cannot be sent (`400`, every field named); an order that is not the
 * caller's, or none at all (`404 order_not_found`, one answer for both, as the order's page gives); an order
 * whose return was already asked for (`409 already_returned` — one return per order); a line that has not
 * arrived (`422 not_delivered`); a line that arrived more than 30 days ago (`422 return_window_closed`,
 * «Late return»).
 *
 * The 30 days are counted against [clock], the simulator's, because the arrival they count from was stamped
 * by it (`shipment_events`); the store's «now» is not that clock.
 */
internal class RequestReturn(
    private val orders: OrderRepository,
    private val shipments: FulfilmentRepository,
    private val returns: ReturnRepository,
    private val clock: PetichClock,
) {
    suspend fun request(
        customerId: String,
        orderId: String,
        entry: ReturnEntry,
    ) {
        val problems =
            returnProblems(entry) +
                listOfNotNull(
                    FieldError("reason", ErrorCode.FieldInvalid, "Choose one of the reasons")
                        .takeIf { entry.reason.isNotBlank() && ReturnReason.byId(entry.reason) == null },
                )
        if (problems.isNotEmpty()) throw ReturnError.Invalid(problems)
        val order =
            orders.order(orderId)?.takeIf { it.placed.customerId == customerId } ?: throw OrderError.NotFound(orderId)
        if (order.returnStatus != null) throw ReturnError.AlreadyReturned(orderId)
        val positions = entry.lines.distinct()
        if (positions.any { it !in order.placed.lines.indices }) {
            throw ReturnError.Invalid(listOf(FieldError("lines", ErrorCode.FieldInvalid, "Choose lines of this order")))
        }
        val history = shipments.history(orderId)
        val arrivals =
            positions.map { position ->
                val line = order.placed.lines[position]
                val shipment = order.shipments.first { it.sellerId == line.sellerId }
                val arrived =
                    shipment.status.takeIf { it in ARRIVED }?.let { history[shipment.id]?.get(it) }
                        ?: throw ReturnError.NotDelivered()
                arrived
            }
        val now = Instant.ofEpochMilli(clock.nowEpochMs())
        arrivals.firstOrNull { !ReturnWindow.open(it, now) }?.let { late ->
            throw ReturnError.WindowClosed(MONTH_DAY.format(ReturnWindow.lastDay(late)))
        }
        val refunds = ReturnRefunds.of(order)
        val lines = positions.sorted().map { ReturnedLine(it, refunds.getValue(it)) }
        val refund = lines.sumOf { it.refundCents }
        val request =
            NewReturn(
                orderId = order.id,
                reason = entry.reason,
                lines = lines,
                points = ReturnRefunds.points(order, refund),
                requestedAt = now,
            )
        if (!returns.request(request)) throw ReturnError.AlreadyReturned(orderId)
    }

    private companion object {
        val ARRIVED = setOf(ShipmentStatus.DELIVERED, ShipmentStatus.PICKED_UP)
        val MONTH_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    }
}
