package io.github.youndie.haul.feature.order.screen

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.feature.cart.screen.CartScreen
import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.PriceList
import io.github.youndie.haul.feature.catalog.domain.count
import io.github.youndie.haul.feature.catalog.domain.money
import io.github.youndie.haul.feature.catalog.screen.navigation
import io.github.youndie.haul.feature.catalog.screen.productLink
import io.github.youndie.haul.feature.checkout.AddressEntry
import io.github.youndie.haul.feature.checkout.DeliveryMethod
import io.github.youndie.haul.feature.checkout.domain.CheckoutRepository
import io.github.youndie.haul.feature.checkout.domain.PaymentMethod
import io.github.youndie.haul.feature.checkout.domain.PickupPoint
import io.github.youndie.haul.feature.checkout.domain.Slot
import io.github.youndie.haul.feature.fulfilment.domain.OrderProgress
import io.github.youndie.haul.feature.fulfilment.domain.OrderTracking
import io.github.youndie.haul.feature.fulfilment.domain.TrackedOrder
import io.github.youndie.haul.feature.fulfilment.domain.TrackedShipment
import io.github.youndie.haul.feature.order.OrderPaths
import io.github.youndie.haul.feature.order.domain.CancelReason
import io.github.youndie.haul.feature.order.domain.OrderLine
import io.github.youndie.haul.feature.order.domain.ShipmentStatus
import io.github.youndie.haul.feature.payment.domain.Instalment
import io.github.youndie.haul.feature.payment.domain.InstalmentPlan
import io.github.youndie.haul.feature.payment.domain.InstalmentStatus
import io.github.youndie.haul.feature.returns.ReturnPaths
import io.github.youndie.haul.feature.returns.domain.ReturnReason
import io.github.youndie.haul.feature.returns.domain.ReturnRefunds
import io.github.youndie.haul.feature.returns.domain.ReturnStatus
import io.github.youndie.haul.feature.returns.domain.ReturnWindow
import io.github.youndie.haul.feature.reviews.screen.ReviewTabs
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.ui.Crumb
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.OrderBody
import io.github.youndie.haul.ui.OrderFact
import io.github.youndie.haul.ui.OrderFactKind
import io.github.youndie.haul.ui.OrderItem
import io.github.youndie.haul.ui.OrderNotice
import io.github.youndie.haul.ui.OrderShipment
import io.github.youndie.haul.ui.OrderSteps
import io.github.youndie.haul.ui.OrderTotals
import io.github.youndie.haul.ui.PaymentPlan
import io.github.youndie.haul.ui.PickupCode
import io.github.youndie.haul.ui.PlanPayment
import io.github.youndie.haul.ui.PlanPaymentState
import io.github.youndie.haul.ui.ReturnForm
import io.github.youndie.haul.ui.ReturnLine
import io.github.youndie.haul.ui.SummaryRow
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.CloseAction
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.PresentAction
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import io.github.youndie.haul.ui.ReturnReason as ReasonOption

/**
 * `GET /ui/account/orders/{id}` (screen-order, feature-orders): the frame and one [OrderBody], drawn from
 * where the order is ([OrderTracking.track], B-17). One tree covers every state the server has: Placed,
 * InTransit, ReadyForPickup, Delivered, Cancelled and Returned are what the order is; a delivered order's
 * «Return items» presents the [ReturnForm] (B-21). The copy is the canvas's (`Order_*`). Another customer's
 * order and one that does not exist are both `null` — the route answers them alike.
 */
