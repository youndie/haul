package io.github.youndie.haul.feature.saved.data

import io.github.youndie.haul.feature.catalog.data.ProductsTable
import io.github.youndie.haul.feature.identity.data.CustomersTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

// The Exposed side of V18__saved.sql; `SchemaTest` holds the two together.

internal object SavedItemsTable : Table("saved_items") {
    val customerId = text("customer_id").references(CustomersTable.id, onDelete = ReferenceOption.CASCADE)
    val productId = text("product_id").references(ProductsTable.id)
    val savedPriceCents = integer("saved_price_cents")
    val savedAt = timestampWithTimeZone("saved_at")
    override val primaryKey = PrimaryKey(customerId, productId, name = "pk_saved_items")
}

internal val savedTables: List<Table> = listOf(SavedItemsTable)
