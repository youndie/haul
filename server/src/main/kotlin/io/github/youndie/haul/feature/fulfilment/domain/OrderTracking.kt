package io.github.youndie.haul.feature.fulfilment.domain

import io.github.youndie.haul.feature.checkout.DeliveryMethod
import io.github.youndie.haul.feature.order.domain.Order
import io.github.youndie.haul.feature.order.domain.OrderRepository
import io.github.youndie.haul.feature.order.domain.OrderStatus
import io.github.youndie.haul.feature.order.domain.ShipmentStatus
import io.github.youndie.haul.feature.payment.domain.PaymentProcessor
import java.time.Instant
import kotlin.time.toJavaDuration

/**
 * Where an order is, as its page names it (screen-order's states): the saga's side while it is placed or
 * undone, then its least advanced shipment's (feature-orders: «the order's status is derived from its
 * shipments, the least advanced one wins»).
 */
internal enum class OrderProgress(
    val id: String,
) {
    Placing("placing"),
    Placed(ShipmentStatus.PLACED),
    Packed(ShipmentStatus.PACKED),
    InTransit(ShipmentStatus.IN_TRANSIT),
    ReadyForPickup(ShipmentStatus.READY_FOR_PICKUP),
    Delivered(ShipmentStatus.DELIVERED),
    PickedUp(ShipmentStatus.PICKED_UP),
    Cancelled("cancelled"),
    ;

    companion object {
        /** The order's progress: [Placing] and [Cancelled] are the saga's, the rest the least advanced shipment's. */
        fun of(order: Order): OrderProgress =
            when (order.status) {
                OrderStatus.Placing -> {
                    Placing
                }

                OrderStatus.Cancelled -> {
                    Cancelled
                }

                OrderStatus.Placed -> {
                    order.shipments
                        .map { shipment -> entries.first { it.id == shipment.status } }
                        .minByOrNull { it.ordinal } ?: Placed
                }
            }
    }
}

/**
 * One shipment as its order's page draws it: where it is and when it got to each step, its share of the
 * order and how much of that has been charged, and — only while it waits at a point or a locker — the code
 * that collects it and the day it is held until.
 */
internal data class TrackedShipment(
    val id: String,
    val sellerId: String,
    val status: String,
    val history: Map<String, Instant>,
    val shareCents: Int,
    val capturedCents: Int,
    val pickupCode: String?,
    val heldUntil: Instant?,
)

/** An order as its customer may see it (screen-order); B-18 draws it. */
internal data class TrackedOrder(
    val order: Order,
    val progress: OrderProgress,
    val shipments: List<TrackedShipment>,
)

/**
 * The order's page's state (screen-order, endpoint-orders `GET /ui/orders/{id}`): the order, where it is,
 * and each shipment's progress and charge. Only the order's own customer is answered; for anyone else the
 * order does not exist (research §5: «not yours» answers `404`, the same as «does not exist»), so a pickup
 * code is never shown to another customer.
 */
internal class OrderTracking(
    private val orders: OrderRepository,
    private val shipments: FulfilmentRepository,
    private val payments: PaymentProcessor,
) {
    suspend fun track(
        customerId: String,
        orderId: String,
    ): TrackedOrder? = orders.order(orderId)?.takeIf { it.placed.customerId == customerId }?.let { of(it) }

    /**
     * [order] as its customer sees it, for a caller that already read it as theirs — the account reads a
     * customer's orders by the customer ([OrderRepository.orders]) and tracks each.
     */
    suspend fun of(order: Order): TrackedOrder {
        val orderId = order.id
        val history = shipments.history(orderId)
        val shares = ShipmentShares.of(order)
        val captured = payments.captured(orderId)
        val pickup = order.placed.method != DeliveryMethod.Courier
        return TrackedOrder(
            order = order,
            progress = OrderProgress.of(order),
            shipments =
                order.shipments.map { shipment ->
                    val steps = history[shipment.id].orEmpty()
                    val waiting = pickup && shipment.status == ShipmentStatus.READY_FOR_PICKUP
                    TrackedShipment(
                        id = shipment.id,
                        sellerId = shipment.sellerId,
                        status = shipment.status,
                        history = steps,
                        shareCents = shares.getValue(shipment.id),
                        capturedCents = captured[shipment.id] ?: 0,
                        pickupCode = shipment.pickupCode.takeIf { waiting },
                        heldUntil =
                            steps[ShipmentStatus.READY_FOR_PICKUP]
                                ?.takeIf { waiting }
                                ?.plus(FulfilmentPace.HELD_FOR.toJavaDuration()),
                    )
                },
        )
    }
}