internal class OrderScreen(
    private val tracking: OrderTracking,
    private val catalog: CatalogRepository,
    private val checkout: CheckoutRepository,
    private val reviews: ReviewTabs,
    private val clock: StoreClock,
) {
    suspend fun build(
        customerId: String,
        orderId: String,
        viewer: Viewer,
    ): KompotComponent? {
        val tracked = tracking.track(customerId, orderId) ?: return null
        return page(view(customerId, tracked, viewer.firstName), viewer)
    }

    /** The page of [view], under the frame [viewer] is looking at. */
    suspend fun page(
        view: OrderView,
        viewer: Viewer,
    ): KompotComponent = Frame.page("order", viewer, navigation(catalog.categories()), listOf(body(view)))

    /** What [tracked] — [customerId]'s order — names, read from the catalog and the customer's checkout. */
    suspend fun view(
        customerId: String,
        tracked: TrackedOrder,
        firstName: String?,
    ): OrderView {
        val placed = tracked.order.placed
        // The options, the tone and the review button: an order draws its own prices, not the catalog's.
        val listed = catalog.listedBySkus(placed.lines.map { it.skuId }.toSet(), PriceList.Public)
        val products =
            placed.lines
                .mapNotNull { line ->
                    val item =
                        listed.firstOrNull { listed -> listed.skus.any { it.id == line.skuId } }
                            ?: return@mapNotNull null
                    val sku = item.skus.first { it.id == line.skuId }
                    line.skuId to
                        Bought(
                            productId = item.product.id,
                            // `jsonb` does not keep the keys' order; the colour leads, as on the cart.
                            options =
                                sku.options.entries
                                    .sortedBy { it.key != "colour" }
                                    .joinToString(" · ") { it.value },
                            tone = item.product.tone,
                            dispatchDays = item.product.dispatchDays,
                            review = reviews.writeReview(item, sku, Viewer(customerId = customerId)),
                        )
                }.toMap()
        return OrderView(
            tracked = tracked,
            firstName = firstName,
            products = products,
            sellers =
                placed.lines
                    .map { it.sellerId }
                    .distinct()
                    .associateWith { catalog.seller(it)?.name ?: it },
            // The order's own copy (B-40): the saved address is edited in place, and a past order keeps where it went.
            address = placed.address,
            point = placed.pointId?.let { id -> checkout.pickupPoints().firstOrNull { it.id == id } },
            now = clock.now(),
        )
    }

    companion object {
        /** The order's page as the server draws it from [view]: everything it needs is in the view. */
        fun body(view: OrderView): OrderBody = OrderPage(view).body()

        /** The order as a card on the account (B-19), drawn from the same [view] as its page. */
        fun card(view: OrderView): OrderCard = OrderPage(view).card()
    }
}

/**
 * What the order's page is drawn from: where the order is, and what it names — each SKU's product as the
 * catalog has it now ([products], by SKU id; a SKU gone from the catalog is drawn from the order alone),
 * the [sellers]' names by id, the [address] a courier brings it to (the order's copy, as it was placed) or the
 * [point] it is collected at, the
 * customer's [firstName], and the store's [now], which «tomorrow» is counted from.
 */
internal data class OrderView(
    val tracked: TrackedOrder,
    val firstName: String?,
    val products: Map<String, Bought>,
    val sellers: Map<String, String>,
    val address: AddressEntry?,
    val point: PickupPoint?,
    val now: ZonedDateTime,
)

/**
 * An order on its way as the account draws it (`Account_Content`): the page's [meta] line, a [title] with
 * its [accent] and the [lead] under it, and the [steps] — or, while a shipment waits at a point, its
 * [pickupCode] instead.
 */
internal data class OrderCard(
    val meta: String,
    val title: String,
    val accent: String?,
    val lead: String?,
    val steps: OrderSteps?,
    val pickupCode: String?,
)

/**
 * A SKU's product as the page draws its line: the tile's tone, the options, how long its seller takes to
 * dispatch, and what «Write a review» does once the line has arrived (the review dialog, B-22).
 */
internal data class Bought(
    val productId: String,
    val options: String,
    val tone: String,
    val dispatchDays: Int,
    val review: KompotAction,
)

