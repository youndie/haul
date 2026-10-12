package io.github.youndie.haul.feature.order

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import com.zaxxer.hikari.HikariDataSource
import io.github.youndie.haul.ErrorBody
import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.db.DatabaseConfig
import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.checkout.CheckoutPaths
import io.github.youndie.haul.feature.checkout.IDEMPOTENCY_KEY_HEADER
import io.github.youndie.haul.feature.checkout.PlaceOrderRequest
import io.github.youndie.haul.feature.order.data.ExposedOrders
import io.github.youndie.haul.feature.order.domain.OrderStatus
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.seed.Seeder
import io.github.youndie.haul.testing.Ledger
import io.github.youndie.haul.testing.MAYAS_SKUS
import io.github.youndie.haul.testing.ShildikHarness
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.ui.CheckoutSummary
import io.github.youndie.kompot.decodeKompotAction
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.standard.NavigateAction
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.net.ServerSocket
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.time.measureTimedValue

/**
 * B-139: the order saga rides out a short database outage, and a page in the same outage still fails fast (B-127).
 *
 * PostgreSQL is stopped **while the saga is inside a member**: the test holds Maya's SKU rows locked, so
 * `reserve-stock`'s update waits on them; once PostgreSQL shows that wait, the container is stopped — the waiting
 * statement dies with the server, as any statement in flight does — and started again after the outage. The pool is
 * the one `main` builds ([Databases.dataSource]), the application the one `main` assembles, over HTTP.
 *
 * Its own PostgreSQL, bound to a host port chosen before it starts, so the server is found at the same address
 * when it comes back; the suite's shared one cannot be stopped.
 */
class PlacementOutageTest {
    /** Five seconds down mid-member: the member waits within its phase, and the order is placed. */
    @Test
    fun `a placement rides out a five second outage and the order is placed`() =
        placingThroughAnOutage(5.seconds) {
            assertEquals(HttpStatusCode.Accepted, placed.status, placed.bodyAsText())
            val deeplink = (haulWireJson.decodeKompotAction(placed.bodyAsText()) as NavigateAction).deeplink
            val orderId = deeplink.removePrefix("/account/orders/")
            val order = assertNotNull(runBlocking { ExposedOrders(Databases.connect(database)).order(orderId) })
            assertEquals(OrderStatus.Placed, order.status)
            assertEquals("COMPLETED", ledger.sagaStatus())
            assertEquals(stockBefore.mapValues { it.value - 1 }, ledger.stock(), "one unit of each line reserved")
        }

    /**
     * Down longer than the member's phase (EXECUTION, 10 s): the member gives up, and the rollback — which waits for
     * the database coming back — undoes the saga, as a failed member is undone. The shopper hears in under 30 s.
     */
    @Test
    fun `a placement whose database stays away past the phase is undone`() =
        placingThroughAnOutage(12.seconds) {
            assertEquals(HttpStatusCode.InternalServerError, placed.status, placed.bodyAsText())
            assertEquals("FAILED", ledger.sagaStatus())
            assertEquals(0, ledger.reservedStock(), "the stock is still held")
            assertEquals(stockBefore, ledger.stock(), "the stock did not come back")
            assertEquals(0, ledger.orders())
        }

    /** What a placement through an outage left: its answer, and the rows. */
    private class Placed(
        val placed: HttpResponse,
        val ledger: Ledger,
        val stockBefore: Map<String, Int>,
        val database: DataSource,
    )

