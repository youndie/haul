package io.github.youndie.haul.feature.payment.data

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

// The Exposed side of V10's `payment_authorisations`. `SchemaTest` holds the two together, CHECK
// constraints included: declared here under the names PostgreSQL gave them there.

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

internal val paymentTables: List<Table> = listOf(PaymentAuthorisationsTable)
