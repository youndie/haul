package io.github.youndie.haul

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.sql.SQLTransientConnectionException
import kotlin.coroutines.cancellation.CancellationException

/**
 * The catch-all (B-32): what answers a failure no feature named. It is logged and handed to [report]
 * (katcher, when it is on), and then answered by what it is, every code through [respondError] so the
 * status is the one `status(code)` gives it:
 * - one of Ktor's own client errors keeps its status — a malformed request is the sender's fault and
 *   must not count against the error budget; a `400` carries `validation_failed`, the rest no body;
 * - a database that cannot be reached is `unavailable` / `503`: an outage, worth retrying;
 * - anything else is `internal` / `500`: a bug, with nothing of the exception in the body.
 *
 * Cancellation is rethrown, not answered: a call cancelled or timed out is Ktor's to finish.
 */
internal fun StatusPagesConfig.unexpectedFailures(report: (Throwable) -> Unit) {
    exception<Throwable> { call, error ->
        if (error is CancellationException) throw error
        val request = "${call.request.httpMethod.value} ${call.request.path()}"
        val clientStatus = error.clientErrorStatus()
        // The log before the report: katcher caches to disk, and a crash would leave the log empty.
        when (clientStatus) {
            null -> log.error("unhandled: $request", error)
            else -> log.info("{} → {}: {}", request, clientStatus.value, error.message)
        }
        report(error)
        when {
            clientStatus == HttpStatusCode.BadRequest -> call.respondError(ErrorCode.ValidationFailed, MALFORMED)
            clientStatus != null -> call.respondText(clientStatus.description, status = clientStatus)
            error.isDatabaseUnreachable() -> call.respondError(ErrorCode.Unavailable, UNAVAILABLE)
            else -> call.respondError(ErrorCode.Internal, INTERNAL)
        }
    }
}

/** Ktor's own exceptions for a request it could not take — the statuses its engine gives them without `StatusPages`. */
private fun Throwable.clientErrorStatus(): HttpStatusCode? =
    when (this) {
        is BadRequestException -> HttpStatusCode.BadRequest
        is NotFoundException -> HttpStatusCode.NotFound
        is UnsupportedMediaTypeException -> HttpStatusCode.UnsupportedMediaType
        is PayloadTooLargeException -> HttpStatusCode.PayloadTooLarge
        else -> null
    }

/**
 * Whether no database connection could be had at all, as opposed to a statement that failed: Hikari's
 * «connection is not available» after its timeout, or an SQLSTATE of class `08` (connection exception),
 * however deep it was wrapped.
 */
private fun Throwable.isDatabaseUnreachable(): Boolean =
    generateSequence(this) { it.cause?.takeIf { cause -> cause !== it } }
        .take(MAX_CAUSES)
        .any { it is SQLTransientConnectionException || (it is SQLException && it.sqlState?.startsWith("08") == true) }

private const val MAX_CAUSES = 16

private const val MALFORMED = "Malformed request"
private const val UNAVAILABLE = "Try again in a moment"
private const val INTERNAL = "Something went wrong"

private val log = LoggerFactory.getLogger("io.github.youndie.haul.ErrorAnswers")
