package io.github.youndie.haul.feature.catalog.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Delivery days, from one «now» given at the composition root (a handler never reads the clock).
 *
 * The rules are research D7's: a courier delivers the day after an order placed before the 23:30
 * cut-off, plus the seller's dispatch days; after the cut-off, one day later; a pickup point or a
 * locker one day after the courier. Days are the store's, New York's.
 */
internal class DeliveryCalendar(
    private val now: () -> ZonedDateTime,
) {
    private fun today(): LocalDate = now().withZoneSameInstant(STORE).toLocalDate()

    private fun beforeCutoff(): Boolean = now().withZoneSameInstant(STORE).toLocalTime().isBefore(CUTOFF)

    fun courier(item: Listed): LocalDate =
        today().plusDays(1L + item.product.dispatchDays + if (beforeCutoff()) 0 else 1)

    fun pickup(item: Listed): LocalDate = courier(item).plusDays(1)

    fun isTomorrow(item: Listed): Boolean = courier(item) == today().plusDays(1)

    /** «Tomorrow», or «Thu, Oct 9». */
    fun label(date: LocalDate): String = if (date == today().plusDays(1)) "Tomorrow" else DAY.format(date)

    /** «Order within 3 h 42 min» while the cut-off is ahead today; `null` after it. */
    fun cutoffLabel(): String? {
        val local = now().withZoneSameInstant(STORE)
        if (!beforeCutoff()) return null
        val left = Duration.between(local.toLocalTime(), CUTOFF)
        return "Order within ${left.toHours()} h ${left.toMinutesPart()} min"
    }

    companion object {
        val STORE: ZoneId = ZoneId.of("America/New_York")
        val CUTOFF: LocalTime = LocalTime.of(23, 30)
        private val DAY = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
    }
}
