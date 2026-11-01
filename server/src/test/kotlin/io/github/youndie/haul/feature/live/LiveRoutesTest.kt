package io.github.youndie.haul.feature.live

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.checkout.DeliveryMethod
import io.github.youndie.haul.feature.order.domain.Reorder
import io.github.youndie.haul.feature.returns.ReturnEntry
import io.github.youndie.haul.feature.returns.domain.RequestReturn
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.SampleCheckout
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.testing.CountingDataSource
import io.github.youndie.haul.testing.FulfilmentWorld
import io.github.youndie.haul.testing.MAYA
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.liveTree
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.LiveOrderRow
import io.github.youndie.haul.ui.LiveOrdersBody
import io.github.youndie.haul.ui.LiveStepState
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.encodeKompotComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.OpenUrlAction
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * B-164: «Live orders» — the last orders across the store, each the saga it ran and what the simulated world did with
 * it since, its compensations shown undoing it, nothing in it that names a customer — at an address a reload opens,
 * and cheap however many orders the store holds.
 */
class LiveRoutesTest {
    /**
     * Three of Maya's orders, as the stand's walks leave them: one delivered and returned, one declined by the test
     * card ···· 0002 — the saga refused at `authorise-payment` and undid what it held, in reverse — and one to collect
     * at a point, still on its way. The page lists them newest first.
     */
    @Test
    fun `the page draws the last orders newest first with the saga each ran and its compensations`() =
        seededFreshDatabase().use { database ->
            val (returned, declined, collected) = threeOrders(database)
            haulTest(database) {
                val page = live()
                assertEquals("live-page", page.id)
                assertEquals(
                    listOf("header", LIVE_BODY, "footer"),
                    (page as ColumnComponent).children.map { it.id },
                    "the header, the body and the footer, as the About page is framed",
                )
                assertEquals("Live orders · Haul", page.only<HaulHeader>().tabTitle)
                val body = page.only<LiveOrdersBody>()
                assertEquals(listOf(collected, declined, returned), body.orders.map { it.id }, "newest first")
                assertEquals(
                    listOf(OpenUrlAction(SAGA_SOURCE)),
                    body.links.map { it.action },
                    "the intro links to the saga's code",
                )
                assertTrue("petich saga" in body.lead, body.lead)

                val back = body.row(returned)
                assertEquals("Returned", back.status)
                assertEquals(false, back.undone)
                assertEquals("The saga", back.lanes.first().title)
                assertEquals(
                    listOf("done", "done", "no points", "done", "done", "done"),
                    back.lanes
                        .first()
                        .steps
                        .map { it.word },
                )
                assertEquals("The return", back.lanes.last().title)
                assertTrue(
                    back.lanes
                        .last()
                        .steps
                        .all { it.state == LiveStepState.Done },
                    "${back.lanes.last()}",
                )
                val shipments = back.lanes.drop(1).dropLast(1)
                assertTrue(shipments.isNotEmpty(), "a lane per seller")
                shipments.forEach { lane ->
                    assertEquals(listOf("Packed", "On its way", "Delivered"), lane.steps.map { it.label })
                    assertTrue(lane.steps.all { it.state == LiveStepState.Done }, "$lane")
                    assertTrue(lane.steps[1].word.startsWith("charged $"), "the card is charged as it leaves: $lane")
                }
                assertTrue(back.trail.any { it.text.startsWith("Refunded $") }, "${back.trail}")

                val undone = body.row(declined)
                assertEquals("Cancelled: card declined", undone.status)
                assertEquals(true, undone.undone)
                assertEquals(listOf("The saga"), undone.lanes.map { it.title }, "a cancelled order shipped nothing")
                assertEquals(
                    listOf(
                        LiveStepState.Undone,
                        LiveStepState.Undone,
                        LiveStepState.Skipped,
                        LiveStepState.Undone,
                        LiveStepState.Failed,
                        LiveStepState.Pending,
                    ),
                    undone.lanes
                        .single()
                        .steps
                        .map { it.state },
                )
                assertEquals(
                    "refused: card declined",
                    undone.lanes
                        .single()
                        .steps[4]
                        .word,
                )
                // The trail: the members as they ran, the refusal, then the compensations in reverse.
                assertEquals(
                    listOf(
                        "reserve-stock" to LiveStepState.Done,
                        "reserve-slot" to LiveStepState.Done,
                        "redeem-points" to LiveStepState.Skipped,
                        "open-order" to LiveStepState.Done,
                        "authorise-payment" to LiveStepState.Failed,
                        "open-order" to LiveStepState.Undone,
                        "reserve-slot" to LiveStepState.Undone,
                        "reserve-stock" to LiveStepState.Undone,
                    ),
                    undone.trail.map { it.member to it.state },
                )

                val pickup = body.row(collected)
                assertEquals(
                    "no window",
                    pickup.lanes
                        .first()
                        .steps[1]
                        .word,
                    "a pickup holds no window",
                )
                pickup.lanes.drop(1).forEach { lane ->
                    assertEquals(listOf("Packed", "On its way", "Ready", "Collected"), lane.steps.map { it.label })
                }
                assertTrue(
                    pickup.lanes.drop(1).any { lane -> lane.steps.any { it.state == LiveStepState.Pending } },
                    "the pickup is still on its way: ${pickup.lanes}",
                )
            }
        }

