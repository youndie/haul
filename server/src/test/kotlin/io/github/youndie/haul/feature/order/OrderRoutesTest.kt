package io.github.youndie.haul.feature.order

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.feature.cart.CartPaths
import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.testing.FulfilmentWorld
import io.github.youndie.haul.testing.MAYAS_SKUS
import io.github.youndie.haul.testing.ShildikHarness
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.assertError
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.ui.CartLine
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.OrderBody
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotAction
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.PresentAction
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * The order's page and its reorder over HTTP (feature-orders, endpoint-orders), against shildik and a
 * seeded PostgreSQL of each test's own. The orders are placed and moved by the production graph on a
 * clock the test holds ([FulfilmentWorld]); the routes read them back as Maya — or as Sam, whose they are not.
 */
class OrderRoutesTest {
    private val maya by lazy {
        ShildikHarness.accessToken(
            ShildikHarness.person("Maya Kowalski", SampleCustomers.MAYA),
        )
    }
    private val sam by lazy { ShildikHarness.accessToken(ShildikHarness.person("Sam Ortiz", SampleCustomers.SAM)) }

    /** Maya's cart placed in a database of its own, moved [moved] along by the simulator, then [block] over HTTP. */
    private fun placed(
        choice: CheckoutChoice = CheckoutChoice(slotId = "2025-10-08T15"),
        moved: Duration? = null,
        block: suspend HttpClient.(orderId: String, database: DataSource) -> Unit,
    ) = seededFreshDatabase().use { database ->
        val orderId =
            FulfilmentWorld(database).use { world ->
                world.place(choice).also { id ->
                    moved?.let {
                        world.advance(Duration.ZERO)
                        world.advance(it)
                    }
                    checkNotNull(world.track(id))
                }
            }
        haulTest(database, signIn = ShildikHarness.signIn) { block(orderId, database) }
    }

    private suspend fun HttpClient.page(
        token: String,
        orderId: String,
    ): HttpResponse = get(OrderPaths.SCREEN.replace("{id}", orderId)) { bearerAuth(token) }

    private suspend fun HttpClient.tree(
        token: String,
        path: String,
    ): KompotComponent {
        val response = get(path) { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return haulWireJson.decodeKompotComponent(response.bodyAsText())
    }

    private suspend fun HttpClient.reorder(
        token: String,
        url: String,
    ): HttpResponse = post(url) { bearerAuth(token) }

    /** The cart as Maya's page draws it: each SKU's quantity and whether it is selected. */
    private suspend fun HttpClient.cartOf(token: String): List<Triple<String, Int, Boolean>> =
        tree(token, CartPaths.SCREEN)
            .all()
            .filterIsInstance<CartLine>()
            .map { Triple(it.skuId, it.quantity, it.selected) }

    /**
     * Where placement lands (scenario «Place by courier», feature-checkout): its `navigate` is this page's
     * address, and the page is the order Maya just placed — its number, the thanks, both shipments placed —
     * under a header whose «Orders» goes to her orders.
     */
    @Test
    fun `the page placement lands on is the order just placed`() =
        placed { orderId, _ ->
            val tree = tree(maya, "/ui" + OrderPaths.page(orderId))
            val body = tree.only<OrderBody>()
            assertEquals("Thanks, Maya — order #$orderId is placed", body.title)
            assertEquals(listOf("Account", "Orders", "#$orderId"), body.crumbs.map { it.label })
            assertEquals(listOf("Placed", "Placed"), body.shipments.map { it.status })
            assertEquals(0, body.steps?.current)
            assertEquals(NavigateAction(Frame.ORDERS), tree.only<HaulHeader>().orders)
            assertNull(body.summary.reorderUrl, "an order on its way is not reordered from its page")
        }

    /**
     * «Not yours» is «not there» (feature-orders, research §5): Sam asking for Maya's order and Maya asking
     * for an order nobody placed get one answer, `404 order_not_found`, and the page drawn for neither; with
     * no token at all it is `401`.
     */
    @Test
    fun `another customer's order and a missing one get the same answer`() =
        placed { orderId, _ ->
            val foreign = page(sam, orderId).assertError(HttpStatusCode.NotFound, ErrorCode.OrderNotFound)
            val missing = page(maya, "HL-99999").assertError(HttpStatusCode.NotFound, ErrorCode.OrderNotFound)
            assertEquals(foreign.field, missing.field)
            assertEquals(foreign.fields, missing.fields)
            get(
                OrderPaths.SCREEN.replace("{id}", orderId),
            ).assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
            reorder(sam, OrderPaths.reorder(orderId)).assertError(HttpStatusCode.NotFound, ErrorCode.OrderNotFound)
            assertEquals(emptyList(), cartOf(sam), "Sam's cart got Maya's order")
        }

    /**
     * Scenario «Reorder» (feature-orders, B-18): a delivered order's page offers «Write a review» on each line —
     * the review dialog (B-22) — and «Reorder», which puts the
     * order's SKUs back into the cart — emptied by placement — selected, and answers `navigate` to the cart.
     * A second press is the same reorder: nothing is added twice.
     */
    @Test
    fun `reorder puts a delivered order back into the cart and twice is once`() =
        placed(moved = 10.days) { orderId, _ ->
            val body = tree(maya, "/ui" + OrderPaths.page(orderId)).only<OrderBody>()
            assertEquals(listOf("Delivered", "Delivered"), body.shipments.map { it.status })
            val url = assertNotNull(body.summary.reorderUrl, "a delivered order offers no reorder")
            val reviews = body.shipments.flatMap { it.items }.map { it.review?.action }
            assertTrue(
                reviews.all { it is PresentAction },
                "a delivered line's «Write a review» is not the dialog: $reviews",
            )
            assertEquals(emptyList(), cartOf(maya), "placement left the cart as it was")

            repeat(2) { press ->
                val answer = reorder(maya, url)
                assertEquals(HttpStatusCode.OK, answer.status, answer.bodyAsText())
                assertEquals(NavigateAction(Frame.CART), haulWireJson.decodeKompotAction(answer.bodyAsText()))
                assertEquals(MAYAS_SKUS.map { Triple(it, 1, true) }, cartOf(maya), "after press ${press + 1}")
            }
        }

    /**
     * Scenario «Declined card cancels and releases» (feature-orders), as its page shows it: cancelled, the
     * card named, nothing charged, and the way back to the cart — no steps and no reorder.
     */
    @Test
    fun `a declined order says so and leads back to the cart`() =
        placed(CheckoutChoice(slotId = "2025-10-08T15", payment = "card-0002")) { orderId, _ ->
            val body = tree(maya, "/ui" + OrderPaths.page(orderId)).only<OrderBody>()
            assertEquals("Order cancelled", body.title)
            assertEquals("Your card ···· 0002 was declined", body.notice?.title)
            assertEquals(Link("Back to cart", NavigateAction(Frame.CART)), body.summary.back)
            assertEquals(true, body.summary.voided)
            assertNull(body.steps)
            assertEquals(listOf("Not shipped", "Not shipped"), body.shipments.map { it.eta })
        }
}
