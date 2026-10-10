package io.github.youndie.haul.feature.identity

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.CANVAS_NOW
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.SIGN_IN_HERE_LABEL
import io.github.youndie.haul.shell.SIGN_IN_PAGE_LABEL
import io.github.youndie.haul.shell.SIGN_IN_PAGE_TAG
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.shell.TRY_POPUP_AGAIN_LABEL
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.HEADER_MENU_TAG
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.LINK_MENU_TAG
import io.github.youndie.haul.ui.OPEN_MENU
import io.github.youndie.haul.ui.SIGN_OUT_LABEL
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.TextComponent
import kotlinx.serialization.PolymorphicSerializer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * B-66, in the whole storefront: every way into the sign-in reaches it or says why not, and a customer
 * can sign out. Found on the stand: `/sign-in` had no tree, so a reload of it, a link to it, back or
 * forward onto it and a press from the shell's own header drew «This page isn't here»; a popup the
 * browser blocked did nothing anyone could see; and no control signed a customer out.
 */
@OptIn(ExperimentalTestApi::class)
class SignInEverywhereTest {
    private val requests = CopyOnWriteArrayList<String>()
    private var signIns = 0
    private val session = FakeSession()

    /** A reload of `/sign-in?next=…`: the client's page, which asks; the sign-in lands on its next. */
    @Test
    fun `a reload of the sign-in page asks, and the sign-in lands on its next`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val history = FakeHistory("/sign-in?next=%2Faccount%2Forders")
            storefront(history, signIn = { signIns += 1 }) { path ->
                if (path ==
                    "/ui/account/orders"
                ) {
                    ok(page(CUSTOMER_HEADER, ORDERS_TEXT))
                } else {
                    error("nothing answers $path")
                }
            }
            onNodeWithTag(SIGN_IN_PAGE_TAG).assertExists()
            onNodeWithText(NOT_HERE).assertDoesNotExist()
            assertEquals(emptyList(), requests.toList(), "the sign-in page asked the server for a tree")
            assertEquals(0, signIns, "the page's arrival started a sign-in: a browser blocks that popup")

            pressButton(SIGN_IN_PAGE_LABEL)
            waitUntil(timeoutMillis = 5_000) { exists(hasText(ORDERS_TEXT)) }

