package io.github.youndie.haul.feature.returns.domain

import io.github.youndie.haul.feature.checkout.domain.PaymentMethod
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentPace
import io.github.youndie.haul.feature.membership.domain.PointsLedger
import io.github.youndie.haul.feature.membership.domain.PointsMovement
import io.github.youndie.haul.feature.order.domain.OrderRepository
import io.github.youndie.haul.feature.payment.domain.HaulPayPlans
import io.github.youndie.haul.feature.payment.domain.PaymentProcessor
import io.github.youndie.haul.feature.payment.domain.Refund
import io.github.youndie.haul.feature.payment.domain.RefundOutcome
import io.github.youndie.petich.PetichClock
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.toJavaDuration

private val log = LoggerFactory.getLogger("io.github.youndie.haul.returns")

/**
 * The simulated world's side of a return (feature-orders: «the simulator picks it up and refunds»): each
 * return asked for is collected by the courier [FulfilmentPace.returnPickup] later and refunded
 * [FulfilmentPace.returnRefund] after that, at [pace], by [clock] — the fulfilment simulator's, so a step is
 * stamped when it came due and a pass after a pause catches up, as a shipment's do.
 *
 * **The refund comes before the move to `refunded`**, named by the order (`refund:<order>`): a pass that died
 * between the two leaves the return `picked_up` with its money given back; the next pass asks again, is
 * answered with what was given, and moves it. A refund the processor refuses holds the return where it is,
 * said at `error` — a return never reads «refunded» without the money. Pay on delivery was paid to the
 * courier and is paid back by the courier: nothing goes through the processor. A Haul Pay order's refund
 * comes off the payments its plan still owes first (B-24, [HaulPayPlans.refund]), the last one first; only
 * what is left is given back through the processor, out of the payments taken.
 *
 * **The points move with the refund** (B-23), before the move as well and each once by its key: the points the
 * returned lines earned are taken back (`reversed:<order>`, the return's [OrderReturn.points]), and the points
 * the order was paid with come back as the returned lines' shares of them ([ReturnRefunds.pointsBack],
 * `returned:<order>` — kept apart from the cancellation's give-back, which an order that was delivered never
 * had); that part of the refund is not paid in money, and the rest is what the return dialog said (B-50).
 *
 * Every move is conditional on the status it leaves, so two passes at once move a return once.
 */
internal class ReturnSimulator(
    private val returns: ReturnRepository,
    private val orders: OrderRepository,
    private val payments: PaymentProcessor,
    private val plans: HaulPayPlans,
    private val points: PointsLedger,
    private val clock: PetichClock,
    private val pace: FulfilmentPace,
) {
    /** One pass; the number of moves it made. */
    suspend fun advance(): Int {
        val now = Instant.ofEpochMilli(clock.nowEpochMs())
        return returns.active().sumOf { carry(it, now) }
    }

    private suspend fun carry(
        orderReturn: OrderReturn,
        now: Instant,
    ): Int {
        var status = orderReturn.status
        var since = orderReturn.history.getValue(status)
        var moves = 0
        while (true) {
            val (to, after) =
                when (status) {
                    ReturnStatus.REQUESTED -> ReturnStatus.PICKED_UP to pace.returnPickup
                    ReturnStatus.PICKED_UP -> ReturnStatus.REFUNDED to pace.returnRefund
                    else -> break
                }
            val due = since + after.toJavaDuration()
            if (due.isAfter(now)) break
            if (to == ReturnStatus.REFUNDED && !refunded(orderReturn, due)) break
            if (to == ReturnStatus.REFUNDED) settlePoints(orderReturn, due)
            if (!returns.move(orderReturn.orderId, status, to, due)) break
            status = to
            since = due
            moves++
        }
        return moves
    }

    /** The points [orderReturn] takes back and gives back, as of [at]; each written once. */
    private suspend fun settlePoints(
        orderReturn: OrderReturn,
        at: Instant,
    ) {
        val order = orders.order(orderReturn.orderId) ?: error("the return of ${orderReturn.orderId} has no order")
        val customer = order.placed.customerId
        val stamp = at.atOffset(ZoneOffset.UTC)
        if (orderReturn.points > 0) {
            points.record(PointsMovement.reversed(customer, order.id, order.id, orderReturn.points, stamp))
        }
        val back = ReturnRefunds.pointsBack(order, orderReturn.lines.map { it.position })
        if (back >
            0
        ) {
            points.record(PointsMovement.returned(customer, order.id, back, stamp, returnId = "return-${order.id}"))
        }
    }

    /** Whether [orderReturn]'s refund was given back — now, or by an earlier pass — or goes back another way. */
    private suspend fun refunded(
        orderReturn: OrderReturn,
        at: Instant,
    ): Boolean {
        val order = orders.order(orderReturn.orderId) ?: error("the return of ${orderReturn.orderId} has no order")
        // What the order was paid with in points comes back as points, not money — the returned lines' shares,
        // the numbers the return dialog added up (B-50).
        val money = orderReturn.refundCents - ReturnRefunds.pointsBack(order, orderReturn.lines.map { it.position })
        if (money <= 0) return true
        val method =
            PaymentMethod.byId(order.placed.payment)
                ?: error("the order ${order.id} pays with «${order.placed.payment}»")
        if (!method.card) return true
        val given =
            if (method == PaymentMethod.HaulPayPlan) plans.refund(order.id, money, at).refundCents else money
        if (given <= 0) return true
        val key = refundKey(order.id)
        return when (val outcome = payments.refund(key, Refund(order.id, given, at))) {
            is RefundOutcome.Refunded -> {
                true
            }

            else -> {
                log.error(
                    "return of {} is held: refunding {} cents was refused: {}",
                    order.id,
                    given,
                    outcome,
                )
                false
            }
        }
    }

    companion object {
        /** The processor's key of an order's refund: one return per order, so one refund. */
        fun refundKey(orderId: String): String = "refund:$orderId"
    }
}
