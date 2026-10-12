package io.github.youndie.haul.ops

import io.github.youndie.haul.haulModule
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.PostgresHarness
import io.github.youndie.haul.testing.SAGA_CLOCK
import io.github.youndie.haul.testing.SeededDatabase
import io.github.youndie.tracy.wire.IngestHeaders
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.TreeMap
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
        val WAIT = 15.seconds
    }
}