            assertEquals(1, signIns)
            // The sign-in page gave its entry to the next: back does not return to a sign-in that is done.
            assertEquals(listOf("/account/orders"), history.entries)
        }

    /** Forward onto the sign-in page, back off it and forward again: the page each time, never a 404. */
    @Test
    fun `back and forward onto the sign-in page draw it`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val history = FakeHistory("/cart")
            history.entries += "/sign-in?next=%2Fcart"
            storefront(history) { path ->
                if (path == "/ui/cart") ok(page(GUEST_HEADER, CART_TEXT)) else error("nothing answers $path")
            }
            waitUntil(timeoutMillis = 5_000) { exists(hasText(CART_TEXT)) }

            history.forward()
            waitUntil(timeoutMillis = 5_000) { exists(hasTestTag(SIGN_IN_PAGE_TAG)) }
            history.back()
            waitUntil(timeoutMillis = 5_000) { exists(hasText(CART_TEXT)) }
            history.forward()
            waitUntil(timeoutMillis = 5_000) { exists(hasTestTag(SIGN_IN_PAGE_TAG)) }

            onNodeWithText(NOT_HERE).assertDoesNotExist()
            assertTrue(requests.none { it.startsWith("/ui/sign-in") }, "asked for a tree at $requests")
        }

    /** A customer who arrives at `/sign-in` — a link kept from before — is sent on to its next. */
    @Test
    fun `a customer arriving at the sign-in page goes on to its next`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            session.signedIn = true
            val history = FakeHistory("/sign-in?next=%2Faccount%2Forders")
            storefront(history, signIn = { signIns += 1 }) { path ->
                if (path ==
                    "/ui/account/orders"
                ) {
                    ok(page(CUSTOMER_HEADER, ORDERS_TEXT))
                } else {
                    error("nothing answers $path")
                }
            }
            waitUntil(timeoutMillis = 5_000) { exists(hasText(ORDERS_TEXT)) }

            assertEquals(0, signIns)
            assertEquals(listOf("/account/orders"), history.entries)
        }

    /**
     * The stand's defect: the browser blocks the popup and the header's «Sign in» did nothing visible. Now
     * the shopper is told, on the sign-in page returning to where they were, and offered the sign-in in
     * this tab — and the window once more.
     */
    @Test
    fun `a blocked popup says so and offers the sign-in on this page`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val history = FakeHistory("/cart")
            storefront(history, signIn = {
                signIns += 1
                throw SignInPopupBlocked()
            }) { path ->
                if (path == "/ui/cart") ok(page(GUEST_HEADER, CART_TEXT)) else error("nothing answers $path")
            }
            waitUntil(timeoutMillis = 5_000) { exists(hasText(CART_TEXT)) }

            onNode(hasText("Sign in") and hasClickAction()).performClick()
            waitUntil(timeoutMillis = 5_000) { exists(hasText(BLOCKED)) }

            assertEquals(listOf("/cart", "/sign-in?next=%2Fcart"), history.entries)
            pressButton(TRY_POPUP_AGAIN_LABEL)
            waitForIdle()
            onNodeWithText(BLOCKED).assertExists()
            assertEquals(2, signIns)

            pressButton(SIGN_IN_HERE_LABEL)
            waitUntil(timeoutMillis = 5_000) { session.leftWith.isNotEmpty() }
            assertEquals(listOf<String?>("/cart"), session.leftWith)
        }

    /** The sign-in in this tab comes back to `/sign-in`: finished there, then on to the next it kept. */
    @Test
    fun `a sign-in in this tab that comes back lands on its next`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            session.returned = true
            session.finish = { "/account/orders" }
            val history = FakeHistory("/sign-in")
            storefront(history) { path ->
                if (path ==
                    "/ui/account/orders"
                ) {
                    ok(page(CUSTOMER_HEADER, ORDERS_TEXT))
                } else {
                    error("nothing answers $path")
                }
            }
            waitUntil(timeoutMillis = 5_000) { exists(hasText(ORDERS_TEXT)) }

            assertEquals(listOf("/account/orders"), history.entries)
        }

    /** One that did not go through says so, and offers it again. */
    @Test
    fun `a sign-in in this tab that failed says so and offers another`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            session.returned = true
            session.finish = { error("the provider refused the code") }
            val history = FakeHistory("/sign-in")
            storefront(history) { path -> error("nothing answers $path") }
            waitUntil(timeoutMillis = 5_000) { exists(hasText("The sign-in didn’t go through")) }

            onNode(hasText(SIGN_IN_HERE_LABEL) and hasClickAction()).assertExists()
            assertEquals(listOf("/sign-in"), history.entries)
        }

    /**
     * The shell's own header — here over a product that is not there — followed `/sign-in?next=…` as a
     * page: `/ui/sign-in`, «This page isn't here». Now it signs in, and lands on the orders.
     */
    @Test
    fun `the header the shell draws itself signs in and lands on its next`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val history = FakeHistory("/cart")
            history.entries += "/p/gone"
            storefront(history, signIn = { signIns += 1 }) { path ->
                when (path) {
                    "/ui/cart" -> ok(page(GUEST_HEADER, CART_TEXT))
                    "/ui/p/gone" -> HaulResponse(404, """{"code":"product_not_found","message":"No product gone"}""")
                    "/ui/account/orders" -> ok(page(CUSTOMER_HEADER, ORDERS_TEXT))
                    else -> error("nothing answers $path")
                }
            }
            waitUntil(timeoutMillis = 5_000) { exists(hasText(CART_TEXT)) }
            history.forward()
            waitUntil(timeoutMillis = 5_000) { exists(hasText("This product is no longer available")) }

            onNodeWithText("Orders").performClick()
            waitUntil(timeoutMillis = 5_000) { exists(hasText(ORDERS_TEXT)) }

            assertEquals(1, signIns)
            assertEquals(listOf("/cart", "/p/gone", "/account/orders"), history.entries)
            assertTrue(requests.none { it.startsWith("/ui/sign-in") }, "asked for a tree at $requests")
        }

    /** A customer on their account signs out from the header's account menu, and leaves the customer's page. */
    @Test
    fun `a customer signs out from the header and leaves a customer's page for home`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            session.signedIn = true
            val history = FakeHistory("/account")
            storefront(history) { path ->
                when (path) {
                    "/ui/account" -> ok(page(CUSTOMER_HEADER, ACCOUNT_TEXT))
                    "/ui/home" -> ok(page(GUEST_HEADER, HOME_TEXT))
                    else -> error("nothing answers $path")
                }
            }
            waitUntil(timeoutMillis = 5_000) { exists(hasText(ACCOUNT_TEXT)) }

            onNodeWithText(CUSTOMER).performClick()
            onNodeWithTag(LINK_MENU_TAG).assertExists()
            onNodeWithText(SIGN_OUT_LABEL).performClick()
            waitUntil(timeoutMillis = 5_000) { exists(hasText(HOME_TEXT)) }

            assertEquals(1, session.signOuts)
            assertEquals(listOf("/account", "/"), history.entries)
        }

    /** Off a customer's page, the page stays and is drawn again for the guest the shopper now is. */
    @Test
    fun `signing out elsewhere draws the page again`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            session.signedIn = true
            val history = FakeHistory("/cart")
            var signedOut = false
            storefront(history) { path ->
                when {
                    path == "/ui/cart" && signedOut -> ok(page(GUEST_HEADER, CART_TEXT))
                    path == "/ui/cart" -> ok(page(CUSTOMER_HEADER, CART_TEXT))
                    else -> error("nothing answers $path")
                }
            }
            session.onSignOut = { signedOut = true }
            waitUntil(timeoutMillis = 5_000) { exists(hasText(CART_TEXT)) }

            onNodeWithText(CUSTOMER).performClick()
            onNodeWithText(SIGN_OUT_LABEL).performClick()
            waitUntil(timeoutMillis = 5_000) { requests.size == 2 }
            waitUntil(timeoutMillis = 5_000) { exists(hasText("Sign in")) }

            assertEquals(1, session.signOuts)
            assertEquals(listOf("/cart"), history.entries)
            onNodeWithText(CUSTOMER).assertDoesNotExist()
        }

    /**
     * The header the shell draws itself had no account to press either: its «Sign in» had no renderer to
     * hand it the action. It signs in now, through the page's own way of following links.
     */
    @Test
    fun `the account slot of the header the shell draws itself signs in`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val history = FakeHistory("/cart")
            history.entries += "/p/gone"
            storefront(history, signIn = { signIns += 1 }) { path ->
                when (path) {
                    "/ui/cart" -> ok(page(GUEST_HEADER, CART_TEXT))
                    "/ui/p/gone" -> HaulResponse(404, """{"code":"product_not_found","message":"No product gone"}""")
                    else -> error("nothing answers $path")
                }
            }
            waitUntil(timeoutMillis = 5_000) { exists(hasText(CART_TEXT)) }
            history.forward()
            waitUntil(timeoutMillis = 5_000) { exists(hasText("This product is no longer available")) }

            onNode(hasText("Sign in") and hasClickAction()).performClick()
            waitUntil(timeoutMillis = 5_000) { signIns == 1 }
            waitUntil(timeoutMillis = 5_000) { requests.count { it == "/ui/p/gone" } == 2 }

            // No next: the page is loaded again for who is looking now.
            assertEquals(listOf("/cart", "/p/gone"), history.entries)
        }

    /** On a phone the header's menu (B-73) is the way in: a guest's «Orders» there signs in and lands on them. */
    @Test
    fun `on a phone the menu's orders signs a guest in and lands on them`() =
        runDesktopComposeUiTest(PHONE, HEIGHT) {
            val history = FakeHistory("/cart")
            storefront(history, compact = true, signIn = { signIns += 1 }) { path ->
                when (path) {
                    "/ui/cart" -> ok(page(GUEST_HEADER, CART_TEXT))
                    "/ui/account/orders" -> ok(page(CUSTOMER_HEADER, ORDERS_TEXT))
                    else -> error("nothing answers $path")
                }
            }
            waitUntil(timeoutMillis = 5_000) { exists(hasText(CART_TEXT)) }

            openMenu()
            inMenu("Orders").performClick()
            waitUntil(timeoutMillis = 5_000) { exists(hasText(ORDERS_TEXT)) }

            assertEquals(1, signIns)
            assertEquals(listOf("/cart", "/account/orders"), history.entries)
        }

    /** On a phone a customer signs out from the header's menu; from a customer's page that is home. */
    @Test
    fun `on a phone a customer signs out from the header's menu`() =
        runDesktopComposeUiTest(PHONE, HEIGHT) {
            session.signedIn = true
            val history = FakeHistory("/account")
            storefront(history, compact = true) { path ->
                when (path) {
                    "/ui/account" -> ok(page(CUSTOMER_HEADER, ACCOUNT_TEXT))
                    "/ui/home" -> ok(page(GUEST_HEADER, HOME_TEXT))
                    else -> error("nothing answers $path")
                }
            }
            waitUntil(timeoutMillis = 5_000) { exists(hasText(ACCOUNT_TEXT)) }

            openMenu()
            inMenu(SIGN_OUT_LABEL).performClick()
            waitUntil(timeoutMillis = 5_000) { exists(hasText(HOME_TEXT)) }

            assertEquals(1, session.signOuts)
            assertEquals(listOf("/account", "/"), history.entries)
            onNodeWithTag(HEADER_MENU_TAG).assertDoesNotExist()
        }

    private fun ComposeUiTest.openMenu() {
        onNodeWithContentDescription(OPEN_MENU).performClick()
        waitUntil(timeoutMillis = 5_000) { exists(hasTestTag(HEADER_MENU_TAG)) }
    }

    private fun ComposeUiTest.inMenu(text: String) =
        onNode(hasText(text) and hasAnyAncestor(hasTestTag(HEADER_MENU_TAG)))

    private fun ComposeUiTest.storefront(
        history: FakeHistory,
        compact: Boolean = false,
        signIn: suspend () -> Unit = { signIns += 1 },
        answer: (path: String) -> HaulResponse,
    ) {
        val transport =
            HaulTransport { path ->
                requests += path
                answer(path)
            }
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = compact) {
                Storefront(transport, history, signIn = signIn, clock = FixedClock, session = session)
            }
        }
    }

    private fun ComposeUiTest.pressButton(label: String) {
        onNode(hasText(label) and hasClickAction()).performClick()
    }

    private fun ComposeUiTest.exists(matcher: SemanticsMatcher): Boolean =
        onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    /** Who the shopper is, as the storefront asks: what it was told, and what it was asked to do. */
    private class FakeSession : SessionControls {
        override var signedIn = false
        var signOuts = 0
        var onSignOut: () -> Unit = {}
        val leftWith = CopyOnWriteArrayList<String?>()
        var returned = false
        var finish: () -> String? = { null }

        override fun signOut() {
            signOuts += 1
            signedIn = false
            onSignOut()
        }

        override val canSignInHere: Boolean = true

        override suspend fun signInHere(next: String?) {
            leftWith += next
        }

        override val returnedFromSignIn: Boolean get() = returned

        override suspend fun finishSignInHere(): String? = finish()
    }

    private object FixedClock : Clock {
        override fun now(): Instant = CANVAS_NOW
    }

    private companion object {
        const val WIDTH = 1440
        const val PHONE = 390
        const val HEIGHT = 1400
        const val CUSTOMER = "Maya"
        const val NOT_HERE = "This page isn’t here"
        const val BLOCKED = "The browser blocked the window"
        const val ORDERS_TEXT = "Your orders"
        const val CART_TEXT = "Your cart"
        const val ACCOUNT_TEXT = "Your account"
        const val HOME_TEXT = "The home page"

        val GUEST_HEADER =
            HaulHeader(
                id = "header",
                deliverTo = "Brooklyn, NY 11211",
                deliveryPromise = "Free delivery over $35",
                customerName = null,
                cartCount = 0,
                searchPlaceholder = "Search 2.4 million products",
                categories = listOf("Electronics"),
                account = NavigateAction("/sign-in"),
                orders = NavigateAction("/sign-in?next=%2Faccount%2Forders"),
                saved = NavigateAction("/sign-in?next=%2Faccount%2Fsaved"),
            )

        val CUSTOMER_HEADER =
            GUEST_HEADER.copy(
                customerName = CUSTOMER,
                account = NavigateAction("/account"),
                orders = NavigateAction("/account/orders"),
                saved = NavigateAction("/account/saved"),
            )

        fun page(
            header: HaulHeader,
            text: String,
        ): KompotComponent =
            ColumnComponent(id = "page", children = listOf(header, TextComponent(id = "body", text = text)))

        fun ok(tree: KompotComponent): HaulResponse =
            HaulResponse(200, haulWireJson.encodeToString(PolymorphicSerializer(KompotComponent::class), tree))
    }
}
