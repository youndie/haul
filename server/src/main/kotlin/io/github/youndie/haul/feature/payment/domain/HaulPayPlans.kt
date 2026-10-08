package io.github.youndie.haul.feature.payment.domain

import io.github.youndie.haul.feature.order.domain.Order
import io.github.youndie.petich.PetichClock
import org.slf4j.LoggerFactory
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toJavaDuration

private val log = LoggerFactory.getLogger("io.github.youndie.haul.payment")

/**
 * Haul Pay's four payments (B-24, research D6), on the simulator's clock. Placement authorises the whole total
 * as for a card (B-16); the plan is what is charged out of that authorisation, in place of each shipment's
 * share (B-17) — the shopper pays the plan, not the shipments.
 *
 * **The plan starts when the first shipment ships** ([ship], called by the fulfilment simulator before the
 * move to `in_transit`): the first payment is taken then, and no Haul Pay shipment is on the road before it
 * is — as no card shipment is before its capture. The other three are due [interval] apart after it and are
 * taken by [advance], the runner's pass.
 *
 * **Each payment is charged once**, by its key ([InstalmentSchedule.key]): it is claimed first (what it
 * charges is frozen then), charged, then marked paid — a pass that died between any two of those leaves the
 * payment claimed, and the next pass asks the processor again under the same key and is answered with what
 * was taken. Every attempt is stamped when it came due, so a pass after a pause catches up as the shipments'
 * does.
 *
 * **A declined payment is tried again [retry] later, once; declined again, it is overdue** and the plan stops
 * there (feature-membership: «a declined instalment is retried once and then marks the plan overdue»).
 * Collections are not covered: nothing chases an overdue plan.
 *
 * **A return takes its refund off what is still owed first** ([refund]): the last payment first, then the one
 * before it; only what is left over is given back through the processor, out of what was paid.
 */
internal class HaulPayPlans(
    private val plans: InstalmentRepository,
    private val payments: PaymentProcessor,
    private val clock: PetichClock,
    private val interval: Duration,
    private val retry: Duration,
) {
    init {
        require(interval.isPositive() && retry.isPositive()) { "a plan's payments are some time apart: $interval, $retry" }
    }

    /** One pass over the plans with a payment due by now; the number of payments it took or gave up on. */
    suspend fun advance(): Int {
        val now = Instant.ofEpochMilli(clock.nowEpochMs())
        return plans.due(now).sumOf { collect(it, now) }
    }

    /**
     * [order]'s plan started at [at] — written now, or found written by an earlier pass — with every payment due
     * by [now] taken; whether its first payment is taken, which is what its shipments wait for.
     */
    suspend fun ship(
        order: Order,
        at: Instant,
        now: Instant,
    ): Boolean {
        plans.start(order.id, order.placed.totalCents, at, InstalmentSchedule.of(order.placed.totalCents, at, interval))
        collect(order.id, now)
        return plans.plan(order.id)?.firstPaid == true
    }

    /** [orderId]'s plan as stored, or `null` before it started. */
    suspend fun plan(orderId: String): InstalmentPlan? = plans.plan(orderId)

    /**
     * The money part of a return's refund of [orderId], [cents], as of [at]: what it takes off the payments still
     * owed, and what is left to give back through the processor out of what was paid. Asked again, it answers the
     * same split and takes nothing more.
     */
    suspend fun refund(
        orderId: String,
        cents: Int,
        at: Instant,
    ): RefundSplit {
        val reduced = plans.reduce(orderId, cents, at)
        return RefundSplit(reducedCents = reduced, refundCents = cents - reduced)
    }

    /** Takes every payment of [orderId] due by [now], in order, stopping at the first that is not; the number of moves. */
    private suspend fun collect(
        orderId: String,
        now: Instant,
    ): Int {
        var moves = 0
        // Four payments, each tried twice at most, and one look past the last.
        repeat(MAX_STEPS) {
            val plan = plans.plan(orderId) ?: return moves
            val instalment = plan.next ?: return moves
            val attemptAt = instalment.nextAttemptAt ?: return moves
            val charge =
                when (instalment.status) {
                    InstalmentStatus.OVERDUE -> {
                        return moves
                    }

                    InstalmentStatus.COLLECTING -> {
                        instalment.chargeCents ?: error("payment ${instalment.number} of $orderId is claimed with no charge")
                    }

                    else -> {
                        if (attemptAt.isAfter(now)) return moves
                        plans.claim(orderId, instalment.number, instalment.declined) ?: return moves
                    }
                }
            if (charge == 0) {
                moves++
                return@repeat
            }
            val key = InstalmentSchedule.key(orderId, instalment.number)
            when (val outcome = payments.capture(key, Capture(orderId, charge, attemptAt))) {
                is CaptureOutcome.Captured -> {
                    if (!plans.paid(orderId, instalment.number, attemptAt)) return moves
                }

                else -> {
                    val retryAt = if (instalment.declined == 0) attemptAt + retry.toJavaDuration() else null
                    log.error(
                        "Haul Pay payment {} of {} ({} cents) was declined: {}; {}",
                        instalment.number,
                        orderId,
                        charge,
                        outcome,
                        retryAt?.let { "tried again at $it" } ?: "the plan is overdue",
                    )
                    if (!plans.declined(orderId, instalment.number, instalment.declined, attemptAt, retryAt)) {
                        return moves
                    }
                }
            }
            moves++
        }
        return moves
    }

    private companion object {
        const val MAX_STEPS = 9
    }
}

/** A return's money refund on a Haul Pay order: [reducedCents] off the payments still owed, [refundCents] given back. */
internal data class RefundSplit(
    val reducedCents: Int,
    val refundCents: Int,
)
