package io.github.youndie.haul.feature.search.data

import io.github.youndie.haul.feature.search.domain.RecentSearches
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.OffsetDateTime

/** Recent searches over Exposed: one row per customer and query, trimmed to the newest ten on write. */
internal class ExposedRecentSearches(
    private val database: Database,
) : RecentSearches {
    private suspend fun <T> tx(block: () -> T): T = withContext(Dispatchers.IO) { transaction(database) { block() } }

    override suspend fun record(
        customerId: String,
        query: String,
        at: OffsetDateTime,
    ) {
        tx {
            RecentSearchesTable.upsert {
                it[RecentSearchesTable.customerId] = customerId
                it[RecentSearchesTable.query] = query
                it[searchedAt] = at
            }
            val beyond =
                RecentSearchesTable
                    .selectAll()
                    .where { RecentSearchesTable.customerId eq customerId }
                    .orderBy(
                        RecentSearchesTable.searchedAt to SortOrder.DESC,
                        RecentSearchesTable.query to SortOrder.ASC,
                    ).drop(RecentSearches.KEPT)
                    .map { it[RecentSearchesTable.query] }
            if (beyond.isNotEmpty()) {
                RecentSearchesTable.deleteWhere {
                    (RecentSearchesTable.customerId eq customerId) and (RecentSearchesTable.query inList beyond)
                }
            }
        }
    }

    override suspend fun list(customerId: String): List<String> =
        tx {
            RecentSearchesTable
                .selectAll()
                .where { RecentSearchesTable.customerId eq customerId }
                .orderBy(RecentSearchesTable.searchedAt to SortOrder.DESC, RecentSearchesTable.query to SortOrder.ASC)
                .limit(RecentSearches.KEPT)
                .map { it[RecentSearchesTable.query] }
        }

    override suspend fun clear(customerId: String) {
        tx { RecentSearchesTable.deleteWhere { RecentSearchesTable.customerId eq customerId } }
    }
}
