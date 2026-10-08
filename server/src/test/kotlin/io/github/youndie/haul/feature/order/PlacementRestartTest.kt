package io.github.youndie.haul.feature.order

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.cart.cartModule
import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.catalog.catalogModule
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.feature.checkout.PlaceOrderRequest
import io.github.youndie.haul.feature.checkout.checkoutModule
import io.github.youndie.haul.feature.checkout.domain.CheckoutCommands
import io.github.youndie.haul.feature.identity.identityModule
import io.github.youndie.haul.feature.order.data.ExposedOrders
import io.github.youndie.haul.feature.order.domain.Order
import io.github.youndie.haul.feature.order.domain.OrderStatus
import io.github.youndie.haul.feature.order.domain.Placement
import io.github.youndie.haul.feature.payment.domain.Authorisation
import io.github.youndie.haul.feature.payment.domain.AuthorisationOutcome
import io.github.youndie.haul.feature.payment.domain.PaymentProcessor
import io.github.youndie.haul.feature.payment.paymentModule
import io.github.youndie.haul.feature.search.searchModule
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.Ledger
import io.github.youndie.haul.testing.MAYAS_SKUS
import io.github.youndie.haul.testing.SAGA_CLOCK
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.petich.PetichClock
import io.ktor.client.request.get
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * B-16's acceptance: «a saga killed mid-way finishes after a restart».
 *
 * The first process places Maya's order through the production graph and dies inside the card
 * processor's call: the stock and the window are taken and the order is open, the authorisation never
 * answered. Dying is cancelling the placement where it stands, which is what a killed JVM does to it —
 * petich rethrows a cancellation and writes nothing, so the saga's row is left `PROCESSING` at the
 * member it died in, exactly as the last committed step wrote it (shashki's `OrderSagaTest` builds that
 * row by hand; here the code that runs in production leaves it). The graph is then closed.
 *
 * The second process is the application as `main` assembles it, over the same database, a minute and
 * more later by the saga's clock — the sweeper takes a saga untouched for `STUCK_AFTER` as abandoned.
 * Nothing calls it: its own sweeper finds the saga, carries it on from the member it died in, and the
 * order is placed — with the stock and the window taken once, not twice, and the bought lines out of the
 * cart.
 */
class PlacementRestartTest {
    /** The card processor of a process that is about to die: it is called, and never answers. */
    private class Unanswered : PaymentProcessor {
        val called = CompletableDeferred<Unit>()

        override suspend fun authorise(
            key: String,
            authorisation: Authorisation,
        ): AuthorisationOutcome {
            called.complete(Unit)
            awaitCancellation()
        }

        override suspend fun void(key: String): Boolean = error("nothing is voided by a process that died")
    }

    /** The first process: the production graph over [dataSource], its card processor [payments]. */
    private fun firstProcess(
        dataSource: DataSource,
        payments: PaymentProcessor,
    ) = koinApplication {
        allowOverride(true)
        modules(
            module {
                single { Databases.connect(dataSource) }
                single<DataSource> { dataSource }
                single<StoreClock> { CANVAS_NOW }
                single<PetichClock> { SAGA_CLOCK }
                single { DeliveryCalendar { CANVAS_NOW.now() } }
                single { ProductPhotos(null) }
            },
            catalogModule,
            searchModule,
            identityModule,
            cartModule,
            checkoutModule,
            paymentModule,
            orderModule,
            module { single<PaymentProcessor> { payments } },
        )
    }

    private fun DataSource.order(id: String): Order? =
        runBlocking {
            ExposedOrders(Databases.connect(this@order)).order(id)
        }

    @Test
    fun `a placement killed inside the payment is finished by the next process`() =
        seededFreshDatabase().use { dataSource ->
            val ledger = Ledger(dataSource)
            val stockBefore = ledger.stock()
            val maya = CartOwner.Customer(SampleCustomers.MAYA, plus = true)
            val payments = Unanswered()

            val first = firstProcess(dataSource, payments)
            try {
                runBlocking {
                    val placement = first.koin.get<Placement>()
                    val quote = first.koin.get<CheckoutCommands>().quote(maya)
                    val placing =
                        launch(
                            Dispatchers.IO,
                        ) { placement.place(maya, "maya-restart", PlaceOrderRequest(quote.fingerprint)) }
                    withTimeout(30.seconds) { payments.called.await() }
                    // The process dies here: the placement is stopped where it stands, inside the processor's call.
                    placing.cancelAndJoin()
                }
            } finally {
                first.close()
            }

            // What the dead process left: the stock and the window taken, the order open, no payment.
            assertEquals("PROCESSING", ledger.sagaStatus())
            assertEquals(OrderStatus.Placing, dataSource.order("HL-48302")?.status)
            assertEquals(stockBefore.mapValues { it.value - 1 }, ledger.stock())
            val window = checkNotNull(dataSource.order("HL-48302")?.placed?.slotId)
            assertEquals(1, ledger.taken(window))
            assertEquals(emptyList(), ledger.authorisations())

            // The next process, started later by the saga's clock than the dead one's last write.
            val later = PetichClock { SAGA_CLOCK.nowEpochMs() + 5.minutes.inWholeMilliseconds }
            haulTest(dataSource, sagaClock = later) {
                get("/healthz")
                // Wait for the saga itself, not for the order: `confirm` makes the order placed one step
                // before the saga writes COMPLETED, and leaving this block stops the application — on a
                // loaded runner the stop overtook that last step and the row stayed PROCESSING.
                withTimeout(30.seconds) {
                    while (ledger.sagaStatus() != "COMPLETED") delay(100)
                }
            }

            assertEquals("COMPLETED", ledger.sagaStatus())
            assertEquals(
                stockBefore.mapValues { it.value - 1 },
                ledger.stock(),
                "the stock was taken twice, or given back",
            )
            assertEquals(1, ledger.taken(window), "the window's place was taken twice, or given back")
            assertEquals(listOf(Triple("HL-48302", 51_200, "authorised")), ledger.authorisations())
            assertEquals(emptyList(), ledger.cartLines(SampleCustomers.MAYA), "the bought lines stayed in the cart")
            assertEquals(
                MAYAS_SKUS,
                dataSource
                    .order("HL-48302")
                    ?.placed
                    ?.lines
                    ?.map { it.skuId },
            )
        }
}
