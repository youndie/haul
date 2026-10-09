package io.github.youndie.haul

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.feature.cart.CartCommand
import io.github.youndie.haul.feature.cart.CartCommands
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.cart.LineCommand
import io.github.youndie.haul.feature.catalog.CLOSE_FILTERS
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.LOADING_LINE_TAG
import io.github.youndie.haul.shell.LOADING_TAG
import io.github.youndie.haul.shell.NOT_UPDATED_TAG
import io.github.youndie.haul.shell.PAGE_SCROLL_TAG
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.ADD_TO_CART
import io.github.youndie.haul.ui.AppliedFilters
import io.github.youndie.haul.ui.Breadcrumbs
import io.github.youndie.haul.ui.Crumb
import io.github.youndie.haul.ui.Facet
import io.github.youndie.haul.ui.FacetOption
import io.github.youndie.haul.ui.FacetPanel
import io.github.youndie.haul.ui.FilteredResults
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.commands.LoadAction
import io.github.youndie.kompot.commands.UpdateHistory
import io.github.youndie.kompot.commands.kompotUpdate
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.RefreshAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.PolymorphicSerializer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-63: a press that only filters, sorts or pages a screen is a `load` — one `GET` to the screen's parts —
 * answered with an `update` of the nodes it changes and the address they make. The shell draws the nodes in
 * place and hands the address to the history without loading the page; the line under the header is on while
 * the answer is on its way, and back to the address before loads that page and draws it, not the update. A
 * cart command answers `update` the same way: the header's count changes with no page asked for. A fake
 * transport serves the pages and the parts, gated where an answer has to be caught on its way.
 */
@OptIn(ExperimentalTestApi::class)
class InPlaceAnswersTest {
    private val requests = CopyOnWriteArrayList<String>()
    private val answers = mutableMapOf<String, ArrayDeque<HaulResponse>>()
    private val history = FakeHistory(MUGS)
    private val sent = CopyOnWriteArrayList<CartCommand>()
    private var cartAnswer: KompotAction? = null

    /** Paths whose answer waits until the test completes the gate: an answer still on its way. */
    private val gates = mutableMapOf<String, CompletableDeferred<Unit>>()

    private fun answer(
        path: String,
        vararg responses: HaulResponse,
    ) {
        answers[path] = ArrayDeque(responses.toList())
    }

    private fun gate(path: String) = CompletableDeferred<Unit>().also { gates[path] = it }

    private val transport =
        HaulTransport { path ->
            requests += path
            gates[path]?.await()
            val queue = answers[path] ?: error("nothing answers $path")
            if (queue.size > 1) queue.removeFirst() else queue.first()
        }

    private val cartCommands =
        CartCommands { command ->
            sent += command
            checkNotNull(cartAnswer) { "nothing answers ${command.url}" }
        }

