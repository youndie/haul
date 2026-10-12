package io.github.youndie.haul.feature.identity

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.CANVAS_NOW
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.registry.haulJson
import io.github.youndie.haul.shell.HEADER_TREE
import io.github.youndie.haul.shell.SIGN_IN_PROMPT_LABEL
import io.github.youndie.haul.shell.SIGN_IN_PROMPT_TAG
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.shell.ktorTransport
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.CART_BUTTON_TAG
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.commands.LoadAction
import io.github.youndie.kompot.standard.ButtonComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
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
 * B-130: a sign-in that ends because the provider refused to renew it is a change of who is looking, as a
 * sign-out is. Maya on her orders presses «Delivered» after her sign-in lapsed: the parts are refused, the
 * renewal is refused, `Identity.send` leaves her the guest she was (B-115), and the page is taken down to the
 * sign-in prompt (B-44, B-62). The header stayed hers — «Maya», her menu, her cart's three — over a tab that
 * was a guest, because the shell read who is looking only when it was drawn again for some other reason. It
 * now follows the session, and asks the server for the guest's header, once.
 */
@OptIn(ExperimentalTestApi::class)
class LapsedHeaderTest {
    private val history = FakeHistory(ORDERS)

    @Test
    fun `a sign-in the provider would not renew draws the guest's header and signing in brings hers back`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val server = Server()
            val identity =
                Identity(
                    IdentityApi(server.http),
                    server.provider,
                    MemorySessionStore(Session(guestId = GUEST_ID, tokens = Tokens("access-old", "refresh-old"))),
                )
            setContent {
                HaulTheme(FixtureFonts.fonts, compact = false) {
                    Storefront(
                        ktorTransport(server.http, ORIGIN, identity::send),
                        history,
                        signIn = identity::signIn,
                        clock = FixedClock,
                        session = identity,
                    )
                }
            }
            waitUntil(timeoutMillis = 5_000) { exists(hasText(ORDERS_TITLE)) }
            assertTrue(exists(hasText("Maya")))
            assertTrue(exists(counted(3)))

            // Her sign-in lapses past renewing: the server refuses the token, the provider the refresh.
            server.accepted -= "access-old"
            onNodeWithText(DELIVERED).performClick()
            waitUntil(timeoutMillis = 5_000) { exists(hasTestTag(SIGN_IN_PROMPT_TAG)) }
            assertFalse(identity.session.value.signedIn, "the lapsed customer is a guest now")

            // The guest's header, as the server draws it: her count, «Sign in», no name.
            waitUntil(timeoutMillis = 5_000) { exists(counted(1)) }
            assertFalse(exists(hasText("Maya")), "the guest's tab drew the customer's name")
            assertFalse(exists(counted(3)), "the guest's tab drew the customer's cart")
            assertTrue(exists(hasText("Sign in") and hasClickAction()))
            assertEquals(listOf(HEADER_TREE), server.requests.filter { it == HEADER_TREE }, "the guest's header, once")
            assertEquals(listOf("refresh-old"), server.provider.refreshedWith)
            assertEquals(GUEST_ID, identity.session.value.guestId, "the guest she was")
            assertFalse(IdentityApi.GUESTS in server.requests, "a new guest was made")

