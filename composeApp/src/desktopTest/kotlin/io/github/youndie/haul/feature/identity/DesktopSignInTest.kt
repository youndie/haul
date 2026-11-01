package io.github.youndie.haul.feature.identity

import io.github.youndie.haul.registry.haulJson
import io.github.youndie.haul.storeClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.ConnectException
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.prefs.Preferences
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The desktop's sign-in against the stand's provider (B-178): shildik's image at the chart's version ([ShildikRealm]),
 * the library's JVM flow returning to a loopback listener, and the test as the person at the system's browser — it is
 * handed the page the app opens, signs Maya in there and follows the provider's redirect, which reaches the listener.
 * The store is a fake that says where to sign in, makes the guest, takes the merge, and judges a token's lifetime as the
 * server does — `401` once its `exp` has passed — on a clock the test can move past it ([now]).
 */
class DesktopSignInTest {
    private val node = Preferences.userRoot().node("io/github/youndie/haul-sign-in-test-${System.nanoTime()}")
    private val sessions = PreferencesSessionStore(ORIGIN, node)
    private val opened = CopyOnWriteArrayList<String>()
    private val returns = CopyOnWriteArrayList<ShildikRealm.Return>()
    private val merges = CopyOnWriteArrayList<Pair<String?, String?>>()
    private val asked = CopyOnWriteArrayList<Pair<String, String?>>()

    /** The store's clock, in epoch seconds: a token whose `exp` is not after it is past its lifetime. */
    @Volatile private var now: Long? = null

    private val store =
        MockEngine { request ->
            val path = request.url.encodedPath
            val bearer = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
            when {
                path == IdentityApi.SIGN_IN_SETTINGS -> {
                    respond(haulJsonOf(ShildikRealm.settings), HttpStatusCode.OK, JSON)
                }

                request.method == HttpMethod.Post && path == IdentityApi.GUESTS -> {
                    respond("""{"id":"$GUEST"}""", HttpStatusCode.Created, JSON)
                }

                path == ShildikRealm.settings.mergeUrl -> {
                    merges += bearer to request.headers[GUEST_HEADER]
                    respond("{}", HttpStatusCode.OK, JSON)
                }

                path == ACCOUNT -> {
                    asked += "account" to (bearer ?: request.headers[GUEST_HEADER])
                    val clock = now ?: providerSecond()
                    if (bearer == null || ShildikRealm.claim(bearer, "exp").toLong() <= clock) {
                        respond("""{"code":"unauthenticated","message":"Sign in"}""", HttpStatusCode.Unauthorized, JSON)
                    } else {
                        respond(ShildikRealm.subject(bearer), HttpStatusCode.OK)
                    }
                }

                else -> {
                    respond("""{"code":"not_found","message":"no"}""", HttpStatusCode.NotFound, JSON)
                }
            }
        }

    /** The system's browser, played: each page the app opens is signed in on, apart from the press, as a person would. */
    private val browser: (String) -> Unit = { page ->
        opened += page
        thread(isDaemon = true) { returns += ShildikRealm.play(page) }
    }

    private fun identity(flow: SignInFlow = LoopbackSignInFlow(browse = browser)): Identity =
        Identity(IdentityApi(storeClient(ORIGIN, store)), flow, sessions)

    @AfterTest
    fun forget() = node.removeNode()

    @Test
    fun `Maya signs in in the system browser, the code returns to a loopback port, the guest cart is merged`() =
        runBlocking {
            sessions.save(Session(guestId = GUEST))
            val identity = identity()

            withTimeout(1.minutes) { identity.signIn() }

            val tokens = checkNotNull(identity.session.value.tokens)
            assertEquals(ShildikRealm.MAYA, ShildikRealm.subject(tokens.accessToken), "the token is Maya's")
            assertTrue(tokens.refreshToken != null, "offline_access gave a refresh token")
            val page = URI(opened.single())
            assertTrue(page.toString().startsWith(ShildikRealm.issuer), "the provider's page was opened: $page")
            val returnTo =
                URI(URLDecoder.decode(ShildikRealm.query(page.toString()).getValue("redirect_uri"), Charsets.UTF_8))
            assertEquals("http", returnTo.scheme)
            assertEquals("127.0.0.1", returnTo.host)
            assertEquals(URI(ShildikRealm.desktopRedirect).path, returnTo.path, "the path the chart registers")
            assertTrue(returnTo.port > 0, "a port of the listener's own: $returnTo")
            val landed = returns.single()
            assertTrue(
                landed.location.startsWith(returnTo.toString() + "?"),
                "the provider sent the browser to the listener",
            )
            assertEquals(200, landed.status)
            assertTrue("Signed in to Haul" in landed.page, "the browser is told to go back to the app: ${landed.page}")
            assertEquals(listOf(tokens.accessToken to GUEST), merges.toList(), "the guest cart merged into Maya's")
            assertConnectionRefused(returnTo.port, "the listener is closed once the code is back")
            assertEquals(
                Session(guestId = GUEST, tokens = tokens),
                PreferencesSessionStore(ORIGIN, node).load(),
                "the next window is still Maya's",
            )
        }