    /**
     * The feed is public: nothing in it names who placed an order or where it goes. Every value the orders hold about
     * their customer — the customer's id and name, the saga's id (the customer's and their key's hash), the address
     * delivered to, the window, the pickup point, the pickup code once the parcel waits, the way to pay — is read off
     * the database and looked for in the page's body. The control is the order's number, which the body does carry.
     */
    @Test
    fun `nothing in the feed names a customer, an address, a window or a pickup code`() =
        seededFreshDatabase().use { database ->
            val (returned, _, collected) = threeOrders(database, collectedToo = true)
            val private = personalData(database)
            assertTrue(private.any { it.startsWith("2025-") }, "the windows were not read: $private")
            assertTrue(private.size > 10, "too little was read to prove anything: $private")
            haulTest(database) {
                val body = live().only<LiveOrdersBody>()
                val words = haulWireJson.encodeKompotComponent(body)
                assertTrue(returned in words && collected in words, "the control: the orders are in the body")
                private.forEach { assertTrue(it !in words, "the feed shows «$it»") }
            }
        }

    /**
     * Two thousand orders on the store and the page reads twenty, in the same few statements as for three: the feed is
     * public, and B-159 found a page that tracked every order taking seconds. The orders are copies of one placed and
     * delivered order, written straight into the tables.
     */
    @Test
    fun `the page reads the same few statements however many orders the store holds`() =
        seededFreshDatabase().use { database ->
            val placed =
                FulfilmentWorld(database).use { world ->
                    world.place(CheckoutChoice(slotId = SLOT)).also {
                        world.advance(Duration.ZERO)
                        world.advance(72.hours)
                    }
                }
            database.connection.use { connection ->
                connection.createStatement().use { it.execute(COPIES.replace("?", "'$placed'")) }
                if (!connection.autoCommit) connection.commit()
            }
            val counted = CountingDataSource(database)
            haulTest(counted) {
                live()
                val sent = counted.statements.get()
                val body = live().only<LiveOrdersBody>()
                val statements = counted.statements.get() - sent
                val took =
                    (1..3).minOf {
                        val started = TimeSource.Monotonic.markNow()
                        live()
                        started.elapsedNow()
                    }
                assertEquals(20, body.orders.size)
                assertTrue(body.orders.all { it.lanes.size >= 2 }, "every copy has its shipments")
                assertTrue(statements <= 10, "the page sent $statements statements")
                assertTrue(took < 2.seconds, "the page of ${COPIED + 1} orders took $took")
            }
        }

    private suspend fun HttpClient.live(): KompotComponent = liveTree(get(LIVE_SCREEN).bodyAsText(), LiveFeed.TOPIC)

    private fun LiveOrdersBody.row(id: String): LiveOrderRow = orders.single { it.id == id }

