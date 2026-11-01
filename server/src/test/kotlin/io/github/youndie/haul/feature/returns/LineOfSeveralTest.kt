package io.github.youndie.haul.feature.returns

import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.cart.domain.CartCommands
import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.fulfilment.domain.ShipmentShares
import io.github.youndie.haul.feature.order.saga.OrderPayload
import io.github.youndie.haul.feature.order.screen.OrderScreen
import io.github.youndie.haul.feature.returns.domain.RequestReturn
import io.github.youndie.haul.feature.returns.domain.ReturnRefunds
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.testing.FulfilmentWorld
import io.github.youndie.haul.testing.MAYA
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.ui.OrderBody
import io.github.youndie.haul.ui.ReturnForm
import io.github.youndie.kompot.standard.PresentAction
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * B-91, through the database: an order line keeps what the whole line cost — the price times the quantity, as
 * placement has written it since V10 and as every order on the stand holds it — and every reader takes it as that.
 * Maya buys Sonora's headphones ×2 ($349.00 each), the duvet cover and the mugs, $861.00 paid partly with her 2,480
 * points; the order draws «2 × $349», shares its total between the shipments by the lines' totals, and returning
 * the two pairs gives back $698.00 — $677.90 to the card and 2,010 points — not the $770.97 and 2,220 points the
 * quantity counted twice gave.
 */
class LineOfSeveralTest {
    /** The order's page as the route draws it for Maya. */
    private fun FulfilmentWorld.body(orderId: String): OrderBody =
        runBlocking {
            val screen = koin.get<OrderScreen>()
            OrderScreen.body(screen.view(SampleCustomers.MAYA, assertNotNull(track(orderId)), "Maya"))
        }

    /** Each line's mono line on the order's page, in its shipment: «Midnight Black · Headphones only · 2 × $349». */
    private fun OrderBody.details(): List<String> = shipments.flatMap { shipment -> shipment.items.map { it.details } }

    /** The stored line as it is in the table, past every repository: quantity, `price_cents`, `list_cents`. */
    private fun DataSource.storedLine(
        orderId: String,
        position: Int,
    ): Triple<Int, Int, Int> =
        connection.use { connection ->
            connection
                .prepareStatement(
                    "SELECT quantity, price_cents, list_cents FROM order_lines WHERE order_id = ? AND position = ?",
                ).use { statement ->
                    statement.setString(1, orderId)
                    statement.setInt(2, position)
                    statement.executeQuery().use {
                        check(it.next()) { "no line $position of $orderId" }
                        Triple(it.getInt(1), it.getInt(2), it.getInt(3))
                    }
                }
        }

    @Test
    fun `a new order of two pairs shows the price of one and its return gives back what the two cost`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val sonora = world.ledger.cartLines(SampleCustomers.MAYA).first()
                runBlocking { world.koin.get<CartCommands>().changeLine(MAYA, sonora, LineChange(quantity = 2)) }
                val orderId = world.place(CheckoutChoice(slotId = "2025-10-08T15", usePoints = true))

                val order = assertNotNull(world.order(orderId))
                assertEquals(86_100 - 2_480, order.placed.totalCents)
                val stored = dataSource.storedLine(orderId, 0)
                assertEquals(
                    2 to 69_800,
                    stored.first to stored.second,
                    "stored as the stand's rows are: the line's total",
                )
                val line = order.placed.lines[0]
                assertEquals(69_800, line.totalCents)
                assertEquals(34_900, line.unitCents)
                assertEquals(mapOf(0 to 69_800, 1 to 13_900, 2 to 2_400), ReturnRefunds.of(order))
                assertEquals(listOf(15_831, 67_789), ShipmentShares.of(order).values.sorted())

                world.advance(Duration.ZERO)
                world.advance(72.hours)
                val body = world.body(orderId)
                assertTrue(body.details().first().endsWith(ONE_PAIR), "the price of one pair: ${body.details()}")
                val form = assertNotNull((body.summary.returnAction as? PresentAction)?.content as? ReturnForm)
                val offered = form.lines.single { it.position == 0 }
                assertTrue(offered.details.endsWith(ONE_PAIR), offered.details)
                assertEquals(69_800, offered.refundCents, "the dialog offers what the two pairs cost")
                assertEquals(2_010, offered.pointsBack)
                assertEquals(listOf(67_789, 15_831), world.captures(orderId).values.sortedDescending())

