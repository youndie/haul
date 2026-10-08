package io.github.youndie.haul.feature.membership.data

import io.github.youndie.haul.feature.identity.data.CustomersTable
import io.github.youndie.haul.feature.membership.domain.Memberships
import io.github.youndie.haul.feature.membership.domain.PlusMembership
import io.github.youndie.haul.feature.membership.domain.PointsKind
import io.github.youndie.haul.feature.membership.domain.PointsLedger
import io.github.youndie.haul.feature.membership.domain.PointsMovement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime

/** Memberships over Exposed. */
internal class ExposedMemberships(
    private val database: Database,
) : Memberships {
    private suspend fun <T> tx(block: () -> T): T = withContext(Dispatchers.IO) { transaction(database) { block() } }

    override suspend fun membership(customerId: String): PlusMembership? =
        tx {
            MembershipsTable
                .selectAll()
                .where { MembershipsTable.customerId eq customerId }
                .singleOrNull()
                ?.let {
                    PlusMembership(
                        customerId = it[MembershipsTable.customerId],
                        startedAt = it[MembershipsTable.startedAt],
                        trial = it[MembershipsTable.trial],
                        paidFrom = it[MembershipsTable.paidFrom],
                        carriedSavingsCents = it[MembershipsTable.carriedSavingsCents],
                        carriedSavingsYear = it[MembershipsTable.carriedSavingsYear],
                    )
                }
        }

    /**
     * The flag first, conditionally: of two trials started at once, one turns `plus` on and the other finds
     * it on and writes nothing. A member without a row — a customer flagged before memberships were kept —
     * is refused the same way.
     */
    override suspend fun start(membership: PlusMembership): Boolean =
        tx {
            val flagged =
                CustomersTable.update({
                    (CustomersTable.id eq membership.customerId) and (CustomersTable.plus eq false)
                }) { it[plus] = true }
            if (flagged == 0) return@tx false
            MembershipsTable.insertIgnore {
                it[customerId] = membership.customerId
                it[startedAt] = membership.startedAt
                it[trial] = membership.trial
                it[paidFrom] = membership.paidFrom
                it[carriedSavingsCents] = membership.carriedSavingsCents
                it[carriedSavingsYear] = membership.carriedSavingsYear
            }
            true
        }
}

/** The points ledger over Exposed: one row per movement, each written once by its key. */
internal class ExposedPointsLedger(
    private val database: Database,
) : PointsLedger {
    private suspend fun <T> tx(block: () -> T): T = withContext(Dispatchers.IO) { transaction(database) { block() } }

    override suspend fun balance(customerId: String): Int = tx { sumOf(customerId) }

    override suspend fun record(movement: PointsMovement): Boolean = tx { insert(movement) }

    override suspend fun redeem(movement: PointsMovement): Boolean =
        tx {
            // The customer's row locked for the transaction: a second redemption waits for this one, then
            // reads the balance it left.
            CustomersTable
                .selectAll()
                .where { CustomersTable.id eq movement.customerId }
                .forUpdate()
                .toList()
            when {
                PointsEntriesTable.selectAll().where { PointsEntriesTable.key eq movement.key }.any() -> true
                sumOf(movement.customerId) + movement.points < 0 -> false
                else -> insert(movement)
            }
        }

    override suspend fun giveBack(
        orderId: String,
        at: OffsetDateTime,
    ): Boolean =
        tx {
            val redeemed =
                PointsEntriesTable
                    .selectAll()
                    .where {
                        (PointsEntriesTable.orderId eq orderId) and
                            (PointsEntriesTable.kind eq PointsKind.Redeemed.id)
                    }.singleOrNull()
                    ?.let(::movement) ?: return@tx false
            insert(PointsMovement.returned(redeemed.customerId, orderId, -redeemed.points, at))
        }

    override suspend fun movements(customerId: String): List<PointsMovement> =
        tx {
            PointsEntriesTable
                .selectAll()
                .where { PointsEntriesTable.customerId eq customerId }
                .orderBy(PointsEntriesTable.at to SortOrder.ASC, PointsEntriesTable.key to SortOrder.ASC)
                .map(::movement)
        }

    private fun sumOf(customerId: String): Int {
        val total = PointsEntriesTable.points.sum()
        return PointsEntriesTable
            .select(total)
            .where { PointsEntriesTable.customerId eq customerId }
            .single()[total] ?: 0
    }

    private fun insert(movement: PointsMovement): Boolean =
        PointsEntriesTable
            .insertIgnore {
                it[key] = movement.key
                it[customerId] = movement.customerId
                it[kind] = movement.kind.id
                it[points] = movement.points
                it[orderId] = movement.orderId
                it[at] = movement.at
            }.insertedCount == 1

    private fun movement(row: ResultRow): PointsMovement =
        PointsMovement(
            key = row[PointsEntriesTable.key],
            customerId = row[PointsEntriesTable.customerId],
            kind = PointsKind.of(row[PointsEntriesTable.kind]),
            points = row[PointsEntriesTable.points],
            orderId = row[PointsEntriesTable.orderId],
            at = row[PointsEntriesTable.at],
        )
}