    /**
     * Maya's three orders, oldest first: delivered and its first line returned and refunded; declined by the test
     * card; and to collect at 214 Bedford Ave, packed and on its way — or, [collectedToo], waiting there with its code.
     */
    private fun threeOrders(
        database: DataSource,
        collectedToo: Boolean = false,
    ): Triple<String, String, String> =
        FulfilmentWorld(database).use { world ->
            val returned = world.place(CheckoutChoice(slotId = SLOT), key = "maya-returned")
            world.advance(Duration.ZERO)
            world.advance(72.hours)
            runBlocking {
                world.koin
                    .get<RequestReturn>()
                    .request(SampleCustomers.MAYA, returned, ReturnEntry(listOf(0), "doesnt_fit"))
            }
            world.advanceReturns(72.hours + 2.days)
            runBlocking { world.koin.get<Reorder>().reorder(MAYA, returned) }
            val declined = world.place(CheckoutChoice(slotId = SLOT, payment = "card-0002"), key = "maya-declined")
            val collected =
                world.place(
                    CheckoutChoice(
                        method = DeliveryMethod.PickupPoint,
                        pointId = SampleCheckout.BEDFORD,
                        payment = "card-4821",
                    ),
                    key = "maya-collected",
                )
            world.advance(72.hours + 2.days + 1.hours)
            world.advance(if (collectedToo) 72.hours + 6.days else 72.hours + 2.days + 30.hours)
            Triple(returned, declined, collected)
        }

    /** Every value the orders on the store hold about who placed them and where they go. */
    private fun personalData(database: DataSource): Set<String> =
        transaction(Databases.connect(database)) {
            val values = mutableSetOf<String>()
            exec(
                """
                SELECT o.customer_id, o.saga_id, o.address::text, o.point_id, o.slot, o.payment, c.name, p.name
                FROM orders o JOIN customers c ON c.id = o.customer_id LEFT JOIN pickup_points p ON p.id = o.point_id
                """.trimIndent(),
            ) { rows ->
                while (rows.next()) {
                    (1..8).mapNotNull { rows.getString(it) }.forEach { value ->
                        if (value.startsWith("{")) {
                            // The address as placed: every value in it.
                            Regex("\"[^\"]+\"\\s*:\\s*\"([^\"]+)\"").findAll(value).forEach {
                                values +=
                                    it.groupValues[1]
                            }
                        } else {
                            values += value
                            values += value.split(' ').filter { it.length > 3 }
                        }
                    }
                }
            }
            exec("SELECT pickup_code FROM shipments WHERE pickup_code IS NOT NULL") { rows ->
                while (rows.next()) values += rows.getString(1)
            }
            // The card's last four, as the checkout names it («Card ···· 4821»).
            values += values.filter { it.startsWith("card-") }.map { it.removePrefix("card-") }
            // A value of two or three characters — «NY», «4B» — would be found in any text; the rest are not.
            values.filter { it.length >= 4 }.toSet()
        }

    private companion object {
        const val SLOT = "2025-10-08T15"
        const val LIVE_BODY = "live-orders"
        const val SAGA_SOURCE =
            "https://github.com/youndie/haul/blob/main/server/src/main/kotlin/io/github/youndie/haul/feature/order/saga/OrderSaga.kt"
        const val COPIED = 2_000

        /**
         * [COPIED] copies of the order `?`, each an hour older than the one before: its row, its lines, its shipments
         * and their history under ids of their own — the same as `AccountRoutesTest`'s, and the history too.
         */
        val COPIES =
            """
            INSERT INTO orders
            SELECT (jsonb_populate_record(o, jsonb_build_object(
                'id', o.id || '-' || n, 'saga_id', o.saga_id || '-' || n, 'placed_at', o.placed_at - n * interval '1 hour'))).*
            FROM orders o, generate_series(1, $COPIED) n WHERE o.id = ?;
            INSERT INTO order_lines
            SELECT (jsonb_populate_record(l, jsonb_build_object('order_id', l.order_id || '-' || n))).*
            FROM order_lines l, generate_series(1, $COPIED) n WHERE l.order_id = ?;
            INSERT INTO shipments
            SELECT (jsonb_populate_record(s, jsonb_build_object('id', s.id || '-' || n, 'order_id', s.order_id || '-' || n))).*
            FROM shipments s, generate_series(1, $COPIED) n WHERE s.order_id = ?;
            INSERT INTO shipment_events
            SELECT (jsonb_populate_record(e, jsonb_build_object('shipment_id', e.shipment_id || '-' || n))).*
            FROM shipment_events e JOIN shipments s ON s.id = e.shipment_id, generate_series(1, $COPIED) n WHERE s.order_id = ?;
            """.trimIndent()
    }
}
