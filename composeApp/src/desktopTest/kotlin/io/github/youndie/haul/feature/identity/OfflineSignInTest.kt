package io.github.youndie.haul.feature.identity

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.CANVAS_NOW
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.registry.haulJson
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.MESSAGE_TAG
import io.github.youndie.haul.shell.NO_ANSWER
import io.github.youndie.haul.shell.RETRY
import io.github.youndie.haul.shell.SIGN_IN_HERE_LABEL
import io.github.youndie.haul.shell.SIGN_IN_PAGE_LABEL
import io.github.youndie.haul.shell.SIGN_IN_PAGE_TAG
import io.github.youndie.haul.shell.SIGN_IN_PROMPT_LABEL
import io.github.youndie.haul.shell.SIGN_IN_PROMPT_TAG
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.PLUS_PILL_TAG
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.SAVE
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.TextComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.PolymorphicSerializer
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * B-147, found by the v0.6.0 run: offline, a guest's «Sign in», «Orders», «Saved», «HAUL PLUS» and the heart asked
 * the store where to sign in (`GET /api/v1/sign-in`), got no answer, then asked for the page again — also with no
 * answer — and showed nothing, where every other press offline says «Couldn’t reach the store». Each now says so,
 * with «Retry», and leaves the page as it was; online again, «Retry» goes on as the press does.
 *
 * The storefront runs over the real [Identity] and [PopupSignInFlow], against a store played by a mock engine that
 * can go offline: what fails is the request the browser sends, not a stand-in for it.
 */
@OptIn(ExperimentalTestApi::class)
class OfflineSignInTest {
    /** The browser offline: every request fails before it is answered, as `ERR_INTERNET_DISCONNECTED` does. */
    private var offline = false
    private val asked = CopyOnWriteArrayList<String>()
    private val pages = CopyOnWriteArrayList<String>()
    private var signIns = 0

