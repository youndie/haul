package io.github.youndie.haul

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.LOADING_LINE_TAG
import io.github.youndie.haul.shell.LOADING_TAG
import io.github.youndie.haul.shell.NOT_UPDATED_TAG
import io.github.youndie.haul.shell.NOT_UPDATED_TITLE
import io.github.youndie.haul.shell.PAGE_SCROLL_TAG
import io.github.youndie.haul.shell.SIGN_IN_PROMPT_TAG
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.theme.HaulTheme
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
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.ButtonComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.TextComponent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.PolymorphicSerializer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-62: a screen is a path. A facet, a sort or a page of results is a new address of the same screen,
 * and the shell loads it behind the page that is drawn — the header, the breadcrumbs and the scroll stay,
 * no placeholder comes in between, and a line under the header says a load is on its way — while another
 * path is another screen, drawn from its placeholder at the top. A load that does not arrive keeps the
 * page under a notice with Retry. A fake transport serves the trees, gated where a page has to be caught
 * on its way. The trees here carry `navigate`, which B-63 turned the server's filters into `load`
 * (`InPlaceAnswersTest`); a `navigate` within a screen still keeps the page, and back and forward are visits.
 */
@OptIn(ExperimentalTestApi::class)
class KeptPageTest {
    private val requests = CopyOnWriteArrayList<String>()
    private val answers = mutableMapOf<String, ArrayDeque<HaulResponse>>()
    private val history = FakeHistory(MUGS)

    /** Paths whose answer waits until the test completes the gate: a page still on its way. */
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

