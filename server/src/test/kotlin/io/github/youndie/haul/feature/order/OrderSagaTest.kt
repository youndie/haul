package io.github.youndie.haul.feature.order

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.cart.data.ExposedCartRepository
import io.github.youndie.haul.feature.checkout.DeliveryMethod
import io.github.youndie.haul.feature.checkout.data.ExposedDeliverySlots
import io.github.youndie.haul.feature.membership.data.ExposedPointsLedger
import io.github.youndie.haul.feature.order.data.ExposedOrders
import io.github.youndie.haul.feature.order.data.ExposedStock
import io.github.youndie.haul.feature.order.domain.CancelReason
import io.github.youndie.haul.feature.order.domain.OrderRepository
import io.github.youndie.haul.feature.order.domain.OrderStatus
import io.github.youndie.haul.feature.order.domain.ShipmentStatus
import io.github.youndie.haul.feature.order.saga.ORDER_SAGA
import io.github.youndie.haul.feature.order.saga.OrderPayload
import io.github.youndie.haul.feature.order.saga.Refused
import io.github.youndie.haul.feature.order.saga.SagaStorage
import io.github.youndie.haul.feature.order.saga.orderEngine
import io.github.youndie.haul.feature.order.saga.orderSaga
import io.github.youndie.haul.feature.payment.data.ExposedPaymentSimulator
import io.github.youndie.haul.feature.payment.domain.Authorisation
import io.github.youndie.haul.feature.payment.domain.AuthorisationOutcome
import io.github.youndie.haul.feature.payment.domain.PaymentProcessor
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCatalog.BROOKLYN_HOME
import io.github.youndie.haul.seed.SampleCatalog.SONY_STORE
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.Ledger
import io.github.youndie.haul.testing.MAYAS_SKUS
import io.github.youndie.haul.testing.SAGA_CLOCK
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.petich.Petich
import io.github.youndie.petich.PetichResult
import io.github.youndie.petich.PetichStatus
import kotlinx.coroutines.runBlocking
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The order saga's rollbacks against PostgreSQL, each member's compensation seen doing its part
 * (feature-orders: «a failed step compensates the previous ones»). The engine is the production one —
 * the same definition, the same stores — driven directly, because the failures it has to survive are
 * not ones an HTTP request can stage: a window filling between the checkout's look and the saga's
 * reservation, stock running out in the same gap, and a member failing after the money was held.
 */
class OrderSagaTest {
    private val slotId = "2025-10-08T15"

    private fun payload(
        orderId: String,
        headphones: Int = 1,
        payment: String = "card-4821",
    ) = OrderPayload(
        orderId = orderId,
        customerId = SampleCustomers.MAYA,
        plus = true,
        method = DeliveryMethod.Courier,
        addressId = "address-maya",
        slotId = slotId,
        payment = payment,
        itemsCents = 65_200,
        discountCents = 14_000,
        deliveryCents = 0,
        totalCents = 51_200,
        points = 1_024,
        placedAt = CatalogSeed.NOW.toString(),
        lines =
            listOf(
                OrderPayload.Line(
                    MAYAS_SKUS[0],
                    SONY_STORE,
                    "Sony WH-1000XM6",
                    headphones,
                    34_900 * headphones,
                    44_900,
                ),
                OrderPayload.Line(MAYAS_SKUS[1], BROOKLYN_HOME, "Linen Duvet Cover Set", 1, 13_900, 17_900),
                OrderPayload.Line(MAYAS_SKUS[2], BROOKLYN_HOME, "Stoneware Mug", 1, 2_400, 2_400),
            ),
    )

    /** The production saga over [dataSource], with the order store [orders] makes of the real one. */
    private fun run(
        dataSource: DataSource,
        payload: OrderPayload,
        orders: (OrderRepository) -> OrderRepository = { it },
        payments: (PaymentProcessor) -> PaymentProcessor = { it },
    ): Pair<PetichResult, Petich> =
        runBlocking {
            val database = Databases.connect(dataSource)
            val storage = SagaStorage(database, SAGA_CLOCK)
            val engine =
                orderEngine(
                    storage,
                    SAGA_CLOCK,
                    listOf(
                        orderSaga(
                            stock = ExposedStock(database),
                            slots = ExposedDeliverySlots(database),
                            orders = orders(ExposedOrders(database)),
                            payments = payments(ExposedPaymentSimulator(database, CANVAS_NOW)),
                            carts = ExposedCartRepository(database),
                            points = ExposedPointsLedger(database),
                        ),
                    ),
                )
            val sagaId = "saga-${payload.orderId}"
            val result =
                engine.process(
                    Petich(id = sagaId, type = ORDER_SAGA, status = PetichStatus.DRAFT, payload = payload),
                )
            result to assertNotNull(storage.sagas.findById(sagaId))
        }

