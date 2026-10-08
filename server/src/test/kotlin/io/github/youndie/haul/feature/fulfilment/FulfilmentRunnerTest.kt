package io.github.youndie.haul.feature.fulfilment

import io.github.youndie.haul.feature.checkout.PlaceOrderRequest
import io.github.youndie.haul.feature.checkout.domain.CheckoutCommands
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentPace
import io.github.youndie.haul.feature.order.domain.Placement
import io.github.youndie.haul.feature.order.domain.ShipmentStatus.DELIVERED
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
}
