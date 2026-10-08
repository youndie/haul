package io.github.youndie.haul.feature.membership

import io.github.youndie.haul.feature.membership.domain.PlusMembership
import io.github.youndie.haul.feature.membership.domain.PlusStatus
import io.github.youndie.haul.seed.CatalogSeed
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** A membership's dates (feature-membership): a trial's 30 free days, then a renewal on the same day each month. */
class PlusMembershipTest {
    private fun day(text: String): LocalDate = LocalDate.parse(text)

    @Test
    fun `a trial is free for 30 days and renews monthly after it`() {
        val trial = PlusMembership.trial("sam", CatalogSeed.NOW, day("2025-10-07"))
        assertEquals(day("2025-11-06"), trial.paidFrom)
        assertEquals(PlusStatus.Trial, trial.status(day("2025-11-05")))
        assertEquals(PlusStatus.Active, trial.status(day("2025-11-06")))
        assertEquals(day("2025-11-06"), trial.renews(day("2025-10-07")))
        assertEquals(day("2025-11-06"), trial.renews(day("2025-11-06")))
        assertEquals(day("2025-12-06"), trial.renews(day("2025-11-07")))
    }

    /** Maya's «Plus since 2023, renews Nov 2» (research §6) on the canvas's day, and a month's end kept. */
    @Test
    fun `a renewal falls on the day it was paid from`() {
        val maya = PlusMembership("maya", CatalogSeed.NOW.minusYears(2), trial = true, paidFrom = day("2023-11-02"))
        assertEquals(day("2025-11-02"), maya.renews(day("2025-10-07")))
        val endOfMonth = PlusMembership("x", CatalogSeed.NOW, trial = false, paidFrom = day("2025-01-31"))
        assertEquals(day("2025-02-28"), endOfMonth.renews(day("2025-02-01")))
        assertEquals(day("2025-03-31"), endOfMonth.renews(day("2025-03-01")))
    }
}
