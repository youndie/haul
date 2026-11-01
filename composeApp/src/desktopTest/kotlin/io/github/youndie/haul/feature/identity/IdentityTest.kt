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
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import java.io.IOException
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

    /** What `POST /api/v1/demo-shoppers` answers (B-162): tokens, or the server's refusal when [demoFull]. */
    private var demoFull = false

    /** The browser offline: every request fails with no answer (B-147). */
    private var offline = false

    /** Bearer tokens answered `401 unauthenticated` — what the server says of a lapsed token. */
    private val lapsed = mutableSetOf<String>()

    /** Guest ids answered `401 guest_not_found` — guests the server does not know (a reseeded stand). */
    private val forgotten = mutableSetOf<String>()

    /** Every guest id answered `401 guest_not_found`, the ones issued from now on included. */
    private var forgetsEveryGuest = false

    private val http =
        HttpClient(
            MockEngine { request ->
                requests += request
                if (offline) throw IOException("net::ERR_INTERNET_DISCONNECTED")
                val bearer = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
                val guest = request.headers[GUEST_HEADER]
                val path = request.url.encodedPath
                when {
                    bearer != null && bearer in lapsed -> {
                        respond(UNAUTHENTICATED, HttpStatusCode.Unauthorized, JSON)
                    }

                    path == settings.mergeUrl && guest != null && guest.isForgotten() -> {
                        respond(GUEST_NOT_FOUND, HttpStatusCode.NotFound, JSON)
                    }

                    bearer == null && guest != null && guest.isForgotten() -> {
                        respond(GUEST_NOT_FOUND, HttpStatusCode.Unauthorized, JSON)
                    }

                    // A page only a customer may open: a guest — one the server knows — is not one.
                    path == CHECKOUT && bearer == null -> {
                        respond(UNAUTHENTICATED, HttpStatusCode.Unauthorized, JSON)
                    }

                    request.url.encodedPath == IdentityApi.GUESTS -> {
                        respond("""{"id":"g-${++guests}"}""", HttpStatusCode.Created, JSON)
                    }

                    request.url.encodedPath == IdentityApi.SIGN_IN_SETTINGS -> {
                        settingsAnswer()
                    }

                    request.url.encodedPath == IdentityApi.DEMO_SHOPPERS && demoFull -> {
                        respond(DEMO_FULL, HttpStatusCode.ServiceUnavailable, JSON)
                    }

                    request.url.encodedPath == IdentityApi.DEMO_SHOPPERS -> {
                        respond(DEMO_TOKENS, HttpStatusCode.Created, JSON)
                    }

                    else -> {
                        respond("""{"type":"refresh"}""", HttpStatusCode.OK, JSON)
                    }
                }
            },
        )

    private fun String.isForgotten(): Boolean = forgetsEveryGuest || this in forgotten

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

    private suspend fun home() = page("/ui/home")

    private suspend fun checkout() = page(CHECKOUT)

    private suspend fun page(path: String) =
        identity.send { headers ->
            http.get(path) {
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

    /** B-162: the demo shopper's tokens are kept as a sign-in's, and the guest cart merges into them. */
    @Test
    fun `a demo shopper is signed in with the server's tokens and gets the guest cart`() =
        runBlocking {
            home()
            identity.tryAsDemoShopper()

            assertEquals(HttpMethod.Post, requests.single { it.url.encodedPath == IdentityApi.DEMO_SHOPPERS }.method)
            val merge = requests.single { it.url.encodedPath == settings.mergeUrl }
            assertEquals("Bearer demo-access", merge.headers[HttpHeaders.Authorization])
            assertEquals("g-1", merge.headers[GUEST_HEADER])
            assertEquals(Tokens("demo-access", "demo-refresh"), store.load().tokens)
            assertTrue(identity.signedIn)
        }

    @Test
    fun `a demo shopper the server refuses says why and leaves the guest as they were`() =
        runBlocking {
            home()
            demoFull = true

            val refused = assertFailsWith<DemoShopperRefused> { identity.tryAsDemoShopper() }

            assertEquals("Every demo shopper for today is taken", refused.reason)
            assertNull(store.load().tokens)
            assertEquals("g-1", store.load().guestId)
        }

    @Test
    fun `a lapsed token is renewed once and the request sent again`() =
        runBlocking {
            identity.signIn()
            lapsed += "access-1"

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
            lapsed += "access-1"
            provider.renewed = null

            assertEquals(HttpStatusCode.OK, home().status)

            assertNull(store.load().tokens)
            assertEquals("g-1", requests.last().headers[GUEST_HEADER])
        }

    @Test
    fun `a guest the server forgot is replaced`() =
        runBlocking {
            home()
            forgotten += "g-1"

            assertEquals(HttpStatusCode.OK, home().status)

            assertEquals("g-2", store.load().guestId)
            assertEquals("g-2", requests.last().headers[GUEST_HEADER])
        }

    /** A server that knows no guest at all gets one new guest per request, not a loop of them. */
    @Test
    fun `a guest is replaced once per request`() =
        runBlocking {
            home()
            forgetsEveryGuest = true

            val refused = home()

            assertEquals(HttpStatusCode.Unauthorized, refused.status)
            assertEquals(2, guests)
            assertEquals(listOf("g-1", "g-1", "g-2"), screens().map { it.headers[GUEST_HEADER] })
        }

    /**
     * B-115, TC-SIGN-06: a guest who opens `/checkout` or an order by its address is told to sign in by
     * the page's `401 unauthenticated` — the expected answer, not a guest the server forgot. The guest, and
     * with it the cart, stays; the `401` reaches the shell, which draws the prompt.
     */
    @Test
    fun `a guest on a customer's page stays the same guest`() =
        runBlocking {
            home()

            val refused = checkout()
            checkout()

            assertEquals(HttpStatusCode.Unauthorized, refused.status)
            assertEquals(UNAUTHENTICATED, refused.bodyAsText(), "the body the shell reads")
            assertEquals(1, guests)
            assertEquals(Session(guestId = "g-1"), store.load())
            home()
            assertEquals(listOf("g-1", "g-1", "g-1", "g-1"), screens().map { it.headers[GUEST_HEADER] })
        }

    /**
     * B-115: a sign-in that lapsed past renewing is the guest the shopper was — on the request that found
     * it out and on every customer's page after it, as «Sign out» leaves it.
     */
    @Test
    fun `a lapsed sign-in leaves the shopper the guest they were`() =
        runBlocking {
            home()
            identity.signIn()
            lapsed += "access-1"
            provider.renewed = null

            assertEquals(HttpStatusCode.Unauthorized, checkout().status)
            assertEquals(HttpStatusCode.Unauthorized, checkout().status)

            assertEquals(listOf("refresh-1"), provider.refreshedWith)
            assertEquals(1, guests)
            assertEquals(Session(guestId = "g-1"), store.load())
            assertEquals("g-1", requests.last().headers[GUEST_HEADER])
        }

    /** The guest a lapsed sign-in falls back to may be one the server forgot meanwhile: it is replaced, once. */
    @Test
    fun `a lapsed sign-in over a forgotten guest gets a new one`() =
        runBlocking {
            home()
            identity.signIn()
            lapsed += "access-1"
            provider.renewed = null
            forgotten += "g-1"

            assertEquals(HttpStatusCode.OK, home().status)

            assertEquals(Session(guestId = "g-2"), store.load())
            assertEquals("g-2", requests.last().headers[GUEST_HEADER])
        }

    /** A guest the server forgot has no cart to merge: the sign-in goes through and the guest is dropped. */
    @Test
    fun `signing in over a forgotten guest keeps the tokens and drops the guest`() =
        runBlocking {
            home()
            forgotten += "g-1"

            identity.signIn()

            assertEquals(Session(tokens = Tokens("access-1", "refresh-1")), store.load())
        }

    @Test
    fun `a server without sign-in says so and nothing changes`() =
        runBlocking {
            signInOff = true

            assertFailsWith<SignInUnavailable> { identity.signIn() }

            assertNull(store.load().tokens)
        }

    /**
     * B-147: a store that does not answer where to sign in is not a server without sign-in, nor a sign-in the shopper
     * abandoned: it says it could not be reached, either way in, and nothing changes. Online again, the sign-in goes on.
     */
    @Test
    fun `a store that does not answer where to sign in could not be reached, and nothing changes`() =
        runBlocking {
            home()
            val identity = Identity(IdentityApi(http), provider, store, Here())
            offline = true

            assertFailsWith<SignInUnreachable> { identity.signIn() }
            assertFailsWith<SignInUnreachable> { identity.signInHere("/cart") }

            assertEquals(Session(guestId = "g-1"), store.load())
            offline = false
            identity.signIn()
            assertTrue(identity.signedIn)
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

    /** B-116: back from the provider's page, which never answered: said once, and forgotten. */
    @Test
    fun `a sign-in in this tab the provider never answered is forgotten once it is told`() =
        runBlocking {
            val here = Here()
            val identity = Identity(IdentityApi(http), provider, store, here)
            identity.signInHere("/cart")

            assertFalse(identity.returnedFromSignIn)
            assertTrue(identity.forgetUnansweredSignIn())
            assertFalse(identity.forgetUnansweredSignIn(), "the same sign-in was reported twice")
            assertFalse(identity.signedIn)
        }

    @Test
    fun `an identity without a sign-in in this tab offers none`() =
        runBlocking {
            assertFailsWith<SignInUnavailable> { identity.signInHere("/checkout") }
            assertFalse(identity.canSignInHere)
            assertFalse(identity.returnedFromSignIn)
            assertFalse(identity.forgetUnansweredSignIn())
        }

    /** The provider's page in this tab, as far as the identity sees it: where it was sent, and what came back. */
    private class Here : SignInHere {
        val left = mutableListOf<String?>()
        private var back: SignedInHere? = null
        private var pending = false

        fun comesBack(signedIn: SignedInHere) {
            back = signedIn
            pending = false
        }

        override val returned: Boolean get() = back != null

        override fun forgetUnanswered(): Boolean = pending.also { pending = false }

        override suspend fun leave(
            settings: SignInSettings,
            next: String?,
        ) {
            left += next
            pending = true
        }

        override suspend fun finish(settings: SignInSettings): SignedInHere =
            checkNotNull(back) { "no sign-in in this tab came back" }.also { back = null }
    }

    private fun screens(): List<HttpRequestData> = requests.filter { it.url.encodedPath.startsWith("/ui/") }

    private companion object {
        const val CHECKOUT = "/ui/checkout"
        const val UNAUTHENTICATED = """{"code":"unauthenticated","message":"no"}"""
        const val DEMO_TOKENS = """{"accessToken":"demo-access","refreshToken":"demo-refresh"}"""
        const val DEMO_FULL = """{"code":"unavailable","message":"Every demo shopper for today is taken"}"""
        const val GUEST_NOT_FOUND = """{"code":"guest_not_found","message":"no such guest"}"""
        val JSON = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
