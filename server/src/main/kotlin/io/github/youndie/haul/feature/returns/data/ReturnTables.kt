package io.github.youndie.haul.feature.returns.data

import io.github.youndie.haul.feature.order.data.OrdersTable
import io.github.youndie.haul.feature.returns.domain.ReturnStatus
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

// The Exposed side of V17__returns.sql. `SchemaTest` holds the two together, CHECK constraints included:
// they are declared here under the names PostgreSQL gave them there.

/** One return per order (feature-orders), keyed by the order: a second request finds the row there. */
internal object ReturnsTable : Table("returns") {
    val orderId = text("order_id").references(OrdersTable.id, onDelete = ReferenceOption.CASCADE)
    val reason = text("reason")
    val refundCents = integer("refund_cents").check("returns_refund_cents_check") { it greaterEq 0 }
    val points = integer("points").check("returns_points_check") { it greaterEq 0 }
    val status = text("status").check("returns_status_check") { it inList ReturnStatus.ALL }
    val requestedAt = timestampWithTimeZone("requested_at")
    val pickedUpAt = timestampWithTimeZone("picked_up_at").nullable()
    val refundedAt = timestampWithTimeZone("refunded_at").nullable()
    override val primaryKey = PrimaryKey(orderId)

    init {
        index("returns_status", false, status)
    }
}

/** The order's lines going back, by their position in the order, each with what it gives back. */
internal object ReturnLinesTable : Table("return_lines") {
    val orderId = text("order_id").references(ReturnsTable.orderId, onDelete = ReferenceOption.CASCADE)
    val position = integer("position")
    val refundCents = integer("refund_cents").check("return_lines_refund_cents_check") { it greaterEq 0 }
    override val primaryKey = PrimaryKey(orderId, position, name = "pk_return_lines")
}

internal val returnTables: List<Table> = listOf(ReturnsTable, ReturnLinesTable)
