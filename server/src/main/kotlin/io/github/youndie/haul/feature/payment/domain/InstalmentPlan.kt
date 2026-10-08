package io.github.youndie.haul.feature.payment.domain

import io.github.youndie.haul.feature.catalog.domain.HaulPay
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Where one payment of a Haul Pay plan is (B-24): [SCHEDULED] while it is owed, [COLLECTING] while a pass is
 * charging it, [PAID] once charged, [COVERED] when a return took all of it off, [OVERDUE] once it was
 * declined twice — where the plan stops, since v1 has no collections (feature-membership).
 */
internal object InstalmentStatus {
    const val SCHEDULED = "scheduled"
    const val COLLECTING = "collecting"
    const val PAID = "paid"
    const val COVERED = "covered"
    const val OVERDUE = "overdue"

    val ALL: List<String> = listOf(SCHEDULED, COLLECTING, PAID, COVERED, OVERDUE)

    /** The statuses that owe nothing more. */
    val SETTLED: Set<String> = setOf(PAID, COVERED)
}

/**
 * One payment of a plan: its [number] (1 to 4), the [amountCents] the schedule gave it less what a return
 * took off ([reducedCents]), when it is [dueAt], where it is ([status]), how many attempts were [declined]
 * and when the next one is ([nextAttemptAt]), what a pass froze to charge for it ([chargeCents]) and when it
 * was [paidAt]. [dueAt] and [nextAttemptAt] are `null` only in a plan that has not started.
 */
internal data class Instalment(
    val number: Int,
    val amountCents: Int,
    val reducedCents: Int = 0,
    val dueAt: Instant?,
    val status: String = InstalmentStatus.SCHEDULED,
    val declined: Int = 0,
    val nextAttemptAt: Instant? = dueAt,
    val chargeCents: Int? = null,
    val paidAt: Instant? = null,
    val declinedAt: Instant? = null,
) {
    /** What it charges, once a return has taken its part off. */
    val owedCents: Int get() = amountCents - reducedCents
}

/**
 * A Haul Pay plan (research D6: «4 interest-free payments two weeks apart», research §5 `InstalmentPlan`): the
 * order's [totalCents] — after the points it was paid with (B-23) — in four [instalments]. A plan that
 * [started] is stored; one that has not is the schedule the total gives ([InstalmentSchedule.projected]), with
 * no dates yet. [reducedCents] is what the order's return took off the payments still owed, `null` until then.
 */
internal data class InstalmentPlan(
    val orderId: String,
    val totalCents: Int,
    val startedAt: Instant?,
    val instalments: List<Instalment>,
    val reducedCents: Int? = null,
) {
    val started: Boolean get() = startedAt != null

    /** Declined twice: the plan stops at that payment (no collections in v1). */
    val overdue: Boolean get() = instalments.any { it.status == InstalmentStatus.OVERDUE }

    val paidCents: Int get() = instalments.filter { it.status == InstalmentStatus.PAID }.sumOf { it.chargeCents ?: 0 }

    /** The first payment still owed, if any. */
    val next: Instalment? get() = instalments.firstOrNull { it.status !in InstalmentStatus.SETTLED }

    /** Whether the first payment is taken — what a Haul Pay shipment waits for before it ships. */
    val firstPaid: Boolean get() = instalments.firstOrNull()?.status in InstalmentStatus.SETTLED
}

/**
 * The schedule (research D6, feature-checkout: «4 equal payments … two weeks apart, the first when the first
 * shipment ships»): the total in [HaulPay.PAYMENTS] payments of the amount the checkout promised
 * ([HaulPay.paymentCents], «4 payments of $121.80»), the last taking what rounding left, so they add up to the
 * total to the cent; the first due when the plan starts, each next one [interval] later.
 */
internal object InstalmentSchedule {
    fun amounts(totalCents: Int): List<Int> {
        require(totalCents > 0) { "a plan pays a positive total, not $totalCents cents" }
        val payment = HaulPay.paymentCents(totalCents)
        val first = List(HaulPay.PAYMENTS - 1) { payment }
        return first + (totalCents - first.sum())
    }

    /** The instalments of a plan of [totalCents] started at [startedAt]. */
    fun of(
        totalCents: Int,
        startedAt: Instant,
        interval: Duration,
    ): List<Instalment> =
        amounts(totalCents).mapIndexed { index, amount ->
            Instalment(
                number = index + 1,
                amountCents = amount,
                dueAt = startedAt + (interval * index).toJavaDuration(),
            )
        }

    /** The plan [orderId] will have, before its first shipment ships: the amounts, no dates. */
    fun projected(
        orderId: String,
        totalCents: Int,
    ): InstalmentPlan =
        InstalmentPlan(
            orderId = orderId,
            totalCents = totalCents,
            startedAt = null,
            instalments =
                amounts(totalCents).mapIndexed {
                    index,
                    amount,
                    ->
                    Instalment(index + 1, amount, dueAt = null)
                },
        )

    /** The processor's key of [orderId]'s payment [number]: a payment is charged once, however often it is asked. */
    fun key(
        orderId: String,
        number: Int,
    ): String = "instalment:$orderId:$number"
}

/**
 * The plans' storage. Every write is conditional on what it leaves, so two simulator passes at once — two
 * processes in a rolling deploy — move each payment once.
 */
internal interface InstalmentRepository {
    /**
     * Writes [orderId]'s plan of [totalCents], started at [startedAt] with [instalments]; a plan written already
     * is left as it was. Answers the plan as stored.
     */
    suspend fun start(
        orderId: String,
        totalCents: Int,
        startedAt: Instant,
        instalments: List<Instalment>,
    ): InstalmentPlan

    suspend fun plan(orderId: String): InstalmentPlan?

    /** The orders with a payment to take by [now]: one scheduled and due in a plan that is not overdue, or one claimed. */
    suspend fun due(now: Instant): List<String>

    /**
     * Claims payment [number] for a charge: `scheduled` with [declined] declines → `collecting`, freezing what it
     * owes as what it charges — or `covered`, when it owes nothing. Answers the charge (0 when covered); `null`
     * when the payment is not where the caller saw it any more and nothing was written.
     */
    suspend fun claim(
        orderId: String,
        number: Int,
        declined: Int,
    ): Int?

    /** `collecting` → `paid` at [at]; `false` when another pass got there first. */
    suspend fun paid(
        orderId: String,
        number: Int,
        at: Instant,
    ): Boolean

    /**
     * A declined attempt at [at]: `collecting` with [declined] declines → `scheduled` again, to be tried at
     * [retryAt], or `overdue` when [retryAt] is `null`. `false` when another pass got there first.
     */
    suspend fun declined(
        orderId: String,
        number: Int,
        declined: Int,
        at: Instant,
        retryAt: Instant?,
    ): Boolean

    /**
     * Takes up to [cents] off the payments [orderId] still owes — the last one first — once per plan, at [at], and
     * answers how much it took; asked again, it answers what it took the first time and takes nothing more. A
     * payment being charged or charged already is not reduced: what was paid is given back by a refund.
     */
    suspend fun reduce(
        orderId: String,
        cents: Int,
        at: Instant,
    ): Int
}
