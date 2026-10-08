package io.github.youndie.haul.feature.checkout.domain

import io.github.youndie.haul.FieldError
import io.github.youndie.haul.feature.cart.domain.PricedLine
import io.github.youndie.haul.feature.cart.domain.PromoCode
import io.github.youndie.haul.feature.cart.domain.Totals
import io.github.youndie.haul.feature.checkout.AddressEntry
import io.github.youndie.haul.feature.checkout.DeliveryMethod
import java.security.MessageDigest

/**
 * What an order would be placed for, computed in one place (`CheckoutCommands.state`) for the tree
 * and for placement alike (B-16): the cart's counted lines — selected, in stock, unchanged — with the
 * cart's [Totals], the promo code that still counts, the method, the address or the point, the window
 * and the way to pay.
 *
 * [complete] is whether it can be placed: courier needs an address and a window, a pickup point or a
 * locker needs the point. [fingerprint] names every value above; the tree hands it to the client, and
 * placement places only a quote whose fingerprint is still the one the shopper saw.
 */
internal data class Quote(
    val lines: List<PricedLine>,
    val promo: PromoCode?,
    val totals: Totals,
    val plus: Boolean,
    val method: DeliveryMethod,
    val address: Address?,
    val point: PickupPoint?,
    val slot: Slot?,
    val payment: PaymentMethod,
) {
    val complete: Boolean
        get() =
            when (method) {
                DeliveryMethod.Courier -> address != null && slot != null
                DeliveryMethod.PickupPoint, DeliveryMethod.ParcelLocker -> point != null
            }

    val fingerprint: String
        get() {
            val canonical =
                buildString {
                    lines.forEach { append("line ${it.sku.id} ${it.stored.quantity} ${it.sku.priceCents}\n") }
                    append("promo ${promo?.code}\n")
                    append("method $method\n")
                    append("address ${address?.id}\n")
                    append("point ${point?.id}\n")
                    append("slot ${slot?.id}\n")
                    append("payment ${payment.id}\n")
                    append("total ${totals.totalCents}\n")
                }
            val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
            return digest.take(FINGERPRINT_BYTES).joinToString("") { "%02x".format(it) }
        }

    private companion object {
        const val FINGERPRINT_BYTES = 16
    }
}

/**
 * Everything the Checkout tree draws: the [quote] and what the shopper can change it to.
 *
 * [slotFilled] is the window they had chosen filling up since (`Checkout_PlaceError`: the window is
 * cleared and they are told); [expiredPromo] is the cart's code that expired after it was applied and
 * no longer counts (endpoint-cart's quirk: checkout must not honour it). [draft] is the last address
 * form refused, with its [draftProblems].
 */
internal data class CheckoutState(
    val quote: Quote,
    val addresses: List<Address>,
    val points: List<PickupPoint>,
    val slots: List<SlotLoad>,
    val payments: List<PaymentMethod>,
    val slotFilled: Boolean,
    val expiredPromo: String?,
    val draft: AddressEntry?,
    val draftProblems: List<FieldError>,
)
