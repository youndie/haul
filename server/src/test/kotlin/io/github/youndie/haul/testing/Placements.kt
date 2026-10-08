package io.github.youndie.haul.testing

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.cart.data.CartLinesTable
import io.github.youndie.haul.feature.cart.data.CartsTable
import io.github.youndie.haul.feature.catalog.data.SkusTable
import io.github.youndie.haul.feature.checkout.data.AddressesTable
import io.github.youndie.haul.feature.checkout.data.DeliverySlotsTable
import io.github.youndie.haul.feature.fulfilment.data.ShipmentEventsTable
import io.github.youndie.haul.feature.order.data.OrdersTable
import io.github.youndie.haul.feature.order.data.SagaTables
import io.github.youndie.haul.feature.order.data.ShipmentsTable
import io.github.youndie.haul.feature.order.data.StockReservationsTable
import io.github.youndie.haul.feature.payment.data.PaymentAuthorisationsTable
import io.github.youndie.haul.feature.payment.data.InstalmentsTable
import io.github.youndie.haul.feature.payment.data.PaymentCapturesTable
import io.github.youndie.haul.feature.payment.data.PaymentRefundsTable
import io.github.youndie.haul.feature.returns.data.ReturnsTable
import io.github.youndie.haul.seed.SampleCatalog.DUVET_COVER
import io.github.youndie.haul.seed.SampleCatalog.SONY_HEADPHONES
import io.github.youndie.haul.seed.SampleCatalog.STONEWARE_MUG
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.LocalDate
import javax.sql.DataSource

/** Maya's three SKUs (research §6): the headphones, the duvet cover set, the mug set. */
internal val MAYAS_SKUS: List<String> = listOf("$SONY_HEADPHONES-0", "$DUVET_COVER-0", "$STONEWARE_MUG-0")

/**
 * What placement left in the database, read past every repository — the rows themselves, so a test
 * sees what a second process would find rather than what the code that wrote them believes.
 */
