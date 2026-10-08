package io.github.youndie.haul.feature.recommendations.data

import io.github.youndie.haul.feature.catalog.data.ProductsTable
import io.github.youndie.haul.feature.recommendations.domain.ProductView
import io.github.youndie.haul.feature.recommendations.domain.ProductViews
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.OffsetDateTime

/**
 * Product views over Exposed: one row per customer and product, trimmed to the newest
 * [ProductViews.KEPT] on the write that went past them, as recent searches are.
 */
internal class ExposedProductViews(
    private val database: Database,
) : ProductViews {
    private suspend fun <T> tx(block: () -> T): T = withContext(Dispatchers.IO) { transaction(database) { block() } }

    override suspend fun record(
        customerId: String,
        productId: String,
        at: OffsetDateTime,
    ) {
        tx {
            // The page and this write run side by side, so an unknown product is not refused here first:
            // it is left out rather than failing the foreign key and logging a warning per mistyped link.
            if (ProductsTable.select(ProductsTable.id).where { ProductsTable.id eq productId }.empty()) return@tx
            ProductViewsTable.upsert {
                it[ProductViewsTable.customerId] = customerId
                it[ProductViewsTable.productId] = productId
                it[viewedAt] = at
            }
            val beyond =
                ProductViewsTable
                    .select(ProductViewsTable.productId)
                    .where { ProductViewsTable.customerId eq customerId }
                    .orderBy(*NEWEST_FIRST)
                    .drop(ProductViews.KEPT)
                    .map { it[ProductViewsTable.productId] }
            if (beyond.isNotEmpty()) {
                ProductViewsTable.deleteWhere {
                    (ProductViewsTable.customerId eq customerId) and (ProductViewsTable.productId inList beyond)
                }
            }
        }
    }

    override suspend fun recent(customerId: String): List<ProductView> =
        tx {
            ProductViewsTable
                .join(ProductsTable, JoinType.INNER, ProductViewsTable.productId, ProductsTable.id)
                .selectAll()
                .where { ProductViewsTable.customerId eq customerId }
                .orderBy(*NEWEST_FIRST)
                .limit(ProductViews.KEPT)
                .map { ProductView(it[ProductViewsTable.productId], it[ProductsTable.categorySlug]) }
        }

    private companion object {
        val NEWEST_FIRST =
            arrayOf<Pair<Expression<*>, SortOrder>>(
                ProductViewsTable.viewedAt to SortOrder.DESC,
                ProductViewsTable.productId to SortOrder.ASC,
            )
    }
}