    private val http =
        HttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                asked += path
                if (offline) throw IOException("net::ERR_INTERNET_DISCONNECTED")
                if (path == IdentityApi.SIGN_IN_SETTINGS) {
                    respond(haulJson.encodeToString(SignInSettings.serializer(), SETTINGS), HttpStatusCode.OK, JSON)
                } else {
                    error("nothing answers $path")
                }
            },
        )

    /** The provider, once the store has said where it is: the shopper signs in. */
    private val provider =
        object : SignInFlow {
            override suspend fun signIn(settings: suspend () -> SignInSettings): Tokens {
                settings()
                signIns += 1
                return Tokens("access-1", "refresh-1")
            }

            override suspend fun refresh(
                settings: SignInSettings,
                refreshToken: String,
            ): Tokens = error("no refresh in these tests")
        }

    private val popup =
        object : SignInPopup {
            override val closed: Boolean = false
            override val answered: Boolean = true

            override fun focus() = Unit

            override fun close() = Unit
        }

    private val here = Here()
    private val identity = Identity(IdentityApi(http), PopupSignInFlow({ popup }, provider), MemorySessionStore(), here)

    @Test
    fun `the header's sign-in offline says so and the page stays, and Retry online signs in`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val history = FakeHistory("/cart")
            storefront(history) { path -> if (path == "/ui/cart") ok(page(header(), CART_TEXT)) else null }
            waitUntil(timeoutMillis = 5_000) { exists(hasText(CART_TEXT)) }

            pressOffline(hasText("Sign in") and hasClickAction())
            assertEquals(listOf("/cart"), history.entries)

            offline = false
            retry()
            waitUntil(timeoutMillis = 5_000) { pages.size == 2 }
            assertEquals(1, signIns)
            assertTrue(identity.signedIn)
            assertEquals(listOf("/cart"), history.entries)
        }

    @Test
    fun `a guest's orders offline says so, and Retry online lands on them`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val history = FakeHistory("/cart")
            storefront(history) { path ->
                when (path) {
                    "/ui/cart" -> ok(page(header(), CART_TEXT))
                    "/ui/account/orders" -> ok(page(header(), ORDERS_TEXT))
                    else -> null
                }
            }
            waitUntil(timeoutMillis = 5_000) { exists(hasText(CART_TEXT)) }

            pressOffline(hasText("Orders") and hasClickAction())

            offline = false
            retry()
            waitUntil(timeoutMillis = 5_000) { exists(hasText(ORDERS_TEXT)) }
            assertEquals(listOf("/cart", "/account/orders"), history.entries)
        }

    /** «Saved» on a page that did not load either — offline from the start — under the header the shell draws itself. */
    @Test
    fun `a guest's saved on a page that failed offline says so, and Retry online lands on them`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            offline = true
            val history = FakeHistory("/cart")
            storefront(history) { path -> if (path == "/ui/account/saved") ok(page(header(), SAVED_TEXT)) else null }
            waitUntil(timeoutMillis = 5_000) { exists(hasText(FAILED_PAGE, substring = true)) }

            pressOffline(hasText("Saved") and hasClickAction())

            offline = false
            retry()
            waitUntil(timeoutMillis = 5_000) { exists(hasText(SAVED_TEXT)) }
            assertEquals(listOf("/cart", "/account/saved"), history.entries)
        }

    @Test
    fun `a guest's HAUL PLUS offline says so`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            storefront(FakeHistory("/cart")) { path -> if (path == "/ui/cart") ok(page(header(), CART_TEXT)) else null }
            waitUntil(timeoutMillis = 5_000) { exists(hasText(CART_TEXT)) }

            pressOffline(hasTestTag(PLUS_PILL_TAG))
        }

    @Test
    fun `a guest's heart offline says so`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val card =
                ProductCard(
                    id = "card",
                    productId = "p-1",
                    title = "Ostra mug",
                    price = "$18",
                    rating = "4.6",
                    reviews = "(120)",
                    tone = "sand",
                    heartAction = NavigateAction(SignInActions.SIGN_IN),
                )
            storefront(FakeHistory("/c/mugs")) { path ->
                if (path == "/ui/c/mugs") ok(page(header(), CART_TEXT, card)) else null
            }
            waitUntil(timeoutMillis = 5_000) { exists(hasText(CART_TEXT)) }

            pressOffline(hasContentDescription(SAVE) and hasClickAction())
        }

    /** A customer's page asks for the sign-in (B-44): offline, its press stays on the prompt rather than going home. */
    @Test
    fun `the prompt's sign-in offline says so and stays, and Retry online loads the page`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val history = FakeHistory("/checkout")
            storefront(history) { path ->
                when {
                    path != "/ui/checkout" -> null
                    identity.signedIn -> ok(page(header(), CHECKOUT_TEXT))
                    else -> HaulResponse(401, """{"code":"unauthenticated","message":"Sign in first"}""")
                }
            }
            waitUntil(timeoutMillis = 5_000) { exists(hasTestTag(SIGN_IN_PROMPT_TAG)) }

            pressOffline(hasText(SIGN_IN_PROMPT_LABEL) and hasClickAction())
            onNode(hasTestTag(SIGN_IN_PROMPT_TAG)).assertExists()
            assertEquals(listOf("/checkout"), history.entries)

            offline = false
            retry()
            waitUntil(timeoutMillis = 5_000) { exists(hasText(CHECKOUT_TEXT)) }
        }

    /**
     * On the client's own sign-in page (B-66) its «Sign in» offline says so as well: the page drew no messages at all
     * before (B-145's finding), so whatever its presses raised was never seen.
     */
    @Test
    fun `the sign-in page's sign-in offline says so`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val history = FakeHistory("/sign-in?next=%2Fcart")
            storefront(history) { null }
            onNode(hasTestTag(SIGN_IN_PAGE_TAG)).assertExists()

            pressOffline(
                hasText(SIGN_IN_PAGE_LABEL) and hasClickAction() and hasAnyAncestor(hasTestTag(SIGN_IN_PAGE_TAG)),
            )
            assertEquals(listOf("/sign-in?next=%2Fcart"), history.entries)
        }

    /**
     * «Sign in on this page» offline (B-66's way when the popup is blocked) said «The sign-in didn’t go through»: the
     * store was not reached, and the page says that instead, staying as it was; online, «Retry» leaves for the provider.
     */
    @Test
    fun `signing in on this page offline says so, and Retry online leaves for the provider`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            // Back from a sign-in in this tab that was never answered: the page offers «Sign in on this page».
            here.pending = true
            val history = FakeHistory("/sign-in?next=%2Fcart")
            storefront(history) { null }
            waitUntil(timeoutMillis = 5_000) { exists(hasText(SIGN_IN_HERE_LABEL)) }

            pressOffline(hasText(SIGN_IN_HERE_LABEL) and hasClickAction())
            assertTrue(here.left.isEmpty(), "left for the provider without knowing where it is")

            offline = false
            retry()
            waitUntil(timeoutMillis = 5_000) { here.left.isNotEmpty() }
            assertEquals(listOf<String?>("/cart"), here.left)
        }

    /**
     * The browser goes offline and [pressed] is pressed: «Couldn’t reach the store» with «Retry» once the request for
     * where to sign in has failed, and the page is not asked for again — offline that request fails too, and showed
     * nothing (the defect).
     */
    private fun ComposeUiTest.pressOffline(pressed: SemanticsMatcher) {
        waitForIdle()
        offline = true
        val before = pages.toList()
        onAllNodes(pressed).onFirst().performClick()
        waitUntil(timeoutMillis = 5_000) { exists(hasText(NO_ANSWER) and hasAnyAncestor(hasTestTag(MESSAGE_TAG))) }
        onNode(hasText(RETRY) and hasClickAction() and hasAnyAncestor(hasTestTag(MESSAGE_TAG))).assertExists()
        assertTrue(IdentityApi.SIGN_IN_SETTINGS in asked, "the store was never asked where to sign in")
        waitForIdle()
        assertEquals(before, pages.toList(), "the page was asked for again after the sign-in got no answer")
        assertFalse(identity.signedIn)
    }

    private fun ComposeUiTest.retry() {
        onNode(hasText(RETRY) and hasClickAction() and hasAnyAncestor(hasTestTag(MESSAGE_TAG))).performClick()
    }

    /** The storefront over [identity]; [answer] is the store's pages online, `null` for one it has not. */
    private fun ComposeUiTest.storefront(
        history: FakeHistory,
        answer: (path: String) -> HaulResponse?,
    ) {
        val transport =
            HaulTransport { path ->
                pages += path
                if (offline) throw IOException("net::ERR_INTERNET_DISCONNECTED")
                answer(path) ?: error("nothing answers $path")
            }
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                Storefront(transport, history, signIn = identity::signIn, clock = FixedClock, session = identity)
            }
        }
    }

    private fun ComposeUiTest.exists(matcher: SemanticsMatcher): Boolean =
        onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    /** The sign-in in this tab: where it left for, and whether one is back unanswered. */
    private class Here : SignInHere {
        val left = CopyOnWriteArrayList<String?>()
        var pending = false

        override val returned: Boolean = false

        override fun forgetUnanswered(): Boolean = pending.also { pending = false }

        override suspend fun leave(
            settings: SignInSettings,
            next: String?,
        ) {
            left += next
        }

        override suspend fun finish(settings: SignInSettings): SignedInHere = error("no sign-in in this tab came back")
    }

    private object FixedClock : Clock {
        override fun now(): Instant = CANVAS_NOW
    }

    private companion object {
        const val WIDTH = 1440
        const val HEIGHT = 1400
        const val CART_TEXT = "Your cart"
        const val ORDERS_TEXT = "Your orders"
        const val SAVED_TEXT = "Your Saved list"
        const val CHECKOUT_TEXT = "Your checkout"
        const val FAILED_PAGE = "We couldn’t reach Haul"

        val SETTINGS =
            SignInSettings("http://shildik.test/realms/haul", "haul-web", "openid", "/api/v1/me/cart/merge")
        val JSON = headersOf(HttpHeaders.ContentType, "application/json")

        /** A guest's header, as the server draws it: every account control is a way to sign in. */
        fun header(): HaulHeader =
            HaulHeader(
                id = "header",
                deliverTo = "Brooklyn, NY 11211",
                deliveryPromise = "Free delivery over $35",
                cartCount = 0,
                searchPlaceholder = "Search products",
                categories = listOf("Electronics"),
                account = NavigateAction(SignInActions.SIGN_IN),
                orders = NavigateAction(SignInActions.returningTo("/account/orders")),
                saved = NavigateAction(SignInActions.returningTo("/account/saved")),
                plus = NavigateAction(SignInActions.SIGN_IN),
            )

        fun page(
            header: HaulHeader,
            text: String,
            vararg rest: KompotComponent,
        ): KompotComponent =
            ColumnComponent(
                id = "page",
                children = listOf(header, TextComponent(id = "body", text = text)) + rest,
            )

        fun ok(tree: KompotComponent): HaulResponse =
            HaulResponse(200, haulWireJson.encodeToString(PolymorphicSerializer(KompotComponent::class), tree))
    }
}
