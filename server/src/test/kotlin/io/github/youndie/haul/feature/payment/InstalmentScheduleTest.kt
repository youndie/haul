package io.github.youndie.haul.feature.payment

import io.github.youndie.haul.feature.catalog.domain.HaulPay
import io.github.youndie.haul.feature.payment.domain.InstalmentSchedule
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

/**
 * The schedule's arithmetic (B-24): four payments of the amount the checkout promised, the last taking what
 * rounding left, so the plan charges the total to the cent and never a cent more; the first on the day the plan
 * starts, each next one two weeks later.
 */
class InstalmentScheduleTest {
    /** Every payment but the last is the checkout's «4 payments of $X»; the four add up to the total. */
    @Test
    fun `the last payment takes what rounding left`() {
        for (total in listOf(5_000, 10_001, 10_002, 10_003, 48_720, 51_200, 199_999, 200_000)) {
            val amounts = InstalmentSchedule.amounts(total)
            assertEquals(4, amounts.size)
            assertEquals(total, amounts.sum(), "the payments of $total add up to it")
            assertEquals(
                List(3) { HaulPay.paymentCents(total) },
                amounts.dropLast(1),
                "the checkout's amount for $total",
            )
        }
        assertEquals(listOf(2_501, 2_501, 2_501, 2_500), InstalmentSchedule.amounts(10_003))
        assertEquals(listOf(2_500, 2_500, 2_500, 2_501), InstalmentSchedule.amounts(10_001))
    }

    /** Two weeks apart from the start, not from one another's payment. */
    @Test
    fun `the payments are due two weeks apart from the start`() {
        val start = Instant.parse("2025-10-08T23:47:23Z")
        assertEquals(
            listOf("2025-10-08T23:47:23Z", "2025-10-22T23:47:23Z", "2025-11-05T23:47:23Z", "2025-11-19T23:47:23Z"),
            InstalmentSchedule.of(51_200, start, 14.days).map { it.dueAt.toString() },
        )
    }
}
