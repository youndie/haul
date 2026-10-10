package io.github.youndie.haul.feature.identity

import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The storefront's identity (feature-identity): a guest until the shopper signs in, then a customer,
 * and back to the guest after signing out.
 *
 * - The guest id is asked of the server once, on the first request that needs one, and kept.
 * - A request carries the customer's bearer token when there is one, the guest id otherwise — never
 *   both: the guest id is the merge's business only.
 * - Signing in runs the provider's flow ([SignInFlow]), keeps the tokens, and merges the guest cart
 *   into the customer's on the server ([SignInSettings.mergeUrl]).
 * - A `401` on a request ([send]) is answered once: a signed-in customer's token is renewed, or the
 *   customer is signed out when it cannot be; a guest the server no longer knows is replaced. Then
 *   the request is sent again.
 * - When the browser blocks the popup, the sign-in can run in this tab instead ([here], B-66): it leaves
 *   for the provider's page and is finished, the same way, when the tab comes back to `/sign-in`.
 */
public class Identity(
    private val api: IdentityApi,
    private val flow: SignInFlow,
    private val store: SessionStore,
    private val here: SignInHere? = null,
) : SessionControls {
    private val state = MutableStateFlow(store.load())
    private val guestLock = Mutex()
    private var settings: SignInSettings? = null

    public val session: StateFlow<Session> = state.asStateFlow()

    /** The headers a request to the server carries now; creates the guest the first time one is needed. */
    public suspend fun headers(): Map<String, String> {
        state.value.tokens?.let { return mapOf(HttpHeaders.Authorization to "Bearer ${it.accessToken}") }
        return mapOf(GUEST_HEADER to guest())
    }

    /**
     * Sends a request built by [request] with the current [headers], and once more after a `401` that
     * a renewed token or a new guest can answer.
     */
    public suspend fun send(request: suspend (headers: Map<String, String>) -> HttpResponse): HttpResponse {
        val first = request(headers())
        if (first.status != HttpStatusCode.Unauthorized) return first
        recover()
        return request(headers())
    }

    /**
     * Signs in through the provider and merges the guest cart. Throws [SignInUnavailable] on a server
     * with sign-in off; a flow the shopper abandoned throws what the flow throws, and nothing changes.
     */
    public suspend fun signIn() {
        var asked: SignInSettings? = null
        val tokens = flow.signIn { (settings() ?: throw SignInUnavailable()).also { asked = it } }
        keep(checkNotNull(asked) { "the sign-in flow answered without asking where to sign in" }, tokens)
    }

    override val signedIn: Boolean get() = state.value.signedIn

    /** Forgets the customer's tokens; the shopper is the guest they were, with an empty cart. */
    override fun signOut() {
        update { it.copy(tokens = null) }
    }

    override val canSignInHere: Boolean get() = here != null

    /** Leaves for the provider's page in this tab. Throws [SignInUnavailable] where there is none, or no sign-in. */
    override suspend fun signInHere(next: String?) {
        val here = here ?: throw SignInUnavailable()
        here.leave(settings() ?: throw SignInUnavailable(), next)
    }

    override val returnedFromSignIn: Boolean get() = here?.returned == true

    /**
     * Finishes the sign-in in this tab that came back: the tokens kept and the guest cart merged, as a
     * popup's are; answers the address it was asked to return to. Throws what [SignInHere.finish] throws.
     */
    override suspend fun finishSignInHere(): String? {
        val here = here ?: throw SignInUnavailable()
        val settings = settings() ?: throw SignInUnavailable()
        val signedIn = here.finish(settings)
        keep(settings, signedIn.tokens)
        return signedIn.next
    }

    private suspend fun keep(
        settings: SignInSettings,
        tokens: Tokens,
    ) {
        val guest = state.value.guestId
        update { it.copy(tokens = tokens) }
        if (guest != null) api.merge(settings.mergeUrl, guest, tokens.accessToken)
    }

    private suspend fun guest(): String =
        guestLock.withLock {
            state.value.guestId ?: api.createGuest().also { id -> update { it.copy(guestId = id) } }
        }

    private suspend fun recover() {
        val tokens = state.value.tokens
        if (tokens == null) {
            update { it.copy(guestId = null) }
            return
        }
        val renewed = tokens.refreshToken?.let { renewed(it) }
        update { it.copy(tokens = renewed) }
    }

    @Suppress(
        "ktlint:kapkan:swallowed-failure",
        "A refresh the provider refused — expired, rotated, revoked — is the end of the sign-in, which is the answer: the shopper is a guest again and sees «Sign in».",
    )
    private suspend fun renewed(refreshToken: String): Tokens? =
        try {
            settings()?.let { flow.refresh(it, refreshToken) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    private suspend fun settings(): SignInSettings? = settings ?: api.settings()?.also { settings = it }

    private fun update(change: (Session) -> Session) {
        state.update(change)
        store.save(state.value)
    }
}

/** Sign-in asked of a server that has none configured. */
public class SignInUnavailable : Exception("sign-in is not configured on this server")
