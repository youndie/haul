package io.github.youndie.haul.feature.membership.domain

import io.github.youndie.haul.ErrorCode
import java.time.LocalDate
import java.time.OffsetDateTime

/** Where a membership stands on a day: on its free [Trial], or [Active] and «renewing» monthly. */
internal enum class PlusStatus(
    val id: String,
) {
    Trial("trial"),
    Active("active"),
}

/**
 * A customer's Haul Plus membership (feature-membership, research D6–D7), as stored: started at
 * [startedAt], billed from [paidFrom] at $4.99 a month — simulated, nothing is charged — and renewing on
 * that day of every month after it. A [trial] is free until [paidFrom], 30 days after it started.
 *
 * [carriedSavingsCents] is what it saved on delivery before the store kept orders, counted in
 * [carriedSavingsYear] only (the sample data's $186 for Maya); every other saving is the orders'.
 */
internal data class PlusMembership(
    val customerId: String,
    val startedAt: OffsetDateTime,
    val trial: Boolean,
    val paidFrom: LocalDate,
    val carriedSavingsCents: Int = 0,
    val carriedSavingsYear: Int? = null,
) {
    fun status(today: LocalDate): PlusStatus {
        val free = trial && today.isBefore(paidFrom)
        return if (free) PlusStatus.Trial else PlusStatus.Active
    }

    /**
     * The next renewal on or after [today]: [paidFrom] itself while the trial runs, then the same day of
     * each month after it — counted from [paidFrom] every time, so a membership paid from the 31st renews
     * on the last day of a shorter month and on the 31st again after it.
     */
    fun renews(today: LocalDate): LocalDate {
        var months = 0L
        while (paidFrom.plusMonths(months).isBefore(today)) months++
        return paidFrom.plusMonths(months)
    }

    /** What it saved before the store kept orders, if that was in [year]. */
    fun carriedSavings(year: Int): Int = if (carriedSavingsYear == year) carriedSavingsCents else 0

    companion object {
        /** How long a trial is free (feature-membership: «lasts 30 days»). */
        const val TRIAL_DAYS = 30L

        /** «then $4.99/month» — simulated, never charged. */
        const val MONTHLY_CENTS = 499

        /** The trial [customerId] starts at [at]: free for [TRIAL_DAYS] of the store's days, then «renewing». */
        fun trial(
            customerId: String,
            at: OffsetDateTime,
            today: LocalDate,
        ): PlusMembership = PlusMembership(customerId, at, trial = true, paidFrom = today.plusDays(TRIAL_DAYS))
    }
}

/** The memberships' storage. */
internal interface Memberships {
    suspend fun membership(customerId: String): PlusMembership?

    /**
     * Starts [membership] and makes its customer a member (`customers.plus`, which every price reads) in
     * one transaction; `false`, and nothing written, when the customer is a member already — two trials
     * started at once start one.
     */
    suspend fun start(membership: PlusMembership): Boolean
}

/** What feature-membership refuses with; the application answers each with its status and body. */
internal sealed class MembershipError(
    val code: ErrorCode,
    override val message: String,
) : Exception(message) {
    /** A trial for a member, on a trial or paying (feature-membership, «Already a member»). */
    class AlreadyMember : MembershipError(ErrorCode.AlreadyMember, "You are a Haul Plus member already")
}
