package io.github.youndie.haul

import io.github.youndie.haul.db.DatabaseConfig
import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.testing.haulTest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

/**
 * B-127: with PostgreSQL stopped under a running server, a page answers `503 unavailable` within five seconds
 * and readiness within its probe's one — over the pool exactly as `main` configures it ([Databases.dataSource]),
 * not one a test tuned. Before B-127 the first readiness took 30 s and the page 90 s and more (the item's findings).
 *
 * Its own PostgreSQL, stopped mid-test: the suite's shared one cannot be.
 */
class DatabaseOutageTest {
    @Test
    fun `a stopped database answers a page in seconds and readiness within its probe's timeout`() {
        val postgres =
            PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:18-alpine")).apply {
                withDatabaseName("haul")
                withUsername("haul")
                withPassword("haul")
                start()
            }
        val dataSource = Databases.dataSource(DatabaseConfig(postgres.jdbcUrl, postgres.username, postgres.password))
        try {
            Databases.migrate(dataSource)
            haulTest(dataSource = dataSource) {
                assertEquals(HttpStatusCode.OK, get("/readyz").status, "the database answers before it is stopped")
                postgres.stop()

                // Nothing has failed yet: readiness gives up on its own, inside the probe's timeout.
                timed("/readyz", "the first ask after the stop", PROBE_TIMEOUT).let {
                    assertEquals(HttpStatusCode.ServiceUnavailable, it.status)
                }
                // The page waits for a connection once, not once per query and per retry.
                timed("/ui/home", "the first page", PAGE_BOUND).let { assertUnavailable(it) }
                timed("/ui/home", "the next page", PAGE_BOUND).let { assertUnavailable(it) }
                timed("/readyz", "after a page failed", PROBE_TIMEOUT).let {
                    assertEquals(HttpStatusCode.ServiceUnavailable, it.status)
                }
            }
        } finally {
            dataSource.close()
            postgres.stop()
        }
    }

    private suspend fun HttpClient.timed(
        path: String,
        which: String,
        bound: Duration,
    ): HttpResponse {
        val (response, took) = measureTimedValue { get(path) }
        println("B-127 outage: $path, $which, answered ${response.status.value} in $took")
        assertTrue(took < bound, "$path took $took, over $bound")
        return response
    }

    private suspend fun assertUnavailable(response: HttpResponse) {
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals(
            ErrorCode.Unavailable,
            haulWireJson.decodeFromString(ErrorBody.serializer(), response.bodyAsText()).code,
        )
    }

    private companion object {
        /** The item's bound for a page. */
        val PAGE_BOUND = 5.seconds

        /** The readiness probe's timeout in `charts/haul/templates/server.yaml`. */
        val PROBE_TIMEOUT = 1.seconds
    }
}
