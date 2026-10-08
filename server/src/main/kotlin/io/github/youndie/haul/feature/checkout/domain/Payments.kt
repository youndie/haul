package io.github.youndie.haul.feature.checkout.domain

import io.github.youndie.haul.feature.catalog.domain.HaulPay
import io.github.youndie.haul.feature.catalog.domain.money
import io.github.youndie.haul.feature.checkout.DeliveryMethod

/**
 * A way to pay (research §5, `PaymentMethod`: `card`, `haul_pay`, `pay_on_delivery`).
 *
 * v1 has no way to add a card — no artboard draws one, and research D6 hides the account's payment
 * methods — so every customer is offered the payment simulator's two cards (research §6): ···· 4821,
 * which it approves, and the test card ···· 0002, which it declines (B-16). [card] is whether the
 * money moves at shipping («Your card is charged when the order ships»).
 */
internal enum class PaymentMethod(
    val id: String,
    val label: String,
    val card: Boolean,
) {
    Card("card-4821", "Card ···· 4821", card = true),
    TestCard("card-0002", "Card ···· 0002", card = true),
    HaulPayPlan("haul_pay", "Haul Pay", card = true),
    OnDelivery("pay_on_delivery", "Pay on delivery", card = false),
    ;

    /** What the option says under its label for an order of [totalCents]. */
    fun detail(totalCents: Int): String? =
        when (this) {
            Card -> "Expires 08/28"
            TestCard -> "Test card: always declined"
            HaulPayPlan -> "${HaulPay.PAYMENTS} payments of ${money(HaulPay.paymentCents(totalCents))}"
            OnDelivery -> null
        }

    /**
     * Whether this way to pay is offered for [method] and [totalCents] (feature-checkout): pay on
     * delivery is not offered for parcel lockers, Haul Pay only for totals from $50 to $2,000.
     */
    fun allowed(
        method: DeliveryMethod,
        totalCents: Int,
    ): Boolean =
        when (this) {
            OnDelivery -> method != DeliveryMethod.ParcelLocker
            HaulPayPlan -> HaulPay.offered(totalCents)
            Card, TestCard -> true
        }

    /** Why [allowed] says no — what the shopper is told when they choose it anyway. */
    fun refusal(): String =
        when (this) {
            OnDelivery -> "Pay on delivery is not offered for a parcel locker"
            HaulPayPlan -> "Haul Pay is offered for orders from $50 to $2,000"
            Card, TestCard -> "This way to pay is not offered"
        }

    companion object {
        /** What a checkout pays with until the shopper chooses. */
        val DEFAULT: PaymentMethod = Card

        fun byId(id: String): PaymentMethod? = entries.firstOrNull { it.id == id }
    }
}
