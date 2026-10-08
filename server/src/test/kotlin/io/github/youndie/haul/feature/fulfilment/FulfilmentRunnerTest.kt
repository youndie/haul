package io.github.youndie.haul.feature.fulfilment

import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.checkout.PlaceOrderRequest
import io.github.youndie.haul.feature.checkout.domain.CheckoutCommands
import io.github.youndie.haul.feature.checkout.domain.PaymentMethod
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentPace
import io.github.youndie.haul.feature.order.domain.Placement
import io.github.youndie.haul.feature.order.domain.ShipmentStatus.DELIVERED
import io.github.youndie.haul.feature.payment.domain.InstalmentStatus
import io.github.youndie.haul.haulModule
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.Ledger
import io.github.youndie.haul.testing.MAYA
import io.github.youndie.haul.testing.SAGA_CLOCK
import io.github.youndie.haul.testing.seededFreshDatabase
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.Koin
import org.koin.ktor.ext.getKoin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The application runs the simulated world by itself when it is told to (`HAUL_FULFILMENT_SPEED`, the
 * stand): nobody calls a pass here — the application `main` assembles, at a pace of milliseconds, delivers
 * an order placed in it.
 *
 * The one test of fulfilment on the wall clock, because what it checks is that the loop runs at all; every
 * rule of the simulator is checked on a clock the test holds (`ShipmentLifecycleTest`).
 */
class FulfilmentRunnerTest {
    private val instant = 1.milliseconds
    private val settings =
        FulfilmentSettings(FulfilmentPace(instant, instant, instant, instant, instant), interval = 20.milliseconds)

    private val later = 400.milliseconds
    private val planned =
        settings.copy(pace = settings.pace.copy(instalmentInterval = later, instalmentRetry = later))

    @Test
    fun `the application delivers an order by itself when fulfilment runs`() =
        seededFreshDatabase().use { dataSource ->
            lateinit var koin: Koin
            testApplication {
                application {
                    haulModule(dataSource, CANVAS_NOW, commit = "test", sagaClock = SAGA_CLOCK, fulfilment = settings)
                    koin = getKoin()
                }
                startApplication()
                val checkout = koin.get<CheckoutCommands>()
                val order =
                    koin.get<Placement>().place(
                        MAYA,
                        "maya-runner",
                        PlaceOrderRequest(checkout.quote(MAYA).fingerprint),
                    )

                val ledger = Ledger(dataSource)
                // Off the test's virtual time: the loop runs on the wall clock, and so does this wait for it.
                withContext(Dispatchers.Default) {
                    withTimeout(30.seconds) {
                        while (ledger.shipmentStatuses(order).values.any { it != DELIVERED }) delay(50.milliseconds)
                    }
                }
                assertEquals(51_200, ledger.captures(order).values.sum())
            }
        }

    /**
     * The runner takes Haul Pay's payments too (B-24): an order on Haul Pay is paid in its four payments with
     * nobody calling a pass — the first by the shipment that ships, the other three by the plans' own pass: they
     * come due [later] apart, after both shipments have arrived and the shipments' pass has nothing to look at.
     */
    @Test
    fun `the application takes a Haul Pay plan's payments by itself when fulfilment runs`() =
        seededFreshDatabase().use { dataSource ->
            lateinit var koin: Koin
            testApplication {
                application {
                    haulModule(dataSource, CANVAS_NOW, commit = "test", sagaClock = SAGA_CLOCK, fulfilment = planned)
                    koin = getKoin()
                }
                startApplication()
                val checkout = koin.get<CheckoutCommands>()
                checkout.choose(MAYA, CheckoutChoice(payment = PaymentMethod.HaulPayPlan.id))
                val order =
                    koin.get<Placement>().place(
                        MAYA,
                        "maya-runner-haul-pay",
                        PlaceOrderRequest(checkout.quote(MAYA).fingerprint),
                    )

                val ledger = Ledger(dataSource)
                withContext(Dispatchers.Default) {
                    withTimeout(30.seconds) {
                        while (ledger.instalments(order).values.count { it.first == InstalmentStatus.PAID } < 4) {
                            delay(50.milliseconds)
                        }
                    }
                }
                assertEquals((1..4).associate { "instalment:$order:$it" to 12_800 }, ledger.captures(order))
            }
        }
}
