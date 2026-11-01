package io.github.youndie.haul.feature.account

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.feature.account.screen.AccountScreen
import io.github.youndie.haul.feature.account.screen.HistoryFilterKey
import io.github.youndie.haul.feature.catalog.domain.count
import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.membership.screen.PlusOffer
import io.github.youndie.haul.feature.order.OrderPaths
import io.github.youndie.haul.feature.returns.ReturnEntry
import io.github.youndie.haul.feature.returns.domain.RequestReturn
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Parts
import io.github.youndie.haul.testing.FulfilmentWorld
import io.github.youndie.haul.testing.ShildikHarness
import io.github.youndie.haul.testing.TestClock
import io.github.youndie.haul.testing.after
import io.github.youndie.haul.testing.answer
import io.github.youndie.haul.testing.assertError
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.json
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.ui.AccountBody
import io.github.youndie.haul.ui.AccountTileKind
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.HistoryRow
import io.github.youndie.haul.ui.HistoryStatusKind
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.commands.LoadAction
import io.github.youndie.kompot.commands.UpdateAction
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.PresentAction
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * The account's overview and its orders' history over HTTP (feature-account, screen-account,
 * endpoint-account), against shildik and a seeded PostgreSQL of each test's own. The orders are placed and
 * moved by the production graph ([FulfilmentWorld]); the routes read them back as Maya, as Sam — whose
 * they are not — and as somebody who has never ordered.
 */
class AccountRoutesTest {
    private val maya by lazy {
        ShildikHarness.accessToken(ShildikHarness.person("Maya Kowalski", SampleCustomers.MAYA))
    }
    private val sam by lazy { ShildikHarness.accessToken(ShildikHarness.person("Sam Ortiz", SampleCustomers.SAM)) }

    /** [orders] placed in a world of its own, then [block] over HTTP against the same database. */
    private fun world(
        orders: FulfilmentWorld.() -> List<String>,
        block: suspend HttpClient.(orderIds: List<String>) -> Unit,
    ) = seededFreshDatabase().use { database ->
        val ids = FulfilmentWorld(database).use { it.orders() }
        haulTest(database, signIn = ShildikHarness.signIn) { block(ids) }
    }

