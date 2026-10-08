package io.github.youndie.haul.feature.order.domain

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.feature.checkout.AddressEntry
import io.github.youndie.haul.feature.checkout.DeliveryMethod
import java.time.OffsetDateTime

/**
 * The order's side of the saga (feature-orders): [Placing] while the saga runs, [Placed] once it
 * confirmed, [Cancelled] once it was undone. What the shopper sees after placement — packed, in transit,
 * delivered — is its shipments' (B-17).
 */
internal enum class OrderStatus(
    val id: String,
) {
    Placing("placing"),
    Placed("placed"),
    Cancelled("cancelled"),
    ;

    companion object {
        fun of(id: String): OrderStatus = entries.first { it.id == id }
    }
}

/** Why an order was cancelled: the card was declined (feature-orders), or the saga failed and was undone. */
internal object CancelReason {
    const val PAYMENT_DECLINED = "payment_declined"
    const val FAILED = "failed"
}

/**
 * A shipment's statuses (research §5, `Shipment`); placement writes [PLACED] and [CANCELLED], the
 * fulfilment simulator the rest (`feature/fulfilment/`).
 */
internal object ShipmentStatus {
    const val PLACED = "placed"
    const val PACKED = "packed"
    const val IN_TRANSIT = "in_transit"
    const val READY_FOR_PICKUP = "ready_for_pickup"
    const val DELIVERED = "delivered"
    const val PICKED_UP = "picked_up"
    const val CANCELLED = "cancelled"

    /** The way a shipment travels, in its order: a courier's ends at [DELIVERED], a pickup's at [PICKED_UP]. */
    val LIFECYCLE: List<String> = listOf(PLACED, PACKED, IN_TRANSIT, READY_FOR_PICKUP, DELIVERED, PICKED_UP)

    val ALL: List<String> = LIFECYCLE + CANCELLED
}

/** A line as bought: the SKU, its seller, its title, and the price it was bought at. */
internal data class OrderLine(
    val skuId: String,
    val sellerId: String,
    val title: String,
    val quantity: Int,
    val priceCents: Int,
    val listCents: Int,
)

/**
 * One seller's part of an order (research §5: «one per seller»). [pickupCode] is a pickup shipment's,
 * written once it is ready to collect (B-17); `null` before that and always for a courier.
 */
internal data class Shipment(
    val id: String,
    val sellerId: String,
    val status: String,
    val pickupCode: String? = null,
)

/**
 * What an order was placed for: the quote at placement, copied (the checkout and the cart move on, the
 * order does not). [id] is `HL-` and five digits (research §5). [address] is the address delivered to as
 * it was then, [addressId] the saved address it was: the address form edits that one in place (B-40).
 */
internal data class NewOrder(
    val id: String,
    val sagaId: String,
    val customerId: String,
    val method: DeliveryMethod,
    val addressId: String?,
    val address: AddressEntry?,
    val pointId: String?,
    val slotId: String?,
    val payment: String,
    val promoCode: String?,
    val itemsCents: Int,
    val discountCents: Int,
    val deliveryCents: Int,
    val totalCents: Int,
    val points: Int,
    val placedAt: OffsetDateTime,
    val lines: List<OrderLine>,
)

/**
 * An order as stored. [returnStatus] is its return's (B-21: `requested`, `picked_up`, `refunded`), `null`
 * while nothing was sent back; the order's progress reads it (`OrderProgress.of`), so a refunded order is
 * «Returned» wherever orders are listed.
 */
internal data class Order(
    val placed: NewOrder,
    val status: OrderStatus,
    val cancelReason: String?,
    val shipments: List<Shipment>,
    val returnStatus: String? = null,
) {
    val id: String get() = placed.id
}

/** The orders' storage, as the saga writes them; every write is idempotent, because a member may run twice. */
internal interface OrderRepository {
    /** The next order number, `HL-48302` and on. */
    suspend fun nextId(): String

    /**
     * Writes [order] as [OrderStatus.Placing], with its lines and one shipment per seller in the order
     * the sellers first appear; an order already written under its saga is left as it is.
     */
    suspend fun open(order: NewOrder)

    /** [OrderStatus.Placing] → [OrderStatus.Placed]; an order in any other status is left as it is. */
    suspend fun confirm(orderId: String)

    /**
     * Cancels the order and its shipments for [reason]; an order cancelled already keeps the reason it
     * was first cancelled for, so «the card was declined» is not overwritten by the rollback that follows.
     */
    suspend fun cancel(
        orderId: String,
        reason: String,
    )

    suspend fun order(orderId: String): Order?

    /**
     * [customerId]'s orders, newest first — the account's history (B-19). Only theirs: the customer is the
     * filter, so another customer's order is never among them.
     */
    suspend fun orders(customerId: String): List<Order>
}

/**
 * Stock held for an order (research D4, «reserve stock»): the units leave the SKU's stock when reserved
 * and come back when released. Both are idempotent per [holder], as a saga member must be.
 */
internal interface StockReservations {
    /**
     * Takes every one of [quantities] (SKU → units) off its stock for [holder], or none of them: `false`
     * when any SKU has fewer units than asked. A holder that already holds its stock is answered `true`
     * and takes nothing more.
     */
    suspend fun reserve(
        holder: String,
        quantities: Map<String, Int>,
        at: OffsetDateTime,
    ): Boolean

    /** Puts back what [holder] holds; `false` when it held nothing. */
    suspend fun release(holder: String): Boolean
}

/**
 * What placement, the order's page and its reorder can refuse with, besides checkout's and the cart's own
 * refusals; the application answers each with its status.
 */
internal sealed class OrderError(
    val code: ErrorCode,
    override val message: String,
    val field: String? = null,
) : Exception(message) {
    class Invalid(
        field: String,
        message: String,
    ) : OrderError(ErrorCode.ValidationFailed, message, field)

    class KeyMissing :
        OrderError(
            ErrorCode.IdempotencyKeyMissing,
            "Send the placement with an Idempotency-Key header",
            "Idempotency-Key",
        )

    class KeyReused :
        OrderError(
            ErrorCode.IdempotencyKeyReused,
            "This Idempotency-Key was already used to place a different order",
            "Idempotency-Key",
        )

    class QuoteChanged :
        OrderError(
            ErrorCode.CartChanged,
            "Your order changed since the page was drawn — check it and place it again",
            "quote",
        )

    /**
     * The checkout holds «Place order» (`CheckoutState.placeable`) for a refused address form while the
     * courier is the method: the quote still names the previous address, and placing it would deliver to
     * the one the shopper is changing. A pickup point or a locker is not held by it (B-42).
     */
    class CheckoutHeld :
        OrderError(
            ErrorCode.CheckoutHeld,
            "Your delivery address has errors — fix it or choose a saved address, then place the order",
        )

    class OutOfStock :
        OrderError(ErrorCode.OutOfStock, "An item in your order is no longer in stock — check your cart", "quote")

    /**
     * The order's page or its reorder for an order that is not the caller's, or none at all: one answer for
     * both (feature-orders), so an order number says nothing about whose it is.
     */
    class NotFound(
        orderId: String,
    ) : OrderError(ErrorCode.OrderNotFound, "No order «$orderId» among yours")
}
