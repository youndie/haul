package io.github.youndie.haul.ops

import ch.qos.logback.classic.Level
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.youndie.haul.haulModule
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.ERROR_ANSWERS_LOG
import io.github.youndie.haul.testing.PostgresHarness
import io.github.youndie.haul.testing.SAGA_CLOCK
import io.github.youndie.haul.testing.SeededDatabase
import io.github.youndie.haul.testing.capturingLog
import io.github.youndie.haul.testing.rawGet
import io.github.youndie.katcher.Katcher
import io.github.youndie.tracy.wire.IngestHeaders
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.util.TreeMap
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * What leaves the server for its watchers (B-122), each read off a fake of the watcher's own endpoint: what
 * the agent posts there is what the real one would file.
 */
class ObservabilityTest {
    /**
     * The stand's start logged «katcher is off» for hours, and tracy — which keeps every `warn` — showed no
     * line of haul-server at all: nothing attached tracy's appender to logback, so no SLF4J line ever left.
     */
    @Test
    fun `a warning logged at start-up is shipped to tracy`() {
        val tracy = FakeEndpoint(accepted = HttpStatusCode.Accepted, answer = """{"accepted":1}""")
        tracy.use {
            testApplication {
                application {
                    haulModule(
                        SeededDatabase.dataSource,
                        CANVAS_NOW,
                        commit = "test",
                        observability = settings(tracy = AgentEndpoint(tracy.url, "test-ingest-key")),
                        sagaClock = SAGA_CLOCK,
                    )
                }
                startApplication()

                val shipped = tracy.awaitBody { "katcher is off" in it }?.lines()?.first { "katcher is off" in it }
                assertNotNull(shipped, "no start-up warning reached tracy; it received: ${tracy.bodies}")
                assertTrue("\"WARN\"" in shipped, shipped)
                assertEquals(SERVICE, tracy.headers.first()[IngestHeaders.SERVICE])
            }
        }
    }

    /**
     * A page whose table is gone — a statement PostgreSQL refuses, through a real route — answers `500` and
     * is filed in katcher with the release it was built from.
     */
    @Test
    fun `a 500 is filed in katcher`() {
        val katcher = FakeEndpoint(accepted = HttpStatusCode.OK, answer = "{}")
        val marker =
            "deals_gone_" +
                UUID
                    .randomUUID()
                    .toString()
                    .replace("-", "")
                    .take(8)
        PostgresHarness.freshDatabase().use { dataSource ->
            dataSource.connection.use {
                it.createStatement().execute("ALTER TABLE deals RENAME TO $marker")
                if (!it.autoCommit) it.commit()
            }
            katcher.use {
                testApplication {
                    application {
                        haulModule(
                            dataSource,
                            CANVAS_NOW,
                            commit = "test",
                            observability = settings(katcher = AgentEndpoint(katcher.url, "test-app-key")),
                            sagaClock = SAGA_CLOCK,
                        )
                    }
                    val response = client.get("/ui/deals")
                    assertEquals(HttpStatusCode.InternalServerError, response.status, response.bodyAsText())
                    assertTrue("\"internal\"" in response.bodyAsText(), response.bodyAsText())

                    val body = katcher.awaitBody { "relation \\\"deals\\\" does not exist" in it }
                    assertNotNull(body, "the 500 was not filed in katcher; it received: ${katcher.bodies}")
                    val report = Json.parseToJsonElement(body).jsonObject
                    assertEquals(RELEASE, report.getValue("release").jsonPrimitive.content)
                    assertEquals("test", report.getValue("environment").jsonPrimitive.content)
                    assertEquals("test-app-key", report.getValue("appKey").jsonPrimitive.content)
                }
            }
        }
    }

    /**
     * katcher gets what the store got wrong, not what a client sent (B-148). On the stand it filed a group per guest's
     * mangled link — the page's address too, answered `200`, since routing still decoded its query after the page
     * had gone. Over a socket to CIO, the stand's engine: Ktor's client cannot send `%zz`. The unexpected `500` at the
     * end is the control, and what says katcher has been sent everything filed before it.
     */
    @Test
    fun `a client's malformed link and an unknown address file nothing in katcher`() {
        val katcher = FakeEndpoint(accepted = HttpStatusCode.OK, answer = "{}")
        val marker = marker()
        val bundle = Files.createTempDirectory("haul-web").toFile().apply { resolve("index.html").writeText("<html/>") }
        katcher.use {
            val server =
                embeddedServer(CIO, port = 0) {
                    haulModule(
                        SeededDatabase.dataSource,
                        CANVAS_NOW,
                        commit = "test",
                        web = bundle,
                        observability = settings(katcher = AgentEndpoint(katcher.url, "test-app-key")),
                        sagaClock = SAGA_CLOCK,
                    )
                    routing { get(BUG) { throw IllegalStateException("unexpected $marker") } }
                }.start(wait = false)
            try {
                katcher.drain()
                val port =
                    runBlocking {
                        server.engine
                            .resolvedConnectors()
                            .first()
                            .port
                    }

                assertEquals(HttpStatusCode.OK.value, rawGet(port, "/p/p-$marker?sku=%zz").status)
                assertEquals(HttpStatusCode.BadRequest.value, rawGet(port, "/ui/p/p-$marker?sku=%zz").status)
                assertEquals(HttpStatusCode.NotFound.value, rawGet(port, "/nowhere-$marker").status)
                assertEquals(HttpStatusCode.InternalServerError.value, rawGet(port, BUG).status)

                assertNotNull(
                    katcher.awaitBody { "unexpected $marker" in it },
                    "the 500 was not filed: ${katcher.bodies}",
                )
                val filed = katcher.drain().filter { marker in it }
                assertEquals(1, filed.size, "katcher was sent a client's request: $filed")
            } finally {
                server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
            }
        }
    }

