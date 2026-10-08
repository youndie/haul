package io.github.youndie.haul.feature.order.data

import io.github.youndie.haul.feature.catalog.data.SellersTable
import io.github.youndie.haul.feature.catalog.data.SkusTable
import io.github.youndie.haul.feature.checkout.AddressEntry
import io.github.youndie.haul.feature.identity.data.CustomersTable
import io.github.youndie.haul.feature.order.domain.OrderStatus
import io.github.youndie.haul.feature.order.domain.ShipmentStatus
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

// The Exposed side of V10__orders.sql. `SchemaTest` holds the two together, CHECK constraints included:
// they are declared here under the names PostgreSQL gave them there.

internal object OrdersTable : Table("orders") {
    val id = text("id")
    val sagaId = text("saga_id").uniqueIndex()
    val customerId = text("customer_id").references(CustomersTable.id, onDelete = ReferenceOption.CASCADE)
    val status = text("status").check("orders_status_check") { it inList OrderStatus.entries.map(OrderStatus::id) }
    val cancelReason = text("cancel_reason").nullable()
    val method =
        text("method").check("orders_method_check") { it inList listOf("courier", "pickup_point", "parcel_locker") }
    val addressId = text("address_id").nullable()

    // V14: the address the order was placed to, copied — the saved one is edited in place (B-40).
    val address = jsonb<AddressEntry>("address", Json).nullable()
    val pointId = text("point_id").nullable()
    val slot = text("slot").nullable()
    val payment = text("payment")
    val promoCode = text("promo_code").nullable()
    val itemsCents = integer("items_cents")
    val discountCents = integer("discount_cents")
    val deliveryCents = integer("delivery_cents")
    val totalCents = integer("total_cents")
    val points = integer("points")
    val placedAt = timestampWithTimeZone("placed_at")
    override val primaryKey = PrimaryKey(id)

    init {
        index("orders_customer_id", false, customerId)
    }
}

internal object OrderLinesTable : Table("order_lines") {
    val orderId = text("order_id").references(OrdersTable.id, onDelete = ReferenceOption.CASCADE)
    val position = integer("position")
    val skuId = text("sku_id").references(SkusTable.id, onDelete = ReferenceOption.RESTRICT)
    val sellerId = text("seller_id").references(SellersTable.id, onDelete = ReferenceOption.RESTRICT)
    val title = text("title")
    val quantity = integer("quantity").check("order_lines_quantity_check") { it greater 0 }
    val priceCents = integer("price_cents")
    val listCents = integer("list_cents")
    override val primaryKey = PrimaryKey(orderId, position, name = "pk_order_lines")
}

internal object ShipmentsTable : Table("shipments") {
    val id = text("id")
    val orderId = text("order_id").references(OrdersTable.id, onDelete = ReferenceOption.CASCADE)
    val sellerId = text("seller_id").references(SellersTable.id, onDelete = ReferenceOption.RESTRICT)
    val position = integer("position")
    val status = text("status").check("shipments_status_check") { it inList ShipmentStatus.ALL }

    // V11: written by the fulfilment simulator when a pickup shipment is ready to collect (B-17).
    val pickupCode = text("pickup_code").nullable()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("shipments_order_id_seller_id_unique", orderId, sellerId)
    }
}

internal object StockReservationsTable : Table("stock_reservations") {
    val holder = text("holder")
    val skuId = text("sku_id").references(SkusTable.id, onDelete = ReferenceOption.RESTRICT)
    val quantity = integer("quantity").check("stock_reservations_quantity_check") { it greater 0 }
    val reservedAt = timestampWithTimeZone("reserved_at")
    override val primaryKey = PrimaryKey(holder, skuId, name = "pk_stock_reservations")
}

/** Every table placement writes, parents before children. */
internal val orderTables: List<Table> =
    listOf(
        SagaTables.petiches,
        SagaTables.outbox,
        SagaTables.idempotencyKeys,
        OrdersTable,
        OrderLinesTable,
        ShipmentsTable,
        StockReservationsTable,
    )
