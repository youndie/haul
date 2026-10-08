package io.github.youndie.haul.feature.checkout.data

import io.github.youndie.haul.feature.checkout.AddressEntry
import io.github.youndie.haul.feature.checkout.DeliveryMethod
import io.github.youndie.haul.feature.checkout.domain.Address
import io.github.youndie.haul.feature.checkout.domain.CheckoutRepository
import io.github.youndie.haul.feature.checkout.domain.PickupPoint
import io.github.youndie.haul.feature.checkout.domain.StoredCheckout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Checkouts and addresses over Exposed. The customer is in every filter: an address is found among the
 * caller's own, never by an id alone, so «not yours» has nothing to match.
 */
internal class ExposedCheckoutRepository(
    private val database: Database,
) : CheckoutRepository {
    private suspend fun <T> tx(block: () -> T): T = withContext(Dispatchers.IO) { transaction(database) { block() } }

    override suspend fun checkout(customerId: String): StoredCheckout =
        tx {
            CheckoutsTable.selectAll().where { CheckoutsTable.customerId eq customerId }.singleOrNull()?.let {
                StoredCheckout(
                    method = method(it[CheckoutsTable.method]),
                    addressId = it[CheckoutsTable.addressId],
                    pointId = it[CheckoutsTable.pointId],
                    slotId = it[CheckoutsTable.slot],
                    payment = it[CheckoutsTable.payment],
                    draft = it[CheckoutsTable.addressDraft],
                )
            } ?: StoredCheckout()
        }

    override suspend fun save(
        customerId: String,
        checkout: StoredCheckout,
    ) {
        tx { write(customerId, checkout) }
    }

    override suspend fun addresses(customerId: String): List<Address> =
        tx {
            AddressesTable
                .selectAll()
                .where { AddressesTable.customerId eq customerId }
                .orderBy(AddressesTable.createdAt to SortOrder.DESC, AddressesTable.id to SortOrder.DESC)
                .map(::address)
        }

    override suspend fun addAddress(
        customerId: String,
        entry: AddressEntry,
        at: OffsetDateTime,
    ): Address =
        tx {
            val id = "address-${UUID.randomUUID()}"
            AddressesTable.insert {
                it[AddressesTable.id] = id
                it[AddressesTable.customerId] = customerId
                it[street] = entry.street
                it[apt] = entry.apt.ifEmpty { null }
                it[city] = entry.city
                it[zip] = entry.zip
                it[doorCode] = entry.doorCode.ifEmpty { null }
                it[courierNote] = entry.courierNote.ifEmpty { null }
                it[createdAt] = at
            }
            val current =
                CheckoutsTable.selectAll().where { CheckoutsTable.customerId eq customerId }.singleOrNull()
            write(
                customerId,
                StoredCheckout(
                    method = DeliveryMethod.Courier,
                    addressId = id,
                    pointId = current?.get(CheckoutsTable.pointId),
                    slotId = current?.get(CheckoutsTable.slot),
                    payment = current?.get(CheckoutsTable.payment),
                    draft = null,
                ),
            )
            AddressesTable
                .selectAll()
                .where { AddressesTable.id eq id }
                .single()
                .let(::address)
        }

    override suspend fun pickupPoints(): List<PickupPoint> =
        tx {
            PickupPointsTable.selectAll().orderBy(PickupPointsTable.position).map {
                PickupPoint(
                    id = it[PickupPointsTable.id],
                    method = method(it[PickupPointsTable.kind]),
                    name = it[PickupPointsTable.name],
                    distanceMeters = it[PickupPointsTable.distanceMeters],
                    hours = it[PickupPointsTable.hours],
                    position = it[PickupPointsTable.position],
                )
            }
        }

    private fun write(
        customerId: String,
        checkout: StoredCheckout,
    ) {
        CheckoutsTable.upsert {
            it[CheckoutsTable.customerId] = customerId
            it[method] = column(checkout.method)
            it[addressId] = checkout.addressId
            it[pointId] = checkout.pointId
            it[slot] = checkout.slotId
            it[payment] = checkout.payment
            it[addressDraft] = checkout.draft
        }
    }

    private fun address(row: ResultRow): Address =
        Address(
            id = row[AddressesTable.id],
            street = row[AddressesTable.street],
            apt = row[AddressesTable.apt],
            city = row[AddressesTable.city],
            zip = row[AddressesTable.zip],
            doorCode = row[AddressesTable.doorCode],
            courierNote = row[AddressesTable.courierNote],
            createdAt = row[AddressesTable.createdAt],
        )

    companion object {
        /** The stored name of a method: its wire name (`courier`, `pickup_point`, `parcel_locker`). */
        fun column(method: DeliveryMethod): String =
            when (method) {
                DeliveryMethod.Courier -> "courier"
                DeliveryMethod.PickupPoint -> PickupPointsTable.PICKUP_POINT
                DeliveryMethod.ParcelLocker -> PickupPointsTable.PARCEL_LOCKER
            }

        fun method(column: String): DeliveryMethod =
            DeliveryMethod.entries.firstOrNull { column(it) == column } ?: error("no delivery method «$column»")
    }
}
