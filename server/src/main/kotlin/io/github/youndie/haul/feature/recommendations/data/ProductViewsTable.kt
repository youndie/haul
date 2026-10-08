package io.github.youndie.haul.feature.recommendations.data

import io.github.youndie.haul.feature.catalog.data.ProductsTable
import io.github.youndie.haul.feature.identity.data.CustomersTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

// The Exposed side of V20__product_views.sql; `SchemaTest` holds the two together.

internal object ProductViewsTable : Table("product_views") {
    val customerId = text("customer_id").references(CustomersTable.id, onDelete = ReferenceOption.CASCADE)
    val productId = text("product_id").references(ProductsTable.id)
    val viewedAt = timestampWithTimeZone("viewed_at")
    override val primaryKey = PrimaryKey(customerId, productId, name = "pk_product_views")
}

/** Every table recommendations own. */
internal val recommendationTables: List<Table> = listOf(ProductViewsTable)
