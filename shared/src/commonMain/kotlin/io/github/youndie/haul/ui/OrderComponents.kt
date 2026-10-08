package io.github.youndie.haul.ui

import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.registry.KompotComponentMarker
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The pieces of the Order screen (feature-orders, screen-order). The page is the frame's header and one
// [OrderBody]: the crumbs, the title and the shipments in one column with the summary beside them at the
// desktop width, under them on a phone — one layout, as the cart's and the checkout's are. One tree covers
// Placed, InTransit, ReadyForPickup, Delivered, Cancelled and Returned: they are what the order is, not
// separate builders. Reorder is a `POST` to [OrderTotals.reorderUrl], answered with `navigate` to the cart;
// «Return items» is kompot's `present` of a [ReturnForm] (B-21).

/**
 * Everything under the header: the [crumbs] («Account / Orders / #HL-48302», the last the order), the
 * [meta] line above the title («#HL-48302 · Placed Oct 7 · $512.00»), the [title] with its [accent] in
 * italics and the [lead] under it, then — in the column — the [steps], the [notice] of a cancelled order,
 * the [pickup] code while a shipment waits at a point, and the [shipments]; the [summary] beside them.
 */
@Serializable
@SerialName("haul_order_body")
@KompotComponentMarker
public data class OrderBody(
    override val id: String,
    val crumbs: List<Crumb>,
    val meta: String,
    val title: String,
    val accent: String? = null,
    val lead: String? = null,
    val steps: OrderSteps? = null,
    val notice: OrderNotice? = null,
    val pickup: PickupCode? = null,
    val shipments: List<OrderShipment>,
    val summary: OrderTotals,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/**
 * Where the order is, as four steps ([labels]: Placed, Packed, In transit, Delivered — or, collected at a
 * point, Placed, Packed, Ready for pickup, Picked up). The steps before [current] are done, [current] is
 * where it is now; once it has [arrived] the last step is done too rather than current (`Order_Delivered`).
 */
@Serializable
public data class OrderSteps(
    val labels: List<String>,
    val current: Int,
    val arrived: Boolean = false,
)

/**
 * The banner of a cancelled order (`Order_Cancelled`): [title] «Your card ···· 0002 was declined», [text]
 * what it means for the shopper, and the way on ([action], «Back to cart»).
 */
@Serializable
public data class OrderNotice(
    val title: String,
    val text: String,
    val action: Link? = null,
)

/**
 * The code that collects a shipment waiting at a point or a locker (`Order_ReadyForPickup`): [label]
 * «Pickup code», the [code], and [text] — how long it is kept — with [highlight], the part of it in bold
 * («Oct 10»).
 */
@Serializable
public data class PickupCode(
    val label: String,
    val code: String,
    val text: String,
    val highlight: String? = null,
)

/**
 * One seller's shipment: the [seller], its [status] in a chip («In transit»), [eta] — when or where it
 * arrives, or that it did («Tomorrow, Oct 8 · 15:00 – 18:00», «Pickup point · 214 Bedford Ave»,
 * «Delivered Sep 26», «Not shipped») — and its [items], drawn faded when it was [cancelled]. The lines of a
 * return are one more such card (`Order_Returned`: «Returned items», «Refunded»), its chip in Blush when it is
 * [returned].
 */
@Serializable
public data class OrderShipment(
    val id: String,
    val seller: String,
    val status: String,
    val eta: String,
    val cancelled: Boolean = false,
    val returned: Boolean = false,
    val items: List<OrderItem>,
)

/**
 * A line as it was bought: the tile ([tone], one of the canvas's tile tones), the [title], [details] — the options, the quantity
 * and the price paid («Midnight Black · Headphones only · 1 × $349») — where a tap on the tile or the
 * title goes ([action], the product page), and, once delivered, [review] («Write a review»).
 */
@Serializable
public data class OrderItem(
    val title: String,
    val details: String,
    val tone: String,
    val action: @Polymorphic KompotAction? = null,
    val review: Link? = null,
)

/** What a fact of the summary is about, which is the icon it is drawn with. */
@Serializable
public enum class OrderFactKind {
    /** How it was paid (the padlock). */
    @SerialName("card")
    Card,

    /** Where it goes (the pin). */
    @SerialName("place")
    Place,

    /** The points it earns (the star). */
    @SerialName("points")
    Points,
}

/** A fact under the total: [title] «Card ···· 4821», [detail] «Authorised at placement, charged per shipment when it ships». */
@Serializable
public data class OrderFact(
    val kind: OrderFactKind,
    val title: String,
    val detail: String,
)

/** How one payment of a [PaymentPlan] stands, which is how it is drawn. */
@Serializable
public enum class PlanPaymentState {
    /** Charged. */
    @SerialName("paid")
    Paid,

    /** Still to come. */
    @SerialName("upcoming")
    Upcoming,

    /** Declined: tried again once, then overdue (drawn in the error colour). */
    @SerialName("declined")
    Declined,

    /** Nothing left to charge: a return took all of it off. */
    @SerialName("covered")
    Covered,
}

/**
 * One payment of a Haul Pay plan: [label] — its day, «Oct 8», or before the plan starts «When it ships», «In 2
 * weeks» — [detail] «Paid», «Upcoming», «Declined, tried again Oct 23», the [amount] it charges, and its [state].
 */
@Serializable
public data class PlanPayment(
    val label: String,
    val detail: String,
    val amount: String,
    val state: PlanPaymentState,
)

/**
 * Haul Pay's schedule (B-24): the [title] «4 payments, two weeks apart» and the [payments] in order, drawn
 * under the summary's payment fact. No artboard draws it; it is the summary's rows under a fact.
 */
@Serializable
public data class PaymentPlan(
    val title: String,
    val payments: List<PlanPayment>,
)

/**
 * «Summary»: the [rows] («Items (3) $652.00», «Discount −$140.00», «Delivery Free»), the total — struck
 * through once it is [voided], a cancelled order's — the [facts], a Haul Pay order's [plan] under the payment
 * fact (B-24), and the ways on: [reorderLabel] puts the order's lines back into the cart (`POST` to
 * [reorderUrl], answered `navigate` to the cart); [returnLabel] is «Return items», and [returnAction] what it
 * does — kompot's `present` of the [ReturnForm] (B-21); [back] is a cancelled order's «Back to cart».
 */
@Serializable
public data class OrderTotals(
    val title: String,
    val rows: List<SummaryRow>,
    val totalLabel: String,
    val total: String,
    val voided: Boolean = false,
    val facts: List<OrderFact> = emptyList(),
    val reorderLabel: String? = null,
    val reorderUrl: String? = null,
    val returnLabel: String? = null,
    val returnAction: @Polymorphic KompotAction? = null,
    val back: Link? = null,
    val plan: PaymentPlan? = null,
)

/**
 * «Return items» (`Order_ReturnDialog`), presented over a delivered order: the [title], the [meta] line
 * («#HL-46102 · delivered Sep 26 · returns until Oct 26»), the [lines] that can go back with a checkbox
 * each, the [reasons] under [reasonLabel] ([reasonHint] until one is chosen), and the refund the ticked lines
 * come to — [refund] with `{amount}` standing for their sum («Refund {amount} to card ···· 4821»), then
 * [note] and, when the order earned points, [pointsOne] or [pointsMany] by how many lines are ticked. Sent
 * as a `ReturnEntry` to [url]; [close] is what «×» and [cancelLabel] do.
 */
@Serializable
@SerialName("haul_return_form")
@KompotComponentMarker
public data class ReturnForm(
    override val id: String,
    val title: String,
    val meta: String,
    val lines: List<ReturnLine>,
    val reasonLabel: String,
    val reasonHint: String,
    val reasons: List<ReturnReason>,
    val refund: String,
    val note: String,
    val pointsOne: String? = null,
    val pointsMany: String? = null,
    val submitLabel: String,
    val cancelLabel: String,
    val url: String,
    val close: @Polymorphic KompotAction? = null,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/**
 * A line the shopper may return, whole: its [position] in the order (what `ReturnEntry.lines` names), its
 * tile, [title] and [details] as the order draws them, and [refundCents] — what returning it gives back.
 */
@Serializable
public data class ReturnLine(
    val position: Int,
    val title: String,
    val details: String,
    val tone: String,
    val refundCents: Int,
)

/** A reason to return, by its [id] (what `ReturnEntry.reason` names) and its [label] («Doesn’t fit»). */
@Serializable
public data class ReturnReason(
    val id: String,
    val label: String,
)