    private fun ComposeUiTest.storefront(compact: Boolean = false) =
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = compact) {
                Storefront(transport, history, signIn = {})
            }
        }

    private fun ComposeUiTest.exists(text: String) = onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.tagged(tag: String) = onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.waitForText(text: String) = waitUntil(timeoutMillis = 5_000) { exists(text) }

    /** How far the page is scrolled, in pixels. */
    private fun ComposeUiTest.scrolled(): Float =
        onNodeWithTag(PAGE_SCROLL_TAG).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun ComposeUiTest.scrollBy(pixels: Float) {
        onNodeWithTag(PAGE_SCROLL_TAG).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, pixels) }
        waitForIdle()
    }

    /**
     * The acceptance of B-62: ticking a facet used to throw the page away — the placeholder took its place
     * and the scroll went back to the top. Now the header, the breadcrumbs and the scroll stay while the new
     * results load, with the loading line under the header, and the new results are drawn in place.
     */
    @Test
    fun `ticking a facet keeps the page and its scroll while the new results load`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            answer("/ui$MUGS", ok(catalog(ostra = false)))
            answer("/ui$BY_OSTRA", ok(catalog(ostra = true)))
            val arriving = gate("/ui$BY_OSTRA")
            storefront()
            waitForText(card("Stoneware", 0))
            scrollBy(SCROLL)
            val before = scrolled()
            assertTrue(before > 0f, "the page did not scroll")

            onNodeWithText("Ostra").performClick()
            waitUntil(timeoutMillis = 5_000) { "/ui$BY_OSTRA" in requests }
            waitForIdle()
            onNodeWithTag(LOADING_TAG).assertDoesNotExist()
            onNodeWithTag(LOADING_LINE_TAG).assertExists()
            onNodeWithText(CUSTOMER).assertExists()
            onNodeWithText(CRUMB, substring = true).assertExists()
            onNodeWithText(card("Stoneware", 0)).assertExists()
            assertEquals(before, scrolled(), "the scroll moved while the results loaded")

            arriving.complete(Unit)
            waitForText(card("Ostra", 0))
            onNodeWithTag(LOADING_TAG).assertDoesNotExist()
            onNodeWithTag(LOADING_LINE_TAG).assertDoesNotExist()
            onNodeWithText(card("Stoneware", 0)).assertDoesNotExist()
            onNodeWithText(CRUMB, substring = true).assertExists()
            assertEquals(before, scrolled(), "the scroll moved when the results arrived")
            assertEquals(listOf(MUGS, BY_OSTRA), history.entries)
        }

    /** Back and forward between two addresses of one screen follow the same rule: the page stays. */
    @Test
    fun `back to the same screen keeps the page while it loads`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            answer("/ui$MUGS", ok(catalog(ostra = false)))
            answer("/ui$BY_OSTRA", ok(catalog(ostra = true)))
            storefront()
            waitForText(card("Stoneware", 0))
            onNodeWithText("Ostra").performClick()
            waitForText(card("Ostra", 0))
            scrollBy(SCROLL)
            val before = scrolled()

            val back = gate("/ui$MUGS")
            history.back()
            waitUntil(timeoutMillis = 5_000) { requests.count { it == "/ui$MUGS" } == 2 }
            waitForIdle()
            onNodeWithTag(LOADING_TAG).assertDoesNotExist()
            onNodeWithText(card("Ostra", 0)).assertExists()

            back.complete(Unit)
            waitForText(card("Stoneware", 0))
            assertEquals(before, scrolled(), "back to the same screen moved the scroll")
        }

    /**
     * Another path is another screen, as before B-62: a product opened from the results draws its
     * placeholder and starts at the top, and back from it returns to the catalog.
     */
    @Test
    fun `opening a product draws its placeholder at the top and back returns to the catalog`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            answer("/ui$MUGS", ok(catalog(ostra = false)))
            // As tall as the catalog, so a scroll carried over from it would show.
            answer("/ui/p/p-0", ok(page(header, TextComponent(id = "product", text = PRODUCT), grid("Related"))))
            val opening = gate("/ui/p/p-0")
            storefront()
            waitForText(card("Stoneware", 0))
            scrollBy(SCROLL)

            onNodeWithText(card("Stoneware", 0)).performClick()
            waitUntil(timeoutMillis = 5_000) { tagged(LOADING_TAG) }
            onNodeWithText(CRUMB, substring = true).assertDoesNotExist()
            onNodeWithTag(LOADING_LINE_TAG).assertDoesNotExist()

            opening.complete(Unit)
            waitForText(PRODUCT)
            assertEquals(0f, scrolled(), "the product page did not start at the top")

            history.back()
            waitForText(card("Stoneware", 0))
            onNodeWithText(CRUMB, substring = true).assertExists()
            assertEquals(listOf(MUGS, "/p/p-0"), history.entries)
            assertEquals(listOf("/ui$MUGS", "/ui/p/p-0", "/ui$MUGS"), requests.toList())
        }

    /**
     * A facet whose page did not arrive used to draw «This category didn’t load» in the page's place. Now
     * the page stays, under a notice with Retry, and Retry asks for the same address again.
     */
    @Test
    fun `a load that fails within a screen keeps the page under a notice and Retry loads it`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            answer("/ui$MUGS", ok(catalog(ostra = false)))
            answer("/ui$BY_OSTRA", SERVER_ERROR, ok(catalog(ostra = true)))
            storefront()
            waitForText(card("Stoneware", 0))

            onNodeWithText("Ostra").performClick()
            waitUntil(timeoutMillis = 5_000) { tagged(NOT_UPDATED_TAG) }
            onNodeWithText(NOT_UPDATED_TITLE).assertExists()
            onNodeWithText(card("Stoneware", 0)).assertExists()
            onNodeWithText(CRUMB, substring = true).assertExists()
            onNodeWithText("This category didn", substring = true).assertDoesNotExist()
            onNodeWithTag(LOADING_LINE_TAG).assertDoesNotExist()

            onNodeWithText("Retry").performClick()
            waitForText(card("Ostra", 0))
            onNodeWithTag(NOT_UPDATED_TAG).assertDoesNotExist()
            assertEquals(listOf("/ui$MUGS", "/ui$BY_OSTRA", "/ui$BY_OSTRA"), requests.toList())
            assertEquals(listOf(MUGS, BY_OSTRA), history.entries)
        }

    /**
     * A customer's page refused within its screen — the sign-in lapsed between two filters — is not kept
     * for the guest the shopper now is: it is taken down and asks for the sign-in (B-44), not a notice.
     */
    @Test
    fun `a sign-in lapsed within a screen takes the page down and asks for one`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            history.entries[0] = ORDERS
            answer(
                "/ui$ORDERS",
                ok(page(header, ButtonComponent(id = "active", text = ACTIVE_LABEL, action = NavigateAction(ACTIVE)))),
            )
            answer("/ui$ACTIVE", UNAUTHENTICATED)
            storefront()
            waitForText(ACTIVE_LABEL)

            onNodeWithText(ACTIVE_LABEL).performClick()
            waitUntil(timeoutMillis = 5_000) { tagged(SIGN_IN_PROMPT_TAG) }
            onNodeWithText(ACTIVE_LABEL).assertDoesNotExist()
            onNodeWithTag(NOT_UPDATED_TAG).assertDoesNotExist()
        }

    private companion object {
        const val WIDTH = 1440
        const val HEIGHT = 1000
        const val SCROLL = 240f
        const val MUGS = "/c/mugs"
        const val BY_OSTRA = "/c/mugs?brand=Ostra"
        const val BY_LUME = "/c/mugs?brand=Lume"
        const val ORDERS = "/account/orders"
        const val ACTIVE = "/account/orders?status=active"
        const val ACTIVE_LABEL = "Active orders"
        const val CUSTOMER = "Maya"
        const val CRUMB = "Kitchen mugs"
        const val PRODUCT = "The product page"

        val SERVER_ERROR = HaulResponse(500, """{"code":"internal","message":"boom"}""")
        val UNAUTHENTICATED = HaulResponse(401, """{"code":"unauthenticated","message":"Sign in first"}""")

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

        fun card(
            brand: String,
            index: Int,
        ) = "$brand Mug $index"

        /** The mugs, unfiltered or with Ostra ticked: sixteen cards, so the page has somewhere to scroll. */
        fun catalog(ostra: Boolean): KompotComponent {
            val brand = if (ostra) "Ostra" else "Stoneware"
            return page(
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
                                    options =
                                        listOf(
                                            FacetOption(
                                                "Ostra",
                                                12,
                                                selected = ostra,
                                                action = NavigateAction(if (ostra) MUGS else BY_OSTRA),
                                            ),
                                            FacetOption("Lume", 9, selected = false, action = NavigateAction(BY_LUME)),
                                        ),
                                ),
                            ),
                        ),
                    applied =
                        AppliedFilters(
                            id = "applied",
                            chips = emptyList(),
                            clearLabel = "Clear all",
                            sortLabel = "Popular",
                            filterCount = if (ostra) 1 else 0,
                            clearAction = NavigateAction(MUGS),
                        ),
                    showLabel = if (ostra) "Show 12 items" else "Show 60 items",
                    grid = grid(brand),
                ),
            )
        }

        /** Sixteen of [brand]'s mugs, four to a row. */
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
                    )
                },
                columns = 4,
            )

        fun page(vararg children: KompotComponent): KompotComponent =
            ColumnComponent(id = "page", children = children.toList())

        fun ok(tree: KompotComponent) =
            HaulResponse(200, haulWireJson.encodeToString(PolymorphicSerializer(KompotComponent::class), tree))
    }
}
