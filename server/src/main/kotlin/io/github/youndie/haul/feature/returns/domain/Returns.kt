package io.github.youndie.haul.feature.returns.domain

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.FieldError
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.order.domain.Order
import java.time.Instant
import java.time.LocalDate

/**
 * Where a return is (research §5, `Return`): asked for, collected by the courier, refunded. The refund
 * comes last — the canvas's `Order_Returned` draws «Requested · Picked up · Refunded» and «$87.50 refunded
 * Sep 19» three weeks after the order — so the money goes back when the parcel is back, not when it is
 * asked for (feature-orders: «the simulator picks it up and refunds»).
 */
internal object ReturnStatus {
    const val REQUESTED = "requested"
    const val PICKED_UP = "picked_up"
    const val REFUNDED = "refunded"

    val ALL: List<String> = listOf(REQUESTED, PICKED_UP, REFUNDED)
}

/** Why a shopper sends something back: the dialog's choices, by the id the request names. */
internal enum class ReturnReason(
    val id: String,
    val label: String,
) {
    DoesNotFit("doesnt_fit", "Doesn’t fit"),
    NotAsDescribed("not_as_described", "Not as described"),
    Damaged("damaged", "Arrived damaged"),
    ChangedMind("changed_mind", "Changed my mind"),
    ;

    companion object {
        fun byId(id: String): ReturnReason? = entries.firstOrNull { it.id == id }
    }
}

/** A line going back, whole, by its [position] in the order, and what returning it gives back. */
internal data class ReturnedLine(
    val position: Int,
    val refundCents: Int,
)

/** A return as it is asked for: the order's lines going back, why, the refund and the points it takes back. */
internal data class NewReturn(
    val orderId: String,
    val reason: String,
    val lines: List<ReturnedLine>,
    val points: Int,
    val requestedAt: Instant,
) {
    val refundCents: Int get() = lines.sumOf { it.refundCents }
}

/**
 * A return as stored: what [NewReturn] asked for, where it is ([ReturnStatus]), and when it entered each
 * status it has passed through ([history]).
 */
internal data class OrderReturn(
    val orderId: String,
    val reason: String,
    val lines: List<ReturnedLine>,
    val refundCents: Int,
    val points: Int,
    val status: String,
    val history: Map<String, Instant>,
)

/** The returns' storage: one return per order, written once, then moved along by the simulator. */
internal interface ReturnRepository {
    /** Writes [request] as [ReturnStatus.REQUESTED]; `false`, writing nothing, when its order has a return already. */
    suspend fun request(request: NewReturn): Boolean

    suspend fun returnOf(orderId: String): OrderReturn?

    /** The returns not refunded yet, for the simulator's pass. */
    suspend fun active(): List<OrderReturn>

    /** Moves [orderId]'s return from [from] to [to] as of [at]; `false` when it is no longer in [from]. */
    suspend fun move(
        orderId: String,
        from: String,
        to: String,
        at: Instant,
    ): Boolean
}

/**
 * The 30 days (research D7: «returns within 30 days of delivery»), counted in the store's days as the order's
 * page writes them: a line delivered on Sep 26 can be returned until Oct 26, all of that day, and not on
 * Oct 27. Each line's days run from its own shipment's arrival.
 */
internal object ReturnWindow {
    const val DAYS: Long = 30

    /** The last day a return can be asked for a line that arrived on [arrived]. */
    fun lastDay(arrived: LocalDate): LocalDate = arrived.plusDays(DAYS)

    fun lastDay(arrived: Instant): LocalDate = lastDay(day(arrived))

    /** Whether a line that arrived at [arrived] can still be returned at [now]. */
    fun open(
        arrived: Instant,
        now: Instant,
    ): Boolean = !day(now).isAfter(lastDay(arrived))

    fun day(instant: Instant): LocalDate = instant.atZone(DeliveryCalendar.STORE).toLocalDate()
}

/**
 * What returning each line gives back: the items' part of what was paid — the promo code's discount shared
 * over the lines in proportion to their price paid, every share but the last rounded down and the last
 * taking the rest, so returning everything gives back the items to the cent. Delivery is not given back.
 *
 * The points taken back are the order's points in proportion to the refund, rounded down: 174 of #HL-44019's
 * 174 for all of it, 160 of #HL-46102's 206 for the $80.00 sweater (the canvas's «87 × 2 = 174»).
 */
internal object ReturnRefunds {
    /** Each line's refund, by its position in [order]. */
    fun of(order: Order): Map<Int, Int> {
        val lines = order.placed.lines
        val costs = lines.map { it.priceCents.toLong() * it.quantity }
        val cost = costs.sum()
        if (lines.isEmpty() || cost == 0L) return lines.indices.associateWith { 0 }
        // The order's items are at the list price and its discount is the sales' and the code's together
        // (feature-cart's Items and Discount), so what was paid for the items is the one less the other.
        val paid = (order.placed.itemsCents.toLong() - order.placed.discountCents).coerceAtLeast(0)
        val shares = costs.dropLast(1).map { (paid * it / cost).toInt() }
        return (shares + (paid - shares.sumOf { it.toLong() }).toInt()).withIndex().associate { it.index to it.value }
    }

    /** The points [refundCents] of [order] takes back. */
    fun points(
        order: Order,
        refundCents: Int,
    ): Int {
        val total = order.placed.totalCents
        if (total <= 0) return 0
        return (order.placed.points.toLong() * refundCents / total).toInt().coerceAtMost(order.placed.points)
    }
}

/** What a return can be refused with; the application answers each with its status. */
internal sealed class ReturnError(
    val code: ErrorCode,
    override val message: String,
    val field: String? = null,
    val fields: List<FieldError> = emptyList(),
) : Exception(message) {
    /** The form is not one that can be sent: every field at fault, the first of them as [field]. */
    class Invalid(
        fields: List<FieldError>,
    ) : ReturnError(
            ErrorCode.ValidationFailed,
            fields.firstOrNull()?.message ?: "The return form has errors",
            fields.firstOrNull()?.field,
            fields,
        )

    class AlreadyReturned(
        orderId: String,
    ) : ReturnError(ErrorCode.AlreadyReturned, "A return for #$orderId has already been requested")

    class NotDelivered : ReturnError(ErrorCode.NotDelivered, "Only what has arrived can be returned", "lines")

    /** «Late return» (feature-orders): a line asked for after [lastDay]. */
    class WindowClosed(
        lastDay: String,
    ) : ReturnError(ErrorCode.ReturnWindowClosed, "Returns for this order closed on $lastDay", "lines")
}
