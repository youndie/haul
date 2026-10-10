package io.github.youndie.haul.feature.identity

import io.github.youndie.haul.registry.haulJson
import io.ktor.http.Url
import kotlinx.browser.sessionStorage
import kotlinx.browser.window
import kotlinx.coroutines.awaitCancellation
import org.publicvalue.multiplatform.oidc.ExperimentalOpenIdConnect
import org.publicvalue.multiplatform.oidc.flows.continueLogin
import org.publicvalue.multiplatform.oidc.types.AuthCodeRequest

/**
 * The sign-in in this tab, for a browser that blocks the popup (B-66). The same request the popup's flow
 * makes — authorization code, PKCE, a state and a nonce, through kotlin-multiplatform-oidc — but the tab
 * itself goes to the provider's page. The request and the address to return to wait in the tab's session
 * storage; the provider returns to the same [redirectUri], `signed-in.html`, which — opened by no
 * storefront window and finding a request waiting — keeps the provider's answer beside it and goes to
 * `/sign-in`, where the storefront finishes the exchange here, as the popup's does.
 *
 * Storage that refuses is a sign-in that cannot leave: [leave] throws rather than leave the shopper at
 * the provider with nothing to come back to.
 */
@OptIn(ExperimentalOpenIdConnect::class)
internal class BrowserSignInHere(
    private val redirectUri: String,
) : SignInHere {
    override val returned: Boolean get() = read(ANSWER) != null && read(REQUEST) != null

    override suspend fun leave(
        settings: SignInSettings,
        next: String?,
    ) {
        val request = oidcClient(settings, redirectUri).createAuthorizationCodeRequest()
        sessionStorage.removeItem(ANSWER)
        sessionStorage.setItem(REQUEST, haulJson.encodeToString(AuthCodeRequest.serializer(), request))
        if (next == null) sessionStorage.removeItem(NEXT) else sessionStorage.setItem(NEXT, next)
        window.location.assign(request.url.toString())
        // The page is on its way out; nothing after this runs in it.
        awaitCancellation()
    }

    override suspend fun finish(settings: SignInSettings): SignedInHere {
        val request = read(REQUEST)
        val answer = read(ANSWER)
        val next = read(NEXT)
        // Used up whatever happens next: a second load of `/sign-in` is not a second exchange of one code.
        listOf(REQUEST, ANSWER, NEXT).forEach(::forget)
        check(request != null && answer != null) { "no sign-in in this tab came back" }
        val response =
            oidcClient(settings, redirectUri)
                .continueLogin(haulJson.decodeFromString(AuthCodeRequest.serializer(), request), Url(answer))
        return SignedInHere(Tokens(response.access_token, response.refresh_token), next)
    }

    @Suppress(
        "ktlint:kapkan:swallowed-failure",
        "Storage that refuses holds no sign-in coming back, which is what a null says.",
    )
    private fun read(key: String): String? =
        try {
            sessionStorage.getItem(key)
        } catch (_: Throwable) {
            null
        }

    @Suppress(
        "ktlint:kapkan:swallowed-failure",
        "Storage that refuses to forget held nothing it could give back: a refusing storage reads as empty.",
    )
    private fun forget(key: String) {
        try {
            sessionStorage.removeItem(key)
        } catch (_: Throwable) {
            // Read as empty from then on (see [read]).
        }
    }

    private companion object {
        // `signed-in.html` reads and writes the same three keys.
        const val REQUEST = "haul.sign-in.request"
        const val NEXT = "haul.sign-in.next"
        const val ANSWER = "haul.sign-in.answer"
    }
}
