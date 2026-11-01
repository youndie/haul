package io.github.youndie.haul.feature.returns

import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentPace
import io.github.youndie.haul.feature.returns.domain.RequestReturn
import io.github.youndie.haul.feature.returns.domain.ReturnRepository
import io.github.youndie.haul.feature.returns.domain.ReturnSimulator
import io.github.youndie.haul.feature.returns.domain.ReturnStatus.PICKED_UP
import io.github.youndie.haul.feature.returns.domain.ReturnStatus.REFUNDED
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.testing.FulfilmentWorld
import io.github.youndie.haul.testing.MAYAS_SKUS
import io.github.youndie.haul.testing.seededFreshDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * A returned unit goes back to stock (B-88): Maya's cart placed through the order saga — which takes its units
 * off the shelf — delivered, a line returned, and the line's units back on its SKU's stock once the return is
 * refunded: not at the door, not twice when a pass dies after the restock and runs again, not twice when two
 * passes run at once. The lines she keeps stay sold.
 */
class ReturnRestocksTest {
    private val asked = 72.hours

    private val sonora = MAYAS_SKUS[0]

    /** Maya's order placed and delivered, and the stock before it was placed; the lines at [positions] asked back at [asked]. */
    private fun FulfilmentWorld.returned(positions: List<Int>): Pair<String, Map<String, Int>> {
        val before = ledger.stock()
        val order = place(CheckoutChoice(slotId = "2025-10-08T15"))
        advance(Duration.ZERO)
        advance(asked)
        runBlocking {
            koin.get<RequestReturn>().request(SampleCustomers.MAYA, order, ReturnEntry(positions, "doesnt_fit"))
        }
        return order to before
    }

    private fun FulfilmentWorld.status(order: String): String? = ledger.storedReturn(order)?.first

    @Test
    fun `a refunded line's units go back to stock and the kept lines stay sold`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val (order, before) = world.returned(listOf(0))
                val sold = world.ledger.stock()
                assertTrue(
                    MAYAS_SKUS.all { sold.getValue(it) < before.getValue(it) },
                    "placement took every line: $before → $sold",
                )

                world.advanceReturns(asked + 1.days)
                assertEquals(PICKED_UP, world.status(order))
                assertEquals(sold, world.ledger.stock(), "nothing is back on the shelf while the courier has it")

                world.advanceReturns(asked + 2.days)
                assertEquals(REFUNDED, world.status(order))
                assertEquals(before + (MAYAS_SKUS - sonora).associateWith { sold.getValue(it) }, world.ledger.stock())

                world.advanceReturns(asked + 10.days)
                assertEquals(
                    before.getValue(sonora),
                    world.ledger.stock().getValue(sonora),
                    "a refunded return restocks no more",
                )
            }
        }

    /** The synthetic shoppers' walk in small (B-31): everything bought comes back, and the shelf is as it was. */
    @Test
    fun `an order returned whole leaves the stock as it was before it was placed`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val (order, before) = world.returned(MAYAS_SKUS.indices.toList())

                assertEquals(2, world.advanceReturns(asked + 2.days))
                assertEquals(REFUNDED, world.status(order))
                assertEquals(before, world.ledger.stock())
            }
        }

    /**
     * A process that dies after the restock and before the move leaves the return `picked_up` with its units back;
     * the next pass restocks under the same key, finds it done, and moves the return — the units come back once.
     */
    @Test
    fun `a pass that died between the restock and the move does not restock twice`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val (order, before) = world.returned(listOf(0))
                world.advanceReturns(asked + 1.days)

                val real = world.koin.get<ReturnRepository>()
                val dying =
                    object : ReturnRepository by real {
                        override suspend fun move(
                            orderId: String,
                            from: String,
                            to: String,
                            at: Instant,
                        ): Boolean =
                            if (to ==
                                REFUNDED
                            ) {
                                error("the process died before the move")
                            } else {
                                real.move(orderId, from, to, at)
                            }
                    }
                world.clock.at(asked + 2.days)
                val first = simulator(world, dying)
                assertFailsWith<IllegalStateException> { runBlocking { first.advance() } }
                assertEquals(PICKED_UP, world.status(order), "the move never landed")
                assertEquals(before.getValue(sonora), world.ledger.stock().getValue(sonora), "the restock did")

                assertEquals(1, world.advanceReturns(asked + 2.days))
                assertEquals(REFUNDED, world.status(order))
                assertEquals(before.getValue(sonora), world.ledger.stock().getValue(sonora), "and added nothing more")
            }
        }

    /**
     * Two passes at once — a rolling deploy runs two — that both reach the refund before either moves the return
     * (held at the move until both are there): the units come back once between them.
     */
    @Test
    fun `two passes at once restock a return once`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val (order, before) = world.returned(listOf(0))
                world.advanceReturns(asked + 1.days)

                val real = world.koin.get<ReturnRepository>()
                val bothThere = CyclicBarrier(2)
                val held =
                    object : ReturnRepository by real {
                        override suspend fun move(
                            orderId: String,
                            from: String,
                            to: String,
                            at: Instant,
                        ): Boolean {
                            if (to == REFUNDED) bothThere.await(10, TimeUnit.SECONDS)
                            return real.move(orderId, from, to, at)
                        }
                    }
                world.clock.at(asked + 3.days)
                val moves =
                    runBlocking { List(2) { async(Dispatchers.IO) { simulator(world, held).advance() } }.awaitAll() }

                assertEquals(1, moves.sum(), "the return moved once between the two: $moves")
                assertEquals(REFUNDED, world.status(order))
                assertEquals(before.getValue(sonora), world.ledger.stock().getValue(sonora))
            }
        }

    private fun simulator(
        world: FulfilmentWorld,
        returns: ReturnRepository,
    ) = ReturnSimulator(
        returns = returns,
        orders = world.koin.get(),
        payments = world.payments,
        plans = world.koin.get(),
        points = world.koin.get(),
        stock = world.koin.get(),
        clock = world.clock,
        pace = FulfilmentPace.STORE,
    )
}
