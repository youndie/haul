package io.github.youndie.haul.feature.identity

import io.github.youndie.haul.ErrorBody
import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.registry.haulJson
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

/**
 * The server's identity routes (endpoint-identity), as the browser calls them. The two paths here are
 * entry points, like `/ui/home`: they are called before any tree has handed the client a URL. The
 * merge's URL comes from the server, in [SignInSettings.mergeUrl].
 */
public class IdentityApi(
    private val http: HttpClient,
) {
    /** A new guest id (`POST /api/v1/guests`, `201`). */
    public suspend fun createGuest(): String =
        haulJson.decodeFromString(GuestDto.serializer(), expect(http.post(GUESTS), HttpStatusCode.Created)).id

    /**
     * Where to sign in, or `null` on a server with sign-in off (`503 unavailable`). A request that got no answer at
     * all — the browser offline, the store down — throws [SignInUnreachable] (B-147): the press that asked says so.
     */
    public suspend fun settings(): SignInSettings? {
        val response =
            try {
                http.get(SIGN_IN_SETTINGS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Throwable: in the browser a failed fetch is a JavaScript error, which is no `Exception` on Wasm.
                throw SignInUnreachable(e)
            }
        if (response.status == HttpStatusCode.ServiceUnavailable) return null
        return haulJson.decodeFromString(SignInSettings.serializer(), expect(response, HttpStatusCode.OK))
    }

    /**
     * A demo shopper of this browser's own (`POST /api/v1/demo-shoppers`, `201`, B-162): their tokens. A refusal — no
     * demo on this server, today's all taken, the provider down — throws [DemoShopperRefused] with what the server
     * said; no answer at all throws [SignInUnreachable], as the sign-in's settings do.
     */
    public suspend fun demoShopper(): Tokens {
        val response =
            try {
                http.post(DEMO_SHOPPERS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Throwable: in the browser a failed fetch is a JavaScript error, which is no `Exception` on Wasm.
                throw SignInUnreachable(e)
            }
        val body = response.bodyAsText()
        if (response.status != HttpStatusCode.Created) throw DemoShopperRefused(errorMessage(body))
        val tokens = haulJson.decodeFromString(DemoShopperTokens.serializer(), body)
        return Tokens(tokens.accessToken, tokens.refreshToken)
    }

    /** Sign-in's merge of [guestId]'s cart into the customer's the [token] names; answered `refresh`. */
    public suspend fun merge(
        url: String,
        guestId: String,
        token: String,
    ) {
        expect(
            http.post(url) {
                bearerAuth(token)
                header(GUEST_HEADER, guestId)
            },
        )
    }

    private suspend fun expect(
        response: HttpResponse,
        status: HttpStatusCode? = null,
    ): String {
        val body = response.bodyAsText()
        if (if (status != null) response.status == status else response.status.isSuccess()) return body
        throw IdentityFailure(response.status.value, errorCode(body))
    }

    public companion object {
        public const val GUESTS: String = "/api/v1/guests"
        public const val SIGN_IN_SETTINGS: String = "/api/v1/sign-in"
        public const val DEMO_SHOPPERS: String = "/api/v1/demo-shoppers"
    }
}

/**
 * The store did not answer the sign-in's request for where to sign in (B-147): no answer at all, not a refusal. The
 * press that started the sign-in says «Couldn’t reach the store» and offers it again, as any press with no answer does.
 */
public class SignInUnreachable(
    cause: Throwable,
) : Exception("the store did not answer the request for where to sign in", cause)

/**
 * The server would not make a demo shopper (B-162): [reason] is what it said — every one of today's taken, the
 * provider down — or `null` when the body said nothing.
 */
public class DemoShopperRefused(
    public val reason: String?,
) : Exception("the demo shopper was refused: ${reason ?: "no reason given"}")

/** An identity route refused: its status and, when the body said, its [code]. */
public class IdentityFailure(
    public val status: Int,
    public val code: ErrorCode?,
) : Exception("identity route answered $status ${code ?: ""}".trim())

/** The body's message; a body that is not an `ErrorBody` (a proxy's page) has none. */
@Suppress(
    "ktlint:kapkan:swallowed-failure",
    "A body that does not parse says nothing to show; the press says its own words instead.",
)
internal fun errorMessage(body: String): String? =
    try {
        haulJson.decodeFromString(ErrorBody.serializer(), body).message
    } catch (_: IllegalArgumentException) {
        null
    }

/** The body's error code; a body that is not an `ErrorBody` (a proxy's page) has none. */
@Suppress(
    "ktlint:kapkan:swallowed-failure",
    "A body that does not parse is answered by the status alone: no code, so nothing a code would change.",
)
internal fun errorCode(body: String): ErrorCode? =
    try {
        haulJson.decodeFromString(ErrorBody.serializer(), body).code
    } catch (_: IllegalArgumentException) {
        null
    }
