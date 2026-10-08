package io.github.youndie.haul.feature.checkout.screen

import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.cart.screen.CartScreen
import io.github.youndie.haul.feature.cart.screen.summaryRows
import io.github.youndie.haul.feature.catalog.domain.count
import io.github.youndie.haul.feature.catalog.domain.money
import io.github.youndie.haul.feature.checkout.CheckoutPaths
import io.github.youndie.haul.feature.checkout.DeliveryMethod
import io.github.youndie.haul.feature.checkout.domain.CheckoutCommands
import io.github.youndie.haul.feature.checkout.domain.CheckoutError
import io.github.youndie.haul.feature.checkout.domain.CheckoutState
import io.github.youndie.haul.feature.checkout.domain.PickupPoint
import io.github.youndie.haul.feature.checkout.domain.Slot
import io.github.youndie.haul.ui.CheckoutAddress
import io.github.youndie.haul.ui.CheckoutBody
import io.github.youndie.haul.ui.CheckoutHeader
import io.github.youndie.haul.ui.CheckoutNotice
import io.github.youndie.haul.ui.CheckoutSummary
import io.github.youndie.haul.ui.DeliveryMethods
import io.github.youndie.haul.ui.DeliverySlots
import io.github.youndie.haul.ui.FormField
import io.github.youndie.haul.ui.MethodOption
import io.github.youndie.haul.ui.PaymentMethods
import io.github.youndie.haul.ui.PaymentOption
import io.github.youndie.haul.ui.PickupPointOption
import io.github.youndie.haul.ui.PickupPoints
import io.github.youndie.haul.ui.SlotDay
import io.github.youndie.haul.ui.SlotOption
import io.github.youndie.haul.ui.SummaryItem
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * `GET /ui/checkout` (screen-checkout, feature-checkout): the checkout's own header and one
 * [CheckoutBody]. One tree covers every server state: Content, PickupPoint, ParcelLocker, Validation and
 * PlaceError are what the stored checkout is. The copy is the canvas's (`Checkout_*`). Points
 * (`PaymentMethods.points`, `Checkout_PointsApplied`) are feature-membership's and are not sent until a
 * customer has a balance (B-23).
 */
