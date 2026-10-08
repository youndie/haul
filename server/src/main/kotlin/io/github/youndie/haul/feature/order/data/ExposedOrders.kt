package io.github.youndie.haul.feature.order.data

import io.github.youndie.haul.feature.checkout.data.ExposedCheckoutRepository
import io.github.youndie.haul.feature.order.domain.NewOrder
import io.github.youndie.haul.feature.order.domain.Order
import io.github.youndie.haul.feature.order.domain.OrderLine
import io.github.youndie.haul.feature.order.domain.OrderRepository
import io.github.youndie.haul.feature.order.domain.OrderStatus
import io.github.youndie.haul.feature.order.domain.Shipment
import io.github.youndie.haul.feature.order.domain.ShipmentStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/** Orders over Exposed; each write is one transaction, and each is safe to run twice. */
internal class ExposedOrders(
    private val database: Database,
) : OrderRepository {
    private suspend fun <T> tx(block: () -> T): T = withContext(Dispatchers.IO) { transaction(database) { block() } }

    override suspend fun nextId(): String =
        withContext(Dispatchers.IO) {
            transaction(database) {
                val number = exec("SELECT nextval('order_numbers')") { if (it.next()) it.getLong(1) else null }
                "HL-${checkNotNull(number) { "the order_numbers sequence answered nothing" }}"
            }
        }

    override suspend fun open(order: NewOrder) {
        tx {
            val inserted =
                OrdersTable
                    .insertIgnore {
                        it[id] = order.id
                        it[sagaId] = order.sagaId
                        it[customerId] = order.customerId
                        it[status] = OrderStatus.Placing.id
                        it[method] = ExposedCheckoutRepository.column(order.method)
                        it[addressId] = order.addressId
                        it[pointId] = order.pointId
                        it[slot] = order.slotId
                        it[payment] = order.payment
                        it[promoCode] = order.promoCode
                        it[itemsCents] = order.itemsCents
                        it[discountCents] = order.discountCents
                        it[deliveryCents] = order.deliveryCents
                        it[totalCents] = order.totalCents
                        it[points] = order.points
                        it[placedAt] = order.placedAt
                    }.insertedCount == 1
            // Written by an earlier run of this member: the lines and the shipments went with it.
            if (!inserted) return@tx
            OrderLinesTable.batchInsert(order.lines.withIndex()) { (position, line) ->
                this[OrderLinesTable.orderId] = order.id
                this[OrderLinesTable.position] = position
                this[OrderLinesTable.skuId] = line.skuId
                this[OrderLinesTable.sellerId] = line.sellerId
                this[OrderLinesTable.title] = line.title
                this[OrderLinesTable.quantity] = line.quantity
                this[OrderLinesTable.priceCents] = line.priceCents
                this[OrderLinesTable.listCents] = line.listCents
            }
            val sellers = order.lines.map { it.sellerId }.distinct()
            ShipmentsTable.batchInsert(sellers.withIndex()) { (position, seller) ->
                this[ShipmentsTable.id] = "${order.id}-${position + 1}"
                this[ShipmentsTable.orderId] = order.id
                this[ShipmentsTable.sellerId] = seller
                this[ShipmentsTable.position] = position
                this[ShipmentsTable.status] = ShipmentStatus.PLACED
            }
        }
    }

    override suspend fun confirm(orderId: String) {
        tx {
            OrdersTable.update({ (OrdersTable.id eq orderId) and (OrdersTable.status eq OrderStatus.Placing.id) }) {
                it[status] = OrderStatus.Placed.id
            }
        }
    }

    override suspend fun cancel(
        orderId: String,
        reason: String,
    ) {
        tx {
            OrdersTable.update({ (OrdersTable.id eq orderId) and (OrdersTable.status neq OrderStatus.Cancelled.id) }) {
                it[status] = OrderStatus.Cancelled.id
                it[cancelReason] = reason
            }
            ShipmentsTable.update({ ShipmentsTable.orderId eq orderId }) { it[status] = ShipmentStatus.CANCELLED }
        }
    }

    override suspend fun order(orderId: String): Order? =
        tx {
            val row = OrdersTable.selectAll().where { OrdersTable.id eq orderId }.singleOrNull() ?: return@tx null
            val lines =
                OrderLinesTable
                    .selectAll()
                    .where { OrderLinesTable.orderId eq orderId }
                    .orderBy(OrderLinesTable.position, SortOrder.ASC)
                    .map {
                        OrderLine(
                            skuId = it[OrderLinesTable.skuId],
                            sellerId = it[OrderLinesTable.sellerId],
                            title = it[OrderLinesTable.title],
                            quantity = it[OrderLinesTable.quantity],
                            priceCents = it[OrderLinesTable.priceCents],
                            listCents = it[OrderLinesTable.listCents],
                        )
                    }
            val shipments =
                ShipmentsTable
                    .selectAll()
                    .where { ShipmentsTable.orderId eq orderId }
                    .orderBy(ShipmentsTable.position, SortOrder.ASC)
                    .map {
                        Shipment(
                            it[ShipmentsTable.id],
                            it[ShipmentsTable.sellerId],
                            it[ShipmentsTable.status],
                            it[ShipmentsTable.pickupCode],
                        )
                    }
            Order(
                placed =
                    NewOrder(
                        id = row[OrdersTable.id],
                        sagaId = row[OrdersTable.sagaId],
                        customerId = row[OrdersTable.customerId],
                        method = ExposedCheckoutRepository.method(row[OrdersTable.method]),
                        addressId = row[OrdersTable.addressId],
                        pointId = row[OrdersTable.pointId],
                        slotId = row[OrdersTable.slot],
                        payment = row[OrdersTable.payment],
                        promoCode = row[OrdersTable.promoCode],
                        itemsCents = row[OrdersTable.itemsCents],
                        discountCents = row[OrdersTable.discountCents],
                        deliveryCents = row[OrdersTable.deliveryCents],
                        totalCents = row[OrdersTable.totalCents],
                        points = row[OrdersTable.points],
                        placedAt = row[OrdersTable.placedAt],
                        lines = lines,
                    ),
                status = OrderStatus.of(row[OrdersTable.status]),
                cancelReason = row[OrdersTable.cancelReason],
                shipments = shipments,
            )
        }
}
