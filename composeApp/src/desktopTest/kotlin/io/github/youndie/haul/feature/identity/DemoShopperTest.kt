package io.github.youndie.haul.feature.identity

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.CANVAS_NOW
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.Fixture
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.decode
import io.github.youndie.haul.feature.checkout.PLACE_TAG
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.read
import io.github.youndie.haul.registry.haulJson
import io.github.youndie.haul.registry.haulRegistry
import io.github.youndie.haul.shell.DEMO_SHOPPER_LABEL
import io.github.youndie.haul.shell.HEADER_TREE
import io.github.youndie.haul.shell.SIGN_IN_PROMPT_TAG
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.shell.ktorTransport
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.DEMO_SHOPPER_PILL
import io.github.youndie.haul.ui.DEMO_SHOPPER_TAG
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotScreen
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.standard.NavigateAction
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.PolymorphicSerializer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * B-162 in the storefront: «Try as a demo shopper» — the guest header's pill, the sign-in prompt's second button —
 * makes a customer of the visitor's own on the server (`POST /api/v1/demo-shoppers`), keeps its tokens, merges the
 * guest cart, and lands where the press asked, with no popup; a refusal is said over the page.
 */
@OptIn(ExperimentalTestApi::class)
class DemoShopperTest {
    private val history = FakeHistory("/checkout")
    private val demoHeader =
        (
            decode(
                "haul_header_guest.json",
            ) as HaulHeader
        ).copy(demo = NavigateAction("/demo-shopper"))

    @Test
    fun `the wide header draws the demo pill beside Sign in and hands the press its action`() =
        runComposeUiTest {
            val handled = mutableListOf<KompotAction>()
            setContent {
                Fixture(compact = false) {
                    KompotScreen(
                        demoHeader,
                        haulRegistry(),
                        FormController(FormSchema(formId = "none", fields = emptyList())),
                        KompotActionHandler { handled += it },
                    )
                }
            }

            onNodeWithText(DEMO_SHOPPER_PILL).assertExists()
            onNodeWithTag(DEMO_SHOPPER_TAG).performClick()

            assertEquals(listOf<KompotAction>(NavigateAction("/demo-shopper")), handled)
        }

    @Test
    fun `a header without the demo draws no pill`() =
        runComposeUiTest {
            setContent {
                Fixture(compact = false) {
                    KompotScreen(
                        demoHeader.copy(demo = null),
                        haulRegistry(),
                        FormController(FormSchema(formId = "none", fields = emptyList())),
                        KompotActionHandler { },
                    )
                }
            }

            onNodeWithTag(DEMO_SHOPPER_TAG).assertDoesNotExist()
        }

    /**
     * The item's path from the checkout a guest opened: the prompt offers the demo shopper; one press makes one, the
     * guest cart merged into it, and the checkout is drawn for them — no popup, no account.
     */
    @Test
    fun `the prompt's demo shopper signs in in one press and the checkout is drawn`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val server = Server()
            val identity = Identity(IdentityApi(server.http), server.provider, MemorySessionStore())
            storefront(server, identity)
            waitUntil(timeoutMillis = 5_000) { exists(hasText(DEMO_SHOPPER_LABEL)) }
            onNodeWithTag(SIGN_IN_PROMPT_TAG).assertExists()

            onNode(hasText(DEMO_SHOPPER_LABEL)).performClick()
            waitUntil(timeoutMillis = 5_000) { exists(hasTestTag(PLACE_TAG)) }

