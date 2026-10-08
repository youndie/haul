package io.github.youndie.haul.feature.returns.domain

import io.github.youndie.haul.feature.checkout.domain.PaymentMethod
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentPace
import io.github.youndie.haul.feature.order.domain.OrderRepository
import io.github.youndie.haul.feature.payment.domain.PaymentProcessor
import io.github.youndie.haul.feature.payment.domain.Refund
import io.github.youndie.haul.feature.payment.domain.RefundOutcome
import io.github.youndie.petich.PetichClock
import org.slf4j.LoggerFactory
import java.time.Instant
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
 * courier and is paid back by the courier: nothing goes through the processor.
 *
 * Every move is conditional on the status it leaves, so two passes at once move a return once.
 */
internal class ReturnSimulator(
    private val returns: ReturnRepository,
    private val orders: OrderRepository,
    private val payments: PaymentProcessor,
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
            if (!returns.move(orderReturn.orderId, status, to, due)) break
            status = to
            since = due
            moves++
        }
        return moves
    }

    /** Whether [orderReturn]'s refund was given back — now, or by an earlier pass — or goes back another way. */
    private suspend fun refunded(
        orderReturn: OrderReturn,
        at: Instant,
    ): Boolean {
        if (orderReturn.refundCents == 0) return true
        val order = orders.order(orderReturn.orderId) ?: error("the return of ${orderReturn.orderId} has no order")
        val method =
            PaymentMethod.byId(order.placed.payment)
                ?: error("the order ${order.id} pays with «${order.placed.payment}»")
        if (!method.card) return true
        val key = refundKey(order.id)
        return when (val outcome = payments.refund(key, Refund(order.id, orderReturn.refundCents, at))) {
            is RefundOutcome.Refunded -> {
                true
            }

            else -> {
                log.error(
                    "return of {} is held: refunding {} cents was refused: {}",
                    order.id,
                    orderReturn.refundCents,
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
