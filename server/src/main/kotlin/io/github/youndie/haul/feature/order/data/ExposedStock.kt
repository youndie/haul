package io.github.youndie.haul.feature.order.data

import io.github.youndie.haul.feature.catalog.data.SkusTable
import io.github.youndie.haul.feature.order.domain.StockReservations
import io.github.youndie.haul.feature.order.domain.StockReturns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime

/**
 * Stock reservations over Exposed. A SKU's units are taken by one conditional update — `stock = stock −
 * n WHERE stock >= n` — which PostgreSQL runs against the row's latest committed version: of two orders
 * racing for the last units, the second waits on the first's row lock, re-reads the row, and updates
 * nothing. All of an order's SKUs are taken in one transaction, so a shortage on any one leaves the
 * others untouched.
 *
 * A return's units come back ([restock], B-88) the way they were taken, claimed first: the return's rows in
 * `stock_restocks` are inserted before a unit is added, so a second run under the same key — a pass that died
 * before the return moved — inserts nothing and adds nothing.
 */
internal class ExposedStock(
    private val database: Database,
) : StockReservations,
    StockReturns {
    private suspend fun <T> tx(block: () -> T): T = withContext(Dispatchers.IO) { transaction(database) { block() } }

    override suspend fun reserve(
        holder: String,
        quantities: Map<String, Int>,
        at: OffsetDateTime,
    ): Boolean =
        try {
            tx {
                // The holder's rows first, as the claim: a holder that holds already — an earlier run of the
                // saga's member — inserts nothing and takes nothing more; a second run at the same moment
                // waits on the first's rows and then finds them.
                val claimed =
                    quantities.toSortedMap().map { (sku, units) ->
                        StockReservationsTable
                            .insertIgnore {
                                it[StockReservationsTable.holder] = holder
                                it[skuId] = sku
                                it[quantity] = units
                                it[reservedAt] = at
                            }.insertedCount
                    }
                if (claimed.all { it == 0 }) return@tx true
                // In the SKUs' order, so two orders over the same SKUs lock them in one order and cannot deadlock.
                quantities.toSortedMap().forEach { (sku, units) ->
                    val taken =
                        SkusTable.update({ (SkusTable.id eq sku) and (SkusTable.stock greaterEq units) }) {
                            it[stock] = SkusTable.stock - units
                        }
                    if (taken != 1) throw Shortage()
                }
                true
            }
        } catch (_: Shortage) {
            // Thrown inside the transaction so that it rolls back the claim and the units taken before the shortage.
            false
        }

    override suspend fun release(holder: String): Boolean =
        tx {
            // Locked, then deleted: of two releases at once, the second finds the rows gone and puts nothing back.
            val held =
                StockReservationsTable
                    .selectAll()
                    .where { StockReservationsTable.holder eq holder }
                    .forUpdate()
                    .map { it[StockReservationsTable.skuId] to it[StockReservationsTable.quantity] }
            if (held.isEmpty()) return@tx false
            StockReservationsTable.deleteWhere { StockReservationsTable.holder eq holder }
            held.sortedBy { it.first }.forEach { (sku, units) ->
                SkusTable.update({ SkusTable.id eq sku }) { it[stock] = SkusTable.stock + units }
            }
            true
        }

    override suspend fun restock(
        key: String,
        quantities: Map<String, Int>,
        at: OffsetDateTime,
    ): Boolean =
        tx {
            // In the SKUs' order, as reserve takes them, so a restock and a placement over the same SKUs cannot deadlock.
            quantities.toSortedMap().filterValues { it > 0 }.count { (sku, units) ->
                val claimed =
                    StockRestocksTable
                        .insertIgnore {
                            it[StockRestocksTable.key] = key
                            it[skuId] = sku
                            it[quantity] = units
                            it[restockedAt] = at
                        }.insertedCount
                if (claimed == 1) SkusTable.update({ SkusTable.id eq sku }) { it[stock] = SkusTable.stock + units }
                claimed == 1
            } > 0
        }

    private class Shortage : RuntimeException("not enough stock")
}
