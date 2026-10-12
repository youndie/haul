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
import io.github.youndie.haul.feature.order.saga.SAGA_CONNECTION_PATIENCE
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
import kotlin.time.TimeMark
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
 *
 * **The outage is the one the saga meets, on the runner's clock** (B-140). It counts from the moment the stop is
 * asked for — the waiting statement dies then, and the pool's first failed connection starts Hikari's tries — to the
 * moment PostgreSQL accepts a connection again, measured, not assumed: how long Docker takes to stop and start the
 * container is the runner's, and on a hosted runner busy with the rest of the build it was enough to push a «five
 * second» outage past the member's ten second phase. Each case checks the outage it actually got against that
 * phase before it checks the saga, so a runner that could not stage the outage says so, and «rode it out» and
 * «undone» stay told apart by the line itself ([SAGA_CONNECTION_PATIENCE]), not by a sleep.
 */
class PlacementOutageTest {
    /** Five seconds down mid-member: the member waits within its phase, and the order is placed. */
    @Test
    fun `a placement rides out a five second outage and the order is placed`() =
        placingThroughAnOutage(5.seconds) {
            assertTrue(
                outage.back < SAGA_CONNECTION_PATIENCE,
                "the runner kept PostgreSQL away as long as the member's phase, the outage is not a short one: $outage",
            )
            assertEquals(HttpStatusCode.Accepted, placed.status, "$outage: ${placed.bodyAsText()}")
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
            assertTrue(
                outage.back > SAGA_CONNECTION_PATIENCE,
                "the database was back within the member's phase: $outage",
            )
            assertEquals(HttpStatusCode.InternalServerError, placed.status, "$outage: ${placed.bodyAsText()}")
            assertEquals("FAILED", ledger.sagaStatus())
            assertEquals(0, ledger.reservedStock(), "the stock is still held")
            assertEquals(stockBefore, ledger.stock(), "the stock did not come back")
            assertEquals(0, ledger.orders())
        }

    /** What a placement through an outage left: its answer, the outage it met, and the rows. */
    private class Placed(
        val placed: HttpResponse,
        val outage: Outage,
        val ledger: Ledger,
        val stockBefore: Map<String, Int>,
        val database: DataSource,
    )

    private fun placingThroughAnOutage(
        outage: Duration,
        check: suspend Placed.() -> Unit,
    ) = StoppablePostgres().use { postgres ->
        val token = ShildikHarness.accessToken(ShildikHarness.person("Maya Kowalski", SampleCustomers.MAYA))
        val stockBefore = Ledger(postgres.dataSource).stock()
        haulTest(postgres.dataSource, signIn = ShildikHarness.signIn) {
            val summary = checkout(token)
            val placed =
                coroutineScope {
                    val locks = withContext(Dispatchers.IO) { postgres.lockSkus() }
                    val placing = async { measureTimedValue { place(token, summary) } }
                    val stopping =
                        withContext(Dispatchers.IO) {
                            postgres.awaitWaitingOnALock(locks)
                            postgres.stop()
                        }
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

                    val met = withContext(Dispatchers.IO) { postgres.startAfter(stopping, outage) }
                    val (placed, placingTook) = placing.await()
                    println(
                        "B-139 outage: ${outage.inWholeSeconds} s asked, $met; placement answered " +
                            "${placed.status.value} ${stopping.began.elapsedNow()} after the stop began, in $placingTook",
                    )
                    assertTrue(placingTook < SHOPPER_BOUND, "the placement took $placingTook ($met)")
                    placed to met
                }
            Placed(placed.first, placed.second, Ledger(postgres.dataSource), stockBefore, postgres.dataSource).check()
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

        /** Stops the container; the outage begins when the stop is asked for, not when Docker is done with it. */
        fun stop(): Stopping {
            val began = TimeSource.Monotonic.markNow()
            container.dockerClient.stopContainerCmd(container.containerId).exec()
            return Stopping(began, began.elapsedNow())
        }

        /**
         * Starts the container again [outage] after [stopping] began, and waits for PostgreSQL to accept a
         * connection: the outage the saga met is measured to that moment.
         */
        fun startAfter(
            stopping: Stopping,
            outage: Duration,
        ): Outage {
            val rest = outage - stopping.began.elapsedNow()
            if (rest.isPositive()) Thread.sleep(rest.inWholeMilliseconds)
            val startAsked = stopping.began.elapsedNow()
            container.dockerClient.startContainerCmd(container.containerId).exec()
            while (true) {
                val accepted =
                    try {
                        DriverManager.getConnection(container.jdbcUrl, container.username, container.password).close()
                        true
                    } catch (_: SQLException) {
                        false
                    }
                val since = stopping.began.elapsedNow()
                if (accepted) return Outage(stopping.took, startAsked, since)
                check(since < startAsked + BACK_BOUND) { "PostgreSQL was not back $BACK_BOUND after its start" }
                Thread.sleep(POLL.inWholeMilliseconds)
            }
        }

        override fun close() {
            dataSource.close()
            container.stop()
        }
    }

    /** When the stop was asked for, and how long Docker took to carry it out. */
    private class Stopping(
        val began: TimeMark,
        val took: Duration,
    )

    /** The outage as the runner staged it, each time counted from the moment the stop was asked for. */
    private class Outage(
        val stopTook: Duration,
        val startAsked: Duration,
        val back: Duration,
    ) {
        override fun toString() =
            "the stop took $stopTook, the start was asked for at $startAsked, PostgreSQL accepted connections again at $back"
    }

    private companion object {
        /** B-127's bound for a page. */
        val PAGE_BOUND = 5.seconds

        /** What the shopper who pressed «Place order» must not wait (B-139). */
        val SHOPPER_BOUND = 30.seconds

        val POLL = 50.milliseconds

        /** The most a started PostgreSQL may take to accept connections before the test gives up on the runner. */
        val BACK_BOUND = 30.seconds
    }
}
