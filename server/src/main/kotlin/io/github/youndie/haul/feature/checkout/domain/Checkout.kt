package io.github.youndie.haul.feature.checkout.domain

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.FieldError
import io.github.youndie.haul.feature.checkout.AddressEntry
import io.github.youndie.haul.feature.checkout.DeliveryMethod
import java.time.OffsetDateTime

/** What checkout can refuse with; the application answers each with its status and body. */
internal sealed class CheckoutError(
    val code: ErrorCode,
    override val message: String,
    val field: String? = null,
    val fields: List<FieldError> = emptyList(),
) : Exception(message) {
    class Invalid(
        field: String,
        message: String,
    ) : CheckoutError(ErrorCode.ValidationFailed, message, field)

    /** Nothing selected in the cart: nothing to quote (endpoint-checkout, `GET /ui/checkout`). */
    class CartEmpty : CheckoutError(ErrorCode.CartEmpty, "Select at least one item in the cart to check out")

    class SlotNotFound(
        slotId: String,
    ) : CheckoutError(ErrorCode.SlotNotFound, "No delivery window «$slotId» is offered", "slotId")

    /** The window is at capacity (feature-checkout: «a slot at capacity is … not selectable»). */
    class SlotUnavailable : CheckoutError(ErrorCode.SlotUnavailable, SLOT_FILLED, "slotId")

    class PickupPointNotFound(
        pointId: String,
    ) : CheckoutError(ErrorCode.PickupPointNotFound, "No pickup point «$pointId»", "pointId")

    /** An address that is not the caller's — or none at all: the same answer (research §5, «not yours»). */
    class AddressNotFound(
        addressId: String,
    ) : CheckoutError(ErrorCode.AddressNotFound, "No address «$addressId» among yours", "addressId")

    class PaymentNotAllowed(
        message: String,
    ) : CheckoutError(ErrorCode.PaymentMethodNotAllowed, message, "payment")

    /** The address form, refused field by field; [fields] names every one at fault, in the form's order. */
    class AddressRefused(
        fields: List<FieldError>,
    ) : CheckoutError(ErrorCode.ValidationFailed, "Check the address", fields.first().field, fields)

    companion object {
        /** What the shopper is told when the window they had filled up (`Checkout_PlaceError`). */
        const val SLOT_FILLED = "That delivery window just filled up — pick another"
    }
}

/** A customer's saved address (research §5). */
internal data class Address(
    val id: String,
    val street: String,
    val apt: String?,
    val city: String,
    val zip: String,
    val doorCode: String?,
    val courierNote: String?,
    val createdAt: OffsetDateTime,
) {
    /**
     * The address as the form holds it, and as an order keeps it (B-40): what is absent is empty. Two
     * addresses with the same entry are the same address — a save never stores it twice.
     */
    fun entry(): AddressEntry =
        AddressEntry(street, apt.orEmpty(), city, zip, doorCode.orEmpty(), courierNote.orEmpty())
}

/**
 * A pickup point or a parcel locker (research §6): [method] says which; [distanceMeters] is how far it
 * is from the store's default place, `null` where the sample data does not say.
 */
internal data class PickupPoint(
    val id: String,
    val method: DeliveryMethod,
    val name: String,
    val distanceMeters: Int?,
    val hours: String,
    val position: Int,
)

/**
 * What a customer chose at checkout, as stored. Every choice may be absent, and the quote fills an
 * absent one with its default: the newest address, the nearest point, the first window with room,
 * the card. [draft] is the last address form the server refused, drawn again until one is accepted.
 * [usePoints] is the points toggle (B-23): on, the order is paid with the customer's points as well.
 */
internal data class StoredCheckout(
    val method: DeliveryMethod = DeliveryMethod.Courier,
    val addressId: String? = null,
    val pointId: String? = null,
    val slotId: String? = null,
    val payment: String? = null,
    val draft: AddressEntry? = null,
    val usePoints: Boolean = false,
)

/** The checkout's storage. Every method takes the customer, and a read of a checkout never written is the default. */
internal interface CheckoutRepository {
    suspend fun checkout(customerId: String): StoredCheckout

    suspend fun save(
        customerId: String,
        checkout: StoredCheckout,
    )

    /** The customer's addresses, the newest first. */
    suspend fun addresses(customerId: String): List<Address>

    /**
     * In one transaction, the address form saved (B-40): an address of the customer's equal to [entry]
     * becomes the checkout's as it is; otherwise the address [editing] — the one the form held — is
     * rewritten with [entry] in place, keeping its id; with no such address of the customer's, [entry] is
     * added. Whichever it is becomes the checkout's address, with the courier as the method, and a
     * refused form is forgotten. An order is not affected: it keeps a copy of the address it was placed
     * to (`NewOrder.address`).
     */
    suspend fun saveAddress(
        customerId: String,
        entry: AddressEntry,
        editing: String?,
        at: OffsetDateTime,
    ): Address

    /** Every pickup point and locker, in their order. */
    suspend fun pickupPoints(): List<PickupPoint>
}
