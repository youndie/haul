package io.github.youndie.haul.feature.fulfilment.data

import io.github.youndie.haul.feature.order.data.ShipmentsTable
import io.github.youndie.haul.feature.order.domain.ShipmentStatus
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

// The Exposed side of V11's `shipment_events`. `SchemaTest` holds the two together, CHECK constraints
// included: declared here under the names PostgreSQL gave them there.

/** When a shipment entered each status of its way (B-17), one row per status, written once. */
internal object ShipmentEventsTable : Table("shipment_events") {
    val shipmentId = text("shipment_id").references(ShipmentsTable.id, onDelete = ReferenceOption.CASCADE)
    val status = text("status").check("shipment_events_status_check") { it inList ShipmentStatus.LIFECYCLE }
    val enteredAt = timestampWithTimeZone("entered_at")
    override val primaryKey = PrimaryKey(shipmentId, status, name = "pk_shipment_events")
}

internal val fulfilmentTables: List<Table> = listOf(ShipmentEventsTable)
