package io.github.youndie.haul.ops

import io.github.youndie.haul.databaseAnswers
import io.github.youndie.haul.testing.PostgresHarness
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class ProbesTest {
    @Test
    fun `readiness answers 200 while the database answers`() =
        testApplication {
            val dataSource = PostgresHarness.freshDatabase()
            application { routing { probes(commit = "abc1234", ready = { databaseAnswers(dataSource) }) } }

            assertEquals(HttpStatusCode.OK, client.get("/readyz").status)
            assertEquals("""{"commit":"abc1234"}""", client.get("/version").bodyAsText())
        }

    /**
     * A database that is gone makes the server not ready, and only not ready: liveness must stay
     * 200, or the orchestrator restarts a process that a restart cannot fix.
     */
    @Test
    fun `a closed database makes readiness 503 and leaves liveness 200`() =
        testApplication {
            val dataSource = PostgresHarness.freshDatabase()
            dataSource.close()
            application { routing { probes(commit = "dev", ready = { databaseAnswers(dataSource) }) } }

            assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/readyz").status)
            assertEquals(HttpStatusCode.OK, client.get("/healthz").status)
        }
}
