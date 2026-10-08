package io.github.youndie.haul.feature.checkout.screen

import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.cart.screen.CartScreen
import io.github.youndie.haul.feature.cart.screen.summaryRows
import io.github.youndie.haul.feature.catalog.domain.count
import io.github.youndie.haul.feature.catalog.domain.money
import io.github.youndie.haul.feature.checkout.CheckoutPaths
import io.github.youndie.haul.feature.checkout.DeliveryMethod
import io.github.youndie.haul.feature.checkout.domain.Address
import io.github.youndie.haul.feature.checkout.domain.CheckoutCommands
import io.github.youndie.haul.feature.checkout.domain.CheckoutError
import io.github.youndie.haul.feature.checkout.domain.CheckoutState
import io.github.youndie.haul.feature.checkout.domain.Slot
import io.github.youndie.haul.ui.AddressOption
import io.github.youndie.haul.ui.CheckoutAddress
import io.github.youndie.haul.ui.CheckoutHeader
import io.github.youndie.haul.ui.CheckoutNotice
import io.github.youndie.haul.ui.CheckoutSummary
import io.github.youndie.haul.ui.DeliveryMethods
import io.github.youndie.haul.ui.DeliverySlots
import io.github.youndie.haul.ui.FormField
import io.github.youndie.haul.ui.MethodOption
import io.github.youndie.haul.ui.PageTitle
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

/**
 * `GET /ui/checkout` (screen-checkout, feature-checkout). One tree covers every server state: Content,
 * PickupPoint, ParcelLocker, Validation and PlaceError are what the stored checkout is. The checkout has
 * its own header instead of the store's frame. Points (`Checkout_PointsApplied`) are feature-membership's
 * and are not drawn until a customer has a balance (B-23).
 */
