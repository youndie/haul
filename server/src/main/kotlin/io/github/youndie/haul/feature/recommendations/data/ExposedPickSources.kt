package io.github.youndie.haul.feature.recommendations.data

import io.github.youndie.haul.feature.catalog.data.ProductsTable
import io.github.youndie.haul.feature.catalog.data.SkusTable
import io.github.youndie.haul.feature.order.data.OrderLinesTable
import io.github.youndie.haul.feature.order.data.OrdersTable
import io.github.youndie.haul.feature.order.domain.OrderStatus
import io.github.youndie.haul.feature.recommendations.domain.PickSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** What «Picked for you» reads besides the views, over Exposed: a customer's orders and the catalog's popular. */
internal class ExposedPickSources(
    private val database: Database,
) : PickSources {
    private suspend fun <T> tx(block: () -> T): T = withContext(Dispatchers.IO) { transaction(database) { block() } }

    override suspend fun bought(customerId: String): Set<String> =
        tx {
            OrderLinesTable
                .join(OrdersTable, JoinType.INNER, OrderLinesTable.orderId, OrdersTable.id)
                .join(SkusTable, JoinType.INNER, OrderLinesTable.skuId, SkusTable.id)
                .select(SkusTable.productId)
                .where { (OrdersTable.customerId eq customerId) and (OrdersTable.status neq OrderStatus.Cancelled.id) }
                .withDistinct()
                .map { it[SkusTable.productId] }
                .toSet()
        }

    override suspend fun popular(
        excluding: Set<String>,
        limit: Int,
    ): List<String> =
        tx {
            val inStock = SkusTable.select(SkusTable.productId).where { SkusTable.stock greater 0 }
            ProductsTable
                .select(ProductsTable.id)
                .where {
                    val stocked = ProductsTable.id inSubQuery inStock
                    if (excluding.isEmpty()) stocked else stocked and (ProductsTable.id notInList excluding)
                }.orderBy(ProductsTable.reviewsCount to SortOrder.DESC, ProductsTable.id to SortOrder.ASC)
                .limit(limit)
                .map { it[ProductsTable.id] }
        }
}