                runBlocking {
                    world.koin.get<RequestReturn>().request(
                        SampleCustomers.MAYA,
                        orderId,
                        ReturnEntry(listOf(0), "doesnt_fit"),
                    )
                }
                assertEquals(69_800, world.ledger.storedReturn(orderId)?.second, "the return asks back what was paid")
                world.advanceReturns(72.hours + 2.days)
                assertEquals(
                    mapOf("refund:$orderId" to 69_800 - 2_010),
                    world.ledger.refunds(orderId),
                    "$677.90 to the card",
                )
            }
        }

    /**
     * An order written before B-91 — by the image the stand ran, raw into the tables as it wrote them — is read the
     * same: its stored `price_cents` is the line's total, so nothing rewrites it and the readers take it as that.
     */
    @Test
    fun `an order stored before the fix is read as its lines' totals`() =
        seededFreshDatabase().use { dataSource ->
            dataSource.connection.use { connection ->
                connection.createStatement().use {
                    it.execute(
                        """
                        INSERT INTO orders (id, saga_id, customer_id, status, method, address_id, payment, items_cents,
                                            discount_cents, delivery_cents, total_cents, points, placed_at, points_redeemed)
                        VALUES ('HL-48303', 'saga-48303', '${SampleCustomers.MAYA}', 'placed', 'courier', 'address-maya',
                                'card-4821', 86100, 0, 0, 83620, 1672, now(), 2480);
                        INSERT INTO order_lines (order_id, position, sku_id, seller_id, title, quantity, price_cents, list_cents)
                        SELECT 'HL-48303', line.position - 1, line.sku_id, product.seller_id, product.title,
                               CASE WHEN line.position = 1 THEN 2 ELSE 1 END,
                               sku.price_cents * CASE WHEN line.position = 1 THEN 2 ELSE 1 END,
                               sku.price_cents * CASE WHEN line.position = 1 THEN 2 ELSE 1 END
                        FROM cart_lines line
                        JOIN carts cart ON cart.id = line.cart_id
                        JOIN skus sku ON sku.id = line.sku_id
                        JOIN products product ON product.id = sku.product_id
                        WHERE cart.customer_id = '${SampleCustomers.MAYA}';
                        INSERT INTO shipments (id, order_id, seller_id, position, status)
                        SELECT 'HL-48303-' || row_number() OVER (ORDER BY first), 'HL-48303', seller_id,
                               row_number() OVER (ORDER BY first), 'delivered'
                        FROM (SELECT seller_id, min(position) AS first FROM order_lines
                              WHERE order_id = 'HL-48303' GROUP BY seller_id) sellers;
                        """.trimIndent(),
                    )
                }
                if (!connection.autoCommit) connection.commit()
            }
            FulfilmentWorld(dataSource).use { world ->
                assertEquals(Triple(2, 69_800, 69_800), dataSource.storedLine("HL-48303", 0))
                val order = assertNotNull(world.order("HL-48303"))
                assertEquals(listOf(69_800, 13_900, 2_400), order.placed.lines.map { it.totalCents })
                assertEquals(34_900, order.placed.lines[0].unitCents)
                assertEquals(mapOf(0 to 69_800, 1 to 13_900, 2 to 2_400), ReturnRefunds.of(order))
                assertEquals(mapOf(0 to 2_010, 1 to 400, 2 to 70), ReturnRefunds.pointsBack(order))
                assertEquals(listOf(15_831, 67_789), ShipmentShares.of(order).values.sorted())
                val details = world.body("HL-48303").details()
                assertTrue(details.first().endsWith(ONE_PAIR), "the price of one pair: $details")
            }
        }

    /**
     * A saga stored before the fields were named for what they hold carries its lines under `priceCents` and
     * `listCents`, already line totals; it is read into the same totals, so an order placed across the deploy
     * keeps what it was placed for.
     */
    @Test
    fun `a saga stored before the rename reads its lines' totals`() {
        val stored =
            """{"skuId":"sku","sellerId":"seller","title":"Sonora","quantity":2,"priceCents":69800,"listCents":89800}"""
        val line = Json.decodeFromString(OrderPayload.Line.serializer(), stored)

        assertEquals(
            OrderPayload.Line("sku", "seller", "Sonora", 2, totalCents = 69_800, listTotalCents = 89_800),
            line,
        )
        assertEquals(stored, Json.encodeToString(OrderPayload.Line.serializer(), line))
    }

    private companion object {
        /** The end of the pair's mono line: the quantity and its price held together by no-break spaces (B-133). */
        const val ONE_PAIR = " · 2\u00A0×\u00A0\$349"
    }
}
