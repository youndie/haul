package io.github.youndie.haul.feature.saved.data

import io.github.youndie.haul.feature.saved.domain.SavedItem
import io.github.youndie.haul.feature.saved.domain.SavedRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime

/**
 * The Saved list over Exposed. A save is an insert that ignores the row already there (`ON CONFLICT DO
 * NOTHING` on the primary key), so a save sent twice — or two at once — keeps the first one's price and
 * day; every read and write names the customer, whose list it is.
 */
internal class ExposedSavedItems(
    private val database: Database,
) : SavedRepository {
    private suspend fun <T> tx(block: () -> T): T = withContext(Dispatchers.IO) { transaction(database) { block() } }

    override suspend fun items(customerId: String): List<SavedItem> =
        tx {
            SavedItemsTable
                .selectAll()
                .where { SavedItemsTable.customerId eq customerId }
                .orderBy(SavedItemsTable.savedAt to SortOrder.DESC, SavedItemsTable.productId to SortOrder.ASC)
                .map {
                    SavedItem(
                        productId = it[SavedItemsTable.productId],
                        savedPriceCents = it[SavedItemsTable.savedPriceCents],
                        savedAt = it[SavedItemsTable.savedAt],
                    )
                }
        }

    override suspend fun productIds(customerId: String): Set<String> =
        tx {
            SavedItemsTable
                .select(SavedItemsTable.productId)
                .where { SavedItemsTable.customerId eq customerId }
                .mapTo(HashSet()) { it[SavedItemsTable.productId] }
        }

    override suspend fun save(
        customerId: String,
        productId: String,
        priceCents: Int,
        at: OffsetDateTime,
    ): Boolean =
        tx {
            SavedItemsTable
                .insertIgnore {
                    it[SavedItemsTable.customerId] = customerId
                    it[SavedItemsTable.productId] = productId
                    it[savedPriceCents] = priceCents
                    it[savedAt] = at
                }.insertedCount > 0
        }

    override suspend fun remove(
        customerId: String,
        productId: String,
    ) {
        tx {
            SavedItemsTable.deleteWhere {
                (SavedItemsTable.customerId eq customerId) and (SavedItemsTable.productId eq productId)
            }
        }
    }
}
