package io.github.youndie.haul

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.SAGA_CLOCK
import io.github.youndie.haul.testing.SeededDatabase
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.sql.SQLException
import java.sql.SQLTransientConnectionException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the catch-all answers (B-32): a code always with the status `status(code)` gives it, a bug
 * apart from an outage, a malformed request apart from both, and every one of them reported.
 */
class ErrorAnswersTest {
    @Test
    fun `an unexpected failure is 500 internal, reported, with nothing of the exception in the body`() =
        errorAnswersTest { reported ->
            val response = client.get("/test/bug")

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals(ErrorCode.Internal, response.errorBody().code)
            assertFalse(SECRET in response.bodyAsText(), response.bodyAsText())
            assertEquals(IllegalStateException::class, reported.single()::class)
            assertEquals(SECRET, reported.single().message)
        }

    @Test
    fun `the assembled application answers an unexpected failure with 500 internal`() =
        testApplication {
            application {
                haulModule(SeededDatabase.dataSource, CANVAS_NOW, commit = "test", sagaClock = SAGA_CLOCK)
                routing { throwing() }
            }
            val response = client.get("/test/bug")

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals(ErrorCode.Internal, response.errorBody().code)
            assertFalse(SECRET in response.bodyAsText(), response.bodyAsText())
        }

    /** Ktor throws this itself for a query or a path it cannot decode, before any handler of ours runs. */
    @Test
    fun `a malformed request keeps Ktor's 400, as validation_failed`() =
        errorAnswersTest { reported ->
            val response = client.get("/test/malformed")

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(ErrorCode.ValidationFailed, response.errorBody().code)
            assertEquals(BadRequestException::class, reported.single()::class)
        }

    @Test
    fun `a pool with no connection to give is 503 unavailable`() =
        errorAnswersTest { _ ->
            val response = client.get("/test/no-connection")

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertEquals(ErrorCode.Unavailable, response.errorBody().code)
        }

    @Test
    fun `a refused connection is 503 unavailable however deep it was wrapped`() =
        errorAnswersTest { _ ->
            val response = client.get("/test/connection-refused")

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertEquals(ErrorCode.Unavailable, response.errorBody().code)
        }

    /** A statement the database refused is a bug of ours, not an outage: «unavailable» would say «retry». */
    @Test
    fun `a failed statement is 500 internal, not unavailable`() =
        errorAnswersTest { _ ->
            val response = client.get("/test/statement-failed")

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals(ErrorCode.Internal, response.errorBody().code)
        }

    /** Cancellation is Ktor's to finish — a timeout is its `504` — and not a bug to report. */
    @Test
    fun `a timed-out call is not answered or reported as a bug`() =
        errorAnswersTest { reported ->
            val response = client.get("/test/timeout")

            assertEquals(HttpStatusCode.GatewayTimeout, response.status)
            assertTrue(reported.isEmpty(), reported.toString())
        }

    /** The whole path: Hikari's timeout, wrapped by Exposed, through a real screen route. */
    @Test
    fun `a database that cannot be reached makes a screen 503 unavailable`() {
        val unreachable =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = "jdbc:postgresql://127.0.0.1:1/haul"
                    driverClassName = "org.postgresql.Driver"
                    connectionTimeout = 250
                    initializationFailTimeout = -1
                    maximumPoolSize = 1
                },
            )
        unreachable.use {
            testApplication {
                application { haulModule(it, CANVAS_NOW, commit = "test", sagaClock = SAGA_CLOCK) }
                val response = client.get("/ui/home")

                assertEquals(HttpStatusCode.ServiceUnavailable, response.status, response.bodyAsText())
                assertEquals(ErrorCode.Unavailable, response.errorBody().code)
            }
        }
    }

    private fun errorAnswersTest(block: suspend ApplicationTestBuilder.(reported: List<Throwable>) -> Unit) =
        testApplication {
            val reported = CopyOnWriteArrayList<Throwable>()
            application {
                install(StatusPages) { unexpectedFailures(report = { reported += it }) }
                routing { throwing() }
            }
            block(reported)
        }

    private fun Route.throwing() {
        get("/test/bug") { throw IllegalStateException(SECRET) }
        get("/test/malformed") { throw BadRequestException("Url decode failed for /ui/search?q=%zz") }
        get("/test/no-connection") {
            throw SQLTransientConnectionException(
                "HikariPool-1 - Connection is not available, request timed out after 30000ms.",
            )
        }
        get("/test/connection-refused") {
            throw RuntimeException("transaction failed", SQLException("Connection to 127.0.0.1:1 refused.", "08001"))
        }
        get("/test/statement-failed") { throw SQLException("duplicate key value violates unique constraint", "23505") }
        get("/test/timeout") { withTimeout(1) { delay(10_000) } }
    }

    /** The body a code arrives in, checked against the status it came with: the pair is the contract. */
    private suspend fun HttpResponse.errorBody(): ErrorBody =
        haulWireJson.decodeFromString(ErrorBody.serializer(), bodyAsText()).also {
            assertEquals(status(it.code), status, "code ${it.code} came with $status")
        }

    private companion object {
        const val SECRET = "relation \"haul_secret_table\" does not exist"
    }
}
