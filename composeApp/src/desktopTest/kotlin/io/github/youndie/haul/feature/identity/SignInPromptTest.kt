package io.github.youndie.haul.feature.identity

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.CANVAS_NOW
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.feature.checkout.PLACE_TAG
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.read
import io.github.youndie.haul.registry.haulJson
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.SIGN_IN_PROMPT_LABEL
import io.github.youndie.haul.shell.SIGN_IN_PROMPT_TAG
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.shell.ktorTransport
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.ButtonComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.RefreshAction
import io.github.youndie.kompot.standard.TextComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.PolymorphicSerializer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * B-44, in the whole storefront: a customer's page refused with `401` — a guest who opened `/checkout`
 * directly, or a customer whose sign-in lapsed past renewing — asks the shopper to sign in instead of
 * drawing «Checkout didn’t load» with a Retry that never helps. The sign-in returns to that page; one
 * that does not go through goes home; a page still refused after a sign-in is an error, not a loop.
 */
@OptIn(ExperimentalTestApi::class)
class SignInPromptTest {
    private val history = FakeHistory("/checkout")
    private val requests = CopyOnWriteArrayList<String>()
    private var signIns = 0

    /**
     * Opening `/checkout` as a guest used to draw Checkout_Error: the tree's `401` was an error like any
     * other. Now it asks for a sign-in whose `next` is the checkout, and the checkout is what is drawn
     * once it has gone through — in place, with no new history entry.
     */
    @Test
    fun `a guest opening the checkout signs in and lands on the checkout`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            var signedIn = false
            val signIn: suspend () -> Unit = {
                signIns += 1
                signedIn = true
            }
            storefront(signIn) { path ->
                when {
                    path == "/ui/checkout" && signedIn -> HaulResponse(200, read(CHECKOUT))
                    path == "/ui/checkout" -> UNAUTHENTICATED
                    else -> error("nothing answers $path")
                }
            }
            onNodeWithTag(SIGN_IN_PROMPT_TAG).assertExists()
            onNodeWithText(CHECKOUT_ERROR).assertDoesNotExist()
            assertEquals(0, signIns, "the page's arrival started a sign-in: a browser blocks that popup")

            pressSignIn()
            waitUntil(timeoutMillis = 5_000) { exists(hasTestTag(PLACE_TAG)) }

