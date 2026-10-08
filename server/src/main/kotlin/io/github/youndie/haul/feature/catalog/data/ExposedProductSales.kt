package io.github.youndie.haul.feature.catalog.data

import io.github.youndie.haul.feature.catalog.domain.ProductSales
import io.github.youndie.haul.feature.order.data.OrderLinesTable
import io.github.youndie.haul.feature.order.data.OrdersTable
import io.github.youndie.haul.feature.order.domain.OrderStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime

/**
 * [ProductSales] over the order tables: one `SUM` over the product's lines, reached through its SKUs
 * (`skus_product`, then V23's `order_lines_sku_id`) and joined to their orders by key.
 */
internal class ExposedProductSales(
    private val database: Database,
) : ProductSales {
    override suspend fun unitsPlaced(
        productId: String,
        from: OffsetDateTime,
        until: OffsetDateTime,
    ): Int =
        withContext(Dispatchers.IO) {
            transaction(database) {
                val units = OrderLinesTable.quantity.sum()
                OrderLinesTable
                    .join(OrdersTable, JoinType.INNER, OrderLinesTable.orderId, OrdersTable.id)
                    .join(SkusTable, JoinType.INNER, OrderLinesTable.skuId, SkusTable.id)
                    .select(units)
                    .where {
                        (SkusTable.productId eq productId) and
                            (OrdersTable.status eq OrderStatus.Placed.id) and
                            (OrdersTable.placedAt greater from) and
                            (OrdersTable.placedAt lessEq until)
                    }.single()[units] ?: 0
            }
        }
}
