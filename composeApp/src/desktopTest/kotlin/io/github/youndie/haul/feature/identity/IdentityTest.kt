package io.github.youndie.haul.feature.identity

import io.github.youndie.haul.registry.haulJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The storefront's identity against a server played by a mock engine and a provider played by a
 * [SignInFlow] that answers what it is told: guest, sign-in and merge, the headers each state sends,
 * and what a `401` does to each.
 */
class IdentityTest {
    private val settings =
        SignInSettings("http://shildik.test/realms/haul", "haul-web", "openid", "/api/v1/me/cart/merge")
    private val requests = mutableListOf<HttpRequestData>()
    private var guests = 0
    private var signInOff = false

    /** `401` for these bearer tokens and guest ids — what the server says of a lapsed token or a forgotten guest. */
    private val refused = mutableSetOf<String>()

    private val http =
        HttpClient(
            MockEngine { request ->
                requests += request
                val credential =
                    request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ") ?: request.headers[GUEST_HEADER]
                when {
                    credential != null && credential in refused -> {
                        respond("""{"code":"unauthenticated","message":"no"}""", HttpStatusCode.Unauthorized, JSON)
                    }

                    request.url.encodedPath == IdentityApi.GUESTS -> {
                        respond("""{"id":"g-${++guests}"}""", HttpStatusCode.Created, JSON)
                    }

                    request.url.encodedPath == IdentityApi.SIGN_IN_SETTINGS -> {
                        settingsAnswer()
                    }

                    else -> {
                        respond("""{"type":"refresh"}""", HttpStatusCode.OK, JSON)
                    }
                }
            },
        )

    private fun MockRequestHandleScope.settingsAnswer(): HttpResponseData =
        if (signInOff) {
            respond("""{"code":"unavailable","message":"off"}""", HttpStatusCode.ServiceUnavailable, JSON)
        } else {
            respond(haulJson.encodeToString(SignInSettings.serializer(), settings), HttpStatusCode.OK, JSON)
        }

    private class Provider : SignInFlow {
        var next = Tokens("access-1", "refresh-1")
        var renewed: Tokens? = Tokens("access-2", "refresh-2")
        val refreshedWith = mutableListOf<String>()

        override suspend fun signIn(settings: suspend () -> SignInSettings): Tokens = next.also { settings() }

        override suspend fun refresh(
            settings: SignInSettings,
            refreshToken: String,
        ): Tokens {
            refreshedWith += refreshToken
            return renewed ?: error("the provider refused the refresh")
        }
    }

    private val provider = Provider()
    private val store = MemorySessionStore()
    private val identity = Identity(IdentityApi(http), provider, store)

    private suspend fun home() =
        identity.send { headers ->
            http.get("/ui/home") {
                headers.forEach { (k, v) -> header(k, v) }
            }
        }

    @Test
    fun `a guest is created once, kept, and named on every request`() =
        runBlocking {
            home()
            home()

            assertEquals(1, guests)
            assertEquals("g-1", store.load().guestId)
            val screens = requests.filter { it.url.encodedPath == "/ui/home" }
            assertEquals(listOf("g-1", "g-1"), screens.map { it.headers[GUEST_HEADER] })
            assertEquals(listOf(null, null), screens.map { it.headers[HttpHeaders.Authorization] })
        }

    @Test
    fun `signing in keeps the tokens and merges the guest cart with them`() =
        runBlocking {
            home()
            identity.signIn()

            val merge = requests.single { it.url.encodedPath == settings.mergeUrl }
            assertEquals(HttpMethod.Post, merge.method)
            assertEquals("Bearer access-1", merge.headers[HttpHeaders.Authorization])
            assertEquals("g-1", merge.headers[GUEST_HEADER])
            assertEquals(Tokens("access-1", "refresh-1"), store.load().tokens)

            // Signed in, a request carries the token and not the guest id.
            home()
            val last = requests.last()
            assertEquals("Bearer access-1", last.headers[HttpHeaders.Authorization])
            assertNull(last.headers[GUEST_HEADER])
        }

