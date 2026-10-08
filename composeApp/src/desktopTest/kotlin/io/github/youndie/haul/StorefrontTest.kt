package io.github.youndie.haul

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.shell.BrowserHistory
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.LOADING_TAG
import io.github.youndie.haul.shell.SUGGEST_DELAY_MS
import io.github.youndie.haul.shell.ShellFailure
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.QuerySuggestion
import io.github.youndie.haul.ui.SEARCH_FIELD_TAG
import io.github.youndie.haul.ui.SearchSuggestPanel
import io.github.youndie.haul.ui.SectionHeader
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.ButtonComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.RefreshAction
import io.github.youndie.kompot.standard.TextComponent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.PolymorphicSerializer
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The shell against a fake transport (B-35): which screen route an address loads, what is drawn while
 * it loads and when it fails, how a tree's actions move between pages and back, `refresh`, the search
 * field's suggestions and submit, and the one clock the countdowns read. Trees are built from the
 * contract and sent as the server sends them, through `haulWireJson`.
 */
@OptIn(ExperimentalTestApi::class)
class StorefrontTest {
    private val requests = CopyOnWriteArrayList<String>()
    private val answers = mutableMapOf<String, ArrayDeque<suspend () -> HaulResponse>>()

    /** Answers [path] with each of [responses] in turn, the last one from then on. */
    private fun answer(
        path: String,
        vararg responses: suspend () -> HaulResponse,
    ) {
        answers[path] = ArrayDeque(responses.toList())
    }

    private val transport =
        HaulTransport { path ->
            requests += path
            val queue = answers[path] ?: error("nothing answers $path")
            val next = if (queue.size > 1) queue.removeFirst() else queue.first()
            next()
        }

    private val history = FakeHistory()
    private var signIns = 0
    private var now: Instant = CANVAS_NOW
    private val clock =
        object : Clock {
            override fun now(): Instant = now
        }

