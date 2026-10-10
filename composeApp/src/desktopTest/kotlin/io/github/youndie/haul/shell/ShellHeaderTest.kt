package io.github.youndie.haul.shell

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.CANVAS_NOW
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.feature.home.PLUS_START_TAG
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.HEADER_MENU_TAG
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.LINK_MENU_TAG
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.OPEN_MENU
import io.github.youndie.haul.ui.PlusBenefit
import io.github.youndie.haul.ui.PlusTrialDialog
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.CloseAction
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.PresentAction
import io.github.youndie.kompot.standard.RefreshAction
import io.github.youndie.kompot.standard.SequenceAction
import io.github.youndie.kompot.standard.TextComponent
import kotlinx.coroutines.awaitCancellation
import kotlinx.serialization.PolymorphicSerializer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * B-67: the header over a page the shell draws itself — a placeholder, an error, a page that is not there, the
 * sign-in's — leads somewhere. It was `SHELL_HEADER`, whose controls carried nothing: on a failed or loading
 * page only the logo and the search worked, and landing straight on a 404 or the sign-in prompt gave the same
 * dead header. Each page kind is walked control by control: pressed, where it went, back.
 */
@OptIn(ExperimentalTestApi::class)
class ShellHeaderTest {
    private val requests = CopyOnWriteArrayList<String>()
    private var signIns = 0

    /** The home page still on its way: its placeholder's header. */
    @Test
    fun `the header over a page still loading leads somewhere`() = walkFrom("/", pending = true) { never() }

    /** The home page answered with an error (Home_Error, `ErrorShell`). */
    @Test
    fun `the header over a page that did not load leads somewhere`() = walkFrom("/", pending = true) { failed() }

    /** A search that did not answer (Search_Error), the query kept in the field. */
    @Test
    fun `the header over a search that did not respond leads somewhere`() =
        walkFrom("/search?q=mugs", pending = true) { failed() }

    /** The cart's error page. */
    @Test
    fun `the header over a cart that did not load leads somewhere`() = walkFrom("/cart", pending = true) { failed() }

    /** The account's error page. */
    @Test
    fun `the header over an account that did not load leads somewhere`() =
        walkFrom("/account", pending = true) { failed() }

    /** The Saved list's error page. */
    @Test
    fun `the header over a saved list that did not load leads somewhere`() =
        walkFrom("/account/saved", pending = true) { failed() }

    /** Landing straight on a product that is not there (Product_NotFound): no tree yet, a guest's header. */
    @Test
    fun `the header over a product that is not here leads somewhere`() =
        walkFrom("/p/gone", pending = false) { HaulResponse(404, """{"code":"product_not_found","message":"gone"}""") }

    /** Landing straight on a category that is not there. */
    @Test
    fun `the header over a page that is not here leads somewhere`() =
        walkFrom("/c/gone", pending = false) { HaulResponse(404, """{"code":"category_not_found","message":"gone"}""") }

    /** Landing straight on a customer's page as a guest: the prompt (B-44). */
    @Test
    fun `the header over the sign-in prompt leads somewhere`() =
        walkFrom("/checkout", pending = false) { HaulResponse(401, """{"code":"unauthenticated","message":"no"}""") }

    /** Landing straight on the sign-in page (B-66). */
    @Test
    fun `the header over the sign-in page leads somewhere`() = walkFrom("/sign-in", pending = false) { failed() }

    /**
     * An order that is not there (Order_NotFound): «Go to your orders» followed the header's «Orders», which the
     * shell's header did not carry — a press that did nothing. It signs a guest in and lands on the orders.
     */
    @Test
    fun `go to your orders on a missing order lands on the orders`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val history = FakeHistory("/account/orders/o-gone")
            storefront(history) { path ->
                if (path == "/ui/account/orders/o-gone") {
                    HaulResponse(404, """{"code":"order_not_found","message":"gone"}""")
                } else {
                    destination(path)
                }
            }
            waitUntil(timeoutMillis = 5_000) { exists(hasText("Order not found")) }
            walk(history, START_ORDER, pending = false)