    @Test
    fun `a lapsed token is renewed once and the request sent again`() =
        runBlocking {
            identity.signIn()
            refused += "access-1"

            assertEquals(HttpStatusCode.OK, home().status)

            assertEquals(listOf("refresh-1"), provider.refreshedWith)
            assertEquals(Tokens("access-2", "refresh-2"), store.load().tokens)
            assertEquals("Bearer access-2", requests.last().headers[HttpHeaders.Authorization])
        }

    @Test
    fun `a token that cannot be renewed signs the customer out, back to the guest`() =
        runBlocking {
            home()
            identity.signIn()
            refused += "access-1"
            provider.renewed = null

            assertEquals(HttpStatusCode.OK, home().status)

            assertNull(store.load().tokens)
            assertEquals("g-1", requests.last().headers[GUEST_HEADER])
        }

    @Test
    fun `a guest the server forgot is replaced`() =
        runBlocking {
            home()
            refused += "g-1"

            assertEquals(HttpStatusCode.OK, home().status)

            assertEquals("g-2", store.load().guestId)
        }

    @Test
    fun `a server without sign-in says so and nothing changes`() =
        runBlocking {
            signInOff = true

            assertFailsWith<SignInUnavailable> { identity.signIn() }

            assertNull(store.load().tokens)
        }

    @Test
    fun `signing out forgets the tokens and keeps the guest`() =
        runBlocking {
            home()
            identity.signIn()

            identity.signOut()

            assertEquals(Session(guestId = "g-1"), store.load())
        }

    /**
     * B-66, a browser that blocks the popup: the sign-in leaves for the provider in this tab with its
     * `next`, and the page it comes back to finishes it as a popup's is finished — the tokens kept, the
     * guest cart merged with them — and answers that `next`.
     */
    @Test
    fun `a sign-in in this tab comes back signed in with the cart merged and its next`() =
        runBlocking {
            home()
            // A page load later: the guest is the one the store kept.
            val here = Here()
            val identity = Identity(IdentityApi(http), provider, store, here)
            assertTrue(identity.canSignInHere)
            assertFalse(identity.returnedFromSignIn)

            identity.signInHere("/account/orders")
            assertEquals(listOf<String?>("/account/orders"), here.left)
            assertFalse(identity.signedIn, "leaving for the provider is not a sign-in yet")

            here.comesBack(SignedInHere(Tokens("access-here", "refresh-here"), "/account/orders"))
            assertTrue(identity.returnedFromSignIn)
            assertEquals("/account/orders", identity.finishSignInHere())

            assertTrue(identity.signedIn)
            assertEquals(Session("g-1", Tokens("access-here", "refresh-here")), store.load())
            val merge = requests.single { it.url.encodedPath == settings.mergeUrl }
            assertEquals("Bearer access-here", merge.headers[HttpHeaders.Authorization])
            assertEquals("g-1", merge.headers[GUEST_HEADER])
        }

    /** One that did not go through leaves the shopper the guest they were. */
    @Test
    fun `a sign-in in this tab that did not go through changes nothing`() =
        runBlocking {
            home()
            // A page load later: the guest is the one the store kept.
            val here = Here()
            val identity = Identity(IdentityApi(http), provider, store, here)

            assertFailsWith<IllegalStateException> { identity.finishSignInHere() }

            assertFalse(identity.signedIn)
            assertEquals(Session(guestId = "g-1"), store.load())
            assertTrue(requests.none { it.url.encodedPath == settings.mergeUrl })
        }

    @Test
    fun `an identity without a sign-in in this tab offers none`() =
        runBlocking {
            assertFailsWith<SignInUnavailable> { identity.signInHere("/checkout") }
            assertFalse(identity.canSignInHere)
            assertFalse(identity.returnedFromSignIn)
        }

    /** The provider's page in this tab, as far as the identity sees it: where it was sent, and what came back. */
    private class Here : SignInHere {
        val left = mutableListOf<String?>()
        private var back: SignedInHere? = null

        fun comesBack(signedIn: SignedInHere) {
            back = signedIn
        }

        override val returned: Boolean get() = back != null

        override suspend fun leave(
            settings: SignInSettings,
            next: String?,
        ) {
            left += next
        }

        override suspend fun finish(settings: SignInSettings): SignedInHere =
            checkNotNull(back) { "no sign-in in this tab came back" }.also { back = null }
    }

    private companion object {
        val JSON = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
