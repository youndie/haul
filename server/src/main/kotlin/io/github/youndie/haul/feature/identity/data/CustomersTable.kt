package io.github.youndie.haul.feature.identity.data

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/** The Exposed side of `customers` (V7__customers.sql); `SchemaTest` holds the two together. */
internal object CustomersTable : Table("customers") {
    val id = text("id")
    val name = text("name")
    val plus = bool("plus")
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}
