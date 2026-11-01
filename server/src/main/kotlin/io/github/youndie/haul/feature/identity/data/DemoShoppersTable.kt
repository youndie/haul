package io.github.youndie.haul.feature.identity.data

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/** The Exposed side of `demo_shoppers` (V32__demo_shoppers.sql, B-162); `SchemaTest` holds the two together. */
internal object DemoShoppersTable : Table("demo_shoppers") {
    val customerId = text("customer_id").references(CustomersTable.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(customerId)

    init {
        index("demo_shoppers_created_at", false, createdAt)
    }
}
