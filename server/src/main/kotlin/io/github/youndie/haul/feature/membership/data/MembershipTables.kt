package io.github.youndie.haul.feature.membership.data

import io.github.youndie.haul.feature.identity.data.CustomersTable
import io.github.youndie.haul.feature.membership.domain.PointsKind
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

// The Exposed side of V19__plus_and_points.sql. `SchemaTest` holds the two together, CHECK constraints
// included: they are declared here under the names PostgreSQL gave them there.

internal object MembershipsTable : Table("memberships") {
    val customerId = text("customer_id").references(CustomersTable.id, onDelete = ReferenceOption.CASCADE)
    val startedAt = timestampWithTimeZone("started_at")
    val trial = bool("trial")
    val paidFrom = date("paid_from")
    val carriedSavingsCents = integer("carried_savings_cents").default(0)
    val carriedSavingsYear = integer("carried_savings_year").nullable()
    override val primaryKey = PrimaryKey(customerId)
}

internal object PointsEntriesTable : Table("points_entries") {
    val key = text("key")
    val customerId = text("customer_id").references(CustomersTable.id, onDelete = ReferenceOption.CASCADE)
    val kind = text("kind").check("points_entries_kind_check") { it inList PointsKind.entries.map(PointsKind::id) }
    val points = integer("points")
    val orderId = text("order_id").nullable()
    val at = timestampWithTimeZone("at")
    override val primaryKey = PrimaryKey(key)

    init {
        index("points_entries_customer_id", false, customerId)
        // What adds is positive, what takes away negative: a row's sign is its kind's.
        check("points_entries_sign_check") {
            ((kind inList listOf(PointsKind.Earned.id, PointsKind.Returned.id)) and (points greater 0)) or
                ((kind inList listOf(PointsKind.Redeemed.id, PointsKind.Reversed.id)) and (points less 0))
        }
    }
}

internal val membershipTables: List<Table> = listOf(MembershipsTable, PointsEntriesTable)
