package io.github.youndie.haul.feature.identity

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.cart.CartPaths
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.checkout.CheckoutPaths
import io.github.youndie.haul.feature.checkout.IDEMPOTENCY_KEY_HEADER
import io.github.youndie.haul.feature.checkout.PlaceOrderRequest
import io.github.youndie.haul.feature.checkout.data.ExposedCheckoutRepository
import io.github.youndie.haul.feature.identity.data.ExposedCustomers
import io.github.youndie.haul.feature.identity.data.ExposedDemoShoppers
import io.github.youndie.haul.feature.identity.data.ShildikDemoAccounts
import io.github.youndie.haul.feature.identity.domain.DemoShoppers
import io.github.youndie.haul.feature.reviews.ReviewEntry
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.Ledger
import io.github.youndie.haul.testing.ShildikHarness
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.assertError
import io.github.youndie.haul.testing.assertRefresh
import io.github.youndie.haul.testing.guest
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.putLine
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.ui.CheckoutSummary
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.PaymentMethods
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotAction
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

/**
 * B-162's demo shoppers over HTTP, against a running shildik ([ShildikHarness]) — every one is a person made in its
 * realm through its management API and signed in at the storefront's client — and a seeded PostgreSQL of each test's
 * own, since a demo shopper places orders and the sweep gives stock and windows back.
 */
class DemoShopperRoutesTest {
    private val headphones = "${SampleCatalog.SONY_HEADPHONES}-0"
    private val mug = "${SampleCatalog.STONEWARE_MUG}-0"

    private fun withDemo(
        dailyLimit: Int = 50,
        token: String? = null,
        block: suspend HttpClient.(database: DataSource) -> Unit,
    ) = seededFreshDatabase().use { database ->
        val demo = if (token == null) ShildikHarness.demo(dailyLimit) else ShildikHarness.demo(dailyLimit, token)
        haulTest(database, signIn = ShildikHarness.signIn, demo = demo) { block(database) }
    }

    /** «Try as a demo shopper» as the client sends it: `201`, kept by no cache, the tokens in the body. */
    private suspend fun HttpClient.demoShopper(): DemoShopperTokens {
        val response = post(DEMO_SHOPPERS)
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        return haulWireJson.decodeFromString(DemoShopperTokens.serializer(), response.bodyAsText())
    }

    private suspend fun HttpClient.tree(
        path: String,
        token: String? = null,
    ): KompotComponent {
        val response = get(path) { token?.let { bearerAuth(it) } }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return haulWireJson.decodeKompotComponent(response.bodyAsText())
    }

    /** The demo shopper's cart placed as the checkout quotes it by default; the order's id. */
    private suspend fun HttpClient.place(token: String): String {
        val summary = tree(CheckoutPaths.SCREEN, token).only<CheckoutSummary>()
        val response =
            post(assertNotNull(summary.placeUrl)) {
                bearerAuth(token)
                header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                contentType(ContentType.Application.Json)
                setBody(haulWireJson.encodeToString(PlaceOrderRequest.serializer(), PlaceOrderRequest(summary.quote)))
            }
        assertEquals(HttpStatusCode.Accepted, response.status, response.bodyAsText())
        return (
            haulWireJson.decodeKompotAction(
                response.bodyAsText(),
            ) as NavigateAction
        ).deeplink.substringAfterLast('/')
    }

    private suspend fun HttpClient.merge(
        token: String,
        guest: String,
    ) = post(CartPaths.MERGE) {
        bearerAuth(token)
        header(GUEST_HEADER, guest)
    }.assertRefresh()

    private fun sweep(
        database: DataSource,
        at: StoreClock,
    ): Int =
        runBlocking {
            val db = Databases.connect(database)
            DemoShoppers(
                ShildikDemoAccounts(ShildikHarness.signIn, ShildikHarness.demo()),
                ExposedDemoShoppers(db),
                ExposedCustomers(db),
                ExposedCheckoutRepository(db),
                at,
                dailyLimit = 50,
            ).sweep()
        }

    private fun later(by: Duration): StoreClock = StoreClock { CANVAS_NOW.now().plus(by.toJavaDuration()) }