            val before = signIns
            press(hasText("Go to your orders") and hasClickAction())
            waitUntil(timeoutMillis = 5_000) { exists(hasText(pageAt("/account/orders"))) }
            assertEquals(listOf(START_ORDER, "/account/orders"), history.entries)
            assertEquals(before + 1, signIns, "a guest's orders did not go through the sign-in")
        }

    /**
     * Once a tree has been drawn, a page the shell draws itself is drawn under that tree's header, whose links are
     * the server's: the category row opens the category itself, not the catalog's root.
     */
    @Test
    fun `after a tree the header of a failed page keeps the tree's links`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val history = FakeHistory("/deals")
            history.entries += "/broken"
            storefront(history) { path -> if (path == "/ui/broken") failed() else destination(path, TREE_HEADER) }
            waitUntil(timeoutMillis = 5_000) { exists(hasText(pageAt("/deals"))) }
            history.forward()
            waitUntil(timeoutMillis = 5_000) { exists(hasText("Retry")) }

            press(hasText("Electronics") and isInRow())
            waitUntil(timeoutMillis = 5_000) { exists(hasText(pageAt("/c/electronics"))) }
            assertEquals(listOf("/deals", "/broken", "/c/electronics"), history.entries)
        }

    /**
     * B-66 left this out: a customer who is not a member presses «HAUL PLUS» on a page the shell draws itself, and
     * the trial's dialog — the `present` the last tree's header carries — was ignored. It is drawn over the page
     * now, and its answer's `refresh` loads the page again.
     */
    @Test
    fun `a non-member's haul plus over a page the shell draws presents the trial`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val history = FakeHistory("/deals")
            history.entries += "/p/gone"
            val trials = CopyOnWriteArrayList<TreeCommand>()
            val commands =
                TreeCommands { command ->
                    trials += command
                    SequenceAction(listOf(CloseAction, RefreshAction))
                }
            storefront(history, treeCommands = commands) { path ->
                if (path == "/ui/p/gone") {
                    HaulResponse(404, """{"code":"product_not_found","message":"gone"}""")
                } else {
                    destination(path, TREE_HEADER.copy(plus = PresentAction(TRIAL, "dialog")))
                }
            }
            waitUntil(timeoutMillis = 5_000) { exists(hasText(pageAt("/deals"))) }
            history.forward()
            waitUntil(timeoutMillis = 5_000) { exists(hasText("This product is no longer available")) }

            press(hasText("HAUL PLUS") and hasClickAction())
            waitUntil(timeoutMillis = 5_000) { exists(hasTestTag(PLUS_START_TAG)) }
            onNodeWithTag(PLUS_START_TAG).performClick()
            waitUntil(timeoutMillis = 5_000) { requests.count { it == "/ui/p/gone" } == 2 }

            assertEquals(listOf<TreeCommand>(TreeCommand.StartTrial(TRIAL.url)), trials.toList())
            waitUntil(timeoutMillis = 5_000) { !exists(hasTestTag(PLUS_START_TAG)) }
            assertEquals(listOf("/deals", "/p/gone"), history.entries)
        }

    /**
     * On a phone the menu button was drawn over a placeholder or an error and did not open (B-73 kept it shut
     * there). It opens, and what it lists leads somewhere.
     */
    @Test
    fun `on a phone the menu over a page that did not load opens and leads somewhere`() =
        runDesktopComposeUiTest(PHONE, HEIGHT) {
            val history = FakeHistory("/")
            storefront(history, compact = true) { path -> if (path == "/ui/home") failed() else destination(path) }
            waitUntil(timeoutMillis = 5_000) { exists(hasText("Retry")) }

            val entries =
                listOf(
                    "Deals" to "/deals",
                    "Orders" to "/account/orders",
                    "Saved" to "/account/saved",
                    "Electronics" to "/c",
                    "Pets" to "/c",
                )
            entries.forEach { (label, to) ->
                openMenu()
                press(hasText(label) and hasAnyAncestor(hasTestTag(HEADER_MENU_TAG)))
                arrivedAt(history, to)
                backTo(history, "/") { exists(hasText("Retry")) }
            }
            val before = signIns
            openMenu()
            press(hasText("Sign in") and hasAnyAncestor(hasTestTag(HEADER_MENU_TAG)))
            waitUntil(timeoutMillis = 5_000) { signIns == before + 1 }
            openMenu()
            press(hasText("HAUL PLUS") and hasAnyAncestor(hasTestTag(HEADER_MENU_TAG)))
            waitUntil(timeoutMillis = 5_000) { signIns == before + 2 }
            assertEquals(listOf("/"), history.entries)
        }

    /** The page at [start], answered by [answer], then every control of its header pressed in turn. */
    private fun walkFrom(
        start: String,
        pending: Boolean,
        answer: suspend () -> HaulResponse,
    ) = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val history = FakeHistory(start)
        val screen = Address(start).screen
        storefront(history) { path -> if (path == screen) answer() else destination(path) }
        waitUntil(timeoutMillis = 5_000) { requests.isNotEmpty() || start == "/sign-in" }
        waitUntil(timeoutMillis = 5_000) { exists(hasText("Catalog")) }
        walk(history, start, pending)
    }

    /**
     * Every control the wide header draws, pressed on the page at [start]: the logo, «Catalog» and an entry of
     * it, the category row, «Orders» and «Saved» (a guest's sign-in, landing on them), the cart, «Deals» and
     * «HAUL PLUS» (the sign-in). The account slot is a placeholder while [pending] — nothing to press — and
     * «Sign in» otherwise. The search field is the shell's own and has its tests (StorefrontTest).
     */
    private fun ComposeUiTest.walk(
        history: FakeHistory,
        start: String,
        pending: Boolean,
    ) {
        val back = { history.entries.size == 1 && exists(hasText("Catalog")) }
        // Each control, where it leads, and whether it goes through a guest's sign-in on the way.
        val controls =
            listOf(
                Triple(hasText("Deals") and hasClickAction() and isInRow(), "/deals", false),
                Triple(hasText("Cart") and hasClickAction(), "/cart", false),
                Triple(hasText("Orders") and hasClickAction(), "/account/orders", true),
                Triple(hasText("Saved") and hasClickAction(), "/account/saved", true),
                Triple(hasText("Fashion", substring = true) and isInRow(), "/c", false),
            )
        controls.forEach { (control, to, signsIn) ->
            val asked = requests.size
            val signed = signIns
            press(control)
            when {
                // A guest's «Saved» on the Saved list: signed in, the page already shown is loaded again.
                to == start && signsIn -> {
                    waitUntil(timeoutMillis = 5_000) { requests.size > asked }
                }

                // The cart's button on the cart is the page already shown, which a link does not open again.
                to == start -> {
                    waitForIdle()
                    assertEquals(listOf(start), history.entries)
                }

                else -> {
                    arrivedAt(history, to)
                    backTo(history, start, back)
                }
            }
            if (signsIn) assertEquals(signed + 1, signIns, "$to did not go through the sign-in")
        }

        press(hasText("Catalog") and hasClickAction())
        waitUntil(timeoutMillis = 5_000) { exists(hasTestTag(LINK_MENU_TAG)) }
        press(hasText("Books") and hasAnyAncestor(hasTestTag(LINK_MENU_TAG)))
        arrivedAt(history, "/c")
        backTo(history, start, back)

        // A guest's sign-in with no next draws the page again for who is looking.
        val before = signIns
        press(hasText("HAUL PLUS") and hasClickAction())
        waitUntil(timeoutMillis = 5_000) { signIns == before + 1 }
        if (pending) {
            assertTrue(!exists(hasText("Sign in")), "the placeholder header drew an account to press")
        } else {
            waitUntil(timeoutMillis = 5_000) { exists(hasText("Sign in") and hasClickAction()) }
            // The header's comes first; the sign-in page's own button reads the same.
            onAllNodes(hasText("Sign in") and hasClickAction())[0].performClick()
            waitUntil(timeoutMillis = 5_000) { signIns == before + 2 }
        }
        assertEquals(listOf(start), history.entries, "a sign-in with no next left the page")

        if (start != "/") {
            waitUntil(timeoutMillis = 5_000) { back() }
            press(hasText("Haul") and hasClickAction())
            arrivedAt(history, "/")
            backTo(history, start, back)
        }
    }

    private fun ComposeUiTest.arrivedAt(
        history: FakeHistory,
        to: String,
    ) {
        waitUntil(timeoutMillis = 5_000) { exists(hasText(pageAt(to))) }
        assertEquals(to, history.location)
    }

    /** Back to [start], the page drawn again as it was. */
    private fun ComposeUiTest.backTo(
        history: FakeHistory,
        start: String,
        drawn: () -> Boolean,
    ) {
        history.back()
        history.entries.subList(1, history.entries.size).clear()
        assertEquals(start, history.location)
        waitUntil(timeoutMillis = 5_000) { drawn() }
    }

    private fun ComposeUiTest.openMenu() {
        onNodeWithContentDescription(OPEN_MENU).performClick()
        waitUntil(timeoutMillis = 5_000) { exists(hasTestTag(HEADER_MENU_TAG)) }
    }

    private fun ComposeUiTest.press(matcher: SemanticsMatcher) {
        waitUntil(timeoutMillis = 5_000) { exists(matcher) }
        onNode(matcher).performClick()
    }

    private fun ComposeUiTest.exists(matcher: SemanticsMatcher): Boolean =
        onAllNodes(matcher, useUnmergedTree = false).fetchSemanticsNodes().isNotEmpty()

    /** Not inside an open menu: the header's own row. */
    private fun isInRow(): SemanticsMatcher =
        !hasAnyAncestor(hasTestTag(LINK_MENU_TAG)) and !hasAnyAncestor(hasTestTag(HEADER_MENU_TAG))

    private fun ComposeUiTest.storefront(
        history: FakeHistory,
        compact: Boolean = false,
        treeCommands: TreeCommands? = null,
        answer: suspend (path: String) -> HaulResponse,
    ) {
        val transport =
            HaulTransport { path ->
                requests += path
                answer(path)
            }
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = compact) {
                Storefront(
                    transport,
                    history,
                    signIn = { signIns += 1 },
                    clock = FixedClock,
                    treeCommands = treeCommands,
                )
            }
        }
    }

    private object FixedClock : Clock {
        override fun now(): Instant = CANVAS_NOW
    }

    private companion object {
        const val WIDTH = 1440
        const val PHONE = 390
        const val HEIGHT = 1400
        const val START_ORDER = "/account/orders/o-gone"

        val TREE_HEADER =
            HaulHeader(
                id = "header",
                deliverTo = "Brooklyn, NY 11211",
                deliveryPromise = "Free delivery over $35",
                customerName = "Sam",
                cartCount = 2,
                searchPlaceholder = "Search 2.4 million products",
                categories = listOf("Electronics"),
                account = NavigateAction("/account"),
                catalog = listOf(Link("Electronics", NavigateAction("/c/electronics"))),
                deals = NavigateAction("/deals"),
                cart = NavigateAction("/cart"),
                orders = NavigateAction("/account/orders"),
                saved = NavigateAction("/account/saved"),
            )

        val TRIAL =
            PlusTrialDialog(
                id = "plus-trial",
                eyebrow = "Haul Plus",
                title = "30 days free",
                benefits = listOf(PlusBenefit("Free delivery on every order", "No minimum basket")),
                terms = "30 days free, then $4.99/month",
                startLabel = "Start trial",
                dismissLabel = "Not now",
                url = "/api/v1/me/plus/trial",
                close = CloseAction,
            )

        fun pageAt(address: String) = "The page at $address"

        /**
         * Any page a control leads to: a tree saying where it is, under [header]. None by default, so the shell
         * has drawn no tree's header and the page walked keeps the shell's own.
         */
        fun destination(
            path: String,
            header: HaulHeader? = null,
        ): HaulResponse {
            val address = if (path == "/ui/home") "/" else path.removePrefix("/ui")
            val tree: KompotComponent =
                ColumnComponent(
                    id = "page",
                    children = listOfNotNull(header, TextComponent(id = "body", text = pageAt(address))),
                )
            return HaulResponse(200, haulWireJson.encodeToString(PolymorphicSerializer(KompotComponent::class), tree))
        }

        fun failed() = HaulResponse(500, """{"code":"internal","message":"boom"}""")

        suspend fun never(): HaulResponse = awaitCancellation()
    }
}
