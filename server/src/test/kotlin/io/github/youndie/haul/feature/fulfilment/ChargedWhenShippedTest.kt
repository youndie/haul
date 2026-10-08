package io.github.youndie.haul.feature.fulfilment

import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.checkout.domain.PaymentMethod
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentPace
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentRepository
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentSimulator
import io.github.youndie.haul.feature.order.domain.ShipmentStatus.DELIVERED
import io.github.youndie.haul.feature.order.domain.ShipmentStatus.IN_TRANSIT
import io.github.youndie.haul.feature.order.domain.ShipmentStatus.PACKED
import io.github.youndie.haul.feature.payment.domain.Capture
import io.github.youndie.haul.feature.payment.domain.CaptureOutcome
import io.github.youndie.haul.feature.payment.domain.PaymentProcessor
import io.github.youndie.haul.testing.FulfilmentWorld
import io.github.youndie.haul.testing.Ledger
import io.github.youndie.haul.testing.seededFreshDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * «Your card is charged when the order ships» (the canvas, research D4): placement authorises the total,
 * and each shipment's share is captured out of it when that shipment leaves for the road — not at
 * placement, not when it is packed, and never twice.
 *
 * Maya's order is $512.00 (research §6): Sony's headphones, $349.00, ship a day before Brooklyn Home Co.'s
 * duvet cover and mugs, $139.00 + $24.00.
 */
class ChargedWhenShippedTest {
    private val sony = "HL-48302-1"
    private val brooklyn = "HL-48302-2"

    /**
     * feature-orders «Charged when shipped»: an order with two shipments; when the first reaches
     * `in_transit`, exactly that shipment's amount is captured and the second is still only authorised.
     * When the second ships, the two captures add up to the authorised total.
     */
    @Test
    fun `charged when shipped`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val ledger = Ledger(dataSource)
                val order = world.place()
                assertEquals(listOf(Triple(order, 51_200, "authorised")), ledger.authorisations())
                assertEquals(emptyMap(), world.captures(order), "nothing is charged at placement")

                world.advance(Duration.ZERO)
                world.advance(4.hours)
                assertEquals(mapOf(sony to PACKED, brooklyn to PACKED), world.statuses(order))
                assertEquals(emptyMap(), world.captures(order), "nothing is charged when it is packed")

                world.advance(24.hours)
                assertEquals(mapOf(sony to IN_TRANSIT, brooklyn to PACKED), world.statuses(order))
                assertEquals(
                    mapOf(sony to 34_900),
                    world.captures(order),
                    "exactly Sony's share, and Brooklyn's still held",
                )
                assertEquals(listOf(Triple(order, 51_200, "authorised")), ledger.authorisations())

                world.advance(48.hours)
                assertEquals(mapOf(sony to 34_900, brooklyn to 16_300), world.captures(order))
                assertEquals(51_200, world.captures(order).values.sum(), "the parts add up to the total")
                val tracked = assertNotNull(world.track(order)).shipments
                assertEquals(listOf(34_900, 16_300), tracked.map { it.capturedCents })
                assertEquals(tracked.map { it.shareCents }, tracked.map { it.capturedCents })

