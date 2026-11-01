package io.github.youndie.haul.feature.order

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.feature.cart.CartPaths
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.cart.domain.CartCommands
import io.github.youndie.haul.feature.checkout.AddressEntry
import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.checkout.CheckoutPaths
import io.github.youndie.haul.feature.identity.GUEST_HEADER
import io.github.youndie.haul.feature.reviews.ReviewEntry
import io.github.youndie.haul.feature.reviews.ReviewPaths
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.SampleCatalog.SONORA_HEADPHONES
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.testing.FulfilmentWorld
import io.github.youndie.haul.testing.MAYA
import io.github.youndie.haul.testing.MAYAS_SKUS
import io.github.youndie.haul.testing.ShildikHarness
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.assertError
import io.github.youndie.haul.testing.guest
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.liveTree
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.ui.CartLine
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.OrderBody
import io.github.youndie.haul.ui.OrderFactKind
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotAction
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.standard.MessageLevel
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.PresentAction
import io.github.youndie.kompot.standard.SequenceAction
import io.github.youndie.kompot.standard.ShowMessageAction
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
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
        cart: suspend FulfilmentWorld.() -> Unit = {},
        block: suspend HttpClient.(orderId: String, database: DataSource) -> Unit,
    ) = seededFreshDatabase().use { database ->
        val orderId =
            FulfilmentWorld(database).use { world ->
                runBlocking { world.cart() }
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

    /** The order's page as [token]'s customer gets it: the tree, out of its envelope naming the order's channel. */
    private suspend fun HttpClient.order(
        token: String,
        orderId: String,
    ): KompotComponent {
        val response = page(token, orderId)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return liveTree(response.bodyAsText(), "order:$orderId")
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
            val tree = order(maya, orderId)
            val body = tree.only<OrderBody>()
            assertEquals("Thanks, Maya — order #$orderId is placed", body.title)
            assertEquals(listOf("Account", "Orders", "#$orderId"), body.crumbs.map { it.label })
            assertEquals(listOf("Placed", "Placed"), body.shipments.map { it.status })
            assertEquals(0, body.steps?.current)
            assertEquals(NavigateAction(Frame.ORDERS), tree.only<HaulHeader>().orders)
            assertNull(body.summary.reorderUrl, "an order on its way is not reordered from its page")
        }

    /**
     * The address a past order went to is the order's own (B-40): Maya editing her saved address after
     * placing — the checkout's form edits it in place — leaves the order's page where the order was sent.
     */
    @Test
    fun `an address edited after placement does not move a past order`() =
        placed { orderId, _ ->
            val edit =
                post(CheckoutPaths.ADDRESSES) {
                    bearerAuth(maya)
                    contentType(ContentType.Application.Json)
                    setBody(
                        haulWireJson.encodeToString(
                            AddressEntry.serializer(),
                            AddressEntry(street = "1 Bedford Avenue", city = "Brooklyn, NY", zip = "11249"),
                        ),
                    )
                }
            assertEquals(HttpStatusCode.OK, edit.status, edit.bodyAsText())
            val body = order(maya, orderId).only<OrderBody>()
            val place = body.summary.facts.single { it.kind == OrderFactKind.Place }
            assertEquals("148 Wythe Avenue, Apt 4F", place.title)
            assertEquals("Brooklyn, NY 11211 · courier", place.detail)
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
            // A guest is not a customer: the same `unauthenticated`, on which the client keeps its guest (B-115).
            get(OrderPaths.SCREEN.replace("{id}", orderId)) { header(GUEST_HEADER, guest()) }
                .assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
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
            val body = order(maya, orderId).only<OrderBody>()
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
     * B-99: a delivered line of a product the customer has reviewed offers their review — the product's reviews
     * tab, where it leads the list — and not a second «Write a review» whose Post answers `409 review_exists`.
     * The other lines still offer the dialog.
     */
    @Test
    fun `a delivered line the customer has reviewed leads to their review`() =
        placed(moved = 10.days) { orderId, _ ->
            val posted =
                post(ReviewPaths.reviews(SONORA_HEADPHONES)) {
                    bearerAuth(maya)
                    contentType(ContentType.Application.Json)
                    setBody(
                        haulWireJson.encodeToString(
                            ReviewEntry.serializer(),
                            ReviewEntry(5, "Quiet at last", "The noise cancelling makes the commute bearable."),
                        ),
                    )
                }
            assertEquals(HttpStatusCode.Created, posted.status, posted.bodyAsText())
            val links =
                order(maya, orderId)
                    .only<OrderBody>()
                    .shipments
                    .flatMap { it.items }
                    .mapNotNull { it.review }
            assertEquals(
                listOf(Link("Your review", NavigateAction("/p/$SONORA_HEADPHONES?sku=$SONORA_HEADPHONES-0&tab=reviews"))),
                links.filter { it.label == "Your review" },
            )
            assertEquals(
                listOf(true, true),
                links.filter { it.label == "Write a review" }.map { it.action is PresentAction },
            )
        }

    /**
     * B-101: a reorder says what it could not put back. Maya's order of two pairs of headphones, the duvet cover and
     * the mugs: with none of the three left, nothing goes in and the answer is a message alone — she stays on the
     * order instead of landing on an empty cart; with one pair left and no mugs, the cart takes one pair and the
     * duvet cover, and the answer is the cart with a message naming the mugs and the pair that did not go in.
     */
    @Test
    fun `a reorder names what it could not put back`() =
        placed(
            moved = 10.days,
            cart = {
                val sonora = ledger.cartLines(SampleCustomers.MAYA).first()
                koin.get<CartCommands>().changeLine(MAYA, sonora, LineChange(quantity = 2))
            },
        ) { orderId, database ->
            val (sonora, duvet, mug) = database.titles(orderId)
            val url = OrderPaths.reorder(orderId)
            MAYAS_SKUS.forEach { database.sql("UPDATE skus SET stock = 0 WHERE id = '$it'") }

            val none = reorder(maya, url)
            assertEquals(HttpStatusCode.OK, none.status, none.bodyAsText())
            assertEquals(
                ShowMessageAction(
                    "Nothing was added: $sonora, $duvet and $mug aren’t available now.",
                    MessageLevel.ERROR,
                ),
                haulWireJson.decodeKompotAction(none.bodyAsText()),
            )
            assertEquals(emptyList(), cartOf(maya), "a reorder of nothing in stock put something in")

            database.sql("UPDATE skus SET stock = 1 WHERE id = '${MAYAS_SKUS[0]}'")
            database.sql("UPDATE skus SET stock = 20 WHERE id = '${MAYAS_SKUS[1]}'")
            val some = reorder(maya, url)
            assertEquals(HttpStatusCode.OK, some.status, some.bodyAsText())
            assertEquals(
                SequenceAction(
                    listOf(
                        NavigateAction(Frame.CART),
                        ShowMessageAction(
                            "Added what was in stock — $mug isn’t available now; $sonora: only 1 of 2 went in.",
                            MessageLevel.ERROR,
                        ),
                    ),
                ),
                haulWireJson.decodeKompotAction(some.bodyAsText()),
            )
            assertEquals(
                listOf(Triple(MAYAS_SKUS[0], 1, true), Triple(MAYAS_SKUS[1], 1, true)),
                cartOf(maya),
            )
        }

    /** The titles [orderId]'s lines were bought under, in the order's order: what a reorder's answer names. */
    private fun DataSource.titles(orderId: String): List<String> =
        connection.use { c ->
            c.prepareStatement("SELECT title FROM order_lines WHERE order_id = ? ORDER BY position").use { statement ->
                statement.setString(1, orderId)
                statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
            }
        }

    private fun DataSource.sql(statement: String) {
        connection.use { c ->
            c.createStatement().use { it.executeUpdate(statement) }
            c.commit()
        }
    }

    /**
     * B-108: an order placed with a promo code draws the summary's rows as the cart and checkout drew them —
     * «Discount» the sale alone, «Promo · AUTUMN10» beside it — and they add up to the total. The order used to
     * draw the two as one «Discount».
     */
    @Test
    fun `an order placed with a promo code draws its promo row`() =
        placed(cart = { koin.get<CartCommands>().applyPromo(MAYA, "AUTUMN10") }) { orderId, _ ->
            val summary = order(maya, orderId).only<OrderBody>().summary
            val rows = summary.rows.associate { it.label to it.value }
            assertEquals(listOf("Items (3)", "Discount", "Promo · AUTUMN10", "Delivery"), summary.rows.map { it.label })
            assertEquals("−$140.00", rows["Discount"], "the sale alone: $449 → $349 and $179 → $139")
            assertEquals("−$50.00", rows["Promo · AUTUMN10"], "10 % of $512, capped at $50")
            assertEquals("$462.00", summary.total)
        }

    /**
     * Scenario «Declined card cancels and releases» (feature-orders), as its page shows it: cancelled, the
     * card named, nothing charged, and the way back to the cart — no steps and no reorder.
     */
    @Test
    fun `a declined order says so and leads back to the cart`() =
        placed(CheckoutChoice(slotId = "2025-10-08T15", payment = "card-0002")) { orderId, _ ->
            val body = order(maya, orderId).only<OrderBody>()
            assertEquals("Order cancelled", body.title)
            assertEquals("Your card ···· 0002 was declined", body.notice?.title)
            // Once, in the notice: the summary drew a second «Back to cart» beside it (B-108).
            assertEquals(Link("Back to cart", NavigateAction(Frame.CART)), body.notice?.action)
            assertEquals(true, body.summary.voided)
            assertNull(body.steps)
            assertEquals(listOf("Not shipped", "Not shipped"), body.shipments.map { it.eta })
        }
}
