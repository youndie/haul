package io.github.youndie.haul.feature.returns

import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentPace
import io.github.youndie.haul.feature.payment.domain.PaymentProcessor
import io.github.youndie.haul.feature.payment.domain.Refund
import io.github.youndie.haul.feature.payment.domain.RefundOutcome
import io.github.youndie.haul.feature.returns.domain.RequestReturn
import io.github.youndie.haul.feature.returns.domain.ReturnRepository
import io.github.youndie.haul.feature.returns.domain.ReturnSimulator
import io.github.youndie.haul.feature.returns.domain.ReturnStatus.PICKED_UP
import io.github.youndie.haul.feature.returns.domain.ReturnStatus.REFUNDED
import io.github.youndie.haul.feature.returns.domain.ReturnStatus.REQUESTED
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.testing.FulfilmentWorld
import io.github.youndie.haul.testing.seededFreshDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/**
 * The simulated courier's side of a return (B-21, feature-orders: «the simulator picks it up and refunds»):
 * collected a day after it is asked for, refunded a day after that — the money goes back once, when the
 * seller has the parcel, and never more than the line was paid. Maya's order is delivered at 72 hours and
 * she returns Sony's headphones, $349.00, at 72 hours.
 */
class ReturnLifecycleTest {
    private val asked = 72.hours

    /** Maya's order delivered, Sony's line asked back at [asked]; the order's id. */
    private fun FulfilmentWorld.returned(choice: CheckoutChoice = CheckoutChoice(slotId = "2025-10-08T15")): String {
        val order = place(choice)
        advance(Duration.ZERO)
        advance(asked)
        runBlocking {
            koin.get<RequestReturn>().request(SampleCustomers.MAYA, order, ReturnEntry(listOf(0), "doesnt_fit"))
        }
        return order
    }

    private fun FulfilmentWorld.status(order: String): String? = ledger.storedReturn(order)?.first

    /** Each step comes due exactly at its time and not a millisecond before; the refund is taken with the last. */
    @Test
    fun `a return is collected a day after it is asked for and refunded a day later`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.returned()

                assertEquals(0, world.advanceReturns(asked + 1.days - 1.milliseconds))
                assertEquals(REQUESTED, world.status(order))
                assertEquals(1, world.advanceReturns(asked + 1.days))
                assertEquals(PICKED_UP, world.status(order))
                assertEquals(emptyMap(), world.ledger.refunds(order), "nothing goes back at the door")

                assertEquals(0, world.advanceReturns(asked + 2.days - 1.milliseconds))
                assertEquals(1, world.advanceReturns(asked + 2.days))
                assertEquals(REFUNDED, world.status(order))
                assertEquals(mapOf("refund:$order" to 34_900), world.ledger.refunds(order))

                assertEquals(0, world.advanceReturns(asked + 10.days), "a refunded return moves no more")
                assertEquals(mapOf("refund:$order" to 34_900), world.ledger.refunds(order))
            }
        }

    /** A pass after a pause catches up: both steps at once, the refund stamped when it came due. */
    @Test
    fun `a pass after a pause collects and refunds in one go`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.returned()

                assertEquals(2, world.advanceReturns(asked + 9.days))
                assertEquals(REFUNDED, world.status(order))
                val stamps = world.track(order)?.returned?.history
                assertEquals(world.clock.at(asked + 2.days).now, stamps?.get(REFUNDED))
            }
        }

    /**
     * A process that dies after the refund and before the move leaves the return `picked_up` with its money
     * given back; the next pass asks again under the same key, is answered with what was given, and moves it
     * — the shopper is refunded once.
     */
    @Test
    fun `a pass that died between the refund and the move does not refund twice`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.returned()
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
                val first =
                    ReturnSimulator(
                        dying,
                        world.koin.get(),
                        world.payments,
                        world.koin.get(),
                        world.koin.get(),
                        world.clock,
                        FulfilmentPace.STORE,
                    )
                assertFailsWith<IllegalStateException> { runBlocking { first.advance() } }
                assertEquals(PICKED_UP, world.status(order), "the move never landed")
                assertEquals(mapOf("refund:$order" to 34_900), world.ledger.refunds(order), "the refund did")

                assertEquals(1, world.advanceReturns(asked + 2.days))
                assertEquals(REFUNDED, world.status(order))
                assertEquals(mapOf("refund:$order" to 34_900), world.ledger.refunds(order), "and gave nothing more")
            }
        }

    /** A return never reads «refunded» without the money: a refund the processor refuses holds it, pass after pass. */
    @Test
    fun `a return whose refund is refused stays picked up`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.returned()
                val real = world.payments
                val refusing =
                    object : PaymentProcessor by real {
                        override suspend fun refund(
                            key: String,
                            refund: Refund,
                        ): RefundOutcome = RefundOutcome.NotCaptured
                    }
                val held =
                    ReturnSimulator(
                        world.koin.get(),
                        world.koin.get(),
                        refusing,
                        world.koin.get(),
                        world.koin.get(),
                        world.clock,
                        FulfilmentPace.STORE,
                    )

                world.clock.at(asked + 5.days)
                assertEquals(1, runBlocking { held.advance() }, "collected, and held there")
                assertEquals(PICKED_UP, world.status(order))
                assertEquals(emptyMap(), world.ledger.refunds(order))

                assertEquals(1, world.advanceReturns(asked + 5.days), "refunded, it moves")
                assertEquals(REFUNDED, world.status(order))
            }
        }

    /** Two passes at once — a rolling deploy runs two — move the return through each step once and refund it once. */
    @Test
    fun `two passes at once move and refund a return once`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.returned()
                world.clock.at(asked + 3.days)

                val moves = runBlocking { List(2) { async(Dispatchers.IO) { world.returns.advance() } }.awaitAll() }

                assertEquals(2, moves.sum(), "two steps, made once between the two: $moves")
                assertEquals(REFUNDED, world.status(order))
                assertEquals(mapOf("refund:$order" to 34_900), world.ledger.refunds(order))
            }
        }

    /** Pay on delivery was paid to the courier and is paid back by the courier: refunded, with nothing through the processor. */
    @Test
    fun `pay on delivery is refunded without the processor`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.returned(CheckoutChoice(slotId = "2025-10-08T15", payment = "pay_on_delivery"))

                assertEquals(2, world.advanceReturns(asked + 2.days))
                assertEquals(REFUNDED, world.status(order))
                assertEquals(emptyMap(), world.ledger.refunds(order))
            }
        }
}