                world.advance(72.hours)
                assertEquals(
                    mapOf(sony to 34_900, brooklyn to 16_300),
                    world.captures(order),
                    "delivery charges nothing",
                )
            }
        }

    /**
     * A process that dies after the capture and before the move leaves the shipment `packed` with its share
     * taken. The next process's pass asks for the capture again under the same name, is answered with what
     * was taken, and moves the shipment — the shopper is charged once.
     */
    @Test
    fun `a pass that died between the capture and the move does not charge twice`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.place()
                world.advance(Duration.ZERO)
                world.advance(4.hours)

                val real = world.shipments
                val dying =
                    object : FulfilmentRepository by real {
                        override suspend fun move(
                            shipmentId: String,
                            from: String,
                            to: String,
                            at: Instant,
                            pickupCode: String?,
                        ): Boolean =
                            if (to == IN_TRANSIT) {
                                error("the process died before the move")
                            } else {
                                real.move(shipmentId, from, to, at, pickupCode)
                            }
                    }
                world.clock.at(24.hours)
                val first =
                    FulfilmentSimulator(
                        dying,
                        world.koin.get(),
                        world.payments,
                        world.koin.get(),
                        world.clock,
                        FulfilmentPace.STORE,
                    )
                assertFailsWith<IllegalStateException> { runBlocking { first.advance() } }
                assertEquals(mapOf(sony to PACKED, brooklyn to PACKED), world.statuses(order), "the move never landed")
                assertEquals(mapOf(sony to 34_900), world.captures(order), "the capture did")

                assertEquals(1, world.advance(24.hours), "the next process moves Sony")
                assertEquals(mapOf(sony to IN_TRANSIT, brooklyn to PACKED), world.statuses(order))
                assertEquals(mapOf(sony to 34_900), world.captures(order), "and charges nothing more")

                world.advance(72.hours)
                assertEquals(mapOf(sony to DELIVERED, brooklyn to DELIVERED), world.statuses(order))
                assertEquals(51_200, world.captures(order).values.sum())
            }
        }

    /**
     * A shipment is never on the road unpaid: a capture the processor refuses holds it `packed`, pass after
     * pass, and it ships once the capture is taken.
     */
    @Test
    fun `a shipment whose capture is refused stays packed`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.place()
                world.advance(Duration.ZERO)
                world.advance(4.hours)
                val real = world.payments
                val refusing =
                    object : PaymentProcessor by real {
                        override suspend fun capture(
                            key: String,
                            capture: Capture,
                        ): CaptureOutcome = CaptureOutcome.NotAuthorised
                    }
                val held =
                    FulfilmentSimulator(
                        world.shipments,
                        world.koin.get(),
                        refusing,
                        world.koin.get(),
                        world.clock,
                        FulfilmentPace.STORE,
                    )

                world.clock.at(30.hours)
                assertEquals(0, runBlocking { held.advance() }, "Sony was due at 24 hours")
                assertEquals(
                    mapOf(sony to PACKED, brooklyn to PACKED),
                    world.statuses(order),
                    "Sony does not ship unpaid",
                )
                assertEquals(emptyMap(), world.captures(order))

                world.advance(30.hours)
                assertEquals(mapOf(sony to IN_TRANSIT, brooklyn to PACKED), world.statuses(order), "paid, it ships")

                world.clock.at(50.hours)
                assertEquals(
                    1,
                    runBlocking { held.advance() },
                    "Sony's delivery needs no capture; Brooklyn's road does",
                )
                assertEquals(mapOf(sony to DELIVERED, brooklyn to PACKED), world.statuses(order))
                assertEquals(mapOf(sony to 34_900), world.captures(order))

                world.advance(50.hours)
                assertEquals(mapOf(sony to DELIVERED, brooklyn to IN_TRANSIT), world.statuses(order))
                assertEquals(51_200, world.captures(order).values.sum())
            }
        }

    /**
     * Two processes passing over the same shipments at once — a rolling deploy runs two — move each
     * shipment through each step once and capture each share once.
     */
    @Test
    fun `two passes at once move and charge each shipment once`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.place()
                world.advance(Duration.ZERO)
                world.clock.at(72.hours)

                val moves =
                    runBlocking {
                        List(2) { async(Dispatchers.IO) { world.simulator.advance() } }.awaitAll()
                    }

                assertEquals(6, moves.sum(), "three steps for each shipment, made once between the two: $moves")
                assertEquals(mapOf(sony to DELIVERED, brooklyn to DELIVERED), world.statuses(order))
                assertEquals(mapOf(sony to 34_900, brooklyn to 16_300), world.captures(order))
                assertEquals(8, world.historyRows(), "four steps of history for each shipment")
            }
        }

    /** Pay on delivery authorises nothing (B-16), so a shipment that ships has nothing to capture — and still ships. */
    @Test
    fun `pay on delivery ships without a charge`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.place(CheckoutChoice(payment = PaymentMethod.OnDelivery.id))
                world.advance(Duration.ZERO)
                world.advance(72.hours)

                assertEquals(mapOf(sony to DELIVERED, brooklyn to DELIVERED), world.statuses(order))
                assertEquals(emptyMap(), world.captures(order))
                assertEquals(emptyList(), Ledger(dataSource).authorisations())
            }
        }
}
