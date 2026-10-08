package io.github.youndie.haul.feature.returns.data

import io.github.youndie.haul.feature.returns.domain.NewReturn
import io.github.youndie.haul.feature.returns.domain.OrderReturn
import io.github.youndie.haul.feature.returns.domain.ReturnRepository
import io.github.youndie.haul.feature.returns.domain.ReturnStatus
import io.github.youndie.haul.feature.returns.domain.ReturnedLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.ZoneOffset

/** Returns over Exposed; the request is one transaction, and every move is conditional on where it leaves. */
internal class ExposedReturns(
    private val database: Database,
) : ReturnRepository {
    private suspend fun <T> tx(block: () -> T): T = withContext(Dispatchers.IO) { transaction(database) { block() } }

    override suspend fun request(request: NewReturn): Boolean =
        tx {
            val inserted =
                ReturnsTable
                    .insertIgnore {
                        it[orderId] = request.orderId
                        it[reason] = request.reason
                        it[refundCents] = request.refundCents
                        it[points] = request.points
                        it[status] = ReturnStatus.REQUESTED
                        it[requestedAt] = request.requestedAt.atOffset(ZoneOffset.UTC)
                    }.insertedCount == 1
            if (inserted) {
                ReturnLinesTable.batchInsert(request.lines) { line ->
                    this[ReturnLinesTable.orderId] = request.orderId
                    this[ReturnLinesTable.position] = line.position
                    this[ReturnLinesTable.refundCents] = line.refundCents
                }
            }
            inserted
        }

    override suspend fun returnOf(orderId: String): OrderReturn? =
        tx {
            ReturnsTable
                .selectAll()
                .where { ReturnsTable.orderId eq orderId }
                .singleOrNull()
                ?.let(::read)
        }

    override suspend fun active(): List<OrderReturn> =
        tx {
            ReturnsTable
                .selectAll()
                .where { ReturnsTable.status neq ReturnStatus.REFUNDED }
                .orderBy(ReturnsTable.requestedAt, SortOrder.ASC)
                .map(::read)
        }

    override suspend fun move(
        orderId: String,
        from: String,
        to: String,
        at: Instant,
    ): Boolean =
        tx {
            ReturnsTable.update({ (ReturnsTable.orderId eq orderId) and (ReturnsTable.status eq from) }) {
                it[status] = to
                val stamp = at.atOffset(ZoneOffset.UTC)
                when (to) {
                    ReturnStatus.PICKED_UP -> it[pickedUpAt] = stamp
                    ReturnStatus.REFUNDED -> it[refundedAt] = stamp
                    else -> error("a return does not move to «$to»")
                }
            } == 1
        }

    /** [row] with its lines; called inside a transaction. */
    private fun read(row: ResultRow): OrderReturn {
        val orderId = row[ReturnsTable.orderId]
        val lines =
            ReturnLinesTable
                .selectAll()
                .where { ReturnLinesTable.orderId eq orderId }
                .orderBy(ReturnLinesTable.position, SortOrder.ASC)
                .map { ReturnedLine(it[ReturnLinesTable.position], it[ReturnLinesTable.refundCents]) }
        val history =
            listOfNotNull(
                ReturnStatus.REQUESTED to row[ReturnsTable.requestedAt],
                row[ReturnsTable.pickedUpAt]?.let { ReturnStatus.PICKED_UP to it },
                row[ReturnsTable.refundedAt]?.let { ReturnStatus.REFUNDED to it },
            ).associate { (status, at) -> status to at.toInstant() }
        return OrderReturn(
            orderId = orderId,
            reason = row[ReturnsTable.reason],
            lines = lines,
            refundCents = row[ReturnsTable.refundCents],
            points = row[ReturnsTable.points],
            status = row[ReturnsTable.status],
            history = history,
        )
    }
}
