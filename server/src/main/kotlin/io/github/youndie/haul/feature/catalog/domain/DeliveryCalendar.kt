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
 *
 * It is the one calendar of every page (B-98): a card, the product page, the cart, the checkout's
 * windows and the order page all read their days from here, so a product promised for Thursday is
 * offered from Thursday at checkout and tracked to Thursday on its order.
 *
 * A day is promised only for what can be bought (B-120): a card and the product page ask [promised], which
 * has none for a SKU with nothing in stock, since there is no restock date to promise from.
 */
internal class DeliveryCalendar(
    private val now: () -> ZonedDateTime,
) {
    private fun today(): LocalDate = now().withZoneSameInstant(STORE).toLocalDate()

    private fun beforeCutoff(): Boolean = now().withZoneSameInstant(STORE).toLocalTime().isBefore(CUTOFF)

    fun courier(item: Listed): LocalDate = courierDay(now(), item.product.dispatchDays)

    /**
     * The first day a courier comes for any of [items] — the earliest of their days, the day after an order
     * placed now when there are none. Checkout offers its windows from it; a slower seller's shipment comes on
     * its own day, as the cart names it per seller and the order page per shipment (B-98).
     */
    fun courier(items: Collection<Listed>): LocalDate = items.minOfOrNull(::courier) ?: courierDay(now(), 0)

    /**
     * The courier's day a card or the product page promises for [sku] of [item], or `null` when [sku] has
     * nothing in stock: nothing can be bought, so nothing arrives (B-120). A card asks for the SKU it shows —
     * out of stock only when every SKU is — and the product page for the SKU chosen.
     */
    fun promised(
        item: Listed,
        sku: Sku,
    ): LocalDate? = if (sku.stock > 0) courier(item) else null

    /** Whether a card of [item] promises tomorrow: the «Delivery: Tomorrow» facet, which keeps none out of stock. */
    fun isTomorrow(item: Listed): Boolean = promised(item, item.shown) == today().plusDays(1)

    /** «Tomorrow», or «Thu, Oct 9». */
    fun label(date: LocalDate): String = if (date == today().plusDays(1)) "Tomorrow" else DAY.format(date)

    /** «Tomorrow, Oct 8», or «Thu, Oct 9»: a day that keeps its date when it is tomorrow (checkout's methods). */
    fun dated(date: LocalDate): String =
        if (date == today().plusDays(1)) "Tomorrow, " + MONTH_DAY.format(date) else DAY.format(date)

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
        private val MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US)

        /**
         * The courier's day for an order placed [at] from a seller that dispatches in [dispatchDays]: the day
         * after, plus the dispatch days, one more after the cut-off (D7). The order page reads an order's days
         * from the moment it was placed, the other pages from now.
         */
        fun courierDay(
            at: ZonedDateTime,
            dispatchDays: Int,
        ): LocalDate {
            val local = at.withZoneSameInstant(STORE)
            val late = if (local.toLocalTime().isBefore(CUTOFF)) 0L else 1L
            return local.toLocalDate().plusDays(1L + dispatchDays + late)
        }

        /** A pickup point or a locker has it a day after the courier would bring it (D7). */
        fun pickupDay(courier: LocalDate): LocalDate = courier.plusDays(1)
    }
}