internal class CheckoutScreen(
    private val commands: CheckoutCommands,
) {
    suspend fun build(owner: CartOwner.Customer): KompotComponent {
        val state = commands.state(owner)
        val courier = state.quote.method == DeliveryMethod.Courier
        val notices =
            buildList {
                if (state.slotFilled) add(CheckoutNotice("slot-filled", CheckoutError.SLOT_FILLED))
                state.expiredPromo?.let {
                    add(CheckoutNotice("promo-expired", "Code $it has expired and is not in the total"))
                }
            }
        return ColumnComponent(
            id = "checkout",
            children =
                listOf(
                    CheckoutHeader(
                        "checkout-header",
                        STEPS,
                        current = 0,
                        secureLabel = "Secure checkout",
                        home = NavigateAction("/"),
                    ),
                    CheckoutBody(
                        id = "checkout-body",
                        title = "Checkout",
                        notices = notices,
                        methods = methods(state),
                        address = if (courier) address(state) else null,
                        slots = if (courier) slots(state) else null,
                        points = if (courier) null else points(state),
                        payment = payments(state),
                        summary = summary(state),
                    ),
                ),
        )
    }

    /**
     * Each way to receive with when — the courier's first window («Tomorrow, Oct 8»), a pickup a day
     * later — and where: the nearest point's distance, the nearest locker's hours.
     */
    private fun methods(state: CheckoutState): DeliveryMethods {
        val fee = state.quote.totals.deliveryCents
        val price = if (fee == 0) "Free" else CartScreen.exact(fee)

        fun nearest(method: DeliveryMethod): PickupPoint? = state.places.firstOrNull { it.method == method }
        val pickup = day(state.pickupDay)
        val details =
            mapOf(
                DeliveryMethod.Courier to "Tomorrow, " + MONTH_DAY.format(state.firstDay),
                DeliveryMethod.PickupPoint to
                    listOfNotNull(pickup, nearest(DeliveryMethod.PickupPoint)?.distance()?.let { "$it away" })
                        .joinToString(" · "),
                DeliveryMethod.ParcelLocker to
                    listOfNotNull(pickup, nearest(DeliveryMethod.ParcelLocker)?.hours?.let { "$it access" })
                        .joinToString(" · "),
            )
        return DeliveryMethods(
            id = "methods",
            title = "How to receive",
            options =
                METHODS.map { (method, label) ->
                    MethodOption(method, label, details[method], price, selected = method == state.quote.method)
                },
            url = CheckoutPaths.CHOICE,
        )
    }

    /** The form holds the address delivered to; a refused form, what was sent and why. */
    private fun address(state: CheckoutState): CheckoutAddress {
        val errors = state.draftProblems.associate { it.field to it.message }
        val shown = state.draft ?: state.quote.address?.entry()
        val values =
            mapOf(
                "street" to shown?.street,
                "apt" to shown?.apt,
                "city" to shown?.city,
                "zip" to shown?.zip,
                "doorCode" to shown?.doorCode,
                "courierNote" to shown?.courierNote,
            )
        return CheckoutAddress(
            id = "address",
            title = "Address",
            form =
                FIELDS.map { field ->
                    FormField(
                        field.name,
                        field.label,
                        values[field.name].orEmpty(),
                        field.placeholder,
                        field.required,
                        errors[field.name],
                    )
                },
            url = CheckoutPaths.ADDRESSES,
        )
    }

    /**
     * Five days of windows. The day shown is the chosen window's, or the one that filled up, or the
     * first; a full window says so in its label.
     */
    private fun slots(state: CheckoutState): DeliverySlots {
        val shownDay =
            (state.quote.slot ?: state.filledSlot)?.day ?: state.slots
                .firstOrNull()
                ?.slot
                ?.day
        return DeliverySlots(
            id = "slots",
            title = "Delivery time",
            days =
                state.slots.groupBy { it.slot.day }.map { (day, loads) ->
                    SlotDay(
                        weekday = Slot.weekday(day),
                        date = day.dayOfMonth.toString(),
                        selected = day == shownDay,
                        slots =
                            loads.map {
                                SlotOption(
                                    it.slot.id,
                                    if (it.full) "${it.slot.label} · Full" else it.slot.label,
                                    available = !it.full,
                                    selected = it.slot == state.quote.slot,
                                )
                            },
                    )
                },
            notice = state.filledSlot?.let { "Pick another window for ${day(it.day)}" },
            url = CheckoutPaths.CHOICE,
        )
    }

    private fun points(state: CheckoutState): PickupPoints {
        val label = METHODS.toMap().getValue(state.quote.method)
        val pickup = day(state.pickupDay)
        return PickupPoints(
            id = "points",
            title = label,
            points =
                state.points.map {
                    PickupPointOption(
                        id = it.id,
                        name = it.name,
                        detail = "$label · ${it.hours} · $pickup",
                        distance = it.distance(),
                        selected = it.id == state.quote.point?.id,
                    )
                },
            url = CheckoutPaths.CHOICE,
        )
    }

    private fun payments(state: CheckoutState): PaymentMethods {
        val total = state.quote.totals.totalCents
        return PaymentMethods(
            id = "payment",
            title = "Payment",
            options =
                state.payments.map {
                    PaymentOption(it.id, it.label, it.detail(total), selected = it == state.quote.payment)
                },
            url = CheckoutPaths.CHOICE,
        )
    }

    private fun summary(state: CheckoutState): CheckoutSummary {
        val quote = state.quote
        // The canvas writes the total as a price tag («$512», «$487.20»), as the cart does, and the
        // button with its cents («Place order · $512.00», Checkout_Content).
        val exact = CartScreen.exact(quote.totals.totalCents)
        val deliveryDay =
            when (quote.method) {
                DeliveryMethod.Courier -> (quote.slot ?: state.filledSlot)?.day
                DeliveryMethod.PickupPoint, DeliveryMethod.ParcelLocker -> state.pickupDay
            }
        return CheckoutSummary(
            id = "summary",
            title = "Your order",
            items =
                quote.lines.map {
                    SummaryItem(
                        title = it.item.product.title,
                        options =
                            it.sku.options.entries
                                .sortedBy { option -> option.key != "colour" }
                                .joinToString(" · ") { option -> option.value },
                        quantity = it.stored.quantity,
                        price = money(it.priceCents),
                        tone = it.item.product.tone,
                        label = it.item.product.label,
                    )
                },
            rows =
                summaryRows(
                    quote.totals,
                    quote.promo?.code,
                    quote.lines.sumOf { it.stored.quantity },
                    delivery = listOfNotNull("Delivery", deliveryDay?.let(::day)).joinToString(" · "),
                ),
            totalLabel = "Total",
            total = money(quote.totals.totalCents),
            note =
                "By placing the order you agree to the Terms of Sale." +
                    if (quote.payment.card) " Your card is charged when the order ships." else "",
            placeLabel = "Place order · $exact",
            placingLabel = "Placing order…",
            placeEnabled = state.placeable,
            placeHint = if (state.placeable) null else hint(state),
            placeUrl = CheckoutPaths.PLACE,
            quote = quote.fingerprint,
        )
    }

    /** What keeps the order from being placed, as the button's hint says it. */
    private fun hint(state: CheckoutState): String {
        val missing =
            state.holdingProblems
                .map { it.field }
                .distinct()
                .mapNotNull { FIELD_NAMES[it] }
        return when {
            missing.isNotEmpty() -> "Fill in the " + missing.joinedWithAnd()
            state.quote.method != DeliveryMethod.Courier -> "Pick a place to collect the order"
            state.quote.address == null -> "Fill in the delivery address"
            else -> "Pick a delivery window"
        }
    }

    private fun PickupPoint.distance(): String? = distanceMeters?.let { "${count(it)} m" }

    private fun day(date: LocalDate): String = Slot.dayLabel(date)

    private fun List<String>.joinedWithAnd(): String =
        if (size < 2) joinToString("") else dropLast(1).joinToString(", ") + " and " + last()

    /** A field of the address form: the body's property, its label, what an empty one says, whether it is required. */
    private data class Field(
        val name: String,
        val label: String,
        val placeholder: String? = null,
        val required: Boolean = false,
    )

    private companion object {
        val STEPS = listOf("Delivery", "Payment", "Review")

        val METHODS =
            listOf(
                DeliveryMethod.Courier to "Courier",
                DeliveryMethod.PickupPoint to "Pickup point",
                DeliveryMethod.ParcelLocker to "Parcel locker",
            )

        /** The address form's fields, in its order (`Checkout_Content`). */
        val FIELDS =
            listOf(
                Field("street", "Street address", required = true),
                Field("apt", "Apt / suite"),
                Field("city", "City", required = true),
                Field("zip", "ZIP", required = true),
                Field("doorCode", "Door code", placeholder = "Optional"),
                Field("courierNote", "Note for courier", placeholder = "e.g. leave with the doorman"),
            )

        /** How the button's hint names a required field at fault («Fill in the street address and ZIP»). */
        val FIELD_NAMES = mapOf("street" to "street address", "city" to "city", "zip" to "ZIP")

        val MONTH_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    }
}