    private suspend fun HttpClient.account(
        token: String,
        path: String = AccountPaths.SCREEN,
    ): KompotComponent {
        val response = get(path) { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return haulWireJson.decodeKompotComponent(response.bodyAsText())
    }

    /**
     * The overview is the customer's own (screen-account, Content): the order Maya just placed is her one
     * active order, with its way to its page, counted on the menu; Sam's overview, over the same database,
     * has none of it — «not yours» is never drawn — and greets him by his name.
     */
    @Test
    fun `the overview draws the customer's own orders and nobody else's`() =
        world({ listOf(place(CheckoutChoice(slotId = "2025-10-08T15"))) }) { (orderId) ->
            val tree = account(maya)
            val body = tree.only<AccountBody>()
            assertEquals("Hi, Maya", body.title)
            assertEquals("Maya", body.accent)
            val card = checkNotNull(body.active).orders.single()
            assertEquals(orderId, card.id)
            assertEquals(NavigateAction(OrderPaths.page(orderId)), card.details.action)
            assertEquals(listOf("Placed", "Packed", "In transit", "Delivered"), card.steps?.labels)
            assertEquals("1", body.menu.single { it.label == "Orders" }.count)
            assertNull(body.history, "nothing has arrived, so there is no history yet")
            assertEquals(NavigateAction(Frame.ORDERS), tree.only<HaulHeader>().orders)

            val sams = account(sam).only<AccountBody>()
            assertEquals("Hi, Sam", sams.title)
            assertEquals(emptyList(), checkNotNull(sams.active).orders, "Maya's order is not Sam's")
            assertEquals("No active orders right now.", sams.active?.empty)
            assertNull(sams.menu.single { it.label == "Orders" }.count)
        }

    /**
     * The tiles the overview draws from feature-membership's ledger and membership (B-23) and from the
     * Saved list B-20 will own: Maya's seeded points and membership, and her price drops, are the canvas's
     * (research §6); Sam, no member, is offered the trial, whose button presents its dialog.
     */
    @Test
    fun `the tiles are the member's standing or the trial's offer`() =
        world({ emptyList() }) {
            val mayas = account(maya).only<AccountBody>()
            assertEquals(
                listOf(
                    Triple(AccountTileKind.Points, "2,480", "Worth $24.80 on your next order"),
                    Triple(AccountTileKind.Plus, "$186", "Saved on delivery this year · renews Nov 2"),
                    Triple(AccountTileKind.PriceDrops, "6", "Items in your Saved list got cheaper"),
                ),
                mayas.tiles.map { Triple(it.kind, it.figure, it.text) },
            )
            // Only the price drops have a page: the Saved list under its filter; the rest are read (B-74).
            assertEquals(
                listOf(null, null, NavigateAction("/account/saved?filter=price-dropped")),
                mayas.tiles.map { it.action },
            )
            assertEquals("Plus member since 2023", mayas.profile.subtitle)
            assertEquals("48", mayas.menu.single { it.label == "Saved" }.count)

            val sams = account(sam).only<AccountBody>()
            val offer = sams.tiles.single { it.kind == AccountTileKind.PlusOffer }
            assertEquals("Try 30 days free", offer.button?.label)
            assertEquals(PresentAction(PlusOffer.dialog, PlusOffer.DIALOG), offer.button?.action)
            assertEquals(listOf("0", "30 days free", "0"), sams.tiles.map { it.figure })
            assertEquals(
                listOf(null, null, NavigateAction("/account/saved?filter=price-dropped")),
                sams.tiles.map { it.action },
                "the offer is pressed on its button, not as a tile",
            )
        }

    /**
     * The history's filter is the address's `status` (screen-account, Orders): the chips count every order
     * and go to their own addresses, each address draws only its orders, and a `status` the history does not
     * have is all of them. A delivered order reorders through the order page's command (B-18); every row,
     * delivered or declined, leads to its page (B-74).
     */
    @Test
    fun `the history filters by the status in its address`() =
        world({
            // Declined first — its lines go back to the cart — then placed again and moved until delivered.
            val declined = place(CheckoutChoice(slotId = "2025-10-08T15", payment = "card-0002"), key = "declined")
            val delivered = place(CheckoutChoice(payment = "card-4821"), key = "delivered")
            advance(Duration.ZERO)
            advance(10.days)
            listOf(declined, delivered)
        }) { (declined, delivered) ->
            val all = checkNotNull(account(maya, AccountPaths.ORDERS).only<AccountBody>().history)
            assertEquals(
                listOf("All" to "2", "Active" to "0", "Delivered" to "1", "Returned" to "0", "Cancelled" to "1"),
                all.filters.map { it.label to it.count },
            )
            assertEquals(
                listOf(
                    // «All» is the page these chips are on: it carries nothing (B-102).
                    null,
                    LoadAction("/ui/parts/account/orders?status=active"),
                    LoadAction("/ui/parts/account/orders?status=delivered"),
                    LoadAction("/ui/parts/account/orders?status=returned"),
                    LoadAction("/ui/parts/account/orders?status=cancelled"),
                ),
                all.filters.map { it.action },
            )
            assertEquals(setOf(declined, delivered), all.rows.map { it.id }.toSet())

            // A chip loads the account's body in place (B-63), and the page after it is the page its address opens.
            val page = account(maya, AccountPaths.ORDERS)
            all.filters.mapNotNull { (it.action as? LoadAction)?.url }.forEach { url ->
                val update = assertNotNull(answer(url) { bearerAuth(maya) } as? UpdateAction, url)
                assertEquals(AccountScreen.PARTS, update.updates.map { it.componentId }, url)
                assertEquals(account(maya, "/ui" + update.deeplink).json(), page.after(update), url)
            }
            assertEquals(HttpStatusCode.Unauthorized, get("/ui/parts/account/orders").status)

            val cancelled =
                checkNotNull(account(maya, "${AccountPaths.ORDERS}?status=cancelled").only<AccountBody>().history)
            assertEquals(listOf("Cancelled"), cancelled.filters.filter { it.selected }.map { it.label })
            assertEquals(null, cancelled.filters.single { it.selected }.action, "the chip of the page it is on")
            assertEquals(LoadAction("/ui/parts/account/orders"), cancelled.filters.single { it.label == "All" }.action)
            val row = cancelled.rows.single()
            assertEquals(declined, row.id)
            assertEquals(
                Triple("Cancelled", "Details", NavigateAction(OrderPaths.page(declined))),
                Triple(row.status, row.actionLabel, row.action),
            )

            val arrived =
                checkNotNull(
                    account(maya, "${AccountPaths.ORDERS}?status=delivered").only<AccountBody>().history,
                ).rows.single()
            assertEquals(delivered, arrived.id)
            assertEquals(
                Triple("Delivered", HistoryStatusKind.Done, "Reorder"),
                Triple(arrived.status, arrived.statusKind, arrived.actionLabel),
            )
            assertEquals(OrderPaths.reorder(delivered), arrived.reorderUrl)
            // A delivered row opens its page too, beside its «Reorder» (B-74).
            assertEquals(NavigateAction(OrderPaths.page(delivered)), arrived.action)

            val active = checkNotNull(account(maya, "${AccountPaths.ORDERS}?status=active").only<AccountBody>().history)
            assertEquals(emptyList(), active.rows)
            assertEquals("No active orders.", active.none)

            val unknown = checkNotNull(account(maya, "${AccountPaths.ORDERS}?status=lost").only<AccountBody>().history)
            assertEquals(2, unknown.rows.size, "a status the history does not have is all of them")
            assertEquals(listOf("All"), unknown.filters.filter { it.selected }.map { it.label })

            assertEquals(emptyList(), checkNotNull(account(sam, AccountPaths.ORDERS).only<AccountBody>().history).rows)
        }

    /**
     * A return in the history (B-21): Maya's delivered order, its first line sent back, reads «Returning» in
     * Blush while the courier has it and, once refunded, «Partly returned» at what was paid and kept — $512.00
     * less the headphones' $349.00, the page's «Paid» (B-101) — under the «Returned» chip, which counts it, and
     * out of «Delivered»; it leads to its page («Details»), as the canvas's history draws a returned order.
     */
    @Test
    fun `a returned order is returned in the history and under its filter`() =
        seededFreshDatabase().use { database ->
            val clock = TestClock()
            val orderId =
                FulfilmentWorld(database, clock).use { world ->
                    world.place(CheckoutChoice(slotId = "2025-10-08T15")).also { order ->
                        world.advance(Duration.ZERO)
                        world.advance(72.hours)
                        runBlocking {
                            world.koin
                                .get<RequestReturn>()
                                .request(SampleCustomers.MAYA, order, ReturnEntry(listOf(0), "doesnt_fit"))
                        }
                        world.advanceReturns(96.hours)
                    }
                }

            suspend fun HttpClient.returnedRows(): Pair<List<Pair<String, String?>>, List<HistoryRow>> {
                val all = checkNotNull(account(maya, AccountPaths.ORDERS).only<AccountBody>().history)
                val returned =
                    checkNotNull(account(maya, "${AccountPaths.ORDERS}?status=returned").only<AccountBody>().history)
                return all.filters.map { it.label to it.count } to returned.rows
            }

            haulTest(database, signIn = ShildikHarness.signIn) {
                val (counts, rows) = returnedRows()
                assertEquals("1", counts.toMap()["Returned"])
                assertEquals("0", counts.toMap()["Delivered"])
                assertEquals(
                    Triple("Returning", HistoryStatusKind.Returned, "Details"),
                    rows.single().let { Triple(it.status, it.statusKind, it.actionLabel) },
                )
                assertEquals("$512.00", rows.single().total, "nothing is back while the courier has it")
            }
            FulfilmentWorld(database, clock).use { it.advanceReturns(120.hours) }
            haulTest(database, signIn = ShildikHarness.signIn) {
                val (counts, rows) = returnedRows()
                assertEquals("1", counts.toMap()["Returned"])
                val row = rows.single()
                assertEquals(orderId, row.id)
                assertEquals(
                    Triple("Partly returned", HistoryStatusKind.Returned, "Details"),
                    Triple(row.status, row.statusKind, row.actionLabel),
                )
                assertEquals("$163.00", row.total)
                assertEquals(NavigateAction(OrderPaths.page(orderId)), row.action)
                assertNull(row.reorderUrl)
            }
        }

    /**
     * Somebody who has never ordered (screen-account, NoOrders): no chips, «No orders yet» and the way to
     * the deals; they are greeted by the month they joined, the first time their token reached the server.
     */
    @Test
    fun `a customer with no orders is sent to the deals`() =
        world({ emptyList() }) {
            val jordan = ShildikHarness.accessToken(ShildikHarness.person("Jordan Lee"))
            val body = account(jordan, AccountPaths.ORDERS).only<AccountBody>()
            val history = checkNotNull(body.history)
            assertEquals(emptyList(), history.filters)
            assertEquals("No orders yet", history.empty?.title)
            assertEquals(NavigateAction(Frame.DEALS), history.empty?.action)
            assertEquals("Joined Oct 2025", body.profile.subtitle)
            assertEquals("JL", body.profile.initials)
        }

    /** The account and its history are the customer tier's: without a token, `401 unauthenticated`. */
    @Test
    fun `the account and its history need a sign-in`() =
        haulTest(signIn = ShildikHarness.signIn) {
            get(AccountPaths.SCREEN).assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
            get(AccountPaths.ORDERS).assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
        }

    /**
     * A customer with thousands of orders gets their history in time, a page at a time (B-159). The synthetic
     * shoppers' customers on the stand waited 6–8 s for it: the page tracked every order — its shipments'
     * history, its payments, a few queries each — and drew a row for each, for a page a person reads twenty of.
     * The order on its way still comes first, the chips still count every order, and the next page is the next
     * twenty. The orders behind it are copies of one placed order, delivered, written straight into the tables:
     * placing five thousand through the saga would take the test longer than the defect took the stand.
     */
    @Test
    fun `a history of thousands of orders answers in time a page at a time`() =
        seededFreshDatabase().use { database ->
            val placed = FulfilmentWorld(database).use { it.place(CheckoutChoice(slotId = "2025-10-08T15")) }
            database.connection.use { connection ->
                // The order's number is the server's own (`HL-48302`), so it is written into the statement as it is.
                connection.createStatement().use { it.execute(COPIES.replace("?", "'$placed'")) }
                if (!connection.autoCommit) connection.commit()
            }
            haulTest(database, signIn = ShildikHarness.signIn) {
                // The first request warms the route; the best of three is the one compared, so a pause of the
                // test's own machine is not read as the route's.
                account(maya, AccountPaths.ORDERS)
                val took =
                    (1..3).minOf {
                        val started = TimeSource.Monotonic.markNow()
                        account(maya, AccountPaths.ORDERS)
                        started.elapsedNow()
                    }
                val first = checkNotNull(account(maya, AccountPaths.ORDERS).only<AccountBody>().history)
                assertEquals(20, first.rows.size)
                assertEquals(placed, first.rows.first().id, "the order on its way comes first")
                assertEquals(count(COPIED + 1), first.filters.single { it.label == "All" }.count)
                assertEquals(count(COPIED), first.filters.single { it.label == "Delivered" }.count)
                val pagination = checkNotNull(first.pagination) { "five thousand orders are more than a page" }
                assertEquals(listOf("1", "2", "3", "…", "251"), pagination.pages)
                assertEquals(
                    LoadAction(Parts.PREFIX + AccountPaths.orders(HistoryFilterKey.All, 2)),
                    pagination.links.single { it.label == "2" }.action,
                )

                val second =
                    checkNotNull(
                        account(maya, AccountPaths.ORDERS + "?page=2").only<AccountBody>().history,
                    )
                assertEquals(20, second.rows.size)
                assertEquals(emptySet(), second.rows.map { it.id }.toSet() intersect first.rows.map { it.id }.toSet())
                assertTrue(took < 300.milliseconds, "the history of ${COPIED + 1} orders took $took")
            }
        }

    private companion object {
        const val COPIED = 5_000

        /**
         * [COPIED] copies of the order `?`, each an hour older than the one before and delivered: its row, its
         * lines and its shipments under ids of their own.
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
            SELECT (jsonb_populate_record(s, jsonb_build_object(
                'id', s.id || '-' || n, 'order_id', s.order_id || '-' || n, 'status', 'delivered'))).*
            FROM shipments s, generate_series(1, $COPIED) n WHERE s.order_id = ?;
            """.trimIndent()
    }
}
