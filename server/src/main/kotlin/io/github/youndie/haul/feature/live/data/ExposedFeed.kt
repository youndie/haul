package io.github.youndie.haul.feature.live.data

import io.github.youndie.haul.feature.catalog.data.SellersTable
import io.github.youndie.haul.feature.checkout.data.ExposedCheckoutRepository
import io.github.youndie.haul.feature.fulfilment.data.ShipmentEventsTable
import io.github.youndie.haul.feature.live.domain.FeedOrder
import io.github.youndie.haul.feature.live.domain.FeedRepository
import io.github.youndie.haul.feature.live.domain.FeedReturn
import io.github.youndie.haul.feature.live.domain.FeedShipment
import io.github.youndie.haul.feature.order.data.OrderLinesTable
import io.github.youndie.haul.feature.order.data.OrdersTable
import io.github.youndie.haul.feature.order.data.ShipmentsTable
import io.github.youndie.haul.feature.order.domain.OrderStatus
import io.github.youndie.haul.feature.payment.data.PaymentCapturesTable
import io.github.youndie.haul.feature.returns.data.ReturnsTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * The feed over Exposed, in five statements whatever the store holds: the last orders by `orders_placed_at` (V31),
 * then their items, their shipments with their sellers and history, the charges their shipments took, and their
 * returns — each by the orders' or the shipments' ids, along a primary key or an index. Not the orders' own repository:
 * that one reads the customer, the address and the codes, which a public page must not even hold.
 */
internal class ExposedFeed(
    private val database: Database,
) : FeedRepository {
    override suspend fun latest(limit: Int): List<FeedOrder> =
        withContext(Dispatchers.IO) {
            transaction(database) {
                val orders =
                    OrdersTable
                        .select(
                            OrdersTable.id,
                            OrdersTable.status,
                            OrdersTable.cancelReason,
                            OrdersTable.method,
                            OrdersTable.payment,
                            OrdersTable.slot,
                            OrdersTable.pointsRedeemed,
                            OrdersTable.totalCents,
                            OrdersTable.placedAt,
                        ).orderBy(OrdersTable.placedAt to SortOrder.DESC, OrdersTable.id to SortOrder.DESC)
                        .limit(limit)
                        .toList()
                if (orders.isEmpty()) return@transaction emptyList()
                val ids = orders.map { it[OrdersTable.id] }
                val quantity = OrderLinesTable.quantity.sum()
                val items =
                    OrderLinesTable
                        .select(OrderLinesTable.orderId, quantity)
                        .where { OrderLinesTable.orderId inList ids }
                        .groupBy(OrderLinesTable.orderId)
                        .associate { it[OrderLinesTable.orderId] to (it[quantity] ?: 0) }
                val history =
                    ShipmentsTable
                        .join(SellersTable, JoinType.INNER, ShipmentsTable.sellerId, SellersTable.id)
                        .join(ShipmentEventsTable, JoinType.LEFT, ShipmentsTable.id, ShipmentEventsTable.shipmentId)
                        .select(
                            ShipmentsTable.id,
                            ShipmentsTable.orderId,
                            ShipmentsTable.position,
                            ShipmentsTable.status,
                            SellersTable.name,
                            ShipmentEventsTable.status,
                            ShipmentEventsTable.enteredAt,
                        ).where { ShipmentsTable.orderId inList ids }
                        .orderBy(ShipmentsTable.position, SortOrder.ASC)
                        .toList()
                // A shipment's charge is named by the shipment (B-17), so it is found by the primary key.
                val shipmentIds = history.map { it[ShipmentsTable.id] }.distinct()
                val captures =
                    if (shipmentIds.isEmpty()) {
                        emptyMap()
                    } else {
                        PaymentCapturesTable
                            .select(
                                PaymentCapturesTable.key,
                                PaymentCapturesTable.amountCents,
                                PaymentCapturesTable.capturedAt,
                            ).where { PaymentCapturesTable.key inList shipmentIds }
                            .associate {
                                it[PaymentCapturesTable.key] to
                                    (
                                        it[PaymentCapturesTable.amountCents] to
                                            it[PaymentCapturesTable.capturedAt].toInstant()
                                    )
                            }
                    }
                val shipments =
                    history
                        .groupBy { it[ShipmentsTable.orderId] }
                        .mapValues { (_, rows) ->
                            rows.groupBy { it[ShipmentsTable.id] }.map { (id, events) ->
                                val first = events.first()
                                FeedShipment(
                                    id = id,
                                    seller = first[SellersTable.name],
                                    status = first[ShipmentsTable.status],
                                    entered =
                                        events
                                            .mapNotNull { event ->
                                                event.getOrNull(ShipmentEventsTable.status)?.let { status ->
                                                    status to event[ShipmentEventsTable.enteredAt].toInstant()
                                                }
                                            }.toMap(),
                                    capturedCents = captures[id]?.first,
                                    capturedAt = captures[id]?.second,
                                )
                            }
                        }
                val returns =
                    ReturnsTable
                        .select(
                            ReturnsTable.orderId,
                            ReturnsTable.status,
                            ReturnsTable.refundCents,
                            ReturnsTable.requestedAt,
                            ReturnsTable.pickedUpAt,
                            ReturnsTable.refundedAt,
                        ).where { ReturnsTable.orderId inList ids }
                        .associate {
                            it[ReturnsTable.orderId] to
                                FeedReturn(
                                    status = it[ReturnsTable.status],
                                    refundCents = it[ReturnsTable.refundCents],
                                    requestedAt = it[ReturnsTable.requestedAt].toInstant(),
                                    pickedUpAt = it[ReturnsTable.pickedUpAt]?.toInstant(),
                                    refundedAt = it[ReturnsTable.refundedAt]?.toInstant(),
                                )
                        }
                orders.map { row ->
                    val id = row[OrdersTable.id]
                    FeedOrder(
                        id = id,
                        status = OrderStatus.of(row[OrdersTable.status]),
                        cancelReason = row[OrdersTable.cancelReason],
                        method = ExposedCheckoutRepository.method(row[OrdersTable.method]),
                        payment = row[OrdersTable.payment],
                        heldWindow = row[OrdersTable.slot] != null,
                        redeemedPoints = row[OrdersTable.pointsRedeemed] > 0,
                        totalCents = row[OrdersTable.totalCents],
                        items = items[id] ?: 0,
                        placedAt = row[OrdersTable.placedAt].toInstant(),
                        shipments = shipments[id].orEmpty(),
                        returned = returns[id],
                    )
                }
            }
        }
}
