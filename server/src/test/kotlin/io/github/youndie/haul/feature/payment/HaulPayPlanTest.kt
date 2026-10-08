package io.github.youndie.haul.feature.payment

import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.checkout.domain.PaymentMethod
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentPace
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentSimulator
import io.github.youndie.haul.feature.order.domain.ShipmentStatus.DELIVERED
import io.github.youndie.haul.feature.order.domain.ShipmentStatus.IN_TRANSIT
import io.github.youndie.haul.feature.order.domain.ShipmentStatus.PACKED
import io.github.youndie.haul.feature.order.screen.OrderScreen
import io.github.youndie.haul.feature.payment.domain.Capture
import io.github.youndie.haul.feature.payment.domain.CaptureOutcome
import io.github.youndie.haul.feature.payment.domain.HaulPayPlans
import io.github.youndie.haul.feature.payment.domain.InstalmentRepository
import io.github.youndie.haul.feature.payment.domain.InstalmentStatus.COVERED
import io.github.youndie.haul.feature.payment.domain.InstalmentStatus.OVERDUE
import io.github.youndie.haul.feature.payment.domain.InstalmentStatus.PAID
import io.github.youndie.haul.feature.payment.domain.InstalmentStatus.SCHEDULED
import io.github.youndie.haul.feature.payment.domain.PaymentProcessor
import io.github.youndie.haul.feature.payment.domain.RefundSplit
import io.github.youndie.haul.feature.returns.ReturnEntry
import io.github.youndie.haul.feature.returns.domain.RequestReturn
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.testing.FulfilmentWorld
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.ui.OrderTotals
import io.github.youndie.haul.ui.PlanPayment
import io.github.youndie.haul.ui.PlanPaymentState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/**
 * Haul Pay as decided (B-24, research D6): four interest-free payments two weeks apart, simulated. Placement
 * authorises the total as for a card; the plan starts when the first shipment ships, takes its first payment
 * then and each next one two weeks later on the simulator's clock — instead of each shipment's share.
 *
 * Maya's cart is $512.00 (research §6): Sony's headphones ship at 24 hours (Oct 8), Brooklyn Home Co.'s duvet
 * cover and mugs at 48; four payments of $128.00 on Oct 8, Oct 22, Nov 5 and Nov 19.
 */
class HaulPayPlanTest {
    private val sony = "HL-48302-1"
    private val brooklyn = "HL-48302-2"
    private val first = 24.hours
    private val twoWeeks = 14.days

    private fun FulfilmentWorld.placeOnHaulPay(choice: CheckoutChoice = CheckoutChoice()): String =
        place(choice.copy(payment = PaymentMethod.HaulPayPlan.id))

    private fun FulfilmentWorld.summary(order: String): OrderTotals =
        runBlocking {
            val screen = koin.get<OrderScreen>()
            val tracked = assertNotNull(track(order))
            OrderScreen.body(screen.view(SampleCustomers.MAYA, tracked, "Maya")).summary
        }

    private fun key(
        order: String,
        number: Int,
    ) = "instalment:$order:$number"

    /**
     * B-24's first half: «an instalment order shows its schedule». Before anything ships the page shows the four
     * payments counted from the first shipment, with the amounts the checkout promised; once Sony ships, the
     * days, the first payment paid and the rest upcoming — and the fact under the total says how far it is.
     */
    @Test
    fun `an instalment order shows its schedule`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.placeOnHaulPay()

                val before = world.summary(order)
                assertEquals(
                    listOf(
                        PlanPayment("When it ships", "Upcoming", "$128.00", PlanPaymentState.Upcoming),
                        PlanPayment("In 2 weeks", "Upcoming", "$128.00", PlanPaymentState.Upcoming),
                        PlanPayment("In 4 weeks", "Upcoming", "$128.00", PlanPaymentState.Upcoming),
                        PlanPayment("In 6 weeks", "Upcoming", "$128.00", PlanPaymentState.Upcoming),
                    ),
                    assertNotNull(before.plan, "a Haul Pay order shows no schedule").payments,
                )
                assertEquals("4 payments, two weeks apart", before.plan?.title)
                assertEquals(
                    "4 interest-free payments of $128.00, the first when it ships",
                    before.facts.first().detail,
                )

                world.advance(Duration.ZERO)
                world.advance(first)
                val shipped = world.summary(order)
                assertEquals(
                    listOf(
                        PlanPayment("Oct 8", "Paid", "$128.00", PlanPaymentState.Paid),
                        PlanPayment("Oct 22", "Upcoming", "$128.00", PlanPaymentState.Upcoming),
                        PlanPayment("Nov 5", "Upcoming", "$128.00", PlanPaymentState.Upcoming),
                        PlanPayment("Nov 19", "Upcoming", "$128.00", PlanPaymentState.Upcoming),
                    ),
                    shipped.plan?.payments,
                )
                assertEquals("Haul Pay", shipped.facts.first().title)
                assertEquals("$128.00 of $512.00 paid · next $128.00 on Oct 22", shipped.facts.first().detail)
            }
        }

    /** A card order has no schedule: the plan is Haul Pay's alone. */
    @Test
    fun `a card order shows no schedule`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.place()
                assertNull(world.track(order)?.plan)
                assertNull(world.summary(order).plan)
            }
        }

    /**
     * B-24's second half: «captures follow it on the simulator clock». The first payment is taken when the first
     * shipment ships, not at placement; the second shipment ships with no charge of its own; each next payment
     * is taken exactly two weeks after the one before — not a millisecond earlier — and the four add up to the
     * authorised total.
     */
    @Test
    fun `captures follow the schedule on the simulator clock`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.placeOnHaulPay()
                assertEquals(emptyMap(), world.captures(order), "nothing is charged at placement")

                world.advance(Duration.ZERO)
                world.advance(first - 1.milliseconds)
                assertEquals(emptyMap(), world.captures(order), "nothing is charged before the first shipment ships")

                world.advance(first)
                assertEquals(mapOf(sony to IN_TRANSIT, brooklyn to PACKED), world.statuses(order))
                assertEquals(mapOf(key(order, 1) to 12_800), world.captures(order), "the first payment, not Sony's share")

                world.advance(first * 2)
                assertEquals(mapOf(sony to DELIVERED, brooklyn to IN_TRANSIT), world.statuses(order))
                assertEquals(mapOf(key(order, 1) to 12_800), world.captures(order), "Brooklyn ships without a charge")

                assertEquals(0, world.advancePlans(first + twoWeeks - 1.milliseconds))
                assertEquals(1, world.advancePlans(first + twoWeeks))
                assertEquals(0, world.advancePlans(first + twoWeeks * 2 - 1.milliseconds))
                assertEquals(1, world.advancePlans(first + twoWeeks * 2))
                assertEquals(1, world.advancePlans(first + twoWeeks * 3))
                assertEquals(0, world.advancePlans(first + twoWeeks * 10), "a paid plan takes nothing more")

                assertEquals((1..4).associate { key(order, it) to 12_800 }, world.captures(order))
                assertEquals(51_200, world.captures(order).values.sum(), "the payments add up to the total")
                assertEquals(
                    (1..4).associateWith { Triple(PAID, 12_800, 0) },
                    world.ledger.instalments(order),
                )
                assertEquals("$512.00 paid in 4 payments", world.summary(order).facts.first().detail)
            }
        }

    /** A pass after a pause catches up: every payment come due is taken, each stamped when it came due. */
    @Test
    fun `a pass after a pause takes every payment come due`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.placeOnHaulPay()
                world.advance(Duration.ZERO)
                world.advance(first * 3)

                assertEquals(2, world.advancePlans(first + twoWeeks * 2 + 1.hours))
                val plan = assertNotNull(world.track(order)?.plan)
                assertEquals(listOf(PAID, PAID, PAID, SCHEDULED), plan.instalments.map { it.status })
                assertEquals(
                    world.clock.at(first + twoWeeks * 2).now,
                    plan.instalments[2].paidAt,
                    "stamped when it came due, not at the pass",
                )
            }
        }

    /** Two passes at once — a rolling deploy runs two — take each payment once. */
    @Test
    fun `two passes at once take each payment once`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.placeOnHaulPay()
                world.advance(Duration.ZERO)
                world.advance(first * 3)
                world.clock.at(first + twoWeeks * 4)

                val moves = runBlocking { List(2) { async(Dispatchers.IO) { world.plans.advance() } }.awaitAll() }

                assertEquals(3, moves.sum(), "three payments, taken once between the two: $moves")
                assertEquals((1..4).associate { key(order, it) to 12_800 }, world.captures(order))
            }
        }

    /**
     * A payment is claimed once: a second claim — a pass that read the plan before another pass took the payment —
     * finds it no longer scheduled and writes nothing, so it can neither be charged again nor be put back to
     * «being charged» once paid.
     */
    @Test
    fun `a payment is claimed once`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.placeOnHaulPay()
                world.advance(Duration.ZERO)
                world.advance(first * 3)
                val repository = world.koin.get<InstalmentRepository>()

                assertEquals(12_800, runBlocking { repository.claim(order, 2, 0) })
                assertNull(runBlocking { repository.claim(order, 2, 0) }, "claimed twice")
                assertNull(runBlocking { repository.claim(order, 1, 0) }, "a paid payment claimed again")
                assertEquals(PAID, world.ledger.instalments(order)[1]?.first)
            }
        }

    /**
     * A process that dies after a payment is charged and before it is marked paid leaves it claimed; the next
     * pass asks the processor again under the same key, is answered with what was taken, and marks it — the
     * shopper pays once.
     */
    @Test
    fun `a pass that died between the charge and the mark does not charge twice`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.placeOnHaulPay()
                world.advance(Duration.ZERO)
                world.advance(first * 3)

                val real = world.koin.get<InstalmentRepository>()
                val dying =
                    object : InstalmentRepository by real {
                        override suspend fun paid(
                            orderId: String,
                            number: Int,
                            at: Instant,
                        ): Boolean = error("the process died before the mark")
                    }
                world.clock.at(first + twoWeeks)
                val plans = HaulPayPlans(dying, world.payments, world.clock, twoWeeks, 1.days)
                assertFailsWith<IllegalStateException> { runBlocking { plans.advance() } }
                assertEquals(mapOf(key(order, 1) to 12_800, key(order, 2) to 12_800), world.captures(order))
                assertEquals("collecting", world.ledger.instalments(order)[2]?.first, "the charge landed, the mark did not")

                assertEquals(1, world.advancePlans(first + twoWeeks))
                assertEquals(PAID, world.ledger.instalments(order)[2]?.first)
                assertEquals(mapOf(key(order, 1) to 12_800, key(order, 2) to 12_800), world.captures(order))
            }
        }

    /**
     * feature-membership: «a declined instalment is retried once and then marks the plan overdue (no collections
     * in v1)». The second payment declined is tried again a day later; declined again, the plan is overdue and
     * stops there — the third and fourth are never asked for, however long the world runs.
     */
    @Test
    fun `a payment declined twice marks the plan overdue and stops it`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.placeOnHaulPay()
                world.advance(Duration.ZERO)
                world.advance(first * 3)
                val asked = mutableListOf<String>()
                val declining =
                    object : PaymentProcessor by world.payments {
                        override suspend fun capture(
                            key: String,
                            capture: Capture,
                        ): CaptureOutcome {
                            asked += key
                            return CaptureOutcome.NotAuthorised
                        }
                    }
                val plans = HaulPayPlans(world.koin.get(), declining, world.clock, twoWeeks, 1.days)

                world.clock.at(first + twoWeeks)
                assertEquals(1, runBlocking { plans.advance() })
                assertEquals(Triple(SCHEDULED, 12_800, 1), world.ledger.instalments(order)[2])
                assertEquals(
                    PlanPayment("Oct 22", "Declined, tried again Oct 23", "$128.00", PlanPaymentState.Declined),
                    world.summary(order).plan?.payments?.get(1),
                )

                world.clock.at(first + twoWeeks + 1.days - 1.milliseconds)
                assertEquals(0, runBlocking { plans.advance() }, "the retry is a day later")
                world.clock.at(first + twoWeeks + 1.days)
                assertEquals(1, runBlocking { plans.advance() })
                assertEquals(Triple(OVERDUE, 12_800, 2), world.ledger.instalments(order)[2])

                world.clock.at(first + twoWeeks * 10)
                assertEquals(0, runBlocking { plans.advance() }, "an overdue plan stops")
                assertEquals(0, world.advancePlans(first + twoWeeks * 10), "with any processor")
                assertEquals(listOf(key(order, 2), key(order, 2)), asked, "tried twice, the rest never")
                assertEquals(mapOf(key(order, 1) to 12_800), world.captures(order))
                assertEquals(listOf(SCHEDULED, SCHEDULED), listOf(3, 4).map { world.ledger.instalments(order)[it]?.first })
                val summary = world.summary(order)
                assertEquals("Payment 2 was declined twice — the plan is overdue", summary.facts.first().detail)
                assertEquals(
                    PlanPayment("Oct 22", "Declined twice · overdue", "$128.00", PlanPaymentState.Declined),
                    summary.plan?.payments?.get(1),
                )
            }
        }

    /** A payment declined once and taken on the retry is paid, and the plan carries on. */
    @Test
    fun `a payment taken on its retry is paid and the plan carries on`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.placeOnHaulPay()
                world.advance(Duration.ZERO)
                world.advance(first * 3)
                val declining =
                    object : PaymentProcessor by world.payments {
                        override suspend fun capture(
                            key: String,
                            capture: Capture,
                        ): CaptureOutcome = CaptureOutcome.NotAuthorised
                    }
                world.clock.at(first + twoWeeks)
                runBlocking { HaulPayPlans(world.koin.get(), declining, world.clock, twoWeeks, 1.days).advance() }

                assertEquals(1, world.advancePlans(first + twoWeeks + 1.days))
                assertEquals(Triple(PAID, 12_800, 1), world.ledger.instalments(order)[2])
                assertEquals(2, world.advancePlans(first + twoWeeks * 3))
                assertEquals(51_200, world.captures(order).values.sum())
            }
        }

    /**
     * No Haul Pay shipment is on the road before the plan's first payment is taken, as no card shipment is
     * before its capture: declined, both shipments stay packed; taken on the retry a day later, they ship.
     */
    @Test
    fun `shipments wait for the first payment`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.placeOnHaulPay()
                world.advance(Duration.ZERO)
                world.advance(4.hours)
                val declining =
                    object : PaymentProcessor by world.payments {
                        override suspend fun capture(
                            key: String,
                            capture: Capture,
                        ): CaptureOutcome = CaptureOutcome.NotAuthorised
                    }
                val held =
                    FulfilmentSimulator(
                        world.shipments,
                        world.koin.get(),
                        declining,
                        HaulPayPlans(world.koin.get(), declining, world.clock, twoWeeks, 1.days),
                        world.koin.get(),
                        world.clock,
                        FulfilmentPace.STORE,
                    )
                world.clock.at(first * 2 - 1.hours)
                assertEquals(0, runBlocking { held.advance() })
                assertEquals(mapOf(sony to PACKED, brooklyn to PACKED), world.statuses(order), "nothing ships unpaid")
                assertEquals(Triple(SCHEDULED, 12_800, 1), world.ledger.instalments(order)[1])

                world.advance(first * 2)
                assertEquals(
                    mapOf(sony to DELIVERED, brooklyn to IN_TRANSIT),
                    world.statuses(order),
                    "paid on the retry, both ship — Sony stamped on the road when it was due, as a held card capture is",
                )
                assertEquals(mapOf(key(order, 1) to 12_800), world.captures(order))
                val plan = assertNotNull(world.track(order)?.plan)
                assertEquals(world.clock.at(first * 2).now, plan.instalments[0].paidAt, "taken on the retry")
                assertEquals(world.clock.at(first + twoWeeks).now, plan.instalments[1].dueAt, "the schedule kept its days")
            }
        }

    /**
     * Points reduce what the plan pays (B-23): Maya's 2,480 points off her $512.00 leave $487.20, four payments of
     * $121.80 — the amount the checkout promised (`Checkout_PointsApplied`).
     */
    @Test
    fun `points reduce the plan's total`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.placeOnHaulPay(CheckoutChoice(usePoints = true))
                assertEquals(
                    List(4) { "$121.80" },
                    world.summary(order).plan?.payments?.map { it.amount },
                )
                world.advance(Duration.ZERO)
                world.advance(first * 3)
                world.advancePlans(first + twoWeeks * 3)
                assertEquals((1..4).associate { key(order, it) to 12_180 }, world.captures(order))
                assertEquals(48_720, world.captures(order).values.sum())
            }
        }

    /**
     * A return takes its refund off what the plan still owes, the last payment first, and gives nothing back
     * through the processor while that covers it: Sony's $349.00 returned after the first payment covers the
     * fourth and the third and takes $93.00 off the second — Maya pays $163.00 in all, what she kept.
     */
    @Test
    fun `a return reduces the payments still owed before refunding any`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.placeOnHaulPay()
                world.advance(Duration.ZERO)
                world.advance(first * 3)
                world.returnSony(order)
                world.advanceReturns(first * 5)

                assertEquals(emptyMap(), world.ledger.refunds(order), "nothing paid needs giving back")
                assertEquals(
                    mapOf(
                        1 to Triple(PAID, 12_800, 0),
                        2 to Triple(SCHEDULED, 3_500, 0),
                        3 to Triple(COVERED, 0, 0),
                        4 to Triple(COVERED, 0, 0),
                    ),
                    world.ledger.instalments(order),
                )
                assertEquals(
                    listOf(
                        PlanPaymentState.Paid,
                        PlanPaymentState.Upcoming,
                        PlanPaymentState.Covered,
                        PlanPaymentState.Covered,
                    ),
                    world.summary(order).plan?.payments?.map { it.state },
                )
                assertEquals("Upcoming · reduced by your return", world.summary(order).plan?.payments?.get(1)?.detail)

                world.advancePlans(first + twoWeeks * 4)
                assertEquals(mapOf(key(order, 1) to 12_800, key(order, 2) to 3_500), world.captures(order))
                assertEquals(16_300, world.captures(order).values.sum(), "what she kept, Brooklyn's lines")
            }
        }

    /**
     * A return bigger than what is still owed covers the rest of the plan and gives the difference back out of
     * what was paid: Sony returned after three payments covers the fourth, $128.00, and refunds $221.00.
     */
    @Test
    fun `a return bigger than what is owed refunds the rest of it`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.placeOnHaulPay()
                world.advance(Duration.ZERO)
                world.advance(first * 3)
                world.advancePlans(first + twoWeeks * 2)
                world.clock.at(first + twoWeeks * 2 + 1.hours)
                world.returnSony(order)
                world.advanceReturns(first + twoWeeks * 2 + 3.days)

                assertEquals(mapOf("refund:$order" to 22_100), world.ledger.refunds(order))
                assertEquals(Triple(COVERED, 0, 0), world.ledger.instalments(order)[4])
                assertEquals(0, world.advancePlans(first + twoWeeks * 5), "nothing is owed any more")
                assertEquals(38_400 - 22_100, world.captures(order).values.sum() - world.ledger.refunds(order).values.sum())
            }
        }

    /** The reduction is made once: asked again — a pass that died before the refund — it answers the same split. */
    @Test
    fun `a reduction asked twice takes off once`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.placeOnHaulPay()
                world.advance(Duration.ZERO)
                world.advance(first * 3)
                val at = world.clock.now

                val split = runBlocking { world.plans.refund(order, 34_900, at) }
                assertEquals(RefundSplit(reducedCents = 34_900, refundCents = 0), split)
                assertEquals(split, runBlocking { world.plans.refund(order, 34_900, at) })
                assertEquals(
                    12_800 + 3_500,
                    world.ledger.instalments(order).values.sumOf { it.second },
                    "owed once the return took $349.00 off once",
                )
                assertEquals(2, world.ledger.instalments(order).values.count { it.first == COVERED })
            }
        }

    /** Sony's headphones, Maya's first line, asked back now. */
    private fun FulfilmentWorld.returnSony(order: String) {
        runBlocking {
            koin.get<RequestReturn>().request(SampleCustomers.MAYA, order, ReturnEntry(listOf(0), "doesnt_fit"))
        }
    }
}
