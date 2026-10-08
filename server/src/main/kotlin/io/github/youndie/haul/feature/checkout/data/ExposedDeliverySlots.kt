package io.github.youndie.haul.feature.checkout.data

import io.github.youndie.haul.feature.checkout.domain.DeliverySlots
import io.github.youndie.haul.feature.checkout.domain.Slot
import io.github.youndie.haul.feature.checkout.domain.SlotLoad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime

/**
 * The windows' capacity over Exposed. A place is taken by one conditional update — `taken = taken + 1
 * WHERE taken < capacity` — which PostgreSQL runs against the row's latest committed version: of two
 * transactions racing for the last place, the second waits on the first's row lock, re-reads the
 * row, and updates nothing. The window's row is created first, with [capacity] places, when no place
 * in it was ever taken.
 */
internal class ExposedDeliverySlots(
    private val database: Database,
    private val capacity: Int = Slot.CAPACITY,
) : DeliverySlots {
    private suspend fun <T> tx(block: () -> T): T = withContext(Dispatchers.IO) { transaction(database) { block() } }

    override suspend fun loads(slots: List<Slot>): List<SlotLoad> {
        if (slots.isEmpty()) return emptyList()
        val stored =
            tx {
                DeliverySlotsTable
                    .selectAll()
                    .where { DeliverySlotsTable.day inList slots.map { it.day }.distinct() }
                    .associate {
                        Slot(it[DeliverySlotsTable.day], it[DeliverySlotsTable.startHour]) to
                            (it[DeliverySlotsTable.capacity] to it[DeliverySlotsTable.taken])
                    }
            }
        return slots.map { slot ->
            stored[slot]?.let { (capacity, taken) -> SlotLoad(slot, capacity, taken) } ?: SlotLoad(slot, capacity, 0)
        }
    }

    override suspend fun reserve(
        slot: Slot,
        holder: String,
        at: OffsetDateTime,
    ): Boolean =
        tx {
            DeliverySlotsTable.insertIgnore {
                it[day] = slot.day
                it[startHour] = slot.startHour
                it[capacity] = this@ExposedDeliverySlots.capacity
                it[taken] = 0
            }
            val held =
                SlotReservationsTable
                    .insertIgnore {
                        it[day] = slot.day
                        it[startHour] = slot.startHour
                        it[SlotReservationsTable.holder] = holder
                        it[reservedAt] = at
                    }.insertedCount == 0
            if (held) return@tx true
            val taken =
                DeliverySlotsTable.update({
                    (DeliverySlotsTable.day eq slot.day) and (DeliverySlotsTable.startHour eq slot.startHour) and
                        (DeliverySlotsTable.taken less DeliverySlotsTable.capacity)
                }) { it[DeliverySlotsTable.taken] = DeliverySlotsTable.taken + 1 } == 1
            // No place left: the reservation written above is taken back.
            if (!taken) deleteReservation(slot, holder)
            taken
        }

    override suspend fun release(
        slot: Slot,
        holder: String,
    ): Boolean =
        tx {
            val released = deleteReservation(slot, holder)
            if (released) {
                DeliverySlotsTable.update({
                    (DeliverySlotsTable.day eq slot.day) and (DeliverySlotsTable.startHour eq slot.startHour)
                }) { it[DeliverySlotsTable.taken] = DeliverySlotsTable.taken - 1 }
            }
            released
        }

    private fun deleteReservation(
        slot: Slot,
        holder: String,
    ): Boolean =
        SlotReservationsTable.deleteWhere {
            (SlotReservationsTable.day eq slot.day) and (SlotReservationsTable.startHour eq slot.startHour) and
                (SlotReservationsTable.holder eq holder)
        } == 1
}
