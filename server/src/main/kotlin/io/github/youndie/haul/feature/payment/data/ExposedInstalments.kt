package io.github.youndie.haul.feature.payment.data

import io.github.youndie.haul.feature.payment.domain.Instalment
import io.github.youndie.haul.feature.payment.domain.InstalmentPlan
import io.github.youndie.haul.feature.payment.domain.InstalmentRepository
import io.github.youndie.haul.feature.payment.domain.InstalmentStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Haul Pay plans over Exposed (V21). A plan and its payments are written in one transaction, once; every move
 * of a payment is one conditional update, so it lands once however many passes ask; a return's reduction locks
 * the plan and its payments, so a claim waits for it and charges what it left — or the reduction skips the
 * payment the claim took first.
 */
internal class ExposedInstalments(
    private val database: Database,
) : InstalmentRepository {
    private suspend fun <T> tx(block: () -> T): T = withContext(Dispatchers.IO) { transaction(database) { block() } }

    override suspend fun start(
        orderId: String,
        totalCents: Int,
        startedAt: Instant,
        instalments: List<Instalment>,
    ): InstalmentPlan =
        tx {
            val written =
                InstalmentPlansTable
                    .insertIgnore {
                        it[this.orderId] = orderId
                        it[this.totalCents] = totalCents
                        it[this.startedAt] = stamp(startedAt)
                    }.insertedCount == 1
            if (written) {
                InstalmentsTable.batchInsert(instalments) { instalment ->
                    val due = checkNotNull(instalment.dueAt) { "payment ${instalment.number} of $orderId has no date" }
                    this[InstalmentsTable.orderId] = orderId
                    this[InstalmentsTable.number] = instalment.number
                    this[InstalmentsTable.amountCents] = instalment.amountCents
                    this[InstalmentsTable.reducedCents] = 0
                    this[InstalmentsTable.dueAt] = stamp(due)
                    this[InstalmentsTable.status] = InstalmentStatus.SCHEDULED
                    this[InstalmentsTable.attempts] = 0
                    this[InstalmentsTable.nextAttemptAt] = stamp(due)
                }
            }
            checkNotNull(read(orderId))
        }

    override suspend fun plan(orderId: String): InstalmentPlan? = tx { read(orderId) }

    override suspend fun due(now: Instant): List<String> =
        tx {
            val overdue = InstalmentsTable.alias("overdue")
            InstalmentsTable
                .selectAll()
                .where {
                    (InstalmentsTable.status eq InstalmentStatus.COLLECTING) or
                        (
                            (InstalmentsTable.status eq InstalmentStatus.SCHEDULED) and
                                (InstalmentsTable.nextAttemptAt lessEq stamp(now)) and
                                notExists(
                                    overdue.selectAll().where {
                                        (overdue[InstalmentsTable.orderId] eq InstalmentsTable.orderId) and
                                            (overdue[InstalmentsTable.status] eq InstalmentStatus.OVERDUE)
                                    },
                                )
                        )
                }.orderBy(InstalmentsTable.nextAttemptAt, SortOrder.ASC)
                .map { it[InstalmentsTable.orderId] }
                .distinct()
        }

    override suspend fun claim(
        orderId: String,
        number: Int,
        declined: Int,
    ): Int? =
        tx {
            val row =
                InstalmentsTable
                    .selectAll()
                    .where {
                        (InstalmentsTable.orderId eq orderId) and (InstalmentsTable.number eq number) and
                            (InstalmentsTable.status eq InstalmentStatus.SCHEDULED) and
                            (InstalmentsTable.attempts eq declined)
                    }.forUpdate()
                    .singleOrNull() ?: return@tx null
            val owed = row[InstalmentsTable.amountCents] - row[InstalmentsTable.reducedCents]
            InstalmentsTable.update({ (InstalmentsTable.orderId eq orderId) and (InstalmentsTable.number eq number) }) {
                it[status] = if (owed == 0) InstalmentStatus.COVERED else InstalmentStatus.COLLECTING
                it[chargeCents] = owed
            }
            owed
        }

    override suspend fun paid(
        orderId: String,
        number: Int,
        at: Instant,
    ): Boolean =
        tx {
            InstalmentsTable.update({
                (InstalmentsTable.orderId eq orderId) and (InstalmentsTable.number eq number) and
                    (InstalmentsTable.status eq InstalmentStatus.COLLECTING)
            }) {
                it[status] = InstalmentStatus.PAID
                it[paidAt] = stamp(at)
            } == 1
        }

    override suspend fun declined(
        orderId: String,
        number: Int,
        declined: Int,
        at: Instant,
        retryAt: Instant?,
    ): Boolean =
        tx {
            InstalmentsTable.update({
                (InstalmentsTable.orderId eq orderId) and (InstalmentsTable.number eq number) and
                    (InstalmentsTable.status eq InstalmentStatus.COLLECTING) and
                    (InstalmentsTable.attempts eq declined)
            }) {
                it[attempts] = declined + 1
                it[declinedAt] = stamp(at)
                it[chargeCents] = null
                if (retryAt == null) {
                    it[status] = InstalmentStatus.OVERDUE
                } else {
                    it[status] = InstalmentStatus.SCHEDULED
                    it[nextAttemptAt] = stamp(retryAt)
                }
            } == 1
        }

    override suspend fun reduce(
        orderId: String,
        cents: Int,
        at: Instant,
    ): Int =
        tx {
            val plan =
                InstalmentPlansTable
                    .selectAll()
                    .where { InstalmentPlansTable.orderId eq orderId }
                    .forUpdate()
                    .singleOrNull() ?: return@tx 0
            plan[InstalmentPlansTable.reducedCents]?.let { return@tx it }
            val owed =
                InstalmentsTable
                    .selectAll()
                    .where { InstalmentsTable.orderId eq orderId }
                    .orderBy(InstalmentsTable.number, SortOrder.DESC)
                    .forUpdate()
                    .toList()
                    .filter { it[InstalmentsTable.status] in REDUCIBLE }
            var left = cents
            for (row in owed) {
                if (left == 0) break
                val number = row[InstalmentsTable.number]
                val reduced = row[InstalmentsTable.reducedCents]
                val take = minOf(left, row[InstalmentsTable.amountCents] - reduced)
                if (take == 0) continue
                left -= take
                InstalmentsTable.update({ (InstalmentsTable.orderId eq orderId) and (InstalmentsTable.number eq number) }) {
                    it[reducedCents] = reduced + take
                    if (reduced + take == row[InstalmentsTable.amountCents]) it[status] = InstalmentStatus.COVERED
                }
            }
            val taken = cents - left
            InstalmentPlansTable.update({ InstalmentPlansTable.orderId eq orderId }) {
                it[reducedCents] = taken
                it[reducedAt] = stamp(at)
            }
            taken
        }

    /** [orderId]'s plan with its payments in order; called inside a transaction. */
    private fun read(orderId: String): InstalmentPlan? {
        val plan =
            InstalmentPlansTable
                .selectAll()
                .where { InstalmentPlansTable.orderId eq orderId }
                .singleOrNull() ?: return null
        val instalments =
            InstalmentsTable
                .selectAll()
                .where { InstalmentsTable.orderId eq orderId }
                .orderBy(InstalmentsTable.number, SortOrder.ASC)
                .map(::instalment)
        return InstalmentPlan(
            orderId = orderId,
            totalCents = plan[InstalmentPlansTable.totalCents],
            startedAt = plan[InstalmentPlansTable.startedAt].toInstant(),
            instalments = instalments,
            reducedCents = plan[InstalmentPlansTable.reducedCents],
        )
    }

    private fun instalment(row: ResultRow): Instalment =
        Instalment(
            number = row[InstalmentsTable.number],
            amountCents = row[InstalmentsTable.amountCents],
            reducedCents = row[InstalmentsTable.reducedCents],
            dueAt = row[InstalmentsTable.dueAt].toInstant(),
            status = row[InstalmentsTable.status],
            declined = row[InstalmentsTable.attempts],
            nextAttemptAt = row[InstalmentsTable.nextAttemptAt].toInstant(),
            chargeCents = row[InstalmentsTable.chargeCents],
            paidAt = row[InstalmentsTable.paidAt]?.toInstant(),
            declinedAt = row[InstalmentsTable.declinedAt]?.toInstant(),
        )

    private fun stamp(at: Instant): OffsetDateTime = at.atOffset(ZoneOffset.UTC)

    private companion object {
        /** What a return may take off: a payment still owed, overdue included — not one being charged or charged. */
        val REDUCIBLE = setOf(InstalmentStatus.SCHEDULED, InstalmentStatus.OVERDUE)
    }
}
