package io.github.youndie.haul.feature.payment.data

import io.github.youndie.haul.feature.catalog.domain.HaulPay
import io.github.youndie.haul.feature.order.data.OrdersTable
import io.github.youndie.haul.feature.payment.domain.InstalmentStatus
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

// The Exposed side of V10's `payment_authorisations`, V11's `payment_captures`, V17's `payment_refunds` and V21's
// Haul Pay plans. `SchemaTest` holds the two together, CHECK constraints included: declared here under the names
// PostgreSQL gave them there.

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

/**
 * One refund per key (B-21): what was given back of an order's captures. Keyed by the order, not by an
 * authorisation — what is given back is out of what was captured, whichever shipment took it.
 */
internal object PaymentRefundsTable : Table("payment_refunds") {
    val key = text("key")
    val orderId = text("order_id")
    val amountCents = integer("amount_cents").check("payment_refunds_amount_cents_check") { it greater 0 }
    val refundedAt = timestampWithTimeZone("refunded_at")
    override val primaryKey = PrimaryKey(key)

    init {
        index("payment_refunds_order_id", false, orderId)
    }
}

/**
 * A Haul Pay plan (B-24), written when it starts: the order's first shipment leaving for the road. [reducedCents]
 * is what the order's return took off the payments still owed, `null` until the return is refunded.
 */
internal object InstalmentPlansTable : Table("instalment_plans") {
    val orderId = text("order_id").references(OrdersTable.id, onDelete = ReferenceOption.CASCADE)
    val totalCents = integer("total_cents").check("instalment_plans_total_cents_check") { it greater 0 }
    val startedAt = timestampWithTimeZone("started_at")
    val reducedCents =
        integer("reduced_cents").check("instalment_plans_reduced_cents_check") { it greaterEq 0 }.nullable()
    val reducedAt = timestampWithTimeZone("reduced_at").nullable()
    override val primaryKey = PrimaryKey(orderId)
}

/** A plan's four payments, each moved by the simulator ([InstalmentStatus]) and each charged once, by its key. */
internal object InstalmentsTable : Table("instalments") {
    val orderId = text("order_id").references(InstalmentPlansTable.orderId, onDelete = ReferenceOption.CASCADE)
    val number = integer("number").check("instalments_number_check") { it inList (1..HaulPay.PAYMENTS).toList() }
    val amountCents = integer("amount_cents").check("instalments_amount_cents_check") { it greater 0 }
    val reducedCents = integer("reduced_cents").check("instalments_reduced_cents_check") { it greaterEq 0 }
    val dueAt = timestampWithTimeZone("due_at")
    val status = text("status").check("instalments_status_check") { it inList InstalmentStatus.ALL }
    val attempts = integer("attempts").check("instalments_attempts_check") { it greaterEq 0 }
    val nextAttemptAt = timestampWithTimeZone("next_attempt_at")
    val chargeCents = integer("charge_cents").check("instalments_charge_cents_check") { it greaterEq 0 }.nullable()
    val paidAt = timestampWithTimeZone("paid_at").nullable()
    val declinedAt = timestampWithTimeZone("declined_at").nullable()
    override val primaryKey = PrimaryKey(orderId, number, name = "pk_instalments")

    init {
        index("instalments_status", false, status)
    }
}

internal val paymentTables: List<Table> =
    listOf(
        PaymentAuthorisationsTable,
        PaymentCapturesTable,
        PaymentRefundsTable,
        InstalmentPlansTable,
        InstalmentsTable,
    )
