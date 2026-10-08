package io.github.youndie.haul.feature.checkout

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.checkout.data.ExposedDeliverySlots
import io.github.youndie.haul.feature.checkout.data.SlotReservationsTable
import io.github.youndie.haul.feature.checkout.domain.Slot
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.testing.PostgresHarness
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The windows' capacity against PostgreSQL (feature-checkout: «slots … with capacity»; placement
 * takes the place, B-16). The property is the race: however many customers go for the last places at
 * once, exactly as many win as there were places, and no window ever holds more than it takes.
 */
class DeliverySlotsTest {
    private val dataSource = PostgresHarness.freshDatabase()
    private val database = Databases.connect(dataSource)
    private val wednesday = LocalDate.parse("2025-10-08")
    private val at = CatalogSeed.NOW

    /** [racers] holders released together on [slot]; returns how many were answered `true`. */
    private fun race(
        slots: ExposedDeliverySlots,
        slot: Slot,
        racers: Int,
    ): Int =
        runBlocking(Dispatchers.IO) {
            val start = CompletableDeferred<Unit>()
            val answers =
                (1..racers).map { n ->
                    async(Dispatchers.IO) {
                        start.await()
                        slots.reserve(slot, "order-${slot.id}-$n", at)
                    }
                }
            start.complete(Unit)
            answers.awaitAll().count { it }
        }

    private fun holders(slot: Slot): Int =
        transaction(database) {
            SlotReservationsTable
                .selectAll()
                .where {
                    (SlotReservationsTable.day eq slot.day) and (SlotReservationsTable.startHour eq slot.startHour)
                }.count()
                .toInt()
        }

    /**
     * Two customers on the last place: exactly one wins, every round. A check-then-write — read the
     * load, then add one — lets both through whenever their reads interleave.
     */
    @Test
    fun `of two customers on the last place exactly one wins`() {
        val slots = ExposedDeliverySlots(database, capacity = 1)
        repeat(ROUNDS) { round ->
            val slot = Slot(wednesday.plusDays(round.toLong() / 4), Slot.WINDOWS[round % 4])
            assertEquals(1, race(slots, slot, racers = 2), "round $round: not exactly one winner")
            val load = runBlocking { slots.loads(listOf(slot)) }.single()
            assertEquals(1 to 1, load.taken to load.capacity, "round $round")
            assertEquals(1, holders(slot), "round $round: a loser's reservation was kept")
        }
    }

    /** Many at once on a window with three places left: three win, and the window is then full. */
    @Test
    fun `a crowd takes exactly the places there are`() {
        val slots = ExposedDeliverySlots(database, capacity = 3)
        val slot = Slot(wednesday.plusDays(10), 15)
        assertEquals(3, race(slots, slot, racers = 16))
        assertTrue(runBlocking { slots.loads(listOf(slot)) }.single().full)
        assertEquals(3, holders(slot))
    }

    /** Placement retried with the same order takes no second place; its compensation gives back exactly one. */
    @Test
    fun `a holder takes one place and gives back one`() =
        runBlocking {
            val slots = ExposedDeliverySlots(database, capacity = 2)
            val slot = Slot(wednesday.plusDays(11), 9)
            assertTrue(slots.reserve(slot, "order-1", at))
            assertTrue(slots.reserve(slot, "order-1", at))
            assertEquals(1, slots.loads(listOf(slot)).single().taken, "the same order took two places")

            assertTrue(slots.release(slot, "order-1"))
            assertFalse(slots.release(slot, "order-1"), "a place was given back twice")
            assertEquals(0, slots.loads(listOf(slot)).single().taken)
        }

    /** A window nobody reserved in has the server's capacity and nothing taken; it is not stored. */
    @Test
    fun `an untouched window is empty at the default capacity`() =
        runBlocking {
            val load = ExposedDeliverySlots(database).loads(listOf(Slot(wednesday.plusDays(12), 18))).single()
            assertEquals(Slot.CAPACITY to 0, load.capacity to load.taken)
            assertFalse(load.full)
        }

    private companion object {
        const val ROUNDS = 20
    }
}