            // Signing in from the prompt brings her page, and her header, back.
            server.accepted += "access-new"
            onNode(hasText(SIGN_IN_PROMPT_LABEL)).performClick()
            waitUntil(timeoutMillis = 5_000) { exists(hasText(ORDERS_TITLE)) }
            waitUntil(timeoutMillis = 5_000) { exists(hasText("Maya")) }
            assertTrue(exists(counted(3)))
            assertFalse(exists(hasTestTag(SIGN_IN_PROMPT_TAG)))
            assertEquals(listOf(ORDERS), history.entries)
        }

    private fun ComposeUiTest.exists(matcher: SemanticsMatcher): Boolean =
        onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    /** The header's cart button showing [count]. */
    private fun counted(count: Int): SemanticsMatcher = hasTestTag(CART_BUTTON_TAG) and hasText(count.toString())

    /**
     * The server as a mock engine: Maya's orders and their parts answer a bearer it [accepted] and refuse
     * everybody else — the guest included — with `401 unauthenticated`; the header alone is Maya's for her
     * bearer and the guest's otherwise; the identity routes answer as the real ones. The provider signs in as
     * `access-new` and refuses every refresh, as shildik's token endpoint does with `400 invalid_grant`.
     */
    private class Server {
        val accepted = mutableSetOf("access-old")
        val provider = Provider()
        val requests = CopyOnWriteArrayList<String>()

        val http =
            HttpClient(
                MockEngine { request ->
                    val bearer = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
                    val customer = bearer != null && bearer in accepted
                    val path = request.url.encodedPath
                    requests += path
                    when {
                        path == IdentityApi.GUESTS -> {
                            respond("""{"id":"g-2"}""", HttpStatusCode.Created, JSON)
                        }

                        path == IdentityApi.SIGN_IN_SETTINGS -> {
                            val settings = haulJson.encodeToString(SignInSettings.serializer(), SETTINGS)
                            respond(settings, HttpStatusCode.OK, JSON)
                        }

                        path == SETTINGS.mergeUrl && request.method == HttpMethod.Post -> {
                            respond("""{"type":"refresh"}""", HttpStatusCode.OK, JSON)
                        }

                        path == HEADER_TREE -> {
                            respond(wire(if (customer) MAYA else GUEST), HttpStatusCode.OK, JSON)
                        }

                        path == "/ui$ORDERS" && customer -> {
                            respond(wire(ordersPage()), HttpStatusCode.OK, JSON)
                        }

                        else -> {
                            respond(UNAUTHENTICATED, HttpStatusCode.Unauthorized, JSON)
                        }
                    }
                },
            )
    }

    private class Provider : SignInFlow {
        val refreshedWith = CopyOnWriteArrayList<String>()

        override suspend fun signIn(settings: suspend () -> SignInSettings): Tokens =
            Tokens("access-new", "refresh-new").also { settings() }

        override suspend fun refresh(
            settings: SignInSettings,
            refreshToken: String,
        ): Tokens {
            refreshedWith += refreshToken
            error("400 invalid_grant")
        }
    }

    private object FixedClock : Clock {
        override fun now(): Instant = CANVAS_NOW
    }

    private companion object {
        const val WIDTH = 1440
        const val HEIGHT = 1000
        const val ORIGIN = "http://haul.test"
        const val GUEST_ID = "g-1"
        const val ORDERS = "/account/orders"
        const val ORDERS_TITLE = "Your orders"
        const val DELIVERED = "Delivered"
        const val UNAUTHENTICATED = """{"code":"unauthenticated","message":"Sign in first"}"""

        val JSON = headersOf(HttpHeaders.ContentType, "application/json")
        val SETTINGS = SignInSettings("http://shildik.test/realms/haul", "haul-web", "openid", "/api/v1/me/cart/merge")

        /** The guest's header: «Sign in», and a count the client could not have drawn on its own. */
        val GUEST =
            HaulHeader(
                id = "header",
                deliverTo = "Brooklyn, NY 11211",
                deliveryPromise = "Free delivery over $35",
                cartCount = 1,
                searchPlaceholder = "Search products",
                categories = listOf("Electronics"),
                account = NavigateAction("/sign-in"),
                cart = NavigateAction("/cart"),
            )

        /** Maya's, with three in her cart. */
        val MAYA = GUEST.copy(customerName = "Maya", cartCount = 3, account = NavigateAction("/account"))

        /** Her orders under her header; «Delivered» loads the parts that change (B-63). */
        fun ordersPage(): KompotComponent =
            ColumnComponent(
                id = "page",
                children =
                    listOf(
                        MAYA,
                        TextComponent(id = "title", text = ORDERS_TITLE),
                        ButtonComponent(
                            id = "delivered",
                            text = DELIVERED,
                            action = LoadAction("/ui/parts$ORDERS?status=delivered"),
                        ),
                    ),
            )

        fun wire(component: KompotComponent): String =
            haulWireJson.encodeToString(PolymorphicSerializer(KompotComponent::class), component)
    }
}
