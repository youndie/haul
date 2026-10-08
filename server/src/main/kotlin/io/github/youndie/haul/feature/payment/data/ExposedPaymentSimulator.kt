package io.github.youndie.haul.feature.payment.data

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.feature.payment.domain.Authorisation
import io.github.youndie.haul.feature.payment.domain.AuthorisationOutcome
import io.github.youndie.haul.feature.payment.domain.PaymentProcessor
import io.github.youndie.haul.feature.payment.domain.PaymentSimulator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

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
                    (PaymentAuthorisationsTable.status eq PaymentAuthorisationsTable.AUTHORISED)
            }) { it[status] = PaymentAuthorisationsTable.VOIDED } == 1
        }
}