            assertEquals(1, server.requests.count { it == "POST ${IdentityApi.DEMO_SHOPPERS}" })
            assertTrue(
                "POST ${SETTINGS.mergeUrl}" in server.requests,
                "the guest cart was not merged: ${server.requests}",
            )
            assertEquals(0, server.provider.signIns, "the demo shopper opened the provider's popup")
            assertEquals(Tokens(DEMO_ACCESS, "demo-refresh"), identity.session.value.tokens)
            assertEquals(listOf("/checkout"), history.entries)
        }

    @Test
    fun `a demo shopper the server refuses is said over the prompt, which stays`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val server = Server(refuses = true)
            val identity = Identity(IdentityApi(server.http), server.provider, MemorySessionStore())
            storefront(server, identity)
            waitUntil(timeoutMillis = 5_000) { exists(hasText(DEMO_SHOPPER_LABEL)) }

            onNode(hasText(DEMO_SHOPPER_LABEL)).performClick()
            waitUntil(timeoutMillis = 5_000) { exists(hasText(FULL)) }

            onNodeWithTag(SIGN_IN_PROMPT_TAG).assertExists()
            assertFalse(identity.signedIn)
        }

    @Test
    fun `the demo shopper's word is sign-in's to answer, and returns to its next`() =
        runBlocking {
            val demos = mutableListOf<Unit>()
            val opened = mutableListOf<String>()
            var redraws = 0
            val actions =
                SignInActions(
                    signIn = { error("the popup's sign-in ran for the demo shopper") },
                    redraw = { redraws++ },
                    demo = { demos += Unit },
                    open = { opened += it },
                )

            assertTrue(SignInActions.claims(NavigateAction(SignInActions.DEMO_SHOPPER)))
            assertTrue(actions.handle(NavigateAction(SignInActions.demoReturningTo("/checkout"))))
            assertTrue(actions.handle(NavigateAction(SignInActions.DEMO_SHOPPER)))

            assertEquals(2, demos.size)
            assertEquals(listOf("/checkout"), opened)
            assertEquals(1, redraws)
            assertFalse(SignInActions.claims(NavigateAction("/demo-shoppers-wanted")))
        }

    @Test
    fun `a refused demo shopper is told with the server's reason, not as a closed popup`() =
        runBlocking {
            val reasons = mutableListOf<String?>()
            var cancelled = 0
            val actions =
                SignInActions(
                    signIn = {},
                    redraw = {},
                    cancelled = { cancelled++ },
                    demo = { throw DemoShopperRefused(FULL) },
                    refused = { reasons += it },
                    open = {},
                )

            actions.handle(NavigateAction(SignInActions.DEMO_SHOPPER))

            assertEquals(listOf<String?>(FULL), reasons)
            assertEquals(0, cancelled)
        }

    private fun ComposeUiTest.storefront(
        server: Server,
        identity: Identity,
    ) {
        val transport = ktorTransport(server.http, ORIGIN, identity::send)
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                Storefront(transport, history, signIn = identity::signIn, clock = FixedClock, session = identity)
            }
        }
    }

    private fun ComposeUiTest.exists(matcher: androidx.compose.ui.test.SemanticsMatcher): Boolean =
        onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    /**
     * The server as a mock engine: the checkout answers the demo shopper's bearer and refuses everybody else with
     * `401`; the header alone is a guest's with the demo offered; the identity routes answer as the real ones — the
     * demo shopper's with tokens, or `503` when it [refuses].
     */
    private inner class Server(
        private val refuses: Boolean = false,
    ) {
        val provider = Provider()
        val requests = CopyOnWriteArrayList<String>()

        val http =
            HttpClient(
                MockEngine { request ->
                    val bearer = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
                    val path = request.url.encodedPath
                    requests += "${request.method.value} $path"
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

                        path == IdentityApi.DEMO_SHOPPERS && refuses -> {
                            respond(
                                """{"code":"unavailable","message":"$FULL"}""",
                                HttpStatusCode.ServiceUnavailable,
                                JSON,
                            )
                        }

                        path == IdentityApi.DEMO_SHOPPERS -> {
                            respond(
                                """{"accessToken":"$DEMO_ACCESS","refreshToken":"demo-refresh"}""",
                                HttpStatusCode.Created,
                                JSON,
                            )
                        }

                        path == SETTINGS.mergeUrl && request.method == HttpMethod.Post -> {
                            respond("""{"type":"refresh"}""", HttpStatusCode.OK, JSON)
                        }

                        path == HEADER_TREE -> {
                            respond(
                                haulWireJson.encodeToString(PolymorphicSerializer(KompotComponent::class), demoHeader),
                                HttpStatusCode.OK,
                                JSON,
                            )
                        }

                        path == "/ui/checkout" && bearer == DEMO_ACCESS -> {
                            respond(read(CHECKOUT), HttpStatusCode.OK, JSON)
                        }

                        else -> {
                            respond(
                                """{"code":"unauthenticated","message":"Sign in first"}""",
                                HttpStatusCode.Unauthorized,
                                JSON,
                            )
                        }
                    }
                },
            )
    }

    private class Provider : SignInFlow {
        var signIns = 0

        override suspend fun signIn(settings: suspend () -> SignInSettings): Tokens {
            signIns += 1
            settings()
            return Tokens("access-popup", null)
        }

        override suspend fun refresh(
            settings: SignInSettings,
            refreshToken: String,
        ): Tokens = error("the provider refused the refresh")
    }

    private object FixedClock : Clock {
        override fun now(): Instant = CANVAS_NOW
    }

    private companion object {
        const val WIDTH = 1440
        const val HEIGHT = 1400
        const val ORIGIN = "http://haul.test"
        const val CHECKOUT = "checkout_content.json"
        const val DEMO_ACCESS = "demo-access"
        const val FULL = "Every demo shopper for today is taken"

        val JSON = headersOf(HttpHeaders.ContentType, "application/json")
        val SETTINGS = SignInSettings("http://shildik.test/realms/haul", "haul-web", "openid", "/api/v1/me/cart/merge")
    }
}
