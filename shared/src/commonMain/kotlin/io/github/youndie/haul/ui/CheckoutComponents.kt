package io.github.youndie.haul.ui

import io.github.youndie.haul.feature.checkout.DeliveryMethod
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.registry.KompotComponentMarker
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The pieces of the Checkout screen (feature-checkout, screen-checkout). The page is the checkout's own
// header and one [CheckoutBody]: the numbered sections in one column and the summary beside them at the
// desktop width, under them on a phone — one layout, which a page's column of sections cannot say. One
// tree covers Content, PickupPoint, ParcelLocker, Validation and PlaceError: they are what the stored
// checkout is, not separate builders. A choice goes where its section says — `url`, the server's string —
// as a `CheckoutChoice` with `PUT`; the address form as an `AddressEntry` with `POST`; both answer
// `refresh` (`feature/checkout/CheckoutCommands.kt`).

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
 * Everything under the header: the [title], the [notices] above the sections, the sections in their
 * order — [methods], then [address] and [slots] for a courier or [points] for a pickup point or a
 * locker, then [payment] — numbered as they come (01, 02, …), and the [summary].
 */
@Serializable
@SerialName("haul_checkout_body")
@KompotComponentMarker
public data class CheckoutBody(
    override val id: String,
    val title: String,
    val notices: List<CheckoutNotice> = emptyList(),
    val methods: DeliveryMethods,
    val address: CheckoutAddress? = null,
    val slots: DeliverySlots? = null,
    val points: PickupPoints? = null,
    val payment: PaymentMethods,
    val summary: CheckoutSummary,
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

/** One way to receive the order: [detail] when and where («Thu, Oct 9 · 240 m away»), [price] what it costs («Free»). */
@Serializable
public data class MethodOption(
    val method: DeliveryMethod,
    val label: String,
    val detail: String? = null,
    val price: String? = null,
    val selected: Boolean,
)

/** «How to receive»: courier, pickup point, parcel locker. A tap sends `CheckoutChoice(method)` to [url]. */
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

/**
 * One field of a form: [name] is the body's property it fills (`street`, `zip`, …), [value] what the
 * field holds — the address the checkout delivers to, or what was typed when a refused form is drawn
 * again — [placeholder] what an empty one says («Optional»), and [error] what to fix in it.
 */
@Serializable
public data class FormField(
    val name: String,
    val label: String,
    val value: String = "",
    val placeholder: String? = null,
    val required: Boolean = false,
    val error: String? = null,
)

/**
 * Where a courier brings the order, as a form: its fields hold the address the checkout delivers to
 * (none yet: empty), and what the shopper changes in them is sent as an `AddressEntry` with `POST` to
 * [url], which saves it as their address and delivers there. `Checkout_Validation` is the form drawn
 * again with the values sent and an error under each field at fault.
 */
@Serializable
@SerialName("haul_checkout_address")
@KompotComponentMarker
public data class CheckoutAddress(
    override val id: String,
    val title: String,
    val form: List<FormField>,
    val url: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/**
 * One delivery window: [label] «15:00 – 18:00»; one at capacity is not [available], and its label says
 * so («15:00 – 18:00 · Full»).
 */
@Serializable
public data class SlotOption(
    val id: String,
    val label: String,
    val available: Boolean,
    val selected: Boolean,
)

/** A day of windows: [weekday] «Wed», [date] «8»; the [selected] day is the one whose windows are shown first. */
@Serializable
public data class SlotDay(
    val weekday: String,
    val date: String,
    val selected: Boolean = false,
    val slots: List<SlotOption>,
)

/**
 * The courier's windows over the next five days. A tap on an available one sends `CheckoutChoice(slotId)`
 * to [url]; [notice] says what to do when the window chosen filled up («Pick another window for Wed, Oct 8»).
 */
@Serializable
@SerialName("haul_delivery_slots")
@KompotComponentMarker
public data class DeliverySlots(
    override val id: String,
    val title: String,
    val days: List<SlotDay>,
    val notice: String? = null,
    val url: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/**
 * A pickup point or a locker: [name] «214 Bedford Ave», [detail] «Pickup point · open until 21:00 · Thu,
 * Oct 9», [distance] «240 m».
 */
@Serializable
public data class PickupPointOption(
    val id: String,
    val name: String,
    val detail: String,
    val distance: String? = null,
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

/** A way to pay: [label] «Card ···· 4821», [detail] «Expires 08/28» or «4 payments of $128». */
@Serializable
public data class PaymentOption(
    val id: String,
    val label: String,
    val detail: String? = null,
    val selected: Boolean,
)

/**
 * The ways to pay this checkout offers: pay on delivery is absent for a locker, Haul Pay outside
 * $50–$2,000. A tap sends `CheckoutChoice(payment)` to [url]. [points] is the points toggle under them.
 */
@Serializable
@SerialName("haul_payment_methods")
@KompotComponentMarker
public data class PaymentMethods(
    override val id: String,
    val title: String,
    val options: List<PaymentOption>,
    val points: PointsToggle? = null,
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
 * «Use 2,480 points (−$24.80)», [detail] «100 points = $1 · all or nothing», and whether it is [on]
 * (`Checkout_PointsApplied`). Feature-membership's (B-23): the server sends none until a customer has a
 * points balance, and [url] — where a tap sends the change — is B-23's too; without one the toggle is
 * drawn and does nothing.
 */
@Serializable
public data class PointsToggle(
    val label: String,
    val detail: String,
    val on: Boolean,
    val url: String? = null,
)

/**
 * The quote: the [items], the [rows] («Items (3) $652.00», «Discount −$140.00», «Delivery · Wed, Oct 8
 * Free»), the total, «Place order · $512.00» and the [note] under it.
 *
 * [quote] names what was quoted — the lines, their prices, the code, the method, the place, the
 * window, the payment and the total — and placement sends it back, so an order is placed only for
 * the quote the shopper saw. [placeEnabled] is `false` until the quote can be placed — courier needs
 * an address and a window, a pickup point or a locker needs the point, and an address form at fault
 * holds it — and [placeHint] says what is missing («Pick a delivery window»). [placeUrl] is where the
 * order is placed (B-16); [placingLabel] is the button while it is being placed («Placing order…»).
 */
@Serializable
@SerialName("haul_checkout_summary")
@KompotComponentMarker
public data class CheckoutSummary(
    override val id: String,
    val title: String,
    val items: List<SummaryItem>,
    val rows: List<SummaryRow>,
    val totalLabel: String,
    val total: String,
    val note: String? = null,
    val placeLabel: String,
    val placingLabel: String,
    val placeEnabled: Boolean,
    val placeHint: String? = null,
    val placeUrl: String? = null,
    val quote: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent
