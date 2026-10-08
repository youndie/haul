package io.github.youndie.haul.feature.membership

import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.checkout.domain.CheckoutCommands
import io.github.youndie.haul.feature.checkout.screen.CheckoutScreen
import io.github.youndie.haul.feature.membership.domain.PointsKind
import io.github.youndie.haul.feature.membership.domain.PointsLedger
import io.github.youndie.haul.feature.membership.domain.PointsMovement
import io.github.youndie.haul.feature.membership.domain.PointsRules
import io.github.youndie.haul.feature.order.domain.CancelReason
import io.github.youndie.haul.feature.order.domain.OrderRepository
import io.github.youndie.haul.feature.order.domain.OrderStatus
import io.github.youndie.haul.feature.order.domain.ShipmentStatus
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.testing.FulfilmentWorld
import io.github.youndie.haul.testing.MAYA
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.ui.CheckoutSummary
import io.github.youndie.haul.ui.PaymentMethods
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * The points ledger through the production graph ([FulfilmentWorld]) over a seeded PostgreSQL of each test's
 * own (feature-membership, feature-checkout): Maya's 2,480 seeded points spent at checkout and given back
 * when her card is declined, points earned as shipments arrive — once, whatever the passes — and a balance
 * that cannot be spent twice.
 */
class PointsTest {
    private fun FulfilmentWorld.ledger(): PointsLedger = koin.get()

    private fun FulfilmentWorld.balance(customerId: String = SampleCustomers.MAYA): Int =
        runBlocking { ledger().balance(customerId) }

    /** Every shipment of what the world holds carried to its end: the first pass sees them, the second delivers. */
    private fun FulfilmentWorld.deliverEverything() {
        advance(Duration.ZERO)
        advance(10.days)
    }

    /**
     * Scenario «Points redeemed» (feature-checkout): Maya with 2,480 points and the same cart turns on «Use
     * 2,480 points» — the summary takes $24.80 off and the total is $487.20, Haul Pay's four payments are of
     * that — and places it: her balance is 0, the order is paid $487.20 and earns twice its whole dollars,
     * 974 points, credited as its two shipments arrive.
     */
    @Test
    fun `Maya pays with her 2480 points and her balance is 0 until the order earns`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                assertEquals(2_480, world.balance())
                runBlocking { world.koin.get<CheckoutCommands>().choose(MAYA, CheckoutChoice(usePoints = true)) }
                val tree = runBlocking { world.koin.get<CheckoutScreen>().build(MAYA) }
                val toggle = assertNotNull(tree.only<PaymentMethods>().points)
                assertEquals("Use 2,480 points (−$24.80)", toggle.label)
                assertTrue(toggle.on)
                assertEquals(
                    "4 payments of $121.80",
                    tree
                        .only<PaymentMethods>()
                        .options
                        .single { it.id == "haul_pay" }
                        .detail,
                )
                val summary = tree.only<CheckoutSummary>()
                assertEquals("$487.20", summary.total)
                assertEquals("−$24.80", summary.rows.single { it.label == "Points" }.value)

                val orderId = world.place()

                val order = assertNotNull(world.order(orderId))
                assertEquals(OrderStatus.Placed, order.status)
                assertEquals(48_720, order.placed.totalCents)
                assertEquals(2_480, order.placed.pointsRedeemed)
                assertEquals(974, order.placed.points)
                assertEquals(0, world.balance())
                assertTrue(world.ledger.authorisations().any { it.first == orderId && it.second == 48_720 })

                world.deliverEverything()
                assertEquals(setOf(ShipmentStatus.DELIVERED), world.statuses(orderId).values.toSet())
                assertEquals(974, world.balance())
                val earned =
                    runBlocking { world.ledger().movements(SampleCustomers.MAYA) }.filter {
                        it.kind ==
                            PointsKind.Earned &&
                            it.orderId != null
                    }
                assertEquals(listOf("earned:$orderId-1", "earned:$orderId-2"), earned.map { it.key }.sorted())
            }
        }

    /**
     * A redemption undone (feature-checkout: «redeemed points come back if the order is cancelled»): the
     * test card declines, the order is cancelled for it, and the 2,480 points are back as a `returned` row.
     */
    @Test
    fun `a declined card gives the redeemed points back`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val orderId = world.place(CheckoutChoice(payment = "card-0002", usePoints = true))
                val order = assertNotNull(world.order(orderId))
                assertEquals(OrderStatus.Cancelled, order.status)
                assertEquals(CancelReason.PAYMENT_DECLINED, order.cancelReason)
                assertEquals(2_480, world.balance())
                assertEquals(
                    listOf(PointsKind.Earned to 2_480, PointsKind.Redeemed to -2_480, PointsKind.Returned to 2_480),
                    runBlocking { world.ledger().movements(SampleCustomers.MAYA) }.map { it.kind to it.points },
                )
            }
        }

    /**
     * Scenario «Points on delivery» (feature-membership): Sam's $103.00 order, without Plus, earns 103 points
     * when its only shipment is delivered — and a second pass over the delivered shipment credits nothing more.
     * The order is written as placement writes one, paid on delivery, so the simulator has nothing to capture.
     */
    @Test
    fun `Sam's order of 103 dollars earns 103 points when its shipment is delivered`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val mayas = assertNotNull(world.order(world.place())).placed
                val orders = world.koin.get<OrderRepository>()
                val sams =
                    mayas.copy(
                        id = "HL-45890",
                        sagaId = "saga-45890",
                        customerId = SampleCustomers.SAM,
                        payment = "pay_on_delivery",
                        lines = listOf(mayas.lines.first().copy(priceCents = 10_300, listCents = 10_300)),
                        itemsCents = 10_300,
                        discountCents = 0,
                        deliveryCents = 0,
                        totalCents = 10_300,
                        points = PointsRules.earned(10_300, plus = false),
                        placedAt = CatalogSeed.NOW,
                    )
                runBlocking {
                    orders.open(sams)
                    orders.confirm(sams.id)
                }
                assertEquals(0, world.balance(SampleCustomers.SAM))

                world.deliverEverything()
                assertEquals(mapOf("HL-45890-1" to ShipmentStatus.DELIVERED), world.statuses("HL-45890"))
                assertEquals(103, world.balance(SampleCustomers.SAM))
                world.advance(11.days)
                assertEquals(103, world.balance(SampleCustomers.SAM), "a delivered shipment credits once")
            }
        }

    /**
     * A balance is spent once: a redemption the balance does not cover is refused and writes nothing, the
     * same redemption written twice — a saga member re-run — is one row, and giving back an order that took
     * nothing gives nothing.
     */
    @Test
    fun `a balance cannot be spent twice`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val ledger = world.ledger()
                runBlocking {
                    val first = PointsMovement.redeemed(SampleCustomers.MAYA, "HL-1", 2_000, CatalogSeed.NOW)
                    assertTrue(ledger.redeem(first))
                    assertTrue(ledger.redeem(first), "the same redemption again is the one written")
                    assertFalse(
                        ledger.redeem(PointsMovement.redeemed(SampleCustomers.MAYA, "HL-2", 481, CatalogSeed.NOW)),
                    )
                    assertEquals(480, ledger.balance(SampleCustomers.MAYA))
                    assertFalse(ledger.giveBack("HL-2", CatalogSeed.NOW), "HL-2 took nothing")
                    assertTrue(ledger.giveBack("HL-1", CatalogSeed.NOW))
                    assertFalse(ledger.giveBack("HL-1", CatalogSeed.NOW), "given back once")
                    assertEquals(2_480, ledger.balance(SampleCustomers.MAYA))
                }
            }
        }

    /** The toggle takes no more than the items after discounts: points never pay for delivery. */
    @Test
    fun `points are capped at the items after discounts`() {
        assertEquals(2_480, PointsRules.redeemable(2_480, 51_200))
        assertEquals(2_400, PointsRules.redeemable(2_480, 2_400))
        assertEquals(0, PointsRules.redeemable(-20, 2_400))
        assertEquals(1_024, PointsRules.earned(51_299, plus = true))
        assertEquals(103, PointsRules.earned(10_300, plus = false))
    }
}