            assertEquals(1, signIns)
            assertEquals(listOf("/checkout"), history.entries)
            assertEquals(listOf("/ui/checkout", "/ui/checkout"), requests.toList())
            onNodeWithTag(SIGN_IN_PROMPT_TAG).assertDoesNotExist()
        }

    /** A popup the shopper closed leaves nothing to draw on a customer's page: the guest goes home. */
    @Test
    fun `a sign-in that does not go through lands on the home page`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            storefront(signIn = { throw SignInUnavailable() }) { path ->
                when (path) {
                    "/ui/checkout" -> UNAUTHENTICATED
                    "/ui/home" -> HaulResponse(200, read(HOME))
                    else -> error("nothing answers $path")
                }
            }
            pressSignIn()
            waitUntil(timeoutMillis = 5_000) { "/ui/home" in requests }
            waitForIdle()

            assertEquals(listOf("/checkout", "/"), history.entries)
            assertEquals(listOf("/ui/checkout", "/ui/home"), requests.toList())
            onNodeWithTag(SIGN_IN_PROMPT_TAG).assertDoesNotExist()
        }

    /**
     * No loop: a page the server still refuses after a sign-in that went through is drawn as the error
     * it now is, and neither the page nor its Retry asks for another sign-in.
     */
    @Test
    fun `a page still refused after a sign-in draws the error page and asks no more`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            storefront(signIn = { signIns += 1 }) { path ->
                if (path == "/ui/checkout") UNAUTHENTICATED else error("nothing answers $path")
            }
            pressSignIn()
            waitUntil(timeoutMillis = 5_000) { exists(hasText(CHECKOUT_ERROR)) }
            onNodeWithTag(SIGN_IN_PROMPT_TAG).assertDoesNotExist()

            onNodeWithText("Retry").performClick()
            waitUntil(timeoutMillis = 5_000) { requests.size == 3 }
            waitForIdle()

            onNodeWithText(CHECKOUT_ERROR).assertExists()
            onNodeWithTag(SIGN_IN_PROMPT_TAG).assertDoesNotExist()
            assertEquals(1, signIns)
            assertEquals(listOf("/checkout"), history.entries)
        }

    /**
     * A customer whose sign-in lapsed while away opens the checkout again: the server refuses the old
     * token, the provider refuses to renew it, `Identity.send` signs the customer out and asks again as a
     * guest — refused too. The storefront asks for a sign-in, and the new one lands on the checkout.
     */
    @Test
    fun `a lapsed sign-in that cannot be renewed asks again and lands on the page`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val server = Server(accepted = mutableSetOf(), pages = mapOf("/ui/checkout" to read(CHECKOUT)))
            val identity = server.identity(signedInAs = "access-old")
            identityStorefront(server, identity)

            // The mock engine answers off the composition's clock, so the test waits for it, not for idle.
            waitUntil(timeoutMillis = 5_000) { exists(hasTestTag(SIGN_IN_PROMPT_TAG)) }
            assertFalse(identity.session.value.signedIn, "the lapsed customer is a guest now")

            server.accepted += "access-new"
            pressSignIn()
            waitUntil(timeoutMillis = 5_000) { exists(hasTestTag(PLACE_TAG)) }

            assertTrue(identity.session.value.signedIn)
            assertEquals(listOf("/checkout"), history.entries)
            assertEquals(listOf("refresh-old"), server.provider.refreshedWith)
        }

    /**
     * The same lapse found by a refresh in place — a command's answer, kompot's `refresh` — on a page
     * already drawn: the refresh used to fail quietly and leave a signed-out shopper on a customer's page
     * that no longer answered. Now the page asks for a sign-in, and the new one draws it again.
     */
    @Test
    fun `a refresh refused after the sign-in lapsed asks again and draws the page`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            history.entries[0] = "/account"
            val server = Server(accepted = mutableSetOf("access-old"), pages = mapOf("/ui/account" to ACCOUNT))
            val identity = server.identity(signedInAs = "access-old")
            identityStorefront(server, identity)
            waitUntil(timeoutMillis = 5_000) { exists(hasText(ACCOUNT_TEXT)) }

            server.accepted -= "access-old"
            onNodeWithText("Reload").performClick()
            waitUntil(timeoutMillis = 5_000) { exists(hasTestTag(SIGN_IN_PROMPT_TAG)) }
            assertFalse(identity.session.value.signedIn)

            server.accepted += "access-new"
            pressSignIn()
            waitUntil(timeoutMillis = 5_000) { exists(hasText(ACCOUNT_TEXT)) }

            assertTrue(identity.session.value.signedIn)
            assertEquals(listOf("/account"), history.entries)
            onNodeWithTag(SIGN_IN_PROMPT_TAG).assertDoesNotExist()
        }

    private fun ComposeUiTest.storefront(
        signIn: suspend () -> Unit,
        answer: (path: String) -> HaulResponse,
    ) {
        val transport =
            HaulTransport { path ->
                requests += path
                answer(path)
            }
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                Storefront(transport, history, signIn = signIn, clock = FixedClock)
            }
        }
    }

    /** The storefront over the real [identity] and the browser's transport, against [server]. */
    private fun ComposeUiTest.identityStorefront(
        server: Server,
        identity: Identity,
    ) {
        val transport = ktorTransport(server.http, ORIGIN, identity::send)
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                Storefront(transport, history, signIn = identity::signIn, clock = FixedClock)
            }
        }
    }

    private fun ComposeUiTest.pressSignIn() {
        onNode(hasText(SIGN_IN_PROMPT_LABEL)).performClick()
    }

    private fun ComposeUiTest.exists(matcher: androidx.compose.ui.test.SemanticsMatcher): Boolean =
        onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    /**
     * The server as a mock engine: a screen in [pages] answers a bearer it [accepted] and refuses
     * everybody else — a guest included — with `401`; the identity routes answer as the real ones. The
     * provider signs in as `access-new` and refuses every refresh.
     */
    private class Server(
        val accepted: MutableSet<String>,
        private val pages: Map<String, String>,
    ) {
        val provider = Provider()

        val http =
            HttpClient(
                MockEngine { request ->
                    val bearer = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
                    val path = request.url.encodedPath
                    when {
                        path == IdentityApi.GUESTS -> {
                            respond("""{"id":"g-1"}""", HttpStatusCode.Created, JSON)
                        }

                        path == IdentityApi.SIGN_IN_SETTINGS -> {
                            respond(
                                haulJson.encodeToString(SignInSettings.serializer(), SETTINGS),
                                HttpStatusCode.OK,
                                JSON,
                            )
                        }

                        path == SETTINGS.mergeUrl && request.method == HttpMethod.Post -> {
                            respond("""{"type":"refresh"}""", HttpStatusCode.OK, JSON)
                        }

                        path in pages && bearer != null && bearer in accepted -> {
                            respond(pages.getValue(path), HttpStatusCode.OK, JSON)
                        }

                        else -> {
                            respond(UNAUTHENTICATED.body, HttpStatusCode.Unauthorized, JSON)
                        }
                    }
                },
            )

        fun identity(signedInAs: String): Identity =
            Identity(
                IdentityApi(http),
                provider,
                MemorySessionStore(Session(tokens = Tokens(signedInAs, "refresh-old"))),
            )
    }

    private class Provider : SignInFlow {
        val refreshedWith = CopyOnWriteArrayList<String>()

        override suspend fun signIn(settings: SignInSettings): Tokens = Tokens("access-new", "refresh-new")

        override suspend fun refresh(
            settings: SignInSettings,
            refreshToken: String,
        ): Tokens {
            refreshedWith += refreshToken
            error("the provider refused the refresh")
        }
    }

    private object FixedClock : Clock {
        override fun now(): Instant = CANVAS_NOW
    }

    private companion object {
        const val WIDTH = 1440
        const val HEIGHT = 1400
        const val ORIGIN = "http://haul.test"
        const val CHECKOUT = "checkout_content.json"
        const val HOME = "home_guest.json"
        const val CHECKOUT_ERROR = "Checkout didn’t load"
        const val ACCOUNT_TEXT = "Your account"

        val UNAUTHENTICATED = HaulResponse(401, """{"code":"unauthenticated","message":"Sign in first"}""")
        val JSON = headersOf(HttpHeaders.ContentType, "application/json")
        val SETTINGS = SignInSettings("http://shildik.test/realms/haul", "haul-web", "openid", "/api/v1/me/cart/merge")

        /** A customer's page with a refresh on it: what a command's answer would run. */
        val ACCOUNT: String =
            haulWireJson.encodeToString(
                PolymorphicSerializer(KompotComponent::class),
                ColumnComponent(
                    id = "account",
                    children =
                        listOf(
                            TextComponent(id = "title", text = ACCOUNT_TEXT),
                            ButtonComponent(id = "reload", text = "Reload", action = RefreshAction),
                        ),
                ),
            )
    }
}