    /**
     * A member failing after the money was held — here the confirmation — undoes every member before it
     * and itself, in reverse: the authorisation is voided by the name it was asked under, the order and
     * its shipments cancelled as a failure, the window's place and the stock given back. The cart is not
     * a member's to undo: the lines leave it only once the order is placed.
     */
    @Test
    fun `a failure after the payment undoes every member in reverse`() =
        seededFreshDatabase().use { dataSource ->
            val ledger = Ledger(dataSource)
            val stockBefore = ledger.stock()
            val failingConfirm: (OrderRepository) -> OrderRepository = { real ->
                object : OrderRepository by real {
                    override suspend fun confirm(orderId: String) = error("the order store refused the confirmation")
                }
            }

            val (result, saga) = run(dataSource, payload("HL-90001"), failingConfirm)

            assertEquals(PetichStatus.FAILED, saga.status, result.toString())
            assertEquals(listOf(Triple("HL-90001", 51_200, "voided")), ledger.authorisations(), "the hold was kept")
            val order = assertNotNull(runBlocking { ExposedOrders(Databases.connect(dataSource)).order("HL-90001") })
            assertEquals(OrderStatus.Cancelled, order.status)
            assertEquals(CancelReason.FAILED, order.cancelReason)
            assertEquals(listOf(ShipmentStatus.CANCELLED, ShipmentStatus.CANCELLED), order.shipments.map { it.status })
            assertEquals(0, ledger.taken(slotId), "the window's place was kept")
            assertEquals(stockBefore, ledger.stock(), "the stock was kept")
            assertEquals(0, ledger.reservedStock())
            assertEquals(MAYAS_SKUS, ledger.cartLines(SampleCustomers.MAYA))
        }

    /**
     * The card processor failing — down, or answering what nobody expected — is a fault, not a
     * decline: the order it was asked for is cancelled as `failed` by `open-order`'s own compensation,
     * and the window and the stock come back. Its own compensation runs too, because petich cannot tell
     * an authorisation that landed from one that did not, and voids nothing here.
     */
    @Test
    fun `a card processor that fails cancels the order it had opened`() =
        seededFreshDatabase().use { dataSource ->
            val ledger = Ledger(dataSource)
            val stockBefore = ledger.stock()
            val failing: (PaymentProcessor) -> PaymentProcessor = { real ->
                object : PaymentProcessor by real {
                    override suspend fun authorise(
                        key: String,
                        authorisation: Authorisation,
                    ): AuthorisationOutcome = error("the card processor answered 502")
                }
            }

            val (result, saga) = run(dataSource, payload("HL-90004"), payments = failing)

            assertEquals(PetichStatus.FAILED, saga.status, result.toString())
            val order = assertNotNull(runBlocking { ExposedOrders(Databases.connect(dataSource)).order("HL-90004") })
            assertEquals(OrderStatus.Cancelled, order.status, "the order a failed payment opened is still open")
            assertEquals(CancelReason.FAILED, order.cancelReason)
            assertEquals(0, ledger.taken(slotId))
            assertEquals(stockBefore, ledger.stock())
            assertEquals(emptyList(), ledger.authorisations())
        }

    /**
     * The window filling between checkout's look and the saga's reservation — the race B-14 left to
     * placement: the saga is refused with `slot_unavailable`, which placement answers `409` with, and the
     * stock it had already taken is given back. No order was opened and nothing was authorised.
     */
    @Test
    fun `a window that fills inside the saga gives the stock back`() =
        seededFreshDatabase().use { dataSource ->
            val ledger = Ledger(dataSource)
            val stockBefore = ledger.stock()
            ledger.fill(slotId)

            val (result, saga) = run(dataSource, payload("HL-90002"))

            assertIs<PetichResult.Error>(result, result.toString())
            assertEquals(PetichStatus.REJECTED, saga.status)
            assertEquals(Refused(Refused.SLOT_UNAVAILABLE), saga.stepRecords["reserve-slot"])
            assertEquals(stockBefore, ledger.stock(), "the stock taken before the window was kept")
            assertEquals(0, ledger.reservedStock())
            assertEquals(0, ledger.orders())
            assertEquals(emptyList(), ledger.authorisations())
        }

    /**
     * Stock that ran out since the quote: the reservation is all or nothing — the duvet and the mugs,
     * which had stock, are not taken either — and the saga is refused with `out_of_stock` before it
     * touches the window.
     */
    @Test
    fun `stock that ran out takes nothing and leaves the window alone`() =
        seededFreshDatabase().use { dataSource ->
            val ledger = Ledger(dataSource)
            val stockBefore = ledger.stock()
            val tooMany = stockBefore.getValue(MAYAS_SKUS[0]) + 1

            val (result, saga) = run(dataSource, payload("HL-90003", headphones = tooMany))

            assertIs<PetichResult.Error>(result, result.toString())
            assertEquals(PetichStatus.REJECTED, saga.status)
            assertEquals(Refused(Refused.OUT_OF_STOCK), saga.stepRecords["reserve-stock"])
            assertEquals(stockBefore, ledger.stock())
            assertEquals(0, ledger.reservedStock())
            assertEquals(0, ledger.taken(slotId))
            assertNull(saga.stepRecords["reserve-slot"])
        }
}