internal class CheckoutScreen(
    private val commands: CheckoutCommands,
) {
    suspend fun build(owner: CartOwner.Customer): KompotComponent {
        val state = commands.state(owner)
        val quote = state.quote
        val units = quote.lines.sumOf { it.stored.quantity }
        val sections =
            buildList {
                add(
                    CheckoutHeader(
                        "checkout-header",
                        STEPS,
                        current = 0,
                        secureLabel = "Secure checkout",
                        home = NavigateAction("/"),
                    ),
                )
                if (state.slotFilled) add(CheckoutNotice("slot-filled", CheckoutError.SLOT_FILLED))
                state.expiredPromo?.let {
                    add(
                        CheckoutNotice("promo-expired", "Code $it has expired and is not in the total"),
                    )
                }
                add(PageTitle("title", "Checkout", if (units == 1) "1 item" else "${count(units)} items"))
                add(methods(state))
                when (quote.method) {
                    DeliveryMethod.Courier -> {
                        add(address(state))
                        add(slots(state))
                    }

                    DeliveryMethod.PickupPoint, DeliveryMethod.ParcelLocker -> {
                        add(points(state))
                    }
                }
                add(payments(state))
                add(summary(state))
            }
        return ColumnComponent(id = "checkout", children = sections)
    }

    private fun methods(state: CheckoutState): DeliveryMethods {
        val fee = state.quote.totals.deliveryCents
        val detail = if (fee == 0) "Free" else CartScreen.exact(fee)
        return DeliveryMethods(
            id = "methods",
            title = "How to receive it",
            options =
                METHODS.map { (method, label) ->
                    MethodOption(method, label, detail, selected = method == state.quote.method)
                },
            url = CheckoutPaths.CHOICE,
        )
    }

    private fun address(state: CheckoutState): CheckoutAddress {
        val draft = state.draft
        val errors = state.draftProblems.associate { it.field to it.message }
        val values =
            mapOf(
                "street" to draft?.street,
                "apt" to draft?.apt,
                "city" to draft?.city,
                "zip" to draft?.zip,
                "doorCode" to draft?.doorCode,
                "courierNote" to draft?.courierNote,
            )
        return CheckoutAddress(
            id = "address",
            title = "Delivery address",
            addresses =
                state.addresses.map {
                    AddressOption(it.id, line(it), "${it.city} ${it.zip}", selected = it.id == state.quote.address?.id)
                },
            choiceUrl = CheckoutPaths.CHOICE,
            url = CheckoutPaths.ADDRESSES,
            form =
                FIELDS.map { (name, label, required) ->
                    FormField(name, label, values[name].orEmpty(), required, errors[name])
                },
            formOpen = state.addresses.isEmpty() || draft != null,
            addLabel = "Add an address",
            submitLabel = "Save address",
        )
    }

    private fun slots(state: CheckoutState): DeliverySlots =
        DeliverySlots(
            id = "slots",
            title = "Delivery window",
            days =
                state.slots.groupBy { it.slot.day }.map { (day, loads) ->
                    SlotDay(
                        label = Slot.dayLabel(day),
                        slots =
                            loads.map {
                                SlotOption(
                                    it.slot.id,
                                    it.slot.label,
                                    available = !it.full,
                                    selected =
                                        it.slot == state.quote.slot,
                                )
                            },
                    )
                },
            url = CheckoutPaths.CHOICE,
        )

    private fun points(state: CheckoutState): PickupPoints =
        PickupPoints(
            id = "points",
            title =
                if (state.quote.method ==
                    DeliveryMethod.ParcelLocker
                ) {
                    "Parcel lockers nearby"
                } else {
                    "Pickup points nearby"
                },
            points =
                state.points.map {
                    PickupPointOption(
                        id = it.id,
                        name = it.name,
                        distance = it.distanceMeters?.let { meters -> "${count(meters)} m" },
                        hours = it.hours,
                        selected = it.id == state.quote.point?.id,
                    )
                },
            url = CheckoutPaths.CHOICE,
        )

    private fun payments(state: CheckoutState): PaymentMethods {
        val total = state.quote.totals.totalCents
        return PaymentMethods(
            id = "payment",
            title = "Payment",
            options =
                state.payments.map {
                    PaymentOption(
                        it.id,
                        it.label,
                        it.detail(total),
                        selected =
                            it == state.quote.payment,
                    )
                },
            url = CheckoutPaths.CHOICE,
        )
    }

    private fun summary(state: CheckoutState): CheckoutSummary {
        val quote = state.quote
        // The canvas writes the total as a price tag («$512», «$487.20»), as the cart does, and the
        // button with its cents («Place order · $512.00», Checkout_Content).
        val exact = CartScreen.exact(quote.totals.totalCents)
        return CheckoutSummary(
            id = "summary",
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
            rows = summaryRows(quote.totals, quote.promo?.code, quote.lines.sumOf { it.stored.quantity }),
            totalLabel = "Total",
            total = money(quote.totals.totalCents),
            points = "You'll earn ${count(quote.totals.points(quote.plus))} points",
            note = if (quote.payment.card) "Your card is charged when the order ships" else null,
            placeLabel = "Place order · $exact",
            placeEnabled = quote.complete,
            quote = quote.fingerprint,
        )
    }

    private fun line(address: Address): String = address.street + (address.apt?.let { ", Apt $it" } ?: "")

    private companion object {
        val STEPS = listOf("Delivery", "Payment", "Review")

        val METHODS =
            listOf(
                DeliveryMethod.Courier to "Courier",
                DeliveryMethod.PickupPoint to "Pickup point",
                DeliveryMethod.ParcelLocker to "Parcel locker",
            )

        /** The address form's fields, in its order: the body's property, the label, whether it is required. */
        val FIELDS =
            listOf(
                Triple("street", "Street and house number", true),
                Triple("apt", "Apartment", false),
                Triple("city", "City", true),
                Triple("zip", "ZIP code", true),
                Triple("doorCode", "Door code", false),
                Triple("courierNote", "Note for the courier", false),
            )
    }
}
