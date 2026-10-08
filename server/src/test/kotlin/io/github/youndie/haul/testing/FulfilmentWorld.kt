package io.github.youndie.haul.testing

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.account.accountModule
import io.github.youndie.haul.feature.cart.cartModule
import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.catalog.catalogModule
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.checkout.PlaceOrderRequest
import io.github.youndie.haul.feature.checkout.checkoutModule
import io.github.youndie.haul.feature.checkout.domain.CheckoutCommands
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentPace
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentRepository
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentSimulator
import io.github.youndie.haul.feature.fulfilment.domain.OrderTracking
import io.github.youndie.haul.feature.fulfilment.domain.TrackedOrder
import io.github.youndie.haul.feature.fulfilment.fulfilmentModule
import io.github.youndie.haul.feature.identity.identityModule
import io.github.youndie.haul.feature.order.domain.OrderRepository
import io.github.youndie.haul.feature.order.domain.Placement
import io.github.youndie.haul.feature.order.orderModule
import io.github.youndie.haul.feature.payment.domain.PaymentProcessor
import io.github.youndie.haul.feature.payment.paymentModule
import io.github.youndie.haul.feature.returns.domain.ReturnSimulator
import io.github.youndie.haul.feature.returns.returnsModule
import io.github.youndie.haul.feature.reviews.reviewsModule
import io.github.youndie.haul.feature.saved.savedModule
import io.github.youndie.haul.feature.search.searchModule
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.petich.PetichClock
import kotlinx.coroutines.runBlocking
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.time.Instant
import javax.sql.DataSource
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * The simulated world's clock in a test: still until the test moves it, so a step comes due exactly when
 * the test says and never because a machine was slow. It starts at the canvas's «now».
 */
internal class TestClock(
    var now: Instant = CatalogSeed.NOW.toInstant(),
) : PetichClock {
    override fun nowEpochMs(): Long = now.toEpochMilli()

    fun at(offset: Duration): TestClock = apply { now = START.plus(offset.toJavaDuration()) }

    companion object {
        val START: Instant = CatalogSeed.NOW.toInstant()
    }
}

internal val MAYA: CartOwner.Customer = CartOwner.Customer(SampleCustomers.MAYA, plus = true)

/**
 * The production graph over [dataSource] — placement, the saga, the payment simulator and the fulfilment
 * simulator — with [clock] as the saga's and the world's clock and [pace] as the world's pace. Nothing runs
 * by itself: a test places through [place] and moves the world through [advance], one pass at a time.
 */
internal class FulfilmentWorld(
    private val dataSource: DataSource,
    val clock: TestClock = TestClock(),
    pace: FulfilmentPace = FulfilmentPace.STORE,
) : AutoCloseable {
    private val application =
        koinApplication {
            modules(
                module {
                    single { Databases.connect(dataSource) }
                    single<DataSource> { dataSource }
                    single<StoreClock> { CANVAS_NOW }
                    single<PetichClock> { clock }
                    single { pace }
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
                fulfilmentModule,
                reviewsModule,
                savedModule,
                accountModule,
                returnsModule,
            )
        }
    val koin: Koin get() = application.koin
    val simulator: FulfilmentSimulator get() = koin.get()
    val shipments: FulfilmentRepository get() = koin.get()
    val payments: PaymentProcessor get() = koin.get()
    val returns: ReturnSimulator get() = koin.get()

    /** Maya's cart placed, after [choice] when there is one; the order's id. */
    fun place(
        choice: CheckoutChoice? = null,
        key: String = "maya-fulfilment",
    ): String =
        runBlocking {
            val checkout = koin.get<CheckoutCommands>()
            choice?.let { checkout.choose(MAYA, it) }
            koin.get<Placement>().place(MAYA, key, PlaceOrderRequest(checkout.quote(MAYA).fingerprint))
        }

    /** One pass of the simulator at [offset] from the start of the world. */
    fun advance(offset: Duration): Int {
        clock.at(offset)
        return runBlocking { simulator.advance() }
    }

    /** One pass of the returns' simulator (B-21) at [offset] from the start of the world. */
    fun advanceReturns(offset: Duration): Int {
        clock.at(offset)
        return runBlocking { returns.advance() }
    }

    fun track(
        orderId: String,
        customerId: String = SampleCustomers.MAYA,
    ): TrackedOrder? = runBlocking { koin.get<OrderTracking>().track(customerId, orderId) }

    /** What the world left in the database, read past every repository. */
    val ledger: Ledger = Ledger(dataSource)

    fun statuses(orderId: String): Map<String, String> = ledger.shipmentStatuses(orderId)

    fun storedCodes(orderId: String): Map<String, String?> = ledger.pickupCodes(orderId)

    fun captures(orderId: String): Map<String, Int> = ledger.captures(orderId)

    fun historyRows(): Int = ledger.historyRows()

    fun order(orderId: String) = runBlocking { koin.get<OrderRepository>().order(orderId) }

    override fun close() = application.close()
}
