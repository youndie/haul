package io.github.youndie.haul.feature.order.saga

import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.cart.domain.CartRepository
import io.github.youndie.haul.feature.checkout.domain.DeliverySlots
import io.github.youndie.haul.feature.checkout.domain.PaymentMethod
import io.github.youndie.haul.feature.checkout.domain.Slot
import io.github.youndie.haul.feature.membership.domain.PointsLedger
import io.github.youndie.haul.feature.membership.domain.PointsMovement
import io.github.youndie.haul.feature.order.domain.CancelReason
import io.github.youndie.haul.feature.order.domain.OrderMoves
import io.github.youndie.haul.feature.order.domain.OrderRepository
import io.github.youndie.haul.feature.order.domain.StockReservations
import io.github.youndie.haul.feature.payment.domain.Authorisation
import io.github.youndie.haul.feature.payment.domain.AuthorisationOutcome
import io.github.youndie.haul.feature.payment.domain.PaymentProcessor
import io.github.youndie.petich.PetichAnnouncement
import io.github.youndie.petich.PetichAnnouncementContext
import io.github.youndie.petich.PetichDefinition
import io.github.youndie.petich.PetichStep
import io.github.youndie.petich.PetichStepContext
import io.github.youndie.petich.petichDefinition

/** The saga's type in its row: what the engine finds this definition by, after a restart included. */
internal const val ORDER_SAGA = "order"

/**
 * Placement as a saga (research D4, feature-orders): reserve the stock, take the delivery window, take
 * the points it is paid with (B-23), open the order, authorise the payment, confirm — and the bought lines
 * leave the cart. A member that refuses or fails undoes the ones before it, in reverse: the authorisation
 * is voided, the order cancelled, the points, the window and the stock given back.
 *
 * The order of the members is a decision, not a habit. The stock and the window come first because a
 * refusal there sends the shopper back to checkout (`409`), and an order that never existed is the
 * right record of that; the order opens before the payment because a declined card is an order that
 * exists and is cancelled (research §6, #HL-48303). Every member is idempotent and names what it holds
 * by the order — a restart re-runs the member it died in, and a rollback releases by that same name.
 *
 * Where the order ends up is told to [moves] (B-56), as the simulators tell theirs: placed by [Confirm],
 * cancelled by [OpenOrder]'s compensation — so a page that opened while the order was placing is drawn again
 * where it went, by this process or by the sweeper of the next one.
 */
internal fun orderSaga(
    stock: StockReservations,
    slots: DeliverySlots,
    orders: OrderRepository,
    payments: PaymentProcessor,
    carts: CartRepository,
    points: PointsLedger,
    moves: OrderMoves = OrderMoves(),
): PetichDefinition<OrderPayload> =
    petichDefinition(ORDER_SAGA) {
        step("reserve-stock", ReserveStock(stock))
        step("reserve-slot", ReserveSlot(slots))
        step("redeem-points", RedeemPoints(points))
        step("open-order", OpenOrder(orders, moves))
        step("authorise-payment", AuthorisePayment(payments, orders))
        step("confirm", Confirm(orders, moves))
        announce("clear-cart", ClearBoughtLines(carts))
    }

/** Every line's units off its SKU's stock, held by the order; a shortage refuses the saga (`out_of_stock`). */
internal class ReserveStock(
    private val stock: StockReservations,
) : PetichStep<OrderPayload> {
    override suspend fun execute(
        ctx: PetichStepContext,
        payload: OrderPayload,
    ) {
        val quantities = payload.lines.groupingBy { it.skuId }.fold(0) { sum, line -> sum + line.quantity }
        if (!stock.reserve(payload.orderId, quantities, payload.placedAtTime)) {
            ctx.record(Refused(Refused.OUT_OF_STOCK))
            ctx.reject(Refused.OUT_OF_STOCK)
        }
    }

    /** Puts back whatever the order holds — nothing, when the reservation never landed. */
    override suspend fun compensate(
        ctx: PetichStepContext,
        payload: OrderPayload,
    ) {
        stock.release(payload.orderId)
    }
}

/**
 * A place in the courier's window, taken now and not at quote time (research D5, «Decided in B-14»): a
 * window that filled since the page was drawn refuses the saga (`slot_unavailable`). A pickup has no
 * window and takes nothing.
 */
internal class ReserveSlot(
    private val slots: DeliverySlots,
) : PetichStep<OrderPayload> {
    override suspend fun execute(
        ctx: PetichStepContext,
        payload: OrderPayload,
    ) {
        val slot = payload.slot() ?: return
        if (!slots.reserve(slot, payload.orderId, payload.placedAtTime)) {
            ctx.record(Refused(Refused.SLOT_UNAVAILABLE))
            ctx.reject(Refused.SLOT_UNAVAILABLE)
        }
    }

    override suspend fun compensate(
        ctx: PetichStepContext,
        payload: OrderPayload,
    ) {
        payload.slot()?.let { slots.release(it, payload.orderId) }
    }

    private fun OrderPayload.slot(): Slot? =
        slotId?.let { Slot.parse(it) ?: error("the order $orderId carries «$it», which is no window") }
}

/**
 * The points the order is paid with, taken off the customer's balance (feature-checkout, «Points
 * redeemed»): once per order, and only while the balance still covers them — another order may have spent
 * them since the quote — or the saga is refused (`points_short`, answered as the quote having changed).
 * Undone — a declined card, a failure further on — they come back as a `returned` row of their own. An
 * order paid without points takes and gives back nothing.
 */
