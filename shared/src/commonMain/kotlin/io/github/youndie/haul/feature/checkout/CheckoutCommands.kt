package io.github.youndie.haul.feature.checkout

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The bodies of checkout's commands (endpoint-checkout, endpoint-identity). Where each one is sent is
// the server's string, carried by the Checkout tree's components (`DeliveryMethods.url`,
// `CheckoutAddress.url`, …); every command answers kompot's `refresh`, which redraws the checkout with
// a new quote, or an `ErrorBody`.

/** How an order is received (research §5, `DeliveryMethod`). */
@Serializable
public enum class DeliveryMethod {
    @SerialName("courier")
    Courier,

    @SerialName("pickup_point")
    PickupPoint,

    @SerialName("parcel_locker")
    ParcelLocker,
}

/**
 * A change to what the checkout is quoted for, `PUT` to the url its component carries: only the
 * fields given change, and at least one is required. [addressId], [pointId], [slotId] and [payment]
 * are the ids the tree's options carry.
 */
@Serializable
public data class CheckoutChoice(
    val method: DeliveryMethod? = null,
    val addressId: String? = null,
    val pointId: String? = null,
    val slotId: String? = null,
    val payment: String? = null,
)

/**
 * The address form, `POST` to `CheckoutAddress.url`. [street], [city] and [zip] are required, the ZIP
 * five digits; the rest may be empty. A form the server refuses is kept and drawn again with an error
 * under each field at fault (`Checkout_Validation`).
 */
@Serializable
public data class AddressEntry(
    val street: String = "",
    val apt: String = "",
    val city: String = "",
    val zip: String = "",
    val doorCode: String = "",
    val courierNote: String = "",
)
