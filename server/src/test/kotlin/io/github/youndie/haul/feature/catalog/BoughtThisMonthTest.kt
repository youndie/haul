package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.checkout.DeliveryMethod
import io.github.youndie.haul.feature.order.data.ExposedOrders
import io.github.youndie.haul.feature.order.domain.NewOrder
import io.github.youndie.haul.feature.order.domain.OrderLine
import io.github.youndie.haul.feature.order.domain.OrderStatus
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.seed.SampleCheckout
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.seed.SampleHeadphones
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.ProductDetails
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import java.time.OffsetDateTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * «Bought this month» on the product page (B-52) through the route, over orders written into a database of
 * its own at the canvas's «now»: which orders count, the threshold, and the seed's base under the orders.
 * The Marshall headphones and the Bose pair have no base, so what the page says is the orders alone.
 */
class BoughtThisMonthTest {
    private val dataSource = seededFreshDatabase()
    private val orders = ExposedOrders(Databases.connect(dataSource))
    private val now = CatalogSeed.NOW

    @AfterTest
    fun close() = dataSource.close()

    private fun line(
        sku: String,
        seller: String,
        quantity: Int,
    ) = OrderLine(sku, seller, sku, quantity, 10_000, 10_000)

    private fun marshall(quantity: Int) = line(MARSHALL, SampleHeadphones.MARSHALL_STORE, quantity)

    /** An order of [lines] placed at [placedAt], left where [status] says: still placing, placed or cancelled. */
    private fun order(
        id: String,
        placedAt: OffsetDateTime,
        status: OrderStatus,
        vararg lines: OrderLine,
    ) = runBlocking {
        val total = lines.sumOf { it.priceCents * it.quantity }
        orders.open(
            NewOrder(
                id = id,
                sagaId = "saga-$id",
                customerId = SampleCustomers.MAYA,
                method = DeliveryMethod.PickupPoint,
                addressId = null,
                address = null,
                pointId = SampleCheckout.BEDFORD,
                slotId = null,
                payment = "card-4821",
                promoCode = null,
                itemsCents = total,
                discountCents = 0,
                deliveryCents = 0,
                totalCents = total,
                points = 0,
                placedAt = placedAt,
                lines = lines.toList(),
            ),
        )
        when (status) {
            OrderStatus.Placing -> Unit
            OrderStatus.Placed -> orders.confirm(id)
            OrderStatus.Cancelled -> orders.cancel(id, "payment_declined")
        }
    }

    private fun refunded(orderId: String) =
        dataSource.connection.use { c ->
            c.createStatement().use {
                it.executeUpdate(
                    "INSERT INTO returns (order_id, reason, refund_cents, points, status, requested_at, refunded_at) " +
                        "VALUES ('$orderId', 'changed_mind', 0, 0, 'refunded', now(), now())",
                )
            }
            c.commit()
        }

    private suspend fun HttpClient.bought(productId: String): String? =
        tree("/ui/p/$productId").only<ProductDetails>().bought

    /**
     * The owner's rule: the units in orders placed in the last 30 store days and not cancelled. An order a
     * day past the window, a cancelled one, one still placing, and another product's line in the same order
     * are all left out; a returned order was still bought and stays in. Exactly fifty are left, so any of
     * them counted moves the number and any of the fifty dropped hides it.
     */
    @Test
    fun `only placed orders of the product inside the last thirty days count`() {
        order("HL-1", now.minusDays(1), OrderStatus.Placed, marshall(20), line(BOSE, SampleHeadphones.BOSE_STORE, 100))
        order("HL-2", now.minusDays(10), OrderStatus.Placed, marshall(15))
        order("HL-3", now.minusDays(29), OrderStatus.Placed, marshall(15))
        refunded("HL-3")
        order("HL-4", now.minusDays(31), OrderStatus.Placed, marshall(100))
        order("HL-5", now.minusDays(2), OrderStatus.Cancelled, marshall(100))
        order("HL-6", now.minusHours(1), OrderStatus.Placing, marshall(100))

        haulTest(dataSource) {
            assertEquals("50 bought this month", bought(MARSHALL_PRODUCT))
            assertEquals("100 bought this month", bought(BOSE_PRODUCT), "the other product's line")
        }
    }

    /** «Absent below 50»: a quiet product does not advertise its quietness; the fiftieth unit brings the line. */
    @Test
    fun `below fifty the page says nothing`() {
        order("HL-1", now.minusDays(3), OrderStatus.Placed, marshall(49))
        haulTest(dataSource) { assertNull(bought(MARSHALL_PRODUCT), "49 units were advertised") }

        order("HL-2", now.minusDays(3), OrderStatus.Placed, marshall(1))
        haulTest(dataSource) { assertEquals("50 bought this month", bought(MARSHALL_PRODUCT)) }
    }

    /**
     * The seed's base is under the orders, not instead of them: the mug's 840 is the item's own «840», and 160
     * more ordered make a thousand, abbreviated as the canvas abbreviates.
     */
    @Test
    fun `orders add to the base the seed gives`() {
        haulTest(dataSource) { assertEquals("840 bought this month", bought(SampleCatalog.STONEWARE_MUG)) }

        order("HL-1", now.minusDays(5), OrderStatus.Placed, line(MUG, SampleCatalog.BROOKLYN_HOME, 160))
        haulTest(dataSource) { assertEquals("1K bought this month", bought(SampleCatalog.STONEWARE_MUG)) }
    }

    private companion object {
        const val MARSHALL_PRODUCT = "p-marshall-major-v"
        const val MARSHALL = "$MARSHALL_PRODUCT-0"
        const val BOSE_PRODUCT = "p-bose-qc-ultra"
        const val BOSE = "$BOSE_PRODUCT-0"
        const val MUG = "${SampleCatalog.STONEWARE_MUG}-0"
    }
}
