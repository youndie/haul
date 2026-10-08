package io.github.youndie.haul.feature.checkout.data

import io.github.youndie.haul.feature.checkout.AddressEntry
import io.github.youndie.haul.feature.identity.data.CustomersTable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

// The Exposed side of V9__checkout.sql. `SchemaTest` holds the two together, CHECK constraints included:
// they are declared here under the names PostgreSQL gave them there.

internal object AddressesTable : Table("addresses") {
    val id = text("id")
    val customerId = text("customer_id").references(CustomersTable.id, onDelete = ReferenceOption.CASCADE)
    val street = text("street")
    val apt = text("apt").nullable()
    val city = text("city")
    val zip = text("zip")
    val doorCode = text("door_code").nullable()
    val courierNote = text("courier_note").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)

    init {
        index("addresses_customer_id", false, customerId)
    }
}

internal object PickupPointsTable : Table("pickup_points") {
    val id = text("id")
    val kind = text("kind").check("pickup_points_kind_check") { it inList listOf(PICKUP_POINT, PARCEL_LOCKER) }
    val name = text("name")
    val distanceMeters = integer("distance_m").nullable()
    val hours = text("hours")
    val position = integer("position")
    override val primaryKey = PrimaryKey(id)

    const val PICKUP_POINT = "pickup_point"
    const val PARCEL_LOCKER = "parcel_locker"
}

internal object DeliverySlotsTable : Table("delivery_slots") {
    val day = date("day")
    val startHour = integer("start_hour")
    val capacity = integer("capacity")
    val taken = integer("taken")
    override val primaryKey = PrimaryKey(day, startHour, name = "pk_delivery_slots")

    init {
        check("delivery_slots_taken_within_capacity") { (taken greaterEq 0) and (taken lessEq capacity) }
    }
}

internal object SlotReservationsTable : Table("slot_reservations") {
    val day = date("day")
    val startHour = integer("start_hour")
    val holder = text("holder")
    val reservedAt = timestampWithTimeZone("reserved_at")
    override val primaryKey = PrimaryKey(day, startHour, holder, name = "pk_slot_reservations")
}

internal object CheckoutsTable : Table("checkouts") {
    val customerId = text("customer_id").references(CustomersTable.id, onDelete = ReferenceOption.CASCADE)
    val method =
        text("method").check("checkouts_method_check") { it inList listOf("courier", "pickup_point", "parcel_locker") }
    val addressId = text("address_id").references(AddressesTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val pointId = text("point_id").references(PickupPointsTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val slot = text("slot").nullable()
    val payment = text("payment").nullable()
    val addressDraft = jsonb<AddressEntry>("address_draft", Json).nullable()

    // V19: the points toggle (B-23).
    val usePoints = bool("use_points").default(false)
    override val primaryKey = PrimaryKey(customerId)
}

internal val checkoutTables: List<Table> =
    listOf(AddressesTable, PickupPointsTable, DeliverySlotsTable, SlotReservationsTable, CheckoutsTable)