    private fun <T> DataSource.sql(block: Connection.() -> T): T = connection.use { it.block() }

    private fun DataSource.count(
        sql: String,
        id: String,
    ): Int =
        sql {
            prepareStatement(sql).use { statement ->
                statement.setString(1, id)
                statement.executeQuery().use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
        }

    private fun DataSource.product(id: String): Pair<Int, String> =
        sql {
            prepareStatement("SELECT reviews_count, rating::text FROM products WHERE id = ?").use { statement ->
                statement.setString(1, id)
                statement.executeQuery().use { rows ->
                    rows.next()
                    rows.getInt(1) to rows.getString(2)
                }
            }
        }

    /** The header a guest is drawn offers the demo beside «Sign in» while the demo is on, and a customer's offers none. */
    @Test
    fun `a guest's header offers the demo shopper, a customer's does not`() =
        withDemo {
            val guestHeader = tree("/ui/home").all().filterIsInstance<HaulHeader>().single()
            assertEquals(NavigateAction("/demo-shopper"), guestHeader.demo)

            val tokens = demoShopper()
            val customerHeader = tree("/ui/home", tokens.accessToken).all().filterIsInstance<HaulHeader>().single()
            assertNull(customerHeader.demo)
            assertEquals("Demo", customerHeader.customerName)
        }

    @Test
    fun `a server whose demo is off offers none and refuses one with unavailable`() =
        haulTest(signIn = ShildikHarness.signIn) {
            assertNull(
                tree("/ui/home")
                    .all()
                    .filterIsInstance<HaulHeader>()
                    .single()
                    .demo,
            )
            post(DEMO_SHOPPERS).assertError(HttpStatusCode.ServiceUnavailable, ErrorCode.Unavailable)
        }

    /**
     * The item's path: one press makes a customer of the visitor's own, signed in; the guest cart merges into it; the
     * checkout opens at once with the address and Haul Pay chosen; the order places; and a second visitor's demo
     * shopper is a different customer, who sees none of it.
     */
    @Test
    fun `one press signs a visitor in as a customer of their own, ready to check out, and nobody else sees it`() =
        withDemo { database ->
            val guest = guest()
            putLine(guest, headphones, LineChange(quantity = 1)).assertRefresh()

            val first = demoShopper()
            assertNotNull(first.refreshToken, "a demo shopper's sign-in renews as any other's")
            merge(first.accessToken, guest)

            val checkout = tree(CheckoutPaths.SCREEN, first.accessToken)
            val payment = checkout.only<PaymentMethods>().options.single { it.selected }
            assertEquals("haul_pay", payment.id)
            assertTrue("148 Wythe Avenue" in haulWireJson.encodeToString(KompotComponent.serializer(), checkout))
            val order = place(first.accessToken)
            assertEquals(200, get("/ui/account/orders/$order") { bearerAuth(first.accessToken) }.status.value)

            val second = demoShopper()
            assertNotEquals(first.accessToken, second.accessToken)
            get("/ui/account/orders/$order") { bearerAuth(second.accessToken) }
                .assertError(HttpStatusCode.NotFound, ErrorCode.OrderNotFound)
            val history = get("/ui/account/orders") { bearerAuth(second.accessToken) }.bodyAsText()
            assertTrue(order !in history, "the second visitor's orders list the first's: $history")
            assertEquals(2, database.count("SELECT count(*) FROM demo_shoppers WHERE customer_id <> ?", "-"))
        }

    /**
     * After a day a demo shopper is gone with everything they did — the order and what it held (stock, the window),
     * their review with the product's count and rating, the customer — and so is their way in: the store refuses
     * their token and the provider their renewal. One made since stays.
     */
    @Test
    fun `a demo shopper older than a day is swept with everything they left, and their sign-in ends at both ends`() =
        seededFreshDatabase().use { database ->
            val ledger = Ledger(database)
            val stockBefore = ledger.stock(listOf(headphones))
            val mugProduct = SampleCatalog.STONEWARE_MUG
            val productBefore = database.product(mugProduct)
            lateinit var tokens: DemoShopperTokens
            lateinit var sub: String
            haulTest(database, signIn = ShildikHarness.signIn, demo = ShildikHarness.demo()) {
                tokens = demoShopper()
                val guest = guest()
                putLine(guest, headphones, LineChange(quantity = 1)).assertRefresh()
                merge(tokens.accessToken, guest)
                place(tokens.accessToken)
                post("/api/v1/products/$mugProduct/reviews") {
                    bearerAuth(tokens.accessToken)
                    contentType(ContentType.Application.Json)
                    setBody(
                        haulWireJson.encodeToString(
                            ReviewEntry.serializer(),
                            ReviewEntry(
                                1,
                                "Not for me",
                                "The glaze chipped on the first wash, which I did not expect at all.",
                            ),
                        ),
                    )
                }.also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                sub =
                    database.sql {
                        createStatement().use { s ->
                            s.executeQuery("SELECT customer_id FROM demo_shoppers").use { rows ->
                                rows.next()
                                rows.getString(1)
                            }
                        }
                    }
            }
            assertNotEquals(stockBefore, ledger.stock(listOf(headphones)))
            assertNotEquals(productBefore, database.product(mugProduct))
            assertEquals(0, sweep(database, later(23.hours)), "a demo shopper younger than a day is kept")

            assertEquals(1, sweep(database, later(25.hours)))

            assertEquals(0, database.count("SELECT count(*) FROM customers WHERE id = ?", sub))
            assertEquals(0, database.count("SELECT count(*) FROM orders WHERE customer_id = ?", sub))
            assertEquals(0, database.count("SELECT count(*) FROM reviews WHERE customer_id = ?", sub))
            assertEquals(0, ledger.reservedStock())
            assertEquals(stockBefore, ledger.stock(listOf(headphones)))
            assertEquals(productBefore, database.product(mugProduct))
            // A fresh database: the order's window held the only place anybody took.
            assertEquals(
                0,
                database.count("SELECT coalesce(sum(taken), 0)::int FROM delivery_slots WHERE ? IS NOT NULL", sub),
            )
            assertNotEquals(200, ShildikHarness.refreshStatus(assertNotNull(tokens.refreshToken)))

            haulTest(database, signIn = ShildikHarness.signIn, demo = ShildikHarness.demo(), clock = later(25.hours)) {
                get("/ui/account") { bearerAuth(tokens.accessToken) }
                    .assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
                demoShopper()
            }
            assertEquals(0, sweep(database, later(25.hours)), "one made since the sweep stays")
        }

    /** The sweep leaves a demo shopper whose order the saga is still placing: forgetting them would pull rows from under it. */
    @Test
    fun `a demo shopper whose order is still being placed is left for the next sweep`() =
        withDemo { database ->
            demoShopper()
            val sub =
                database.sql {
                    createStatement().use { s ->
                        s.executeQuery("SELECT customer_id FROM demo_shoppers").use { rows ->
                            rows.next()
                            rows.getString(1)
                        }
                    }
                }
            database.sql {
                prepareStatement(
                    """
                    INSERT INTO orders (id, saga_id, customer_id, status, method, payment, items_cents, discount_cents,
                        delivery_cents, total_cents, points, placed_at)
                    VALUES ('HL-1', 'order-placing', ?, 'placing', 'courier', 'card', 100, 0, 0, 100, 0, now())
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, sub)
                    statement.executeUpdate()
                }
            }

            assertEquals(0, sweep(database, later(25.hours)))
            assertEquals(1, database.count("SELECT count(*) FROM customers WHERE id = ?", sub))
        }

    @Test
    fun `past the day's limit the demo shopper is unavailable`() =
        withDemo(dailyLimit = 1) {
            demoShopper()
            post(DEMO_SHOPPERS).assertError(HttpStatusCode.ServiceUnavailable, ErrorCode.Unavailable)
        }

    /** A provider that refuses — here its management API, to a token it does not know — makes nobody on either side. */
    @Test
    fun `a provider that refuses makes no demo shopper here either`() =
        withDemo(token = "not-the-bootstrap-token") { database ->
            post(DEMO_SHOPPERS).assertError(HttpStatusCode.ServiceUnavailable, ErrorCode.Unavailable)
            assertEquals(0, database.count("SELECT count(*) FROM demo_shoppers WHERE customer_id <> ?", "-"))
        }
}
