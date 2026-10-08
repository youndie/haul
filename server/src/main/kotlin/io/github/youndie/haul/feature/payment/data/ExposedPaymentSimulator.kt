package io.github.youndie.haul.feature.payment.data

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.feature.payment.domain.Authorisation
import io.github.youndie.haul.feature.payment.domain.AuthorisationOutcome
import io.github.youndie.haul.feature.payment.domain.Capture
import io.github.youndie.haul.feature.payment.domain.CaptureOutcome
import io.github.youndie.haul.feature.payment.domain.PaymentProcessor
import io.github.youndie.haul.feature.payment.domain.PaymentSimulator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.ZoneOffset

/**
 * The card processor the store simulates (research D4), with a ledger in PostgreSQL so that what it
 * answered survives the process: one row per key, written once. A key asked again reads the row back —
 * the answer, not the rule, so a retry after the rule changed still gets what the first call got.
 */
internal class ExposedPaymentSimulator(
    private val database: Database,
    private val clock: StoreClock,
) : PaymentProcessor {
    private suspend fun <T> tx(block: () -> T): T = withContext(Dispatchers.IO) { transaction(database) { block() } }

    override suspend fun authorise(
        key: String,
        authorisation: Authorisation,
    ): AuthorisationOutcome =
        tx {
            PaymentAuthorisationsTable.insertIgnore {
                it[this.key] = key
                it[orderId] = authorisation.orderId
                it[method] = authorisation.method.id
                it[amountCents] = authorisation.amountCents
                it[status] =
                    when (PaymentSimulator.decide(authorisation.method)) {
                        AuthorisationOutcome.Authorised -> PaymentAuthorisationsTable.AUTHORISED
                        AuthorisationOutcome.Declined -> PaymentAuthorisationsTable.DECLINED
                    }
                it[createdAt] = clock.now().toOffsetDateTime()
            }
            val status =
                PaymentAuthorisationsTable
                    .selectAll()
                    .where { PaymentAuthorisationsTable.key eq key }
                    .single()[PaymentAuthorisationsTable.status]
            // A voided key stays what it was asked as: it was authorised once, and asking again holds nothing.
            if (status ==
                PaymentAuthorisationsTable.DECLINED
            ) {
                AuthorisationOutcome.Declined
            } else {
                AuthorisationOutcome.Authorised
            }
        }

    override suspend fun void(key: String): Boolean =
        tx {
            PaymentAuthorisationsTable.update({
                (PaymentAuthorisationsTable.key eq key) and
                    (PaymentAuthorisationsTable.status eq PaymentAuthorisationsTable.AUTHORISED) and
                    notExists(
                        PaymentCapturesTable.selectAll().where { PaymentCapturesTable.authorisationKey eq key },
                    )
            }) { it[status] = PaymentAuthorisationsTable.VOIDED } == 1
        }

    /**
     * The order's authorisation is locked first, so two captures of one order — two shipments shipping in
     * two processes at once — are taken one after the other and the second sees what the first took. Only
     * then is the key looked up: a key captured already answers what it took, even if the authorisation was
     * voided since.
     */
    override suspend fun capture(
        key: String,
        capture: Capture,
    ): CaptureOutcome =
        tx {
            val authorisation =
                PaymentAuthorisationsTable
                    .selectAll()
                    .where {
                        (PaymentAuthorisationsTable.orderId eq capture.orderId) and
                            (PaymentAuthorisationsTable.status eq PaymentAuthorisationsTable.AUTHORISED)
                    }.forUpdate()
                    .firstOrNull()
            val earlier =
                PaymentCapturesTable
                    .selectAll()
                    .where { PaymentCapturesTable.key eq key }
                    .singleOrNull()
            when {
                earlier != null -> {
                    CaptureOutcome.Captured(earlier[PaymentCapturesTable.amountCents])
                }

                authorisation == null -> {
                    CaptureOutcome.NotAuthorised
                }

                else -> {
                    val authorisationKey = authorisation[PaymentAuthorisationsTable.key]
                    val taken =
                        PaymentCapturesTable
                            .selectAll()
                            .where { PaymentCapturesTable.authorisationKey eq authorisationKey }
                            .sumOf { it[PaymentCapturesTable.amountCents] }
                    val remaining = authorisation[PaymentAuthorisationsTable.amountCents] - taken
                    if (capture.amountCents > remaining) {
                        CaptureOutcome.Exceeds(remaining)
                    } else {
                        PaymentCapturesTable.insert {
                            it[this.key] = key
                            it[this.authorisationKey] = authorisationKey
                            it[orderId] = capture.orderId
                            it[amountCents] = capture.amountCents
                            it[capturedAt] = capture.at.atOffset(ZoneOffset.UTC)
                        }
                        CaptureOutcome.Captured(capture.amountCents)
                    }
                }
            }
        }

    override suspend fun captured(orderId: String): Map<String, Int> =
        tx {
            PaymentCapturesTable
                .selectAll()
                .where { PaymentCapturesTable.orderId eq orderId }
                .associate { it[PaymentCapturesTable.key] to it[PaymentCapturesTable.amountCents] }
        }
}