internal class RedeemPoints(
    private val points: PointsLedger,
) : PetichStep<OrderPayload> {
    override suspend fun execute(
        ctx: PetichStepContext,
        payload: OrderPayload,
    ) {
        if (payload.pointsRedeemed <= 0) return
        val redemption =
            PointsMovement.redeemed(payload.customerId, payload.orderId, payload.pointsRedeemed, payload.placedAtTime)
        if (!points.redeem(redemption)) {
            ctx.record(Refused(Refused.POINTS_SHORT))
            ctx.reject(Refused.POINTS_SHORT)
        }
    }

    /** Gives back what the order took — nothing, when the redemption never landed. */
    override suspend fun compensate(
        ctx: PetichStepContext,
        payload: OrderPayload,
    ) {
        if (payload.pointsRedeemed > 0) points.giveBack(payload.orderId, payload.placedAtTime)
    }
}

/**
 * The order, its lines and a shipment per seller, written as `placing`; undone, it is cancelled.
 *
 * **Every cancellation of a placing order comes through [compensate]**, so it is the one place one is told to
 * [moves]: a declined card's rollback (the order already cancelled `payment_declined` by `authorise-payment`,
 * which this keeps), a failure further on, and the sweeper carrying on a pass or a rollback a dead process
 * left. Told once the cancellation is written — [OrderRepository.cancel] is a transaction of its own,
 * committed when it returns — so a page is never drawn a cancellation that did not happen.
 */
internal class OpenOrder(
    private val orders: OrderRepository,
    private val moves: OrderMoves,
) : PetichStep<OrderPayload> {
    override suspend fun execute(
        ctx: PetichStepContext,
        payload: OrderPayload,
    ) {
        orders.open(payload.order(ctx.petich.id))
    }

    /** A cancelled order keeps its first reason: a declined card stays «payment declined» through the rollback. */
    override suspend fun compensate(
        ctx: PetichStepContext,
        payload: OrderPayload,
    ) {
        orders.cancel(payload.orderId, CancelReason.FAILED)
        moves.moved(payload.orderId)
    }
}

/**
 * The total held on the way to pay (research D4: authorised now, captured per shipment when it ships).
 * Pay on delivery holds nothing (feature-orders). A declined card cancels the order for that reason and
 * refuses the saga, which gives the window and the stock back.
 */
internal class AuthorisePayment(
    private val payments: PaymentProcessor,
    private val orders: OrderRepository,
) : PetichStep<OrderPayload> {
    override suspend fun execute(
        ctx: PetichStepContext,
        payload: OrderPayload,
    ) {
        val method = payload.paymentMethod()
        if (!method.card) return
        val outcome = payments.authorise(ctx.idempotencyKey, Authorisation(payload.orderId, method, payload.totalCents))
        if (outcome == AuthorisationOutcome.Declined) {
            orders.cancel(payload.orderId, CancelReason.PAYMENT_DECLINED)
            ctx.record(Refused(Refused.PAYMENT_DECLINED))
            ctx.reject(Refused.PAYMENT_DECLINED)
        }
    }

    /** Voids by the name the authorisation was asked under: a no-op when none is held. */
    override suspend fun compensate(
        ctx: PetichStepContext,
        payload: OrderPayload,
    ) {
        payments.void(ctx.idempotencyKey)
    }

    private fun OrderPayload.paymentMethod(): PaymentMethod =
        PaymentMethod.byId(payment) ?: error("the order $orderId pays with «$payment», which is no way to pay")
}

/**
 * `placing` → `placed`: the last member that can still undo the rest.
 *
 * Told to [moves] once the order is placed (B-56): [OrderRepository.confirm] is a transaction of its own,
 * committed when it returns, so the page is drawn again from what is written. That is before the saga's row
 * says `COMPLETED`, and need not wait for it: the page draws the order, not the saga, and a confirmed order
 * stays placed — this member is undone only when it threw itself.
 */
internal class Confirm(
    private val orders: OrderRepository,
    private val moves: OrderMoves,
) : PetichStep<OrderPayload> {
    override suspend fun execute(
        ctx: PetichStepContext,
        payload: OrderPayload,
    ) {
        orders.confirm(payload.orderId)
        moves.moved(payload.orderId)
    }

    /** Only when this member itself threw or timed out — it may have confirmed — and the order is then cancelled. */
    override suspend fun compensate(
        ctx: PetichStepContext,
        payload: OrderPayload,
    ) {
        orders.cancel(payload.orderId, CancelReason.FAILED)
    }
}

/**
 * The bought lines leave the customer's cart. An announcement, not a step: by now the order is placed,
 * and a cart that could not be cleared is no reason to take it back (petich counts the failure). Deleting
 * lines twice deletes nothing the second time, so a restart that re-runs it is harmless.
 */
internal class ClearBoughtLines(
    private val carts: CartRepository,
) : PetichAnnouncement<OrderPayload> {
    override suspend fun announce(
        ctx: PetichAnnouncementContext,
        payload: OrderPayload,
    ) {
        carts.removeLines(CartOwner.Customer(payload.customerId, payload.plus), payload.lines.map { it.skuId }.toSet())
    }
}
