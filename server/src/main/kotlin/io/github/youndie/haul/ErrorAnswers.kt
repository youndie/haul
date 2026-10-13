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
import io.ktor.utils.io.ClosedWriteChannelException
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.sql.SQLTransientConnectionException
import kotlin.coroutines.cancellation.CancellationException

/**
 * The catch-all (B-32): what answers a failure no feature named. It is answered by what it is, every code through
 * [respondError] so the status is the one `status(code)` gives it, and only what the store got wrong is handed to
 * [report] (katcher, when it is on) — B-148:
 * - one of Ktor's own client errors keeps its status — a malformed request is the sender's fault and
 *   must not count against the error budget; a `400` carries `validation_failed`, the rest no body. A query
 *   that does not decode is one: `/ui/…` and `/api/…` answer it here, a storefront page's address is the page
 *   whatever its query ([PageAtAnyQuery], B-117) — the browser would show this JSON as the page's text.
 *   A line at `info`, not reported: on a public stand they are crawlers' and guests' typos, not defects;
 * - a database that cannot be reached is `unavailable` / `503`: an outage, worth retrying. A line at `info` with its
 *   cause and no stack, not reported: the outage is said once, at `warn` with its cause, by the pool that met it
 *   (`FailFastDataSource`), and every page it fails would otherwise file the same report;
 * - anything else is `internal` / `500`: a bug, logged at `error` with its stack, reported, and nothing of the
 *   exception in the body.
 *
 * Cancellation is rethrown, not answered: a call cancelled or timed out is Ktor's to finish. A client that
 * left while it was being answered ([isClientGone]) — a reader leaving the order page closes its update stream
 * (B-110) — is a line at `debug`, neither reported nor answered: there is nobody to answer, and nothing failed.
 */
internal fun StatusPagesConfig.unexpectedFailures(report: (Throwable) -> Unit) {
    exception<Throwable> { call, error ->
        if (error is CancellationException) throw error
        val request = "${call.request.httpMethod.value} ${call.request.path()}"
        if (call.response.isCommitted && error.isClientGone()) {
            log.debug("{}: the client left mid-answer ({})", request, error.message)
            return@exception
        }
        val clientStatus = error.clientErrorStatus()
        when {
            clientStatus != null -> {
                log.info("{} → {}: {}", request, clientStatus.value, error.message)
                if (clientStatus == HttpStatusCode.BadRequest) {
                    call.respondError(ErrorCode.ValidationFailed, MALFORMED)
                } else {
                    call.respondText(clientStatus.description, status = clientStatus)
                }
            }

            error.isDatabaseUnreachable() -> {
                log.info("{} → 503: the database cannot be reached ({})", request, error.rootCause().message)
                call.respondError(ErrorCode.Unavailable, UNAVAILABLE)
            }

            else -> {
                // The log before the report: katcher caches to disk, and a crash would leave the log empty.
                log.error("unhandled: $request", error)
                report(error)
                call.respondError(ErrorCode.Internal, INTERNAL)
            }
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
 * Whether the connection to the client closed under a write: Ktor's [ClosedWriteChannelException] (a broken
 * pipe, a reset), as the stream's heartbeat meets it, or wrapped in the [io.ktor.util.cio.ChannelWriteException]
 * a response's own write is rethrown as. Asked only of a response already under way, so a write of ours
 * that failed before the answer began — to another service, say — is still a failure.
 */
private fun Throwable.isClientGone(): Boolean = causes().any { it is ClosedWriteChannelException }

/**
 * Whether no database connection could be had at all, as opposed to a statement that failed: Hikari's
 * «connection is not available» after its timeout, or an SQLSTATE of class `08` (connection exception),
 * however deep it was wrapped.
 */
private fun Throwable.isDatabaseUnreachable(): Boolean =
    causes().any {
        it is SQLTransientConnectionException ||
            (it is SQLException && it.sqlState?.startsWith("08") == true)
    }

/** The deepest cause [causes] reaches: what the database or the network said, under the wrappers. */
private fun Throwable.rootCause(): Throwable = causes().last()

/** This and what caused it, however deep it was wrapped (within reason). */
private fun Throwable.causes(): Sequence<Throwable> =
    generateSequence(this) { it.cause?.takeIf { cause -> cause !== it } }.take(MAX_CAUSES)

private const val MAX_CAUSES = 16

private const val MALFORMED = "Malformed request"
private const val UNAVAILABLE = "Try again in a moment"
private const val INTERNAL = "Something went wrong"

private val log = LoggerFactory.getLogger("io.github.youndie.haul.ErrorAnswers")