    private fun placingThroughAnOutage(
        outage: Duration,
        check: suspend (
            placed: HttpResponse,
            ledger: Ledger,
            stockBefore: Map<String, Int>,
            database: DataSource,
        ) -> Unit,
    ) = StoppablePostgres().use { postgres ->
        val token = ShildikHarness.accessToken(ShildikHarness.person("Maya Kowalski", SampleCustomers.MAYA))
        val stockBefore = Ledger(postgres.dataSource).stock()
        haulTest(postgres.dataSource, signIn = ShildikHarness.signIn) {
            val summary = checkout(token)
            val placed =
                coroutineScope {
                    val locks = withContext(Dispatchers.IO) { postgres.lockSkus() }
                    val placing = async { measureTimedValue { place(token, summary) } }
                    withContext(Dispatchers.IO) {
                        postgres.awaitWaitingOnALock(locks)
                        postgres.stop()
                    }
                    val stopped = TimeSource.Monotonic.markNow()
                    try {
                        locks.close()
                    } catch (_: SQLException) {
                        // Its server is gone, and the connection with it.
                    }

                    // B-127's bound holds for a page in the same outage: the saga's waiting does not spread to it.
                    val (page, took) = measureTimedValue { get("/ui/home") }
                    println("B-139 outage: /ui/home during the outage answered ${page.status.value} in $took")
                    assertEquals(HttpStatusCode.ServiceUnavailable, page.status)
                    assertEquals(
                        ErrorCode.Unavailable,
                        haulWireJson.decodeFromString(ErrorBody.serializer(), page.bodyAsText()).code,
                    )
                    assertTrue(took < PAGE_BOUND, "a page took $took during the outage, over $PAGE_BOUND")

                    withContext(Dispatchers.IO) {
                        (outage - stopped.elapsedNow()).takeIf { it.isPositive() }?.let {
                            Thread.sleep(it.inWholeMilliseconds)
                        }
                        postgres.start()
                    }
                    val (placed, placingTook) = placing.await()
                    println(
                        "B-139 outage: ${outage.inWholeSeconds} s down, placement answered ${placed.status.value} " +
                            "${stopped.elapsedNow()} after the stop, in $placingTook",
                    )
                    assertTrue(placingTook < SHOPPER_BOUND, "the placement took $placingTook")
                    placed
                }
            Placed(placed, Ledger(postgres.dataSource), stockBefore, postgres.dataSource).check()
        }
    }

    private suspend fun HttpClient.checkout(token: String): CheckoutSummary {
        val response = get(CheckoutPaths.SCREEN) { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return haulWireJson.decodeKompotComponent(response.bodyAsText()).only<CheckoutSummary>()
    }

    private suspend fun HttpClient.place(
        token: String,
        summary: CheckoutSummary,
    ): HttpResponse =
        post(assertNotNull(summary.placeUrl)) {
            bearerAuth(token)
            header(IDEMPOTENCY_KEY_HEADER, "maya-outage")
            contentType(ContentType.Application.Json)
            setBody(haulWireJson.encodeToString(PlaceOrderRequest.serializer(), PlaceOrderRequest(summary.quote)))
        }

    /** A PostgreSQL of the test's own that can be stopped and started again at the same address. */
    private class StoppablePostgres : AutoCloseable {
        private val port = ServerSocket(0).use { it.localPort }
        private val container =
            PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:18-alpine")).apply {
                withDatabaseName("haul")
                withUsername("haul")
                withPassword("haul")
                withCreateContainerCmdModifier { command ->
                    command.hostConfig!!.withPortBindings(
                        PortBinding(Ports.Binding.bindPort(port), ExposedPort.tcp(PostgreSQLContainer.POSTGRESQL_PORT)),
                    )
                }
                start()
            }

        val dataSource: HikariDataSource =
            Databases.dataSource(DatabaseConfig(container.jdbcUrl, container.username, container.password)).also {
                Databases.migrate(it)
                check(
                    Seeder.seedIfEmpty(
                        Databases.connect(it),
                        CatalogSeed.generate(CatalogSeed.CANVAS_DAY),
                        generatedReviews = false,
                    ),
                )
            }

        /** Maya's SKU rows, locked by a transaction of the test's that stays open: `reserve-stock` waits on them. */
        fun lockSkus(): Connection =
            DriverManager.getConnection(container.jdbcUrl, container.username, container.password).apply {
                autoCommit = false
                prepareStatement("SELECT id FROM skus WHERE id = ANY (?) FOR UPDATE").use {
                    it.setArray(1, createArrayOf("text", MAYAS_SKUS.toTypedArray()))
                    it.executeQuery().close()
                }
            }

        fun awaitWaitingOnALock(on: Connection) {
            val since = TimeSource.Monotonic.markNow()
            while (true) {
                val waiting =
                    on.createStatement().use { statement ->
                        // The activity is read once per transaction, and this one stays open: read it afresh.
                        statement.execute("SELECT pg_stat_clear_snapshot()")
                        statement
                            .executeQuery("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'")
                            .use { it.next() && it.getInt(1) > 0 }
                    }
                if (waiting) return
                check(since.elapsedNow() < 10.seconds) { "the saga never waited on the locked SKUs" }
                Thread.sleep(POLL.inWholeMilliseconds)
            }
        }

        fun stop() {
            container.dockerClient.stopContainerCmd(container.containerId).exec()
        }

        fun start() {
            container.dockerClient.startContainerCmd(container.containerId).exec()
        }

        override fun close() {
            dataSource.close()
            container.stop()
        }
    }

    private companion object {
        /** B-127's bound for a page. */
        val PAGE_BOUND = 5.seconds

        /** What the shopper who pressed «Place order» must not wait (B-139). */
        val SHOPPER_BOUND = 30.seconds

        val POLL = 50.milliseconds
    }
}
