package io.github.youndie.haul.shell

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.CANVAS_NOW
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.feature.identity.SessionControls
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.CART_BUTTON_TAG
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.LINK_MENU_TAG
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.SIGN_OUT_LABEL
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.TextComponent
import kotlinx.coroutines.awaitCancellation
import kotlinx.serialization.PolymorphicSerializer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * B-105: a page opened directly knows who is signed in. Reloading a missing order, product or category as a
 * customer drew the guest's header — «Sign in», no count — because no tree had drawn one, and its category words
 * all led to `/c`. The shell asks the server for the header alone over such a page, and before it answers draws
 * the customer's own: the account, not the sign-in, and no count.
 */
@OptIn(ExperimentalTestApi::class)
class KnownHeaderTest {
    private val requests = CopyOnWriteArrayList<String>()
    private var signIns = 0
    private val session = FakeSession()

    /** The three addresses of the QA walk, reloaded as Maya: her name, her count, category words to their category. */
    @Test
    fun `a customer landing on a page that is not there sees her own header`() {
        val missing =
            mapOf(
                "/account/orders/HL-99999" to ("order_not_found" to "Order not found"),
                "/p/p-does-not-exist" to ("product_not_found" to "This product is no longer available"),
                "/c/no-such-category" to ("category_not_found" to "This category isn’t here"),
            )
        missing.forEach { (start, answer) ->
            val (code, title) = answer
            runDesktopComposeUiTest(WIDTH, HEIGHT) {
                session.signedIn = true
                val history = FakeHistory(start)
                storefront(history) { path ->
                    when (path) {
                        Address(start).screen -> notFound(code)
                        HEADER_TREE -> tree(MAYA)
                        else -> destination(path)
                    }
                }
                waitUntil(timeoutMillis = 5_000) { exists(hasText(title)) }
                waitUntil(timeoutMillis = 5_000) { exists(hasText("Maya")) }
                assertEquals(false, exists(hasText("Sign in")), "$start: a customer's header offered the sign-in")
                assertEquals(true, exists(counted(3)), start)

                press(hasText("Electronics") and isInRow())
                waitUntil(timeoutMillis = 5_000) { exists(hasText(pageAt("/c/electronics"))) }
                assertEquals(listOf(start, "/c/electronics"), history.entries)
                assertEquals(0, signIns, "$start: the header sent a customer to the sign-in")
            }
        }
    }

    /**
     * Before the header alone answers — or when it never does — the header is the customer's own: the account slot
     * opens the account's menu with «Sign out», «Orders» opens them without a sign-in, and the cart shows no count.
     */
    @Test
    fun `before the header answers a customer's header is hers, with no count`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            session.signedIn = true
            val history = FakeHistory("/account/orders/HL-99999")
            storefront(history) { path ->
                when (path) {
                    "/ui/account/orders/HL-99999" -> notFound("order_not_found")
                    HEADER_TREE -> awaitCancellation()
                    else -> destination(path)
                }
            }
            waitUntil(timeoutMillis = 5_000) { exists(hasText("Order not found")) }
            waitUntil(timeoutMillis = 5_000) { HEADER_TREE in requests }
            assertEquals(false, exists(hasText("Sign in")), "a customer's header offered the sign-in")
            assertEquals(false, exists(counted(0)))

            press(hasText(ACCOUNT_LABEL) and hasClickAction())
            waitUntil(timeoutMillis = 5_000) {
                exists(hasText(SIGN_OUT_LABEL) and hasAnyAncestor(hasTestTag(LINK_MENU_TAG)))
            }
            press(hasText("Account") and hasAnyAncestor(hasTestTag(LINK_MENU_TAG)))
            waitUntil(timeoutMillis = 5_000) { exists(hasText(pageAt("/account"))) }
            history.back()
            waitUntil(timeoutMillis = 5_000) { exists(hasText("Order not found")) }

