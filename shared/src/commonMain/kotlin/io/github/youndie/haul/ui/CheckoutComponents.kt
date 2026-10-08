package io.github.youndie.haul.ui

import io.github.youndie.haul.feature.checkout.DeliveryMethod
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.registry.KompotComponentMarker
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The pieces of the Checkout screen (feature-checkout, screen-checkout). One tree covers Content,
// PickupPoint, ParcelLocker, Validation and PlaceError: they are what the stored checkout is, not
// separate builders. A choice goes where its component says — `url`, the server's string — as a
// `CheckoutChoice` with `PUT`; the address form as an `AddressEntry` with `POST`; both answer `refresh`
// (`feature/checkout/CheckoutCommands.kt`).

/**
 * The checkout's own header, instead of `HaulHeader`: the logo ([home] goes back to the store), the
 * [steps] with the [current] one marked (an index into them), and [secureLabel].
 */
@Serializable
@SerialName("haul_checkout_header")
@KompotComponentMarker
public data class CheckoutHeader(
    override val id: String,
    val steps: List<String>,
    val current: Int = 0,
    val secureLabel: String,
    val home: @Polymorphic KompotAction? = null,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/**
 * A banner above the sections: the window chosen filled up before the order was placed
 * (`Checkout_PlaceError`, the slot cleared), or the cart's promo code expired and no longer counts.
 */
@Serializable
@SerialName("haul_checkout_notice")
@KompotComponentMarker
public data class CheckoutNotice(
    override val id: String,
    val text: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/** One way to receive the order; [detail] is what it costs («Free», «$5.99»). */
@Serializable
public data class MethodOption(
    val method: DeliveryMethod,
    val label: String,
    val detail: String? = null,
    val selected: Boolean,
)

/** «How to receive it»: courier, pickup point, parcel locker. A tap sends `CheckoutChoice(method)` to [url]. */
@Serializable
@SerialName("haul_delivery_methods")
@KompotComponentMarker
public data class DeliveryMethods(
    override val id: String,
    val title: String,
    val options: List<MethodOption>,
    val url: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/** A saved address: [line] «148 Wythe Avenue, Apt 4F», [detail] «Brooklyn, NY 11211». */
@Serializable
public data class AddressOption(
    val id: String,
    val line: String,
    val detail: String,
    val selected: Boolean,
)

/**
 * One field of a form: [name] is the body's property it fills (`street`, `zip`, …), [value] what the
 * field holds — what was typed, when a refused form is drawn again — and [error] what to fix in it.
 */
@Serializable
public data class FormField(
    val name: String,
    val label: String,
    val value: String = "",
    val required: Boolean = false,
    val error: String? = null,
)

/**
 * Where a courier brings the order: the customer's saved [addresses] (a tap sends
 * `CheckoutChoice(addressId)` to [choiceUrl]) and the address [form] — an `AddressEntry` with `POST` to
 * [url], labelled [submitLabel]. The form is [formOpen] when there is no address yet or the last one
 * sent was refused; otherwise [addLabel] opens it. `Checkout_Validation` is the form drawn again with
 * the values sent and an error under each field at fault.
 */
@Serializable
@SerialName("haul_checkout_address")
@KompotComponentMarker
public data class CheckoutAddress(
    override val id: String,
    val title: String,
    val addresses: List<AddressOption>,
    val choiceUrl: String,
    val url: String,
    val form: List<FormField>,
    val formOpen: Boolean,
    val addLabel: String,
    val submitLabel: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/** One delivery window: [label] «15:00–18:00»; one at capacity is drawn and not [available]. */
@Serializable
public data class SlotOption(
    val id: String,
    val label: String,
    val available: Boolean,
    val selected: Boolean,
)

/** A day of windows: [label] «Wed 8». */
@Serializable
public data class SlotDay(
    val label: String,
    val slots: List<SlotOption>,
)

/** The courier's windows over the next five days. A tap on an available one sends `CheckoutChoice(slotId)` to [url]. */
@Serializable
@SerialName("haul_delivery_slots")
@KompotComponentMarker
public data class DeliverySlots(
    override val id: String,
    val title: String,
    val days: List<SlotDay>,
    val url: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/**
 * A pickup point or a locker: [name] «214 Bedford Ave», [distance] «240 m» (absent where the sample
 * data gives none), [hours] «until 21:00».
 */
@Serializable
public data class PickupPointOption(
    val id: String,
    val name: String,
    val distance: String? = null,
    val hours: String,
    val selected: Boolean,
)

/**
 * The points nearby for the method chosen (`Checkout_PickupPoint`, `Checkout_ParcelLocker`). A tap
 * sends `CheckoutChoice(pointId)` to [url].
 */
@Serializable
@SerialName("haul_pickup_points")
@KompotComponentMarker
public data class PickupPoints(
    override val id: String,
    val title: String,
    val points: List<PickupPointOption>,
    val url: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/** A way to pay: [label] «Card ···· 4821», [detail] «Expires 08/28» or «4 payments of $128.00». */
@Serializable
public data class PaymentOption(
    val id: String,
    val label: String,
    val detail: String? = null,
    val selected: Boolean,
)

/**
 * The ways to pay this checkout offers: pay on delivery is absent for a locker, Haul Pay outside
 * $50–$2,000. A tap sends `CheckoutChoice(payment)` to [url].
 */
@Serializable
@SerialName("haul_payment_methods")
@KompotComponentMarker
public data class PaymentMethods(
    override val id: String,
    val title: String,
    val options: List<PaymentOption>,
    val url: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/** A line the order holds: the tile, the title and options, «× 2», the line's price. */
@Serializable
public data class SummaryItem(
    val title: String,
    val options: String,
    val quantity: Int,
    val price: String,
    val tone: String,
    val label: String,
)

/**
 * «Use 2,480 points (−$24.80)» and whether it is on (`Checkout_PointsApplied`). Feature-membership's
 * (B-23): the server sends none until a customer has a points balance.
 */
@Serializable
public data class PointsToggle(
    val label: String,
    val on: Boolean,
    val url: String,
)

/**
 * The quote: the [items], the [rows] («Items $652.00», «Discount −$140.00», «Delivery Free»), the
 * total, the points it earns, and «Place order · $512.00».
 *
 * [quote] names what was quoted — the lines, their prices, the code, the method, the place, the
 * window, the payment and the total — and placement sends it back, so an order is placed only for
 * the quote the shopper saw. [placeEnabled] is `false` until the quote is complete: courier needs an
 * address and a window, a pickup point or a locker needs the point. [placeUrl] is where the order is
 * placed (B-16); [note] is said under the button («Your card is charged when the order ships»).
 */
@Serializable
@SerialName("haul_checkout_summary")
@KompotComponentMarker
public data class CheckoutSummary(
    override val id: String,
    val items: List<SummaryItem>,
    val rows: List<SummaryRow>,
    val totalLabel: String,
    val total: String,
    val points: String? = null,
    val redeem: PointsToggle? = null,
    val note: String? = null,
    val placeLabel: String,
    val placeEnabled: Boolean,
    val placeUrl: String? = null,
    val quote: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent
