package io.github.youndie.haul.feature.search.data

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/** The Exposed side of `recent_searches` (V3__search.sql); `SchemaTest` holds the two together. */
internal object RecentSearchesTable : Table("recent_searches") {
    val customerId = text("customer_id")
    val query = text("query")
    val searchedAt = timestampWithTimeZone("searched_at")
    override val primaryKey = PrimaryKey(customerId, query)
}

/** Every table search owns. */
internal val searchTables: List<Table> = listOf(RecentSearchesTable)
