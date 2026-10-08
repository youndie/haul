package io.github.youndie.haul.feature.checkout.domain

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.feature.cart.domain.CartCommands
import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.cart.domain.CartRepository
import io.github.youndie.haul.feature.cart.domain.PricedLine
import io.github.youndie.haul.feature.cart.domain.PromoCode
import io.github.youndie.haul.feature.cart.domain.Totals
import io.github.youndie.haul.feature.cart.domain.activeAt
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.checkout.AddressEntry
import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.checkout.DeliveryMethod
import io.github.youndie.haul.feature.membership.domain.PointsLedger
import io.github.youndie.haul.feature.membership.domain.PointsRules

/**
 * Checkout (feature-checkout, endpoint-checkout): the quote and the choices that change it.
 *
 * The quote is the cart's — the lines it counts and its [Totals], computed by B-11's code and not again
 * here — with what the customer chose to receive and pay with, and the points they pay with ([ledger],
 * B-23). Every refusal is a [CheckoutError].
 */
internal class CheckoutCommands(
    private val checkouts: CheckoutRepository,
    private val slots: DeliverySlots,
    private val carts: CartRepository,
    private val cartCommands: CartCommands,
    private val ledger: PointsLedger,
    private val clock: StoreClock,
) {
    /**
     * The checkout as it stands: the quote, with every choice not made — or no longer possible — filled
     * with its default, and the options the shopper can choose instead. `cart_empty` when the cart counts
     * no line.
     */
    suspend fun state(owner: CartOwner.Customer): CheckoutState {
        val basis = basis(owner)
        val stored = checkouts.checkout(owner.id)
        val addresses = checkouts.addresses(owner.id)
        val method = stored.method
        val places = checkouts.pickupPoints()
        val points = places.filter { it.method == method }
        val loads = slots.loads(basis.offered)
        val chosen = stored.slotId?.let(Slot::parse)?.let { slot -> loads.firstOrNull { it.slot == slot } }
        val filled = chosen?.slot?.takeIf { method == DeliveryMethod.Courier && chosen.full }
        val slot =
            when {
                method != DeliveryMethod.Courier -> null
                chosen != null -> chosen.slot.takeUnless { chosen.full }
                else -> loads.firstOrNull { !it.full }?.slot
            }
        val redeemable = PointsRules.redeemable(ledger.balance(owner.id), basis.capCents)
        val redeemed = if (stored.usePoints) redeemable else 0
        val payable = basis.payable(redeemed)
        val allowed = PaymentMethod.entries.filter { it.allowed(method, payable) }
        val payment = allowed.firstOrNull { it.id == stored.payment } ?: PaymentMethod.DEFAULT
        val quote =
            Quote(
                lines = basis.lines,
                promo = basis.promo,
                totals = basis.totals,
                plus = owner.plus,
                method = method,
                address = if (method == DeliveryMethod.Courier) delivered(stored, addresses) else null,
                point = points.firstOrNull { it.id == stored.pointId } ?: points.firstOrNull(),
                slot = slot,
                payment = payment,
                pointsRedeemed = redeemed,
            )
        return CheckoutState(
            quote = quote,
            addresses = addresses,
            points = points,
            places = places,
            slots = loads,
            payments = allowed.filter { it.listed || it == payment },
            firstDay = basis.offered.first().day,
            filledSlot = filled,
            expiredPromo = basis.expiredPromo,
            draft = stored.draft,
            draftProblems = stored.draft?.let(::addressProblems).orEmpty(),
            redeemable = redeemable,
            usePoints = stored.usePoints,
        )
    }

    /** The quote alone: what placement (B-16) compares the shopper's fingerprint against. */
    suspend fun quote(owner: CartOwner.Customer): Quote = state(owner).quote

    /**
     * Changes what the checkout is quoted for; only the fields given change. A window must be one of
     * those offered and have room; a point decides the method (a locker's id is delivery to a locker);
     * a way to pay must be offered for the method and the total. A method that no longer allows the way
     * to pay chosen falls back to the card. The points toggle is the customer's to turn either way; with
     * no points to spend it takes nothing off, and the total a way to pay is offered for is the one left
     * once the points are off.
     */
    suspend fun choose(
        owner: CartOwner.Customer,
        choice: CheckoutChoice,
    ) {
        if (choice == CheckoutChoice()) {
            throw CheckoutError.Invalid(
                "body",
                "Choose a method, an address, a point, a window, a way to pay or whether to use points",
            )
        }
        val basis = basis(owner)
        var next = checkouts.checkout(owner.id)
        choice.usePoints?.let { next = next.copy(usePoints = it) }
        val payable =
            basis.payable(
                if (next.usePoints) PointsRules.redeemable(ledger.balance(owner.id), basis.capCents) else 0,
            )
        choice.method?.let { method ->
            next = next.copy(method = method, pointId = next.pointId.takeIf { method == next.method })
        }
        choice.addressId?.let { id ->
            if (checkouts.addresses(owner.id).none { it.id == id }) throw CheckoutError.AddressNotFound(id)
            next = next.copy(method = DeliveryMethod.Courier, addressId = id, draft = null)
        }
        choice.pointId?.let { id ->
            val point =
                checkouts.pickupPoints().firstOrNull { it.id == id } ?: throw CheckoutError.PickupPointNotFound(id)
            if (choice.method != null && choice.method != point.method) {
                throw CheckoutError.Invalid("pointId", "«$id» is not a place for ${choice.method}")
            }
            next = next.copy(method = point.method, pointId = id)
        }
        choice.slotId?.let { id ->
            val slot = Slot.parse(id)?.takeIf { it in basis.offered } ?: throw CheckoutError.SlotNotFound(id)
            if (slots.loads(listOf(slot)).single().full) throw CheckoutError.SlotUnavailable()
            next = next.copy(slotId = slot.id)
        }
        choice.payment?.let { id ->
            val payment = PaymentMethod.byId(id) ?: throw CheckoutError.Invalid("payment", "No way to pay «$id»")
            if (!payment.allowed(next.method, payable)) {
                throw CheckoutError.PaymentNotAllowed(payment.refusal())
            }
            next = next.copy(payment = payment.id)
        }
        val payment = next.payment?.let(PaymentMethod::byId)
        if (payment != null && !payment.allowed(next.method, payable)) {
            next = next.copy(payment = null)
        }
        checkouts.save(owner.id, next)
    }

    /**
     * Saves the address form and makes it the checkout's (B-40). The form holds the address delivered to
     * — the one chosen, or by default the newest — and a save edits that address in place rather than
     * adding another: changing «4F» to «5B» leaves one address, at «5B». The address is named by the
     * server's state, not by the request: the form's body carries no id. A form equal to an address the
     * customer already has chooses that one and stores nothing; a customer with no address gets their
     * first. A form at fault is kept — the tree draws it again with an error under each field
     * (`Checkout_Validation`) — and refused with every field at fault.
     */
    suspend fun saveAddress(
        owner: CartOwner.Customer,
        entry: AddressEntry,
    ) {
        val problems = addressProblems(entry)
        val stored = checkouts.checkout(owner.id)
        if (problems.isNotEmpty()) {
            checkouts.save(owner.id, stored.copy(method = DeliveryMethod.Courier, draft = entry))
            throw CheckoutError.AddressRefused(problems)
        }
        val editing = delivered(stored, checkouts.addresses(owner.id))
        checkouts.saveAddress(owner.id, entry.trimmed(), editing?.id, clock.now().toOffsetDateTime())
    }

    /**
     * The address a courier delivers to, whatever the method chosen now: the one chosen while it is still
     * the customer's, else the newest. The quote's address and the one the address form edits are this
     * one, so the form always rewrites the address it shows.
     */
    private fun delivered(
        stored: StoredCheckout,
        addresses: List<Address>,
    ): Address? = addresses.firstOrNull { it.id == stored.addressId } ?: addresses.firstOrNull()

    /** The cart's side of the quote, which every command needs: the lines, the code, the totals, the windows. */
    private suspend fun basis(owner: CartOwner.Customer): Basis {
        val cart = carts.cart(owner)
        val lines = cartCommands.priced(cart).filter { it.counted }
        if (lines.isEmpty()) throw CheckoutError.CartEmpty()
        val now = clock.now().toOffsetDateTime()
        val applied = cart.promoCode?.let { carts.promo(it) }
        val promo = applied?.takeIf { it.activeAt(now) }
        return Basis(
            lines = lines,
            promo = promo,
            expiredPromo = applied?.takeIf { promo == null }?.code,
            totals = Totals.of(lines, promo, owner.plus, now),
            // «The next 5 days from tomorrow» (feature-checkout), whatever the items' dispatch days: the
            // canvas offers Maya Wed 8 for a cart whose duvet and mugs dispatch in a day.
            offered =
                Slot.offered(
                    clock
                        .now()
                        .withZoneSameInstant(DeliveryCalendar.STORE)
                        .toLocalDate()
                        .plusDays(1),
                ),
        )
    }

    private data class Basis(
        val lines: List<PricedLine>,
        val promo: PromoCode?,
        val expiredPromo: String?,
        val totals: Totals,
        val offered: List<Slot>,
    ) {
        /** The most points can take off: the items after discounts — never the delivery fee. */
        val capCents: Int get() = totals.itemsCents - totals.discountCents

        /** The total left to pay once [redeemed] points are off. */
        fun payable(redeemed: Int): Int = totals.totalCents - redeemed * PointsRules.CENTS_PER_POINT
    }

    private fun AddressEntry.trimmed(): AddressEntry =
        AddressEntry(street.trim(), apt.trim(), city.trim(), zip.trim(), doorCode.trim(), courierNote.trim())
}