    @Test
    fun `a token past its lifetime is renewed at the provider, which rotates the refresh token`() =
        runBlocking {
            val identity = identity()
            withTimeout(1.minutes) { identity.signIn() }
            val first = checkNotNull(identity.session.value.tokens)
            pastLifetimeOf(first)

            val account = account(identity)

            assertEquals(HttpStatusCode.OK, account.status, "the page answered once the token was renewed")
            assertEquals(ShildikRealm.MAYA, account.bodyAsText())
            val renewed = checkNotNull(identity.session.value.tokens)
            assertNotEquals(first.accessToken, renewed.accessToken)
            // The spent one is not asked again: shildik takes it for a few seconds more, and after that revokes the
            // whole chain as replayed (its protocol-oidc-browser §4a).
            assertNotEquals(first.refreshToken, renewed.refreshToken, "shildik rotates the refresh token")
            assertEquals(listOf(first.accessToken, renewed.accessToken), asked.map { it.second })
            assertEquals(renewed, PreferencesSessionStore(ORIGIN, node).load().tokens, "the renewed tokens are kept")
        }

    @Test
    fun `a demo shopper's token, signed in at the browser's return page, is renewed on the desktop too`() =
        runBlocking {
            // The server makes a demo shopper's tokens at the same client, returning to the browser's page (B-162).
            val demo = ShildikRealm.browserTokens()
            sessions.save(Session(guestId = GUEST, tokens = demo))
            pastLifetimeOf(demo)
            val identity = identity(LoopbackSignInFlow(browse = { error("no browser is opened to renew") }))

            val account = account(identity)

            assertEquals(HttpStatusCode.OK, account.status, "renewed where B-176 left it to lapse")
            val renewed = checkNotNull(identity.session.value.tokens)
            assertNotEquals(demo.accessToken, renewed.accessToken)
            assertEquals(renewed, PreferencesSessionStore(ORIGIN, node).load().tokens, "kept as a sign-in's are")
        }

    @Test
    fun `signing out forgets the tokens here and in the preferences, and the next request is the guest's`() =
        runBlocking {
            sessions.save(Session(guestId = GUEST))
            val identity = identity()
            withTimeout(1.minutes) { identity.signIn() }

            identity.signOut()
            account(identity)

            assertEquals(Session(guestId = GUEST), identity.session.value)
            assertEquals(
                Session(guestId = GUEST),
                PreferencesSessionStore(ORIGIN, node).load(),
                "the next window is the guest's",
            )
            assertEquals("account" to GUEST, asked.last(), "asked as the guest the shopper was")
        }

    @Test
    fun `a press while a sign-in is under way opens its page again and starts no second one`() =
        runBlocking {
            val held = CopyOnWriteArrayList<String>()
            val identity = identity(LoopbackSignInFlow(browse = { held += it }))
            val first = async { identity.signIn() }
            withTimeout(30.seconds) { while (held.isEmpty()) delay(50) }

            assertFailsWith<SignInPending> { identity.signIn() }
            assertEquals(2, held.size, "the provider's page was opened again")
            assertEquals(held[0], held[1], "the same sign-in's page, not a second one")

            browser(held[0])
            withTimeout(1.minutes) { first.await() }
            assertEquals(
                ShildikRealm.MAYA,
                ShildikRealm.subject(checkNotNull(identity.session.value.tokens).accessToken),
            )
        }

    @Test
    fun `a sign-in nobody finishes ends as one that did not go through, and its port is closed`() =
        runBlocking {
            val held = CopyOnWriteArrayList<String>()
            val identity = identity(LoopbackSignInFlow(browse = { held += it }, waitAtMost = 2.seconds))

            val failure = assertFailsWith<Exception> { withTimeout(1.minutes) { identity.signIn() } }

            assertTrue(
                generateSequence<Throwable>(failure) { it.cause }.any { it is SignInAbandoned },
                "abandoned: $failure",
            )
            assertNull(identity.session.value.tokens)
            val returnTo =
                URI(URLDecoder.decode(ShildikRealm.query(held.single()).getValue("redirect_uri"), Charsets.UTF_8))
            assertConnectionRefused(returnTo.port, "nothing listens for an abandoned sign-in")
        }

    private val http = storeClient(ORIGIN, store)

    @Suppress(
        "ktlint:kapkan:wall-clock",
        "the provider's container runs on this machine and stamps `iat` and `exp` by its clock; the store judges by it too",
    )
    private fun providerSecond(): Long = System.currentTimeMillis() / 1000

    /**
     * Moves the store's clock to the end of [tokens]' lifetime, once the provider's has moved on from the second they
     * were issued in: a token renewed within that second would carry the same `exp` and be past its lifetime too.
     */
    private fun pastLifetimeOf(tokens: Tokens) {
        val issued = ShildikRealm.claim(tokens.accessToken, "iat").toLong()
        while (providerSecond() <= issued) Thread.sleep(50)
        now = ShildikRealm.claim(tokens.accessToken, "exp").toLong()
    }

    /** A customer's page, asked for through [identity] as the store asks for every page. */
    private suspend fun account(identity: Identity): HttpResponse =
        identity.send { sent -> http.get(ACCOUNT) { sent.forEach { (name, value) -> header(name, value) } } }

    private fun assertConnectionRefused(
        port: Int,
        message: String,
    ) {
        assertFailsWith<ConnectException>(message) { Socket("127.0.0.1", port).close() }
    }

    private companion object {
        const val ORIGIN = "https://stand.test"
        const val GUEST = "g-desktop"
        const val ACCOUNT = "/ui/account"
        val JSON = headersOf(HttpHeaders.ContentType, "application/json")

        fun haulJsonOf(settings: SignInSettings): String =
            haulJson.encodeToString(SignInSettings.serializer(), settings)
    }
}
