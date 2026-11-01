package io.github.youndie.haul.feature.live.domain

import io.github.youndie.haul.feature.checkout.DeliveryMethod
import io.github.youndie.haul.feature.order.domain.OrderStatus
import java.time.Instant

/**
 * An order as the public feed of live orders reads it (B-164): what it is — its number, its [status] and why it was
 * cancelled, how it pays and whether it holds a window or points — and what has happened to it: its [shipments], each
 * with the moment it entered each status and what its leaving charged, and its [returned] part. Nothing that names the
 * customer is read at all: no customer, no address, no window, no pickup point, no pickup code.
 *
 * [payment] is the way to pay's id (`card-4242`, `pay-on-delivery`); the feed tells only whether the saga held money
 * for it. [heldWindow] and [redeemedPoints] are whether the saga's window and points members had anything to take.
 */
internal data class FeedOrder(
    val id: String,
    val status: OrderStatus,
    val cancelReason: String?,
    val method: DeliveryMethod,
    val payment: String,
    val heldWindow: Boolean,
    val redeemedPoints: Boolean,
    val totalCents: Int,
    val items: Int,
    val placedAt: Instant,
    val shipments: List<FeedShipment>,
    val returned: FeedReturn? = null,
)

/**
 * One seller's part: the seller's [seller] name, the shipment's [status], when it entered each status of its way
 * ([entered], by `ShipmentStatus`), and what was charged as it left, [capturedCents] at [capturedAt] — `null` before it
 * left, and for an order paid on delivery or by Haul Pay, which a shipment does not charge.
 */
internal data class FeedShipment(
    val id: String,
    val seller: String,
    val status: String,
    val entered: Map<String, Instant>,
    val capturedCents: Int? = null,
    val capturedAt: Instant? = null,
)

/** The order's return: its status (`ReturnStatus`), what it gives back, and when it entered each status. */
internal data class FeedReturn(
    val status: String,
    val refundCents: Int,
    val requestedAt: Instant,
    val pickedUpAt: Instant?,
    val refundedAt: Instant?,
)

/** The orders the feed shows. */
internal interface FeedRepository {
    /**
     * The [limit] orders placed last on the whole store, newest first, each with its shipments, its charges and its
     * return — read in a fixed number of statements whatever the store holds, so a public page costs the same on a
     * stand with a hundred thousand orders as on one with ten.
     */
    suspend fun latest(limit: Int): List<FeedOrder>
}