    private fun ComposeUiTest.storefront(compact: Boolean = false) =
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = compact) {
                Storefront(transport, history, signIn = {}, cartCommands = cartCommands)
            }
        }

    private fun ComposeUiTest.exists(text: String) = onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.tagged(tag: String) = onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.waitForText(text: String) = waitUntil(timeoutMillis = 5_000) { exists(text) }

    private fun ComposeUiTest.scrolled(): Float =
        onNodeWithTag(PAGE_SCROLL_TAG).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun ComposeUiTest.scrollBy(pixels: Float) {
        onNodeWithTag(PAGE_SCROLL_TAG).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, pixels) }
        waitForIdle()
    }

    /** The mugs, and Ostra or Lume ticked: as pages, and as the parts their `load` answers with. */
    private fun mugs() {
        answer("/ui$MUGS", ok(catalog(null)))
        answer("/ui$BY_OSTRA", ok(catalog("Ostra")))
        answer("/ui$BY_LUME", ok(catalog("Lume")))
        answer(PARTS + BY_OSTRA, ok(parts(BY_OSTRA, catalog("Ostra"))))
        answer(PARTS + BY_LUME, ok(parts(BY_LUME, catalog("Lume"))))
    }

    /**
     * The acceptance of B-63 in the client: ticking a facet sends one `GET` to its `load` endpoint, not the
     * page's; the results change in place under the same header and scroll, the line on while they come, and
     * the address is the tick's.
     */
    @Test
    fun `ticking a facet loads its parts and draws them in place without a page`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            mugs()
            val arriving = gate(PARTS + BY_OSTRA)
            storefront()
            waitForText(card("Stoneware", 0))
            scrollBy(SCROLL)
            val before = scrolled()

            onNodeWithText("Ostra").performClick()
            waitUntil(timeoutMillis = 5_000) { PARTS + BY_OSTRA in requests }
            waitForIdle()
            onNodeWithTag(LOADING_LINE_TAG).assertExists()
            onNodeWithTag(LOADING_TAG).assertDoesNotExist()
            onNodeWithText(card("Stoneware", 0)).assertExists()

            arriving.complete(Unit)
            waitForText(card("Ostra", 0))
            onNodeWithText(card("Stoneware", 0)).assertDoesNotExist()
            onNodeWithTag(LOADING_LINE_TAG).assertDoesNotExist()
            onNodeWithText(CRUMB, substring = true).assertExists()
            assertEquals(before, scrolled(), "the scroll moved when the parts arrived")
            assertEquals(listOf("/ui$MUGS", PARTS + BY_OSTRA), requests.toList(), "a tick asked for a page")
            assertEquals(listOf(MUGS, BY_OSTRA), history.entries)
        }

    /**
     * Back after a tick is a visit: the page before it is loaded and drawn — the very tree the screen started
     * from, which kompot before 0.40.0.215 took for no news and left under the tick's update (B-64) — and
     * forward loads the tick's address whole.
     */
    @Test
    fun `back after a tick draws the page before it and forward the page of the tick`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            mugs()
            storefront()
            waitForText(card("Stoneware", 0))
            onNodeWithText("Ostra").performClick()
            waitForText(card("Ostra", 0))

            history.back()
            waitForText(card("Stoneware", 0))
            onNodeWithText(card("Ostra", 0)).assertDoesNotExist()
            history.forward()
            waitForText(card("Ostra", 0))
            assertEquals(listOf("/ui$MUGS", PARTS + BY_OSTRA, "/ui$MUGS", "/ui$BY_OSTRA"), requests.toList())
        }

    /** A tick whose answer does not arrive keeps the page as it was under the notice; Retry presses it again. */
    @Test
    fun `a tick whose answer does not arrive keeps the page under a notice and Retry presses it again`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            mugs()
            answer(PARTS + BY_OSTRA, SERVER_ERROR, ok(parts(BY_OSTRA, catalog("Ostra"))))
            storefront()
            waitForText(card("Stoneware", 0))

            onNodeWithText("Ostra").performClick()
            waitUntil(timeoutMillis = 5_000) { tagged(NOT_UPDATED_TAG) }
            onNodeWithText(card("Stoneware", 0)).assertExists()
            onNodeWithTag(LOADING_LINE_TAG).assertDoesNotExist()
            assertEquals(listOf(MUGS), history.entries, "a tick that did not arrive moved the address")

            onNodeWithText("Retry").performClick()
            waitForText(card("Ostra", 0))
            onNodeWithTag(NOT_UPDATED_TAG).assertDoesNotExist()
            assertEquals(listOf("/ui$MUGS", PARTS + BY_OSTRA, PARTS + BY_OSTRA), requests.toList())
            assertEquals(listOf(MUGS, BY_OSTRA), history.entries)
        }

    /** Two ticks in a row: the answer to the first, arriving last, is dropped (kompot SPEC §16.4.7). */
    @Test
    fun `the last tick wins`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            mugs()
            val first = gate(PARTS + BY_OSTRA)
            storefront()
            waitForText(card("Stoneware", 0))
            onNodeWithText("Ostra").performClick()
            waitUntil(timeoutMillis = 5_000) { PARTS + BY_OSTRA in requests }
            onNodeWithText("Lume").performClick()
            waitForText(card("Lume", 0))

            first.complete(Unit)
            waitForIdle()
            onNodeWithText(card("Lume", 0)).assertExists()
            onNodeWithText(card("Ostra", 0)).assertDoesNotExist()
            assertEquals(listOf(MUGS, BY_LUME), history.entries)
        }

    /** An answer that cannot be partial is `navigate`: the address is opened, its page loaded. */
    @Test
    fun `a load answered with navigate opens its address`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            mugs()
            answer(PARTS + BY_OSTRA, ok(NavigateAction(BY_OSTRA)))
            storefront()
            waitForText(card("Stoneware", 0))
            onNodeWithText("Ostra").performClick()
            waitForText(card("Ostra", 0))
            assertEquals(listOf("/ui$MUGS", PARTS + BY_OSTRA, "/ui$BY_OSTRA"), requests.toList())
            assertEquals(listOf(MUGS, BY_OSTRA), history.entries)
        }

    /** «Add to cart» on a card: the answer is the header's count and the card, and no page is asked for. */
    @Test
    fun `a card's plus updates the header's count without a page`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            mugs()
            val first = grid("Stoneware").cards.first()
            cartAnswer =
                kompotUpdate {
                    addComponent(header.copy(cartCount = 7))
                    addComponent(first.copy(add = LineCommand(LINE, LineChange(quantity = 2))))
                }
            storefront()
            waitForText(card("Stoneware", 0))
            assertTrue(!exists("7"), "the cart counts 7 before the press")

            onAllNodes(hasContentDescription(ADD_TO_CART))[0].performClick()
            waitForText("7")
            onAllNodes(hasContentDescription(ADD_TO_CART))[0].performClick()
            waitUntil(timeoutMillis = 5_000) { sent.size == 2 }
            assertEquals(
                listOf(LineChange(quantity = 1), LineChange(quantity = 2)),
                sent.map {
                    (it as CartCommand.ChangeLine).change
                },
            )
            assertEquals(listOf("/ui$MUGS"), requests.toList(), "«+» asked for a page")
        }

    /**
     * A refresh is an arrival even when the page it brings is the one drawn (B-64): the count an `update` wrote
     * into the header goes with it, and the page's own is drawn again.
     */
    @Test
    fun `a refresh that brings the page drawn drops what an update wrote over it`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            mugs()
            cartAnswer = kompotUpdate { addComponent(header.copy(cartCount = 7)) }
            storefront()
            waitForText(card("Stoneware", 0))
            onAllNodes(hasContentDescription(ADD_TO_CART))[0].performClick()
            waitForText("7")

            cartAnswer = RefreshAction
            onAllNodes(hasContentDescription(ADD_TO_CART))[0].performClick()
            waitUntil(timeoutMillis = 5_000) { requests.size == 2 }
            waitUntil(timeoutMillis = 5_000) { !exists("7") }
            assertEquals(listOf("/ui$MUGS", "/ui$MUGS"), requests.toList(), "a refresh asked for another page")
        }

    /**
     * The phone's sheet over a tick (B-54 with B-63): its facets follow nothing while the tick's answer is
     * on its way, stay open over the updated results, and follow the page again when an answer did not arrive.
     */
    @Test
    fun `the filter sheet waits for a tick's answer and follows the page again when it does not arrive`() =
        runDesktopComposeUiTest(PHONE, HEIGHT) {
            mugs()
            answer(PARTS + BY_LUME, SERVER_ERROR)
            val arriving = gate(PARTS + BY_OSTRA)
            storefront(compact = true)
            waitForText("Filters")
            onNodeWithText("Filters").performClick()
            waitUntil(
                timeoutMillis = 5_000,
            ) { onAllNodes(hasContentDescription(CLOSE_FILTERS)).fetchSemanticsNodes().isNotEmpty() }

            onNodeWithText("Ostra").performClick()
            waitUntil(timeoutMillis = 5_000) { PARTS + BY_OSTRA in requests }
            waitForIdle()
            onNodeWithText("Lume").assertHasNoClickAction()
            arriving.complete(Unit)
            waitForText("1 applied")
            onNodeWithContentDescription(CLOSE_FILTERS).assertExists()
            onNodeWithText("Lume").assertHasClickAction()

            onNodeWithText("Lume").performClick()
            waitUntil(timeoutMillis = 5_000) { tagged(NOT_UPDATED_TAG) }
            waitForIdle()
            onNodeWithContentDescription(CLOSE_FILTERS).assertExists()
            onNodeWithText("Lume").assertHasClickAction()
            assertEquals(listOf(MUGS, BY_OSTRA), history.entries)
        }

    private companion object {
        const val WIDTH = 1440
        const val PHONE = 390
        const val HEIGHT = 1000
        const val SCROLL = 240f
        const val PARTS = "/ui/parts"
        const val MUGS = "/c/mugs"
        const val BY_OSTRA = "/c/mugs?brand=Ostra"
        const val BY_LUME = "/c/mugs?brand=Lume"
        const val LINE = "/api/v1/cart/lines/p-0-0?answer=card"
        const val CRUMB = "Kitchen mugs"

        val SERVER_ERROR = HaulResponse(500, """{"code":"internal","message":"boom"}""")

        val header =
            HaulHeader(
                id = "header",
                deliverTo = "Brooklyn, NY 11211",
                deliveryPromise = "Free delivery over $35",
                customerName = "Maya",
                cartCount = 0,
                searchPlaceholder = "Search 2.4 million products",
                categories = listOf("Electronics"),
            )

        fun card(
            brand: String,
            index: Int,
        ) = "$brand Mug $index"

        /** The mugs with [ticked] ticked — Ostra, Lume or none — each tick a `load` of its parts. */
        fun catalog(ticked: String?): KompotComponent {
            fun option(
                brand: String,
                count: Int,
            ): FacetOption {
                val address = if (ticked == brand) MUGS else "$MUGS?brand=$brand"
                return FacetOption(brand, count, selected = ticked == brand, action = LoadAction(PARTS + address))
            }
            return ColumnComponent(
                id = "page",
                children =
                    listOf(
                        header,
                        Breadcrumbs("breadcrumbs", listOf(Crumb("Home", NavigateAction("/")), Crumb(CRUMB))),
                        FilteredResults(
                            id = "results",
                            facets =
                                FacetPanel(
                                    "facets",
                                    listOf(
                                        Facet(
                                            "brand",
                                            "Brand",
                                            "checkbox",
                                            options = listOf(option("Ostra", 12), option("Lume", 9)),
                                        ),
                                    ),
                                ),
                            applied =
                                AppliedFilters(
                                    id = "applied",
                                    chips = emptyList(),
                                    clearLabel = "Clear all",
                                    sortLabel = "Popular",
                                    filterCount = if (ticked == null) 0 else 1,
                                    clearAction = LoadAction(PARTS + MUGS),
                                ),
                            showLabel = "Show 16 items",
                            grid = grid(ticked ?: "Stoneware"),
                        ),
                    ),
            )
        }

        /** Sixteen of [brand]'s mugs, four to a row, so the page has somewhere to scroll; the first one in stock. */
        fun grid(brand: String) =
            ProductGrid(
                "grid",
                List(16) { index ->
                    ProductCard(
                        id = "card-$index",
                        productId = "p-$index",
                        title = card(brand, index),
                        price = "$24",
                        rating = "4.8",
                        reviews = "420",
                        delivery = "Tomorrow",
                        tone = "#E3F5D8",
                        label = "mug",
                        action = NavigateAction("/p/p-$index"),
                        add = if (index == 0) LineCommand(LINE, LineChange(quantity = 1)) else null,
                    )
                },
                columns = 4,
            )

        /** The `load` endpoint's answer for [address]: the results of its [page], and the address. */
        fun parts(
            address: String,
            page: KompotComponent,
        ): KompotAction =
            kompotUpdate(address, UpdateHistory.PUSH) {
                addComponent(
                    (page as ColumnComponent).children.single {
                        it.id ==
                            "results"
                    },
                )
            }

        fun ok(tree: KompotComponent) =
            HaulResponse(200, haulWireJson.encodeToString(PolymorphicSerializer(KompotComponent::class), tree))

        fun ok(action: KompotAction) =
            HaulResponse(200, haulWireJson.encodeToString(PolymorphicSerializer(KompotAction::class), action))
    }
}
