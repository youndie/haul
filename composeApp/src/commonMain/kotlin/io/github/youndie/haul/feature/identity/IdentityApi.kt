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

    /** Where to sign in, or `null` on a server with sign-in off (`503 unavailable`). */
    public suspend fun settings(): SignInSettings? {
        val response = http.get(SIGN_IN_SETTINGS)
        if (response.status == HttpStatusCode.ServiceUnavailable) return null
        return haulJson.decodeFromString(SignInSettings.serializer(), expect(response, HttpStatusCode.OK))
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

    /** The body's error code; a body that is not an `ErrorBody` (a proxy's page) has none. */
    @Suppress(
        "ktlint:kapkan:swallowed-failure",
        "A body that does not parse is answered by the status alone, which the failure carries.",
    )
    private fun errorCode(body: String): ErrorCode? =
        try {
            haulJson.decodeFromString(ErrorBody.serializer(), body).code
        } catch (_: IllegalArgumentException) {
            null
        }

    public companion object {
        public const val GUESTS: String = "/api/v1/guests"
        public const val SIGN_IN_SETTINGS: String = "/api/v1/sign-in"
    }
}

/** An identity route refused: its status and, when the body said, its [code]. */
public class IdentityFailure(
    public val status: Int,
    public val code: ErrorCode?,
) : Exception("identity route answered $status ${code ?: ""}".trim())