internal class Ledger(
    dataSource: DataSource,
) {
    private val database = Databases.connect(dataSource)

    fun stock(skuIds: List<String> = MAYAS_SKUS): Map<String, Int> =
        transaction(database) {
            SkusTable
                .selectAll()
                .where { SkusTable.id inList skuIds }
                .associate { it[SkusTable.id] to it[SkusTable.stock] }
        }

    fun reservedStock(): Int = transaction(database) { StockReservationsTable.selectAll().count().toInt() }

    /** How many places [slotId]'s window has taken; 0 for a window never written. */
    fun taken(slotId: String): Int {
        val (day, hour) = slotId.split('T')
        return transaction(database) {
            DeliverySlotsTable
                .selectAll()
                .where {
                    (DeliverySlotsTable.day eq LocalDate.parse(day)) and
                        (DeliverySlotsTable.startHour eq hour.toInt())
                }.singleOrNull()
                ?.get(DeliverySlotsTable.taken) ?: 0
        }
    }

    /** Every place in [slotId]'s window taken, as twenty orders would leave it. */
    fun fill(slotId: String) {
        val (day, hour) = slotId.split('T')
        transaction(database) {
            DeliverySlotsTable.upsert {
                it[DeliverySlotsTable.day] = LocalDate.parse(day)
                it[startHour] = hour.toInt()
                it[capacity] = 20
                it[taken] = 20
            }
        }
    }

    /** The simulator's ledger: each authorisation's order, amount and status. */
    fun authorisations(): List<Triple<String, Int, String>> =
        transaction(database) {
            PaymentAuthorisationsTable.selectAll().map {
                Triple(
                    it[PaymentAuthorisationsTable.orderId],
                    it[PaymentAuthorisationsTable.amountCents],
                    it[PaymentAuthorisationsTable.status],
                )
            }
        }

    fun orders(): Int = transaction(database) { OrdersTable.selectAll().count().toInt() }

    /** [customerId]'s saved addresses, the newest first: each one's id and apt. */
    fun addresses(customerId: String): List<Pair<String, String?>> =
        transaction(database) {
            AddressesTable
                .selectAll()
                .where { AddressesTable.customerId eq customerId }
                .orderBy(AddressesTable.createdAt to SortOrder.DESC, AddressesTable.id to SortOrder.DESC)
                .map { it[AddressesTable.id] to it[AddressesTable.apt] }
        }

    /** The SKUs in [customerId]'s cart, in its order. */
    fun cartLines(customerId: String): List<String> =
        transaction(database) {
            val cart =
                CartsTable.selectAll().where { CartsTable.customerId eq customerId }.singleOrNull()
                    ?: return@transaction emptyList()
            CartLinesTable
                .selectAll()
                .where { CartLinesTable.cartId eq cart[CartsTable.id] }
                .orderBy(CartLinesTable.position)
                .map { it[CartLinesTable.skuId] }
        }

    /** How many sagas were stored: a placement refused before the saga stores none. */
    fun sagas(): Int =
        transaction(database) {
            SagaTables.petiches
                .selectAll()
                .count()
                .toInt()
        }

    /** The status of the one saga in the database. */
    fun sagaStatus(): String =
        transaction(database) {
            SagaTables.petiches
                .selectAll()
                .single()[SagaTables.petiches.status]
                .name
        }

    /** Each shipment of [orderId]'s status, by id. */
    fun shipmentStatuses(orderId: String): Map<String, String> =
        transaction(database) {
            ShipmentsTable
                .selectAll()
                .where { ShipmentsTable.orderId eq orderId }
                .associate { it[ShipmentsTable.id] to it[ShipmentsTable.status] }
        }

    /** Each shipment of [orderId]'s pickup code as stored, by id. */
    fun pickupCodes(orderId: String): Map<String, String?> =
        transaction(database) {
            ShipmentsTable
                .selectAll()
                .where { ShipmentsTable.orderId eq orderId }
                .associate { it[ShipmentsTable.id] to it[ShipmentsTable.pickupCode] }
        }

    /** The payment simulator's captures of [orderId], by key. */
    fun captures(orderId: String): Map<String, Int> =
        transaction(database) {
            PaymentCapturesTable
                .selectAll()
                .where { PaymentCapturesTable.orderId eq orderId }
                .associate { it[PaymentCapturesTable.key] to it[PaymentCapturesTable.amountCents] }
        }

    /**
     * [orderId]'s Haul Pay payments as stored (V21), read past the repository: number → status, what it owes
     * after a return's reduction, and how many attempts were declined.
     */
    fun instalments(orderId: String): Map<Int, Triple<String, Int, Int>> =
        transaction(database) {
            InstalmentsTable
                .selectAll()
                .where { InstalmentsTable.orderId eq orderId }
                .associate {
                    it[InstalmentsTable.number] to
                        Triple(
                            it[InstalmentsTable.status],
                            it[InstalmentsTable.amountCents] - it[InstalmentsTable.reducedCents],
                            it[InstalmentsTable.attempts],
                        )
                }
        }

    /** How many steps of shipment history there are, every shipment's. */
    fun historyRows(): Int = transaction(database) { ShipmentEventsTable.selectAll().count().toInt() }

    /** The payment simulator's refunds of [orderId], by key (B-21). */
    fun refunds(orderId: String): Map<String, Int> =
        transaction(database) {
            PaymentRefundsTable
                .selectAll()
                .where { PaymentRefundsTable.orderId eq orderId }
                .associate { it[PaymentRefundsTable.key] to it[PaymentRefundsTable.amountCents] }
        }

    /** [orderId]'s return as stored — its status, refund and points — or `null` when none was written. */
    fun storedReturn(orderId: String): Triple<String, Int, Int>? =
        transaction(database) {
            ReturnsTable
                .selectAll()
                .where { ReturnsTable.orderId eq orderId }
                .singleOrNull()
                ?.let { Triple(it[ReturnsTable.status], it[ReturnsTable.refundCents], it[ReturnsTable.points]) }
        }

    /** How many returns there are, everybody's. */
    fun returns(): Int = transaction(database) { ReturnsTable.selectAll().count().toInt() }
}
