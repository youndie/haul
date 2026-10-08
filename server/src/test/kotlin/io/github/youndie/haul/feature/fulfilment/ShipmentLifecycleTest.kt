package io.github.youndie.haul.feature.fulfilment

import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentPace
import io.github.youndie.haul.feature.fulfilment.domain.OrderProgress
import io.github.youndie.haul.feature.fulfilment.domain.PickupCodes
import io.github.youndie.haul.feature.order.domain.ShipmentStatus.DELIVERED
import io.github.youndie.haul.feature.order.domain.ShipmentStatus.IN_TRANSIT
import io.github.youndie.haul.feature.order.domain.ShipmentStatus.PACKED
import io.github.youndie.haul.feature.order.domain.ShipmentStatus.PICKED_UP
import io.github.youndie.haul.feature.order.domain.ShipmentStatus.PLACED
import io.github.youndie.haul.feature.order.domain.ShipmentStatus.READY_FOR_PICKUP
import io.github.youndie.haul.seed.SampleCheckout
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.testing.FulfilmentWorld
import io.github.youndie.haul.testing.TestClock
import io.github.youndie.haul.testing.seededFreshDatabase
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * The fulfilment simulator moves each seller's shipment along its way (feature-orders), on a clock the test
 * holds: every pass is the test's own call at a moment it names, so a step that comes due early or late is
 * a failure here and never a slow machine.
 *
 * Maya's cart is two shipments (research §6): Sony's headphones (`HL-48302-1`, dispatched the same day) and
 * Brooklyn Home Co.'s duvet cover and mugs (`HL-48302-2`, one dispatch day).
 */
class ShipmentLifecycleTest {
    private val sony = "HL-48302-1"
    private val brooklyn = "HL-48302-2"

    private fun at(offset: Duration): Instant = TestClock.START.plus(offset.toJavaDuration())

    /**
     * The store's pace, step by step: a shipment moves at the moment its step comes due and not a
     * millisecond before; Sony leaves a day before Brooklyn Home Co.; the order is as far as its least
     * advanced shipment; a courier shipment never gets a pickup code.
     */
    @Test
    fun `each step comes due at the store's pace and not a moment before`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.place()

                fun expect(
                    offset: Duration,
                    sonyStatus: String,
                    brooklynStatus: String,
                    progress: OrderProgress,
                ) {
                    world.advance(offset)
                    assertEquals(
                        mapOf(sony to sonyStatus, brooklyn to brooklynStatus),
                        world.statuses(order),
                        "at $offset",
                    )
                    assertEquals(progress, world.track(order)?.progress, "at $offset")
                    assertEquals(
                        mapOf(sony to null, brooklyn to null),
                        world.storedCodes(order),
                        "a courier has no code",
                    )
                }

                assertEquals(OrderProgress.Placed, world.track(order)?.progress)
                expect(Duration.ZERO, PLACED, PLACED, OrderProgress.Placed)
                expect(4.hours - 1.milliseconds, PLACED, PLACED, OrderProgress.Placed)
                expect(4.hours, PACKED, PACKED, OrderProgress.Packed)
                expect(24.hours - 1.milliseconds, PACKED, PACKED, OrderProgress.Packed)
                expect(24.hours, IN_TRANSIT, PACKED, OrderProgress.Packed)
                expect(48.hours, DELIVERED, IN_TRANSIT, OrderProgress.InTransit)
                expect(72.hours, DELIVERED, DELIVERED, OrderProgress.Delivered)

