package io.github.youndie.haul.feature.checkout.domain

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * A courier's delivery window (research §5, `DeliverySlot`): a store day and the hour the window
 * starts. Its [id] — `2025-10-08T15` — is what the tree carries and the checkout stores.
 */
internal data class Slot(
    val day: LocalDate,
    val startHour: Int,
) {
    val id: String get() = "${day}T" + "%02d".format(startHour)

    /** «15:00–18:00». */
    val label: String get() = "%02d:00–%02d:00".format(startHour, startHour + LENGTH_HOURS)

    companion object {
        /** Four windows a day, three hours each (feature-checkout); the hours are a decision of B-14. */
        val WINDOWS: List<Int> = listOf(9, 12, 15, 18)
        const val LENGTH_HOURS = 3

        /** «The next 5 days from tomorrow» (feature-checkout). */
        const val DAYS = 5

        /**
         * How many orders one window takes. A decision of B-14, not an observation: nothing on the
         * canvas or in the brief names a number.
         */
        const val CAPACITY = 20

        /** The windows offered when the earliest courier day is [first]: five days of four windows. */
        fun offered(first: LocalDate): List<Slot> =
            (0 until DAYS).flatMap { offset -> WINDOWS.map { Slot(first.plusDays(offset.toLong()), it) } }

        /** The slot an [id] names, or `null` for one that is not a window's id at all. */
        fun parse(id: String): Slot? {
            val day = id.substringBefore('T', missingDelimiterValue = "")
            val hour = id.substringAfter('T', missingDelimiterValue = "").toIntOrNull() ?: return null
            if (hour !in WINDOWS || id != "${day}T" + "%02d".format(hour)) return null
            return try {
                Slot(LocalDate.parse(day), hour)
            } catch (_: DateTimeParseException) {
                null
            }
        }

        /** «Wed 8». */
        fun dayLabel(day: LocalDate): String = DAY.format(day)

        private val DAY = DateTimeFormatter.ofPattern("EEE d", Locale.US)
    }
}

/** A window with what it holds: [taken] of [capacity] places. */
internal data class SlotLoad(
    val slot: Slot,
    val capacity: Int,
    val taken: Int,
) {
    val full: Boolean get() = taken >= capacity
}

/**
 * The windows' capacity (research §5: «slots … with capacity»). A window is stored from the first
 * place taken in it; one never written has [Slot.CAPACITY] places and none taken.
 *
 * A place is taken at placement, not at quote time (feature-checkout, «Slot filled meanwhile»: the
 * window can fill between the page and the order): [reserve] is placement's step (B-16), and its
 * compensation is [release].
 */
internal interface DeliverySlots {
    /** The load of each of [slots], in their order. */
    suspend fun loads(slots: List<Slot>): List<SlotLoad>

    /**
     * Takes one place in [slot] for [holder] (an order) if one is left, atomically: of any number of
     * holders racing for the last place, exactly one is answered `true`. A holder that already has a
     * place in the window is answered `true` again and takes no second one.
     */
    suspend fun reserve(
        slot: Slot,
        holder: String,
        at: OffsetDateTime,
    ): Boolean

    /** Gives [holder]'s place in [slot] back; `false` when it had none. */
    suspend fun release(
        slot: Slot,
        holder: String,
    ): Boolean
}
