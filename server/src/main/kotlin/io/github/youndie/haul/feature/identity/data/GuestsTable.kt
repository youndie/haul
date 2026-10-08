package io.github.youndie.haul.feature.identity.data

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/** The Exposed side of `guests` (V4__cart.sql); `SchemaTest` holds the two together. */
internal object GuestsTable : Table("guests") {
    val id = text("id")
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}
