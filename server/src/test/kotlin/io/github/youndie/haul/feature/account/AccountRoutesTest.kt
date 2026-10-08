package io.github.youndie.haul.feature.account

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.order.OrderPaths
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.testing.FulfilmentWorld
import io.github.youndie.haul.testing.ShildikHarness
import io.github.youndie.haul.testing.assertError
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.ui.AccountBody
import io.github.youndie.haul.ui.AccountTileKind
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.HistoryStatusKind
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

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
     * The tiles the overview draws from sources B-23 and B-20 will own: Maya's points, membership and
     * price drops are the canvas's (research §6) until then; Sam, no member, is offered the trial, whose
     * button starts nothing yet.
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
            assertEquals("Plus member since 2023", mayas.profile.subtitle)
            assertEquals("48", mayas.menu.single { it.label == "Saved" }.count)

            val sams = account(sam).only<AccountBody>()
            val offer = sams.tiles.single { it.kind == AccountTileKind.PlusOffer }
            assertEquals("Try 30 days free", offer.button?.label)
            assertNull(offer.button?.action, "the trial is B-23's")
            assertEquals(listOf("0", "30 days free", "0"), sams.tiles.map { it.figure })
        }

    /**
     * The history's filter is the address's `status` (screen-account, Orders): the chips count every order
     * and go to their own addresses, each address draws only its orders, and a `status` the history does not
     * have is all of them. A delivered order reorders through the order page's command (B-18); a declined
     * one leads to its page.
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
                    NavigateAction("/account/orders"),
                    NavigateAction("/account/orders?status=active"),
                    NavigateAction("/account/orders?status=delivered"),
                    NavigateAction("/account/orders?status=returned"),
                    NavigateAction("/account/orders?status=cancelled"),
                ),
                all.filters.map { it.action },
            )
            assertEquals(setOf(declined, delivered), all.rows.map { it.id }.toSet())

            val cancelled =
                checkNotNull(account(maya, "${AccountPaths.ORDERS}?status=cancelled").only<AccountBody>().history)
            assertEquals(listOf("Cancelled"), cancelled.filters.filter { it.selected }.map { it.label })
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

            val active = checkNotNull(account(maya, "${AccountPaths.ORDERS}?status=active").only<AccountBody>().history)
            assertEquals(emptyList(), active.rows)
            assertEquals("No active orders.", active.none)

            val unknown = checkNotNull(account(maya, "${AccountPaths.ORDERS}?status=lost").only<AccountBody>().history)
            assertEquals(2, unknown.rows.size, "a status the history does not have is all of them")
            assertEquals(listOf("All"), unknown.filters.filter { it.selected }.map { it.label })

            assertEquals(emptyList(), checkNotNull(account(sam, AccountPaths.ORDERS).only<AccountBody>().history).rows)
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
}