    private fun ComposeUiTest.storefront() =
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                Storefront(transport, history, signIn = { signIns += 1 }, clock = clock)
            }
        }

    @Test
    fun `the home page is the home screen's tree`() =
        runDesktopComposeUiTest(CANVAS_WIDTH, 1_000) {
            answer("/ui/home", ok(home))
            storefront()
            onNodeWithText(CARD_TITLE).assertExists()
            assertEquals(listOf("/ui/home"), requests)
        }

    @Test
    fun `a page on its way draws its placeholders until the tree arrives`() =
        runDesktopComposeUiTest(CANVAS_WIDTH, 1_000) {
            val arrived = CompletableDeferred<HaulResponse>()
            answer("/ui/home", { arrived.await() })
            storefront()
            onNodeWithTag(LOADING_TAG).assertExists()
            onNodeWithText(CARD_TITLE).assertDoesNotExist()
            arrived.complete(ok(home)())
            waitUntil(timeoutMillis = 5_000) { requests.size == 1 && exists(CARD_TITLE) }
            onNodeWithTag(LOADING_TAG).assertDoesNotExist()
        }

    @Test
    fun `a card follows its action to the product page and back returns home`() =
        runDesktopComposeUiTest(CANVAS_WIDTH, 1_000) {
            answer("/ui/home", ok(home))
            answer("/ui/p/p-1", ok(product))
            storefront()
            onNodeWithText(CARD_TITLE).performClick()
            onNodeWithText(PRODUCT_TEXT).assertExists()
            assertEquals(listOf("/", "/p/p-1"), history.entries)

            history.back()
            onNodeWithText(CARD_TITLE).assertExists()
            onNodeWithText(PRODUCT_TEXT).assertDoesNotExist()
            assertEquals(listOf("/ui/home", "/ui/p/p-1", "/ui/home"), requests)
        }

    @Test
    fun `a deep address loads its own screen route`() =
        runDesktopComposeUiTest(CANVAS_WIDTH, 1_000) {
            history.entries[0] = "/c/headphones?brand=Sony"
            answer("/ui/c/headphones?brand=Sony", ok(product))
            storefront()
            onNodeWithText(PRODUCT_TEXT).assertExists()
            assertEquals(listOf("/ui/c/headphones?brand=Sony"), requests)
        }

    @Test
    fun `a server error draws the error page and Retry loads the page again`() =
        runDesktopComposeUiTest(CANVAS_WIDTH, 1_000) {
            answer("/ui/home", status(500, """{"code":"internal","message":"boom"}"""), ok(home))
            storefront()
            onNodeWithText(ShellFailure.Server.message).assertExists()
            onNodeWithText("Retry").performClick()
            onNodeWithText(CARD_TITLE).assertExists()
            assertEquals(listOf("/ui/home", "/ui/home"), requests)
        }

    @Test
    fun `no answer at all says the server could not be reached`() =
        runDesktopComposeUiTest(CANVAS_WIDTH, 1_000) {
            answer("/ui/home", { throw IOException("connection refused") })
            storefront()
            onNodeWithText(ShellFailure.Unreachable.message).assertExists()
        }

    @Test
    fun `a failed search keeps its query and says search did not respond`() =
        runDesktopComposeUiTest(CANVAS_WIDTH, 1_000) {
            history.entries[0] = "/search?q=running%20shoes"
            answer("/ui/search?q=running%20shoes", status(503, ""))
            storefront()
            onNodeWithText("Your query is still in the field. Try again in a moment.").assertExists()
            onNodeWithTag(SEARCH_FIELD_TAG).assertTextContains("running shoes")
        }

    @Test
    fun `a product that is not there is drawn under the header last drawn`() =
        runDesktopComposeUiTest(CANVAS_WIDTH, 1_000) {
            answer("/ui/home", ok(home))
            answer("/ui/p/p-1", status(404, """{"code":"product_not_found","message":"No product p-1"}"""))
            storefront()
            onNodeWithText(CARD_TITLE).performClick()
            onNodeWithText("This product is no longer available").assertExists()
            onNodeWithText(CUSTOMER).assertExists()
            onNodeWithText("Go to the home page").performClick()
            onNodeWithText(CARD_TITLE).assertExists()
            assertEquals(listOf("/", "/p/p-1", "/"), history.entries)
        }

    @Test
    fun `refresh fetches the screen again and draws it in place`() =
        runDesktopComposeUiTest(CANVAS_WIDTH, 1_000) {
            answer("/ui/home", ok(refreshable(1)), ok(refreshable(2)))
            storefront()
            onNodeWithText("Version 1").assertExists()
            onNodeWithText("Reload").performClick()
            onNodeWithText("Version 2").assertExists()
            onNodeWithTag(LOADING_TAG).assertDoesNotExist()
            assertEquals(listOf("/ui/home", "/ui/home"), requests)
            assertEquals(listOf("/"), history.entries, "refresh is not navigation")
        }

    @Test
    fun `the header's sign-in is sign-in's and the screen is drawn again in place`() =
        runDesktopComposeUiTest(CANVAS_WIDTH, 1_000) {
            val guest = page(header.copy(customerName = null, account = NavigateAction("/sign-in")))
            answer("/ui/home", ok(guest), ok(home))
            storefront()
            onNodeWithText("Sign in").performClick()
            onNodeWithText(CARD_TITLE).assertExists()
            assertEquals(1, signIns)
            assertEquals(listOf("/ui/home", "/ui/home"), requests)
            assertEquals(listOf("/"), history.entries, "sign-in is not a page")
        }

    @Test
    fun `typing asks for suggestions once the shopper pauses and Enter opens the results`() =
        runDesktopComposeUiTest(CANVAS_WIDTH, 1_000) {
            answer("/ui/home", ok(home))
            answer("/ui/search/suggest?q=run", ok(suggest))
            answer("/ui/search?q=run", ok(product))
            storefront()
            val field = onNodeWithTag(SEARCH_FIELD_TAG)
            field.performClick()
            mainClock.autoAdvance = false
            field.performTextInput("r")
            mainClock.advanceTimeBy(SUGGEST_DELAY_MS / 2)
            field.performTextInput("u")
            mainClock.advanceTimeBy(SUGGEST_DELAY_MS / 2)
            field.performTextInput("n")
            mainClock.advanceTimeBy(SUGGEST_DELAY_MS * 2)
            mainClock.autoAdvance = true
            onNodeWithText("running shoes").assertExists()
            assertEquals(listOf("/ui/home", "/ui/search/suggest?q=run"), requests, "one request, after the pause")

            field.performKeyInput { pressKey(Key.Enter) }
            onNodeWithText(PRODUCT_TEXT).assertExists()
            onNodeWithText("running shoes").assertDoesNotExist()
            assertEquals(listOf("/", "/search?q=run"), history.entries)
        }

    @Test
    fun `a suggestion follows its own action`() =
        runDesktopComposeUiTest(CANVAS_WIDTH, 1_000) {
            answer("/ui/home", ok(home))
            answer("/ui/search/suggest?q=run", ok(suggest))
            answer("/ui/search?q=running%20shoes", ok(product))
            storefront()
            onNodeWithTag(SEARCH_FIELD_TAG).performClick()
            onNodeWithTag(SEARCH_FIELD_TAG).performTextInput("run")
            waitUntil(timeoutMillis = 5_000) { exists("running shoes") }
            onNodeWithText("running shoes").performClick()
            onNodeWithText(PRODUCT_TEXT).assertExists()
            assertEquals(listOf("/", "/search?q=running%20shoes"), history.entries)
        }

    @Test
    fun `the countdown ticks with the one clock`() =
        runDesktopComposeUiTest(CANVAS_WIDTH, 1_000) {
            answer(
                "/ui/home",
                ok(page(header, SectionHeader(id = "deals", title = "Deals", countdownEndsAt = MIDNIGHT))),
            )
            storefront()
            onNodeWithText("04:12:37").assertExists()
            now += 1.seconds
            mainClock.advanceTimeBy(1_000)
            onNodeWithText("04:12:36").assertExists()
        }

    private fun ComposeUiTest.exists(text: String): Boolean =
        onAllNodes(
            androidx.compose.ui.test
                .hasText(text),
        ).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        /** The canvas's desktop width: the header's field, and the suggest panel under it, are drawn for it. */
        const val CANVAS_WIDTH = 1440
        const val CARD_TITLE = "Sony WH-1000XM6"
        const val PRODUCT_TEXT = "The next page"
        const val CUSTOMER = "Maya"

        /** The canvas's deals end at local midnight: 04:12:37 from its «now». */
        const val MIDNIGHT = "2025-10-08T00:00-04:00"

        val header =
            HaulHeader(
                id = "header",
                deliverTo = "Brooklyn, NY 11211",
                deliveryPromise = "Free delivery over $35",
                customerName = CUSTOMER,
                cartCount = 0,
                searchPlaceholder = "Search 2.4 million products",
                categories = listOf("Electronics"),
            )

        val card =
            ProductCard(
                id = "card-p-1",
                productId = "p-1",
                title = CARD_TITLE,
                price = "$349",
                rating = "4.8",
                reviews = "2,341",
                delivery = "Tomorrow",
                tone = "#E6E4FF",
                label = "headphones",
                action = NavigateAction("/p/p-1"),
            )

        val home = page(header, ProductGrid(id = "grid", cards = listOf(card), columns = 6))
        val product = page(header, TextComponent(id = "next", text = PRODUCT_TEXT))

        val suggest =
            SearchSuggestPanel(
                id = "suggest",
                query = "run",
                suggestions =
                    listOf(
                        QuerySuggestion(
                            typed = "run",
                            completion = "ning shoes",
                            action = NavigateAction("/search?q=running%20shoes"),
                        ),
                    ),
                categories = emptyList(),
                products = emptyList(),
                allResultsLabel = "All 3 results",
            )

        fun refreshable(version: Int) =
            page(
                header,
                TextComponent(id = "version", text = "Version $version"),
                ButtonComponent(id = "reload", text = "Reload", action = RefreshAction),
            )

        fun page(vararg children: KompotComponent): KompotComponent =
            ColumnComponent(id = "page", children = children.toList())

        fun ok(tree: KompotComponent): suspend () -> HaulResponse =
            { HaulResponse(200, haulWireJson.encodeToString(PolymorphicSerializer(KompotComponent::class), tree)) }

        fun status(
            code: Int,
            body: String,
        ): suspend () -> HaulResponse = { HaulResponse(code, body) }
    }
}

/** The browser's history in memory: entries, where the page is among them, and back. */
internal class FakeHistory(
    start: String = "/",
) : BrowserHistory {
    val entries = mutableListOf(start)
    private var index = 0
    private val listeners = mutableListOf<(String) -> Unit>()

    override val location: String get() = entries[index]

    override fun push(location: String) {
        while (entries.size > index + 1) entries.removeAt(entries.lastIndex)
        entries += location
        index = entries.lastIndex
    }

    override fun listen(listener: (location: String) -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    /** The back button: the browser moves first, then tells the page where it arrived. */
    fun back() {
        index -= 1
        listeners.toList().forEach { it(location) }
    }
}