/** One order's page, built from its [view]; every date is the store's (New York's). */
private class OrderPage(
    private val view: OrderView,
) {
    private val order = view.tracked.order
    private val placed = order.placed
    private val progress = view.tracked.progress
    private val pickup = placed.method != DeliveryMethod.Courier
    private val today = view.now.withZoneSameInstant(STORE).toLocalDate()
    private val slot = placed.slotId?.let(Slot::parse)
    private val payment = PaymentMethod.byId(placed.payment)
    private val arrived = progress == OrderProgress.Delivered || progress == OrderProgress.PickedUp
    private val returned = view.tracked.returned
    private val refunded = returned?.status == ReturnStatus.REFUNDED

    /**
     * What the return gives back in money and as points (B-55): the split the return dialog drew (B-50) and the
     * refund pays, the returned lines' shares of the points the order was paid with coming back as points. An
     * order paid without points gives back its whole value in money and no points, and reads as before.
     */
    private val moneyBack = returned?.let { ReturnRefunds.moneyBack(order, it) } ?: 0
    private val pointsBack =
        returned?.let { ret -> ReturnRefunds.pointsBack(order, ret.lines.map { it.position }) } ?: 0

    /** «+ 790 points back» beside the money, as the dialog writes it under its amount — nothing when none come back. */
    private val pointsBackNote = if (pointsBack > 0) " · + ${count(pointsBack)} points back" else ""

    fun body(): OrderBody {
        val (title, accent, lead) = heading()
        return OrderBody(
            id = "order",
            crumbs =
                listOf(
                    Crumb("Account", NavigateAction(Frame.ACCOUNT)),
                    Crumb("Orders", NavigateAction(Frame.ORDERS)),
                    Crumb("#${order.id}"),
                ),
            meta = meta(),
            title = title,
            accent = accent,
            lead = lead,
            steps = steps(),
            notice = notice(),
            pickup = pickupCode(),
            shipments = shipments(),
            summary = summary(),
        )
    }

    /** The title, its accent and the line under it, by where the order is. */
    private fun heading(): Triple<String, String?, String?> =
        when (progress) {
            OrderProgress.Cancelled -> {
                Triple("Order cancelled", "cancelled", null)
            }

            OrderProgress.Placing, OrderProgress.Placed -> {
                val number = "#${order.id}"
                val thanks = view.firstName?.let { "Thanks, $it — order" } ?: "Thanks — order"
                Triple("$thanks $number is placed", number, shipmentsLead())
            }

            OrderProgress.Packed, OrderProgress.InTransit -> {
                val (title, word) = arriving()
                Triple(title, word, if (pickup) pointLine() else courierLead())
            }

            OrderProgress.ReadyForPickup -> {
                val kept = heldUntil()?.let { "kept until " + MONTH_DAY.format(it) }
                Triple("Ready for pickup", "pickup", listOfNotNull(pointLine(), kept).joinToString(" · "))
            }

            OrderProgress.Delivered, OrderProgress.PickedUp -> {
                val day = arrivedOn()
                val verb = if (progress == OrderProgress.PickedUp) "Picked up" else "Delivered"
                val shown = day?.let(MONTH_DAY::format)
                val last = day?.let(ReturnWindow::lastDay)
                Triple(
                    listOfNotNull(verb, shown).joinToString(" "),
                    shown,
                    last?.let {
                        if (today.isAfter(it)) {
                            "Returns closed on ${MONTH_DAY.format(it)}."
                        } else {
                            "Returns are open until ${MONTH_DAY.format(it)}."
                        }
                    },
                )
            }

            OrderProgress.Returning, OrderProgress.Returned -> {
                returnHeading()
            }
        }

    /**
     * A return's title, by where it is, and where the money goes: «Return refunded», «$87.50 is back on your
     * card ···· 4821.» (`Order_Returned`); before that, when it will be. The amount is the money the refund
     * pays, not the lines' value: an order paid partly with points reads «$155.10 goes back to your card ····
     * 4821 once the seller has it · + 790 points back» (B-55), the dialog's split.
     */
    private fun returnHeading(): Triple<String, String?, String?> {
        val amount = exact(moneyBack)
        val sentence: (String) -> String = { text -> if (pointsBackNote.isEmpty()) "$text." else text + pointsBackNote }
        return when (returned?.status) {
            ReturnStatus.REFUNDED -> {
                Triple("Return refunded", "refunded", sentence("$amount is back ${refundedTo()}"))
            }

            ReturnStatus.PICKED_UP -> {
                Triple(
                    "Return picked up",
                    "picked up",
                    sentence("$amount goes back ${refundTo()} once the seller has it"),
                )
            }

            else -> {
                Triple(
                    "Return requested",
                    "requested",
                    "${collection()} " + sentence("$amount goes back ${refundTo()} once the seller has it"),
                )
            }
        }
    }

    /** How the parcel goes back: the courier collects a courier's order; a point's goes back to the point. */
    private fun collection(): String =
        if (pickup) {
            "Bring it back to ${view.point?.name ?: "the pickup point"} for free."
        } else {
            "A courier picks it up for free."
        }

    /** Where a refund goes, before it went. */
    private fun refundTo(): String =
        when (payment) {
            PaymentMethod.OnDelivery -> "in cash, from the courier"
            PaymentMethod.HaulPayPlan -> "to your Haul Pay plan"
            else -> "to your ${paymentName()}"
        }

    /** Where a refund went. */
    private fun refundedTo(): String =
        when (payment) {
            PaymentMethod.OnDelivery -> "with you, in cash"
            PaymentMethod.HaulPayPlan -> "on your Haul Pay plan"
            else -> "on your ${paymentName()}"
        }

    /**
     * The order as a card on the account's overview (`Account_Content`): the meta line, where it is in a
     * line — «Arriving *tomorrow*, 15:00 – 18:00» from placement until it arrives, «Ready for *pickup*» with
     * the point and how long it is kept — and either the steps or, waiting at a point, the pickup code.
     */
    fun card(): OrderCard {
        val code = view.tracked.shipments.firstNotNullOfOrNull { it.pickupCode }
        val method = METHODS[placed.method]
        val (title, accent, lead) =
            when (progress) {
                OrderProgress.ReadyForPickup -> {
                    val kept = heldUntil()?.let { "kept until " + MONTH_DAY.format(it) }
                    Triple("Ready for pickup", "pickup", listOfNotNull(method, pointLine(), kept).joinToString(" · "))
                }

                OrderProgress.Placing, OrderProgress.Placed, OrderProgress.Packed, OrderProgress.InTransit -> {
                    val (title, word) = arriving()
                    val lead = if (pickup) listOfNotNull(method, pointLine()).joinToString(" · ") else null
                    Triple(title, word, lead)
                }

                else -> {
                    heading()
                }
            }
        return OrderCard(
            meta = meta(),
            title = title,
            accent = accent,
            lead = lead,
            steps = steps().takeIf { code == null },
            pickupCode = code,
        )
    }

    /** «#HL-48302 · Placed Oct 7 · $512.00». */
    private fun meta(): String =
        "#${order.id} · Placed ${MONTH_DAY.format(day(placed.placedAt.toInstant()))} · ${exact(placed.totalCents)}"

    /**
     * «Arriving tomorrow, 15:00 – 18:00» and the word in it the title accents: the day the last shipment
     * arrives, said as it is seen from today, and the chosen window when it is that day's.
     */
    private fun arriving(): Pair<String, String> {
        val day =
            view.tracked.shipments
                .mapNotNull(::arrival)
                .maxOrNull() ?: today
        val word = dayWord(day)
        // «– 18:00» kept together, as the canvas breaks the title on a phone («15:00 / – 18:00»): a
        // no-break space alone still lets a line end after the dash, a word joiner before it does not.
        val window =
            slot
                ?.takeIf { !pickup && it.day == day }
                ?.let { ", " + it.label.replace(" – ", " –\u2060\u00A0") }
                .orEmpty()
        return "Arriving $word$window" to word
    }

    /** «Two shipments, one per seller. We’ll update this page as each one moves.» */
    private fun shipmentsLead(): String {
        val shipments = view.tracked.shipments.size
        return if (shipments == 1) {
            "One shipment. We’ll update this page as it moves."
        } else {
            "${NUMBERS.getOrElse(shipments) { shipments.toString() }} shipments, one per seller. " +
                "We’ll update this page as each one moves."
        }
    }

    private fun courierLead(): String? = view.address?.let { "Courier to ${street(it)}." }

    /** «214 Bedford Ave · open until 21:00». */
    private fun pointLine(): String? = view.point?.let { "${it.name} · ${it.hours}" }

    private fun steps(): OrderSteps? {
        returned?.let {
            val at = ReturnStatus.ALL.indexOf(it.status)
            return OrderSteps(RETURN_STEPS, at, arrived = it.status == ReturnStatus.REFUNDED)
        }
        val labels =
            if (pickup) {
                listOf(
                    "Placed",
                    "Packed",
                    "Ready for pickup",
                    "Picked up",
                )
            } else {
                listOf("Placed", "Packed", "In transit", "Delivered")
            }
        val current =
            when (progress) {
                OrderProgress.Cancelled -> return null

                OrderProgress.Placing, OrderProgress.Placed -> 0

                OrderProgress.Packed -> 1

                // A pickup's road has no step of its own: it is packed until the point has it.
                OrderProgress.InTransit -> if (pickup) 1 else 2

                OrderProgress.ReadyForPickup -> 2

                OrderProgress.Delivered, OrderProgress.PickedUp -> 3

                // Drawn from the return above.
                OrderProgress.Returning, OrderProgress.Returned -> return null
            }
        return OrderSteps(labels, current, arrived)
    }

    /** A cancelled order's banner: why, that nothing was charged, and the way back to the cart. */
    private fun notice(): OrderNotice? {
        if (progress != OrderProgress.Cancelled) return null
        val title =
            if (order.cancelReason == CancelReason.PAYMENT_DECLINED) {
                "Your ${paymentName()} was declined"
            } else {
                "We couldn’t place this order"
            }
        return OrderNotice(
            title,
            "Nothing was charged. The items are back in your cart.",
            Link("Back to cart", toCart()),
        )
    }

    /** «card ···· 0002», or the way to pay by its name. */
    private fun paymentName(): String =
        when (payment) {
            PaymentMethod.Card, PaymentMethod.TestCard -> "card " + payment.label.removePrefix("Card ")
            null -> "payment"
            else -> payment.label + " payment"
        }

    /** The code of the first shipment waiting at the point, and how long it is kept. */
    private fun pickupCode(): PickupCode? {
        val waiting = view.tracked.shipments.firstOrNull { it.pickupCode != null } ?: return null
        val code = waiting.pickupCode ?: return null
        val until = waiting.heldUntil?.let { MONTH_DAY.format(day(it)) }
        val place = if (placed.method == DeliveryMethod.ParcelLocker) "locker" else "point"
        val action =
            if (placed.method ==
                DeliveryMethod.ParcelLocker
            ) {
                "Enter the code at the locker."
            } else {
                "Show the code at the counter."
            }
        val kept =
            until?.let {
                " The $place keeps the parcel until $it, then it goes back to the seller and you’re refunded."
            }
        return PickupCode("Pickup code", code, action + kept.orEmpty(), until)
    }

    /**
     * Each seller's shipment with what it still holds, then — once something went back — the returned lines
     * as one card of their own («Returned items», `Order_Returned`). A shipment all of whose lines went back
     * is drawn only there.
     */
    private fun shipments(): List<OrderShipment> {
        val back =
            returned
                ?.lines
                ?.map { it.position }
                ?.toSet()
                .orEmpty()
        val kept = view.tracked.shipments.mapNotNull { shipment(it, back) }
        val ret = returned ?: return kept
        val status = RETURN_STATUSES.getValue(ret.status)
        val day = ret.history[ret.status]?.let { MONTH_DAY.format(day(it)) }
        return kept +
            OrderShipment(
                id = "return",
                seller = "Returned items",
                status = status,
                eta = listOfNotNull(status, day).joinToString(" "),
                returned = true,
                items =
                    placed.lines
                        .withIndex()
                        .filter { it.index in back }
                        .map { item(it.value, received = false) },
            )
    }

    private fun shipment(
        shipment: TrackedShipment,
        back: Set<Int>,
    ): OrderShipment? {
        val received = shipment.status == ShipmentStatus.DELIVERED || shipment.status == ShipmentStatus.PICKED_UP
        val lines = placed.lines.withIndex().filter { it.value.sellerId == shipment.sellerId && it.index !in back }
        if (lines.isEmpty() && back.isNotEmpty()) return null
        return OrderShipment(
            id = shipment.id,
            seller = view.sellers[shipment.sellerId] ?: shipment.sellerId,
            status = STATUSES[shipment.status] ?: shipment.status,
            eta = eta(shipment),
            cancelled = shipment.status == ShipmentStatus.CANCELLED,
            items = lines.map { item(it.value, received) },
        )
    }

    private fun item(
        line: OrderLine,
        received: Boolean,
    ): OrderItem {
        val bought = view.products[line.skuId]
        return OrderItem(
            title = line.title,
            details = details(line),
            tone = bought?.tone.orEmpty(),
            action = bought?.let { productLink(it.productId) },
            review = bought?.review?.takeIf { received }?.let { Link("Write a review", it) },
        )
    }

    /** «Moss · M · 1 × $80»: the options, the quantity and the price paid. */
    private fun details(line: OrderLine): String =
        listOfNotNull(
            view.products[line.skuId]?.options?.ifEmpty { null },
            "${line.quantity} × ${money(line.priceCents)}",
        ).joinToString(" · ")

    /** When or where a shipment arrives, or that it did: the mono line at the right of its seller. */
    private fun eta(shipment: TrackedShipment): String =
        when (shipment.status) {
            ShipmentStatus.CANCELLED -> {
                "Not shipped"
            }

            ShipmentStatus.DELIVERED, ShipmentStatus.PICKED_UP -> {
                val verb = if (shipment.status == ShipmentStatus.DELIVERED) "Delivered" else "Picked up"
                listOfNotNull(
                    verb,
                    shipment.history[shipment.status]?.let { MONTH_DAY.format(day(it)) },
                ).joinToString(" ")
            }

            else -> {
                if (pickup) {
                    listOfNotNull(METHODS[placed.method], view.point?.name).joinToString(" · ")
                } else {
                    val day = arrival(shipment) ?: today
                    // On its way, the day is said as it is seen from today; before, with its date.
                    val moving = shipment.status == ShipmentStatus.IN_TRANSIT
                    val label =
                        when (day) {
                            today -> "Today"
                            today.plusDays(1) -> if (moving) "Tomorrow" else "Tomorrow, " + MONTH_DAY.format(day)
                            else -> DAY.format(day)
                        }
                    label + (slot?.takeIf { it.day == day }?.let { " · ${it.label}" }.orEmpty())
                }
            }
        }

    /**
     * The day a shipment still on its way arrives, never before today: the courier's day for an order
     * placed when this one was — the day after, plus the seller's dispatch days, one more after the
     * 23:30 cut-off, one more again for a point or a locker (research D7) — or the window chosen, when
     * that is later.
     */
    private fun arrival(shipment: TrackedShipment): LocalDate? {
        if (shipment.status == ShipmentStatus.CANCELLED) return null
        val at = placed.placedAt.atZoneSameInstant(STORE)
        val dispatch =
            placed.lines
                .filter { it.sellerId == shipment.sellerId }
                .maxOfOrNull { view.products[it.skuId]?.dispatchDays ?: 0 } ?: 0
        val late = if (at.toLocalTime().isBefore(DeliveryCalendar.CUTOFF)) 0 else 1
        val earliest = at.toLocalDate().plusDays(1L + dispatch + late + if (pickup) 1 else 0)
        val promised = slot?.day?.takeIf { !pickup && it.isAfter(earliest) } ?: earliest
        return maxOf(promised, today)
    }

    /** «tomorrow», «today», or the day itself. */
    private fun dayWord(day: LocalDate): String =
        when (day) {
            today -> "today"
            today.plusDays(1) -> "tomorrow"
            else -> DAY.format(day)
        }

    /** The day the last shipment arrived. */
    private fun arrivedOn(): LocalDate? =
        view.tracked.shipments
            .mapNotNull { it.history[ShipmentStatus.DELIVERED] ?: it.history[ShipmentStatus.PICKED_UP] }
            .maxOrNull()
            ?.let(::day)

    private fun heldUntil(): LocalDate? =
        view.tracked.shipments
            .firstNotNullOfOrNull { it.heldUntil }
            ?.let(::day)

    private fun summary(): OrderTotals {
        val units = placed.lines.sumOf { it.quantity }
        val rows =
            buildList {
                add(SummaryRow("Items ($units)", exact(placed.itemsCents)))
                if (placed.discountCents >
                    0
                ) {
                    add(SummaryRow("Discount", "−" + exact(placed.discountCents), saving = true))
                }
                // Paid in part with points (B-23): a point a cent, as the checkout's summary drew it.
                if (placed.pointsRedeemed > 0) {
                    add(SummaryRow("Points", "−" + exact(placed.pointsRedeemed), saving = true))
                }
                add(SummaryRow("Delivery", if (placed.deliveryCents == 0) "Free" else exact(placed.deliveryCents)))
                // The money given back (B-55): the rows then add up to «Paid», which is what was paid in money
                // and kept — the points that came back are the payment fact's «+ N points back», not money.
                if (refunded) {
                    add(SummaryRow("Refunded", "−" + exact(moneyBack), saving = true))
                }
            }
        val cancelled = progress == OrderProgress.Cancelled
        val form = returnForm()
        return OrderTotals(
            title = "Summary",
            rows = rows,
            totalLabel = if (refunded) "Paid" else "Total",
            total = exact(placed.totalCents - if (refunded) moneyBack else 0),
            voided = cancelled,
            facts =
                listOfNotNull(
                    paid(),
                    place().takeUnless { cancelled || returned != null },
                    points(),
                    reversed(),
                ),
            reorderLabel = "Reorder".takeIf { arrived },
            reorderUrl = OrderPaths.reorder(order.id).takeIf { arrived },
            returnLabel = "Return items".takeIf { form != null },
            returnAction = form?.let { PresentAction(it, ReviewTabs.DIALOG) },
            back = Link("Back to cart", toCart()).takeIf { cancelled },
            plan = plan(),
        )
    }

    /**
     * Haul Pay's schedule (B-24) — no artboard draws it, so it is the summary's rows under the payment fact:
     * each payment's day, where it stands and what it charges; before the first shipment ships the days are
     * counted from it. `null` for every other way to pay and for a cancelled order.
     */
    private fun plan(): PaymentPlan? {
        val plan = view.tracked.plan ?: return null
        return PaymentPlan(
            title = "${plan.instalments.size} payments, two weeks apart",
            payments =
                plan.instalments.map { instalment ->
                    PlanPayment(
                        label = instalment.dueAt?.let { MONTH_DAY.format(day(it)) } ?: UNSTARTED[instalment.number - 1],
                        detail = standing(instalment),
                        amount =
                            exact(
                                if (instalment.status == InstalmentStatus.PAID) {
                                    instalment.chargeCents ?: instalment.owedCents
                                } else {
                                    instalment.owedCents
                                },
                            ),
                        state = state(instalment),
                    )
                },
        )
    }

    /** How one payment is drawn: paid, covered, declined — once or for good — or still to come. */
    private fun state(instalment: Instalment): PlanPaymentState =
        when {
            instalment.status == InstalmentStatus.PAID -> PlanPaymentState.Paid
            instalment.status == InstalmentStatus.COVERED -> PlanPaymentState.Covered
            instalment.status == InstalmentStatus.OVERDUE -> PlanPaymentState.Declined
            instalment.declined > 0 -> PlanPaymentState.Declined
            else -> PlanPaymentState.Upcoming
        }

    /** Where one payment stands, in words: «Paid», «Upcoming», «Declined, tried again Oct 23», «Covered by your return». */
    private fun standing(instalment: Instalment): String {
        val reduced = instalment.reducedCents > 0
        return when (instalment.status) {
            InstalmentStatus.PAID -> {
                val paid = instalment.paidAt?.let(::day)
                if (paid == null || paid == instalment.dueAt?.let(::day)) "Paid" else "Paid " + MONTH_DAY.format(paid)
            }

            InstalmentStatus.COLLECTING -> {
                "Processing"
            }

            InstalmentStatus.COVERED -> {
                "Covered by your return"
            }

            InstalmentStatus.OVERDUE -> {
                "Declined twice · overdue"
            }

            else -> {
                val retry =
                    instalment.nextAttemptAt?.takeIf { instalment.declined > 0 }?.let {
                        MONTH_DAY.format(
                            day(it),
                        )
                    }
                when {
                    retry != null -> "Declined, tried again $retry"
                    reduced -> "Upcoming · reduced by your return"
                    else -> "Upcoming"
                }
            }
        }
    }

    /** A Haul Pay order's payment fact: how much of the plan is paid and what comes next, or why it stopped. */
    private fun planned(plan: InstalmentPlan): String {
        val payments = plan.instalments.size
        val next = plan.next
        val overdue = plan.instalments.firstOrNull { it.status == InstalmentStatus.OVERDUE }
        return when {
            !plan.started -> {
                "$payments interest-free payments of ${exact(
                    plan.instalments.first().amountCents,
                )}, the first when it ships"
            }

            overdue != null -> {
                "Payment ${overdue.number} was declined twice — the plan is overdue"
            }

            next == null -> {
                "${exact(plan.paidCents)} paid in $payments payments"
            }

            else -> {
                val due = next.nextAttemptAt?.let { " on " + MONTH_DAY.format(day(it)) }.orEmpty()
                "${exact(plan.paidCents)} of ${exact(plan.totalCents)} paid · next ${exact(next.owedCents)}$due"
            }
        }
    }

    /**
     * «Return items» (`Order_ReturnDialog`): the lines of an order that has arrived, each still inside its 30
     * days, with what returning it gives back and the part of that which comes back as points (B-50); `null` —
     * no button — once nothing can go back any more. One return per order, so an order with a return has none.
     */
    private fun returnForm(): ReturnForm? {
        if (!arrived || returned != null) return null
        val now = view.now.toInstant()
        val refunds = ReturnRefunds.of(order)
        val pointsBack = ReturnRefunds.pointsBack(order)
        val lines =
            placed.lines.withIndex().mapNotNull { (position, line) ->
                val shipment = view.tracked.shipments.firstOrNull { it.sellerId == line.sellerId }
                val at = shipment?.let { it.history[ShipmentStatus.DELIVERED] ?: it.history[ShipmentStatus.PICKED_UP] }
                if (at == null || !ReturnWindow.open(at, now)) return@mapNotNull null
                ReturnLine(
                    position,
                    line.title,
                    details(line),
                    view.products[line.skuId]?.tone.orEmpty(),
                    refunds.getValue(position),
                    pointsBack.getValue(position),
                )
            }
        if (lines.isEmpty()) return null
        val day = arrivedOn() ?: return null
        val verb = if (progress == OrderProgress.PickedUp) "picked up" else "delivered"
        val points = placed.points > 0
        return ReturnForm(
            id = "return-form",
            title = "Return items",
            meta =
                "#${order.id} · $verb ${MONTH_DAY.format(day)} · returns until " +
                    MONTH_DAY.format(ReturnWindow.lastDay(day)),
            lines = lines,
            reasonLabel = "Reason",
            reasonHint = "Choose a reason",
            reasons = ReturnReason.entries.map { ReasonOption(it.id, it.label) },
            refund =
                when (payment) {
                    PaymentMethod.OnDelivery -> "Refund {amount} in cash, from the courier"
                    PaymentMethod.HaulPayPlan -> "Refund {amount} to your Haul Pay plan"
                    else -> "Refund {amount} to ${paymentName()}"
                },
            // Paid partly with points (B-50): the box reads the money and, under it, the points that come back.
            pointsBack = "+ {points} points back".takeIf { placed.pointsRedeemed > 0 },
            note = collection(),
            pointsOne = "Points earned on this line are reversed.".takeIf { points },
            pointsMany = "Points earned on these lines are reversed.".takeIf { points },
            submitLabel = "Request return",
            cancelLabel = "Cancel",
            url = ReturnPaths.returns(order.id),
            close = CloseAction,
        )
    }

    /**
     * How it was paid, and how much of it has been charged so far — or, once a return is refunded, the money given
     * back and, beside it, the points that came back («$155.10 refunded Oct 12 · + 790 points back», B-55).
     */
    private fun paid(): OrderFact {
        returned?.takeIf { refunded }?.let { ret ->
            val on = ret.history[ReturnStatus.REFUNDED]?.let { " " + MONTH_DAY.format(day(it)) }.orEmpty()
            return OrderFact(
                OrderFactKind.Card,
                payment?.label ?: placed.payment,
                "${exact(moneyBack)} refunded$on$pointsBackNote",
            )
        }
        val total = placed.totalCents
        val captured = view.tracked.shipments.sumOf { it.capturedCents }
        val several = view.tracked.shipments.size > 1
        val detail =
            when {
                progress == OrderProgress.Cancelled -> {
                    if (order.cancelReason ==
                        CancelReason.PAYMENT_DECLINED
                    ) {
                        "Declined · nothing charged"
                    } else {
                        "Nothing charged"
                    }
                }

                payment == PaymentMethod.OnDelivery -> {
                    "Paid when it is handed over"
                }

                view.tracked.plan != null -> {
                    planned(view.tracked.plan)
                }

                captured == 0 -> {
                    "Authorised at placement, charged per shipment when it ships"
                }

                captured < total -> {
                    "${exact(captured)} of ${exact(total)} charged, the rest when it ships"
                }

                arrived -> {
                    "${exact(captured)} charged"
                }

                several -> {
                    "${exact(captured)} charged as the shipments shipped"
                }

                else -> {
                    "${exact(captured)} charged when it shipped"
                }
            }
        return OrderFact(OrderFactKind.Card, payment?.label ?: placed.payment, detail)
    }

    /** Where it goes: the courier's address, or the point with how far and how long it is open. */
    private fun place(): OrderFact? =
        if (pickup) {
            view.point?.let { point ->
                val distance = point.distanceMeters?.let { "${count(it)} m" }
                OrderFact(
                    OrderFactKind.Place,
                    point.name,
                    listOfNotNull(METHODS[placed.method], distance, point.hours).joinToString(" · "),
                )
            }
        } else {
            view.address?.let { OrderFact(OrderFactKind.Place, street(it), "${it.city} ${it.zip} · courier") }
        }

    /** The points the order earns, while none of it has moved yet (`Order_Placed`). */
    private fun points(): OrderFact? {
        if (progress != OrderProgress.Placed || placed.points <= 0) return null
        val delivered = if (view.tracked.shipments.size > 1) "the shipments are delivered" else "it is delivered"
        return OrderFact(OrderFactKind.Points, "${count(placed.points)} points", "Credited when $delivered")
    }

    /** The points a refunded return took back (`Order_Returned`: «−174 points»). */
    private fun reversed(): OrderFact? {
        val ret = returned?.takeIf { refunded && it.points > 0 } ?: return null
        return OrderFact(
            OrderFactKind.Points,
            "−${count(ret.points)} points",
            "Points earned on the returned lines were reversed",
        )
    }

    private fun street(address: AddressEntry): String =
        address.street +
            address.apt
                .ifBlank { null }
                ?.let { ", Apt $it" }
                .orEmpty()

    private fun toCart() = NavigateAction(Frame.CART)

    private fun day(instant: Instant): LocalDate = instant.atZone(STORE).toLocalDate()

    private fun exact(cents: Int): String = CartScreen.exact(cents)

    private companion object {
        val STORE = DeliveryCalendar.STORE

        val MONTH_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

        val STATUSES =
            mapOf(
                ShipmentStatus.PLACED to "Placed",
                ShipmentStatus.PACKED to "Packed",
                ShipmentStatus.IN_TRANSIT to "In transit",
                ShipmentStatus.READY_FOR_PICKUP to "Ready for pickup",
                ShipmentStatus.DELIVERED to "Delivered",
                ShipmentStatus.PICKED_UP to "Picked up",
                ShipmentStatus.CANCELLED to "Cancelled",
            )

        val RETURN_STATUSES =
            mapOf(
                ReturnStatus.REQUESTED to "Requested",
                ReturnStatus.PICKED_UP to "Picked up",
                ReturnStatus.REFUNDED to "Refunded",
            )

        /** A return's steps (`Order_Returned`: «Requested · Picked up · Refunded»), in [ReturnStatus.ALL]'s order. */
        val RETURN_STEPS = ReturnStatus.ALL.map { RETURN_STATUSES.getValue(it) }

        val METHODS =
            mapOf(
                DeliveryMethod.Courier to "Courier",
                DeliveryMethod.PickupPoint to "Pickup point",
                DeliveryMethod.ParcelLocker to "Parcel locker",
            )

        /** A plan's days before it starts, counted from the first shipment's leaving (B-24). */
        val UNSTARTED = listOf("When it ships", "In 2 weeks", "In 4 weeks", "In 6 weeks")

        val NUMBERS = listOf("No", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten")
    }
}