            press(hasText("Go to your orders") and hasClickAction())
            waitUntil(timeoutMillis = 5_000) { exists(hasText(pageAt("/account/orders"))) }
            assertEquals(listOf("/account/orders/HL-99999", "/account/orders"), history.entries)
            assertEquals(0, signIns, "a customer's orders went through the sign-in")
        }

    /** A guest landing on a missing page: still «Sign in», but her count and category words to their category. */
    @Test
    fun `a guest landing on a page that is not there gets the server's header`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val history = FakeHistory("/p/p-does-not-exist")
            storefront(history) { path ->
                when (path) {
                    "/ui/p/p-does-not-exist" -> notFound("product_not_found")
                    HEADER_TREE -> tree(GUEST)
                    else -> destination(path)
                }
            }
            waitUntil(timeoutMillis = 5_000) { exists(counted(2)) }
            assertEquals(true, exists(hasText("Sign in") and hasClickAction()))

            press(hasText("Electronics") and isInRow())
            waitUntil(timeoutMillis = 5_000) { exists(hasText(pageAt("/c/electronics"))) }
        }

    /**
     * A sign-out on a page with no tree: the header drawn for the customer is not the guest's. The page is drawn
     * again under the guest's header, asked for anew, rather than under the last tree's, which named her.
     */
    @Test
    fun `a sign-out over a page with no tree draws the guest's header`() =
        signOutOverMissingProduct(guestHeader = true) {
            waitUntil(timeoutMillis = 5_000) { exists(counted(2)) }
        }

    /** The same while the guest's header does not arrive: the client's own guest header, not the customer's. */
    @Test
    fun `a sign-out over a page with no tree forgets the customer's header`() =
        signOutOverMissingProduct(guestHeader = false) {
            waitUntil(timeoutMillis = 5_000) { requests.count { it == HEADER_TREE } == 1 }
            assertEquals(false, exists(hasText("Maya")), "the guest's header named the customer")
        }

    /** Maya on `/deals`, then on a missing product under its header; she signs out from the account's menu. */
    private fun signOutOverMissingProduct(
        guestHeader: Boolean,
        then: ComposeUiTest.() -> Unit,
    ) = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        session.signedIn = true
        val history = FakeHistory("/deals")
        history.entries += "/p/p-does-not-exist"
        storefront(history) { path ->
            when {
                path == "/ui/p/p-does-not-exist" -> notFound("product_not_found")
                path == HEADER_TREE && guestHeader -> tree(GUEST)
                path == HEADER_TREE -> HaulResponse(500, """{"code":"internal","message":"boom"}""")
                else -> destination(path, MAYA)
            }
        }
        waitUntil(timeoutMillis = 5_000) { exists(hasText(pageAt("/deals"))) }
        history.forward()
        waitUntil(timeoutMillis = 5_000) { exists(hasText("This product is no longer available")) }
        assertEquals(false, HEADER_TREE in requests, "a header the last tree drew was asked for again")

        press(hasText("Maya") and hasClickAction())
        press(hasText(SIGN_OUT_LABEL) and hasAnyAncestor(hasTestTag(LINK_MENU_TAG)))
        waitUntil(timeoutMillis = 5_000) { exists(hasText("Sign in") and hasClickAction()) }
        then()
        assertEquals(false, exists(hasText("Maya")), "the guest's header named the customer")
        assertEquals(listOf("/deals", "/p/p-does-not-exist"), history.entries)
    }

    private fun ComposeUiTest.press(matcher: SemanticsMatcher) {
        waitUntil(timeoutMillis = 5_000) { exists(matcher) }
        onAllNodes(matcher)[0].performClick()
    }

    private fun ComposeUiTest.exists(matcher: SemanticsMatcher): Boolean =
        onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    /** The header's cart button showing [count]. */
    private fun counted(count: Int): SemanticsMatcher = hasTestTag(CART_BUTTON_TAG) and hasText(count.toString())

    /** Not inside an open menu: the header's own row. */
    private fun isInRow(): SemanticsMatcher = !hasAnyAncestor(hasTestTag(LINK_MENU_TAG))

    private fun ComposeUiTest.storefront(
        history: FakeHistory,
        answer: suspend (path: String) -> HaulResponse,
    ) {
        val transport =
            HaulTransport { path ->
                requests += path
                answer(path)
            }
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                Storefront(transport, history, signIn = { signIns += 1 }, clock = FixedClock, session = session)
            }
        }
    }

    /** Who the shopper is, as the storefront asks. */
    private class FakeSession : SessionControls {
        override var signedIn = false

        override fun signOut() {
            signedIn = false
        }

        override val canSignInHere: Boolean = false

        override suspend fun signInHere(next: String?) = Unit

        override val returnedFromSignIn: Boolean = false

        override suspend fun finishSignInHere(): String? = null
    }

    private object FixedClock : Clock {
        override fun now(): Instant = CANVAS_NOW
    }

    private companion object {
        const val WIDTH = 1440
        const val HEIGHT = 1400

        /** The server's header for a guest with two in the cart: its category words lead to their categories. */
        val GUEST =
            HaulHeader(
                id = "header",
                deliverTo = "Brooklyn, NY 11211",
                deliveryPromise = "Free delivery over $35",
                cartCount = 2,
                searchPlaceholder = "Search products",
                categories = listOf("Electronics"),
                account = NavigateAction("/sign-in"),
                catalog = listOf(Link("Electronics", NavigateAction("/c/electronics"))),
                deals = NavigateAction("/deals"),
                cart = NavigateAction("/cart"),
                orders = NavigateAction("/sign-in?next=%2Faccount%2Forders"),
                saved = NavigateAction("/sign-in?next=%2Faccount%2Fsaved"),
                plus = NavigateAction("/sign-in"),
            )

        /** Maya's, with three in her cart. */
        val MAYA =
            GUEST.copy(
                customerName = "Maya",
                cartCount = 3,
                account = NavigateAction("/account"),
                orders = NavigateAction("/account/orders"),
                saved = NavigateAction("/account/saved"),
                plus = NavigateAction("/account"),
            )

        fun pageAt(address: String) = "The page at $address"

        fun tree(component: KompotComponent) =
            HaulResponse(200, haulWireJson.encodeToString(PolymorphicSerializer(KompotComponent::class), component))

        fun notFound(code: String) = HaulResponse(404, """{"code":"$code","message":"gone"}""")

        /** Any page a control leads to: a tree saying where it is, under [header] when one is given. */
        fun destination(
            path: String,
            header: HaulHeader? = null,
        ): HaulResponse {
            val address = if (path == "/ui/home") "/" else path.removePrefix("/ui")
            return tree(
                ColumnComponent(
                    id = "page",
                    children = listOfNotNull(header, TextComponent(id = "body", text = pageAt(address))),
                ),
            )
        }
    }
}