                val history = assertNotNull(world.track(order)).shipments.associate { it.id to it.history }
                assertEquals(
                    mapOf(
                        PLACED to at(0.hours),
                        PACKED to at(4.hours),
                        IN_TRANSIT to at(24.hours),
                        DELIVERED to at(48.hours),
                    ),
                    history[sony],
                )
                assertEquals(at(48.hours), history[brooklyn]?.get(IN_TRANSIT), "a dispatch day later")
                assertEquals(0, world.advance(30.days), "a delivered order is not moved again")
            }
        }

    /**
     * B-17's acceptance: an order reaches delivered on the fast clock — the stand's speed (a day a
     * minute), the world polled at that speed's interval, the clock moved one interval per pass.
     */
    @Test
    fun `an order reaches delivered on the fast clock`() =
        seededFreshDatabase().use { dataSource ->
            val fast = FulfilmentSettings.running(speed = 1_440.0)
            FulfilmentWorld(dataSource, pace = fast.pace).use { world ->
                val order = world.place()
                val interval = assertNotNull(fast.interval)
                val seen = mutableListOf<OrderProgress>()
                var elapsed = Duration.ZERO
                while (seen.lastOrNull() != OrderProgress.Delivered) {
                    check(elapsed < 10.minutes) { "not delivered after $elapsed of a day-a-minute clock: $seen" }
                    world.advance(elapsed)
                    val progress = assertNotNull(world.track(order)).progress
                    if (seen.lastOrNull() != progress) seen += progress
                    elapsed += interval
                }

                assertEquals(
                    listOf(
                        OrderProgress.Placed,
                        OrderProgress.Packed,
                        OrderProgress.InTransit,
                        OrderProgress.Delivered,
                    ),
                    seen,
                )
                assertTrue(elapsed <= 4.minutes, "three days of the store's pace took $elapsed at a day a minute")
            }
        }

    /**
     * A pass after a pause — a process down for days — carries every shipment through every step that came
     * due meanwhile, each stamped when it came due: the history an unbroken run would have written.
     */
    @Test
    fun `a pass after a long pause catches up and stamps each step when it came due`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.place()
                world.advance(Duration.ZERO)

                assertEquals(6, world.advance(10.days), "three steps for each of the two shipments")

                assertEquals(mapOf(sony to DELIVERED, brooklyn to DELIVERED), world.statuses(order))
                val history = assertNotNull(world.track(order)).shipments.associate { it.id to it.history }
                assertEquals(
                    mapOf(
                        PLACED to at(0.hours),
                        PACKED to at(4.hours),
                        IN_TRANSIT to at(48.hours),
                        DELIVERED to at(72.hours),
                    ),
                    history[brooklyn],
                )
            }
        }

    /**
     * A pickup order waits at the point (feature-orders): ready to collect with a four-digit code, held five
     * days, then collected. The code is the deterministic one for the shipment, shown only while it waits,
     * and only to the order's customer — to Sam the order does not exist.
     */
    @Test
    fun `a pickup shipment waits with a code only its customer sees`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.place(CheckoutChoice(pointId = SampleCheckout.BEDFORD))
                listOf(0.hours, 4.hours, 24.hours).forEach { world.advance(it) }
                assertNull(world.track(order)?.shipments?.firstNotNullOfOrNull { it.pickupCode }, "no code on the road")

                world.advance(48.hours)
                assertEquals(mapOf(sony to READY_FOR_PICKUP, brooklyn to IN_TRANSIT), world.statuses(order))
                val tracked = assertNotNull(world.track(order))
                assertEquals(OrderProgress.InTransit, tracked.progress, "the least advanced shipment wins")
                val waiting = tracked.shipments.first { it.id == sony }
                val sagaId = assertNotNull(world.order(order)).placed.sagaId
                val code = assertNotNull(waiting.pickupCode)
                assertEquals(PickupCodes.of(sagaId, sony), code)
                assertTrue(code.matches(Regex("[0-9]{4}")), "four digits: $code")
                assertEquals(at(48.hours + FulfilmentPace.HELD_FOR), waiting.heldUntil)
                assertNull(tracked.shipments.first { it.id == brooklyn }.pickupCode)

                assertNull(world.track(order, SampleCustomers.SAM), "another customer's order is not found")

                world.advance(72.hours)
                assertEquals(OrderProgress.ReadyForPickup, world.track(order)?.progress)
                world.advance(96.hours)
                val collected = assertNotNull(world.track(order)).shipments.first { it.id == sony }
                assertEquals(PICKED_UP, collected.status)
                assertNull(collected.pickupCode, "a collected shipment shows no code")
                assertEquals(PickupCodes.of(sagaId, sony), world.storedCodes(order)[sony], "the code stays on the row")
                world.advance(120.hours)
                assertEquals(OrderProgress.PickedUp, world.track(order)?.progress)
            }
        }

    /** A locker is a pickup too: ready to collect with a code, never delivered to a door. */
    @Test
    fun `a locker shipment is collected not delivered`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.place(CheckoutChoice(pointId = SampleCheckout.WYTHE_LOCKER))
                world.advance(Duration.ZERO)
                world.advance(72.hours)

                assertEquals(mapOf(sony to READY_FOR_PICKUP, brooklyn to READY_FOR_PICKUP), world.statuses(order))
                assertTrue(world.storedCodes(order).values.all { it != null })
            }
        }
}
