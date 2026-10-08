package io.github.youndie.haul.feature.fulfilment.data

import io.github.youndie.haul.feature.catalog.data.ProductsTable
import io.github.youndie.haul.feature.catalog.data.SkusTable
import io.github.youndie.haul.feature.checkout.DeliveryMethod
import io.github.youndie.haul.feature.checkout.data.ExposedCheckoutRepository
import io.github.youndie.haul.feature.fulfilment.domain.ActiveShipment
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentRepository
import io.github.youndie.haul.feature.order.data.OrderLinesTable
import io.github.youndie.haul.feature.order.data.OrdersTable
import io.github.youndie.haul.feature.order.data.ShipmentsTable
import io.github.youndie.haul.feature.order.domain.OrderStatus
import io.github.youndie.haul.feature.order.domain.ShipmentStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.ZoneOffset

/**
 * The shipments' moves over Exposed. A move is one conditional update of the shipment — `WHERE status =
 * <from>` — and the history row it enters, in one transaction: of two passes moving the same shipment at
 * once, the second waits on the first's row lock, re-reads the row, and moves nothing.
 */
internal class ExposedFulfilment(
    private val database: Database,
) : FulfilmentRepository {
    private suspend fun <T> tx(block: () -> T): T = withContext(Dispatchers.IO) { transaction(database) { block() } }

    override suspend fun active(): List<ActiveShipment> =
        tx {
            val rows =
                ShipmentsTable
                    .join(OrdersTable, JoinType.INNER, ShipmentsTable.orderId, OrdersTable.id)
                    .join(ShipmentEventsTable, JoinType.LEFT, ShipmentsTable.id, ShipmentEventsTable.shipmentId) {
                        ShipmentEventsTable.status eq ShipmentsTable.status
                    }.select(
                        ShipmentsTable.id,
                        ShipmentsTable.orderId,
                        ShipmentsTable.sellerId,
                        ShipmentsTable.status,
                        OrdersTable.sagaId,
                        OrdersTable.method,
                        ShipmentEventsTable.enteredAt,
                    ).where {
                        (OrdersTable.status eq OrderStatus.Placed.id) and (ShipmentsTable.status inList ON_THE_WAY)
                    }.orderBy(ShipmentsTable.id)
                    .toList()
            if (rows.isEmpty()) return@tx emptyList()
            val dispatchDays = dispatchDays(rows.map { it[ShipmentsTable.orderId] }.distinct())
            rows.map {
                val orderId = it[ShipmentsTable.orderId]
                ActiveShipment(
                    id = it[ShipmentsTable.id],
                    orderId = orderId,
                    sagaId = it[OrdersTable.sagaId],
                    status = it[ShipmentsTable.status],
                    since = it.getOrNull(ShipmentEventsTable.enteredAt)?.toInstant(),
                    pickup = ExposedCheckoutRepository.method(it[OrdersTable.method]) != DeliveryMethod.Courier,
                    dispatchDays = dispatchDays[orderId to it[ShipmentsTable.sellerId]] ?: 0,
                )
            }
        }

    /** The most dispatch days among each seller's products in [orderIds], by (order, seller). */
    private fun dispatchDays(orderIds: List<String>): Map<Pair<String, String>, Int> {
        val most = ProductsTable.dispatchDays.max()
        return OrderLinesTable
            .join(SkusTable, JoinType.INNER, OrderLinesTable.skuId, SkusTable.id)
            .join(ProductsTable, JoinType.INNER, SkusTable.productId, ProductsTable.id)
            .select(OrderLinesTable.orderId, OrderLinesTable.sellerId, most)
            .where { OrderLinesTable.orderId inList orderIds }
            .groupBy(OrderLinesTable.orderId, OrderLinesTable.sellerId)
            .associate { (it[OrderLinesTable.orderId] to it[OrderLinesTable.sellerId]) to (it[most] ?: 0) }
    }

    override suspend fun receive(
        shipmentId: String,
        at: Instant,
    ): Boolean =
        tx {
            ShipmentEventsTable
                .insertIgnore {
                    it[this.shipmentId] = shipmentId
                    it[status] = ShipmentStatus.PLACED
                    it[enteredAt] = at.atOffset(ZoneOffset.UTC)
                }.insertedCount == 1
        }

    override suspend fun move(
        shipmentId: String,
        from: String,
        to: String,
        at: Instant,
        pickupCode: String?,
    ): Boolean =
        tx {
            val moved =
                ShipmentsTable.update({ (ShipmentsTable.id eq shipmentId) and (ShipmentsTable.status eq from) }) {
                    it[status] = to
                    if (pickupCode != null) it[this.pickupCode] = pickupCode
                } == 1
            if (moved) {
                ShipmentEventsTable.insertIgnore {
                    it[this.shipmentId] = shipmentId
                    it[status] = to
                    it[enteredAt] = at.atOffset(ZoneOffset.UTC)
                }
            }
            moved
        }

    override suspend fun history(orderId: String): Map<String, Map<String, Instant>> =
        tx {
            ShipmentEventsTable
                .join(ShipmentsTable, JoinType.INNER, ShipmentEventsTable.shipmentId, ShipmentsTable.id)
                .select(ShipmentEventsTable.shipmentId, ShipmentEventsTable.status, ShipmentEventsTable.enteredAt)
                .where { ShipmentsTable.orderId eq orderId }
                .orderBy(ShipmentEventsTable.enteredAt)
                .toList()
                .groupBy(
                    { it[ShipmentEventsTable.shipmentId] },
                    { it[ShipmentEventsTable.status] to it[ShipmentEventsTable.enteredAt].toInstant() },
                ).mapValues { (_, steps) -> steps.toMap() }
        }

    private companion object {
        /** Neither arrived nor cancelled. */
        val ON_THE_WAY: List<String> =
            listOf(
                ShipmentStatus.PLACED,
                ShipmentStatus.PACKED,
                ShipmentStatus.IN_TRANSIT,
                ShipmentStatus.READY_FOR_PICKUP,
            )
    }
}