    /**
     * An outage is said once, not once a page (B-148): every page it fails answers `503` with a line at `info` and no
     * stack, katcher is sent none of them, and the one `warn` with its cause is the pool's. Before, each page logged
     * `ERROR … unhandled` with the whole stack and filed a report — on the stand, a katcher report per page shown.
     */
    @Test
    fun `a database outage answered 503 to every page files nothing and logs no stack per page`() {
        val katcher = FakeEndpoint(accepted = HttpStatusCode.OK, answer = "{}")
        val marker = marker()
        unreachablePool().use { pool ->
            katcher.use {
                capturingLog(ERROR_ANSWERS_LOG, FAIL_FAST_LOG) { lines ->
                    testApplication {
                        application {
                            haulModule(
                                pool,
                                CANVAS_NOW,
                                commit = "test",
                                observability = settings(katcher = AgentEndpoint(katcher.url, "test-app-key")),
                                sagaClock = SAGA_CLOCK,
                            )
                            routing { get(BUG) { throw IllegalStateException("unexpected $marker") } }
                        }
                        startApplication()
                        katcher.drain()

                        repeat(PAGES) {
                            val page = client.get("/ui/home")
                            assertEquals(HttpStatusCode.ServiceUnavailable, page.status, page.bodyAsText())
                        }
                        assertEquals(HttpStatusCode.InternalServerError, client.get(BUG).status)

                        assertNotNull(katcher.awaitBody { "unexpected $marker" in it }, "the 500 was not filed")
                        val others = katcher.drain().filterNot { "unexpected $marker" in it }
                        assertTrue(others.isEmpty(), "the outage was filed in katcher: $others")

                        val answered =
                            lines.filter {
                                it.loggerName == ERROR_ANSWERS_LOG &&
                                    "→ 503" in it.formattedMessage
                            }
                        assertEquals(PAGES, answered.size, lines.map { it.formattedMessage }.toString())
                        answered.forEach {
                            assertEquals(Level.INFO, it.level, it.formattedMessage)
                            assertNull(it.throwableProxy, "a 503 was logged with its stack: ${it.formattedMessage}")
                        }
                        val warnings = lines.filter { it.level == Level.WARN }
                        assertEquals(1, warnings.size, warnings.map { it.formattedMessage }.toString())
                        assertEquals(FAIL_FAST_LOG, warnings.single().loggerName)
                        assertTrue(
                            "127.0.0.1:1 refused" in warnings.single().formattedMessage,
                            warnings.single().formattedMessage,
                        )
                        val errors = lines.filter { it.level == Level.ERROR }
                        assertEquals(listOf("unhandled: GET $BUG"), errors.map { it.formattedMessage })
                    }
                }
            }
        }
    }

    /** A pool whose database is not there: nothing listens on port 1, and a connection is refused at once. */
    private fun unreachablePool() =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:postgresql://127.0.0.1:1/haul"
                driverClassName = "org.postgresql.Driver"
                connectionTimeout = 250
                initializationFailTimeout = -1
                maximumPoolSize = 1
            },
        )

    private fun marker() =
        UUID
            .randomUUID()
            .toString()
            .replace("-", "")
            .take(12)

    private fun settings(
        tracy: AgentEndpoint? = null,
        katcher: AgentEndpoint? = null,
    ) = ObservabilitySettings(
        release = RELEASE,
        environment = "test",
        instance = "observability-test",
        tracy = tracy,
        tracySampleRate = 0.0,
        katcher = katcher,
    )

    /** An HTTP endpoint that keeps every body posted to it, and answers [accepted] with [answer]. */
    private class FakeEndpoint(
        accepted: HttpStatusCode,
        answer: String,
    ) : AutoCloseable {
        val bodies = CopyOnWriteArrayList<String>()
        val headers = CopyOnWriteArrayList<Map<String, String>>()

        private val server: EmbeddedServer<*, *> =
            embeddedServer(CIO, host = "127.0.0.1", port = 0) {
                routing {
                    post("{path...}") {
                        headers +=
                            call.request.headers
                                .entries()
                                .associateTo(TreeMap(String.CASE_INSENSITIVE_ORDER)) { it.key to it.value.first() }
                        bodies += call.receiveText()
                        call.respondText(answer, status = accepted)
                    }
                }
            }.start(wait = false)

        val url: String =
            "http://127.0.0.1:" +
                runBlocking {
                    server.engine
                        .resolvedConnectors()
                        .first()
                        .port
                }

        /**
         * Everything katcher's client still holds sent here, and taken: what was posted so far, the bodies emptied.
         * A report left on disk by an earlier test JVM is delivered on start, so a test drains before it begins.
         */
        fun drain(): List<String> {
            runBlocking { Katcher.flush(WAIT) }
            return bodies.toList().also { bodies.clear() }
        }

        /** The first body posted [matching], waiting up to [WAIT] for the agent's next delivery. */
        fun awaitBody(matching: (String) -> Boolean): String? =
            runBlocking {
                withTimeoutOrNull(WAIT) {
                    var found: String? = null
                    while (found == null) {
                        found = bodies.firstOrNull(matching)
                        if (found == null) delay(100)
                    }
                    found
                }
            }

        override fun close() = server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
    }

    private companion object {
        const val RELEASE = "b122-test-release"
        const val BUG = "/test/bug"
        const val PAGES = 5
        const val FAIL_FAST_LOG = "io.github.youndie.haul.db.FailFastDataSource"
        val WAIT = 15.seconds
    }
}
