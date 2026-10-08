package io.github.youndie.haul.shell

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.youndie.haul.ErrorBody
import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.registry.haulJson
import io.github.youndie.haul.ui.SearchSuggestPanel
import io.github.youndie.kompot.KompotComponent
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import kotlinx.serialization.PolymorphicSerializer
import kotlin.coroutines.cancellation.CancellationException

// How the shell asks its server for a screen or a suggest panel: one function, so a test swaps the
// network for a fake and the browser's goes through whatever every request needs on the way.

/** What the server answered: its status and its body as text. */
public class HaulResponse(
    public val status: Int,
    public val body: String,
)

/** `GET`s a path on the page's origin («/ui/home») and returns whatever the server answered; no answer throws. */
public fun interface HaulTransport {
    public suspend fun get(path: String): HaulResponse
}

/**
 * The browser's transport: [http] against [origin], the page's own; an error status is an answer, not a
 * throw. Each request goes through [send], which adds the headers it is given — sign-in's `Identity.send`
 * there (B-12): the customer's bearer token or the guest id, and one more try after a `401`.
 */
public fun ktorTransport(
    http: HttpClient,
    origin: String,
    send: suspend (
        request: suspend (headers: Map<String, String>) -> HttpResponse,
    ) -> HttpResponse = { it(emptyMap()) },
): HaulTransport =
    HaulTransport { path ->
        val response =
            send { headers ->
                http.get(origin.trimEnd('/') + path) { headers.forEach { (name, value) -> header(name, value) } }
            }
        HaulResponse(response.status.value, response.bodyAsText())
    }

/**
 * Sends a command a tree carries (B-37): [method] to [path] — the server's string, from the tree — with
 * a JSON [body] or none, and returns what the server answered (a kompot action, `refresh`, or an
 * `ErrorBody`); no answer throws. The screens' half of the conversation is [HaulTransport].
 */
public fun interface HaulCommands {
    public suspend fun send(
        method: String,
        path: String,
        body: String?,
    ): HaulResponse
}

/**
 * Where a renderer sends a command its component carries with no seam of its own — the Plus trial's
 * «Start trial» (B-23); the storefront provides its [HaulCommands]. `null` — a screenshot — draws the same
 * pixels, and pressing does nothing.
 */
public val LocalHaulCommands: ProvidableCompositionLocal<HaulCommands?> = staticCompositionLocalOf { null }

/** The browser's commands: [http] against [origin], each through [send] — `Identity.send`, as for the screens. */
public fun ktorCommands(
    http: HttpClient,
    origin: String,
    send: suspend (
        request: suspend (headers: Map<String, String>) -> HttpResponse,
    ) -> HttpResponse = { it(emptyMap()) },
): HaulCommands =
    HaulCommands { method, path, body ->
        val response =
            send { headers ->
                http.request(origin.trimEnd('/') + path) {
                    this.method = HttpMethod.parse(method)
                    headers.forEach { (name, value) -> header(name, value) }
                    body?.let { setBody(TextContent(it, ContentType.Application.Json)) }
                }
            }
        HaulResponse(response.status.value, response.bodyAsText())
    }

/** Why a screen's tree did not arrive; the shell draws each differently. */
public sealed class ScreenFailed(
    path: String,
    reason: String,
    cause: Throwable? = null,
) : Exception("$path: $reason", cause) {
    /** No answer at all: the network, or no server there. */
    public class Unreachable(
        path: String,
        cause: Throwable,
    ) : ScreenFailed(path, "no answer", cause)

    /** `404`: what the address names is not there ([code] says what, when the server said). */
    public class NotFound(
        path: String,
        public val code: ErrorCode?,
    ) : ScreenFailed(path, "not found ($code)")

    /** Any other answer that is not a tree: an error of the server's, or a body this build cannot read. */
    public class Refused(
        path: String,
        public val status: Int,
        public val code: ErrorCode?,
        cause: Throwable? = null,
    ) : ScreenFailed(path, "status $status ($code)", cause)
}

/** The kompot tree at [path], or the [ScreenFailed] that says why there is none. */
internal suspend fun HaulTransport.tree(path: String): KompotComponent {
    val response =
        try {
            get(path)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            // Throwable, not Exception: in the browser a failed fetch arrives as a JavaScript error,
            // which is no `Exception` on Kotlin/Wasm — caught narrower, an unreachable server was drawn
            // as the server's own error.
            throw ScreenFailed.Unreachable(path, error)
        }
    return when (response.status) {
        in 200..299 -> {
            try {
                haulJson.decodeFromString(PolymorphicSerializer(KompotComponent::class), response.body)
            } catch (error: IllegalArgumentException) {
                // A body this build cannot decode is the server's answer gone wrong, not a missing network.
                throw ScreenFailed.Refused(path, response.status, null, error)
            }
        }

        404 -> {
            throw ScreenFailed.NotFound(path, errorCode(response.body))
        }

        else -> {
            throw ScreenFailed.Refused(path, response.status, errorCode(response.body))
        }
    }
}

/**
 * The suggest panel for what the shopper has typed, or `null` when there is none to show: a query the
 * server refuses (too short) or a request that failed closes the panel rather than drawing an error.
 */
internal suspend fun HaulTransport.suggest(query: String): SearchSuggestPanel? =
    try {
        tree(Address.suggest(query)) as? SearchSuggestPanel
    } catch (failed: ScreenFailed) {
        null
    }

private fun errorCode(body: String): ErrorCode? =
    try {
        haulJson.decodeFromString(ErrorBody.serializer(), body).code
    } catch (unreadable: IllegalArgumentException) {
        null
    }
