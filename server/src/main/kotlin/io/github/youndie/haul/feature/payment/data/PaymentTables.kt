package io.github.youndie.haul.feature.payment.data

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

// The Exposed side of V10's `payment_authorisations` and V11's `payment_captures`. `SchemaTest` holds the
// two together, CHECK constraints included: declared here under the names PostgreSQL gave them there.

internal object PaymentAuthorisationsTable : Table("payment_authorisations") {
    val key = text("key")
    val orderId = text("order_id")
    val method = text("method")
    val amountCents = integer("amount_cents")
    val status =
        text("status").check("payment_authorisations_status_check") { it inList listOf(AUTHORISED, DECLINED, VOIDED) }
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(key)

    const val AUTHORISED = "authorised"
    const val DECLINED = "declined"
    const val VOIDED = "voided"
}

/** One capture per key, out of one authorisation (B-17): what the shopper has actually been charged. */
internal object PaymentCapturesTable : Table("payment_captures") {
    val key = text("key")
    val authorisationKey =
        text("authorisation_key").references(PaymentAuthorisationsTable.key, onDelete = ReferenceOption.RESTRICT)
    val orderId = text("order_id")
    val amountCents = integer("amount_cents").check("payment_captures_amount_cents_check") { it greater 0 }
    val capturedAt = timestampWithTimeZone("captured_at")
    override val primaryKey = PrimaryKey(key)

    init {
        index("payment_captures_authorisation_key", false, authorisationKey)
    }
}

internal val paymentTables: List<Table> = listOf(PaymentAuthorisationsTable, PaymentCapturesTable)
