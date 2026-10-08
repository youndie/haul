package io.github.youndie.haul

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.feature.cart.CartCommand
import io.github.youndie.haul.feature.cart.CartCommands
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.cart.LineCommand
import io.github.youndie.haul.shell.HaulCommands
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.ADD_TO_CART
import io.github.youndie.haul.ui.AppliedFilters
import io.github.youndie.haul.ui.FacetPanel
import io.github.youndie.haul.ui.FilteredResults
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.HaulPagination
import io.github.youndie.haul.ui.LINK_MENU_TAG
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.SEARCH_FIELD_TAG
import io.github.youndie.haul.ui.SearchSuggestPanel
import io.github.youndie.haul.ui.SectionHeader
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.RefreshAction
import io.github.youndie.kompot.standard.TextComponent
import kotlinx.serialization.PolymorphicSerializer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-37 in the client: each control that the trees used to draw with nothing to follow, pressed, follows
 * the action its tree now carries — a `navigate` opens its address, a command goes to the server and
 * the page (or the suggest panel) is drawn again. A fake transport serves the trees and records what
 * was fetched; fake commands record what was sent.
 */
@OptIn(ExperimentalTestApi::class)
class DrawnActionsTest {
    private val requests = CopyOnWriteArrayList<String>()
    private val answers = mutableMapOf<String, ArrayDeque<KompotComponent>>()
    private val sent = CopyOnWriteArrayList<String>()
    private val history = FakeHistory()

    /** Answers [path] with each of [trees] in turn, the last one from then on. */
    private fun answer(
        path: String,
        vararg trees: KompotComponent,
    ) {
        answers[path] = ArrayDeque(trees.toList())
    }

    private val transport =
        HaulTransport { path ->
            requests += path
            val queue = answers[path] ?: error("nothing answers $path")
            val tree = if (queue.size > 1) queue.removeFirst() else queue.first()
            HaulResponse(200, haulWireJson.encodeToString(PolymorphicSerializer(KompotComponent::class), tree))
        }

    private val commands =
        HaulCommands { method, path, body ->
            sent += "$method $path ${body.orEmpty()}".trim()
            HaulResponse(200, REFRESH)
        }

    /** The cart's commands (B-13), which a card's «+» goes through; each answers `refresh`. */
    private val cartCommands =
        CartCommands { command ->
            sent += command.toString()
            RefreshAction
        }

    private fun ComposeUiTest.storefront() =
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                Storefront(transport, history, signIn = {}, cartCommands = cartCommands, commands = commands)
            }
        }

    /** Answers every address the page's controls lead to, so following one draws the next page. */
    private fun destinations(vararg addresses: String) =
        addresses.forEach {
            answer(
                if (it ==
                    "/"
                ) {
                    "/ui/home"
                } else {
                    "/ui$it"
                },
                page(header, TextComponent(id = "next", text = NEXT)),
            )
        }

    @Test
    fun `show more and a page number open their pages`() =
        runDesktopComposeUiTest(WIDTH, 1_600) {
            answer("/ui/home", results)
            destinations(PAGE_2, PAGE_3)
            storefront()
            onNodeWithText("Show 24 more").performClick()
            onNodeWithText(NEXT).assertExists()
            assertEquals(listOf("/", PAGE_2), history.entries)

            history.back()
            onNodeWithText("3").performClick()
            onNodeWithText(NEXT).assertExists()
            assertEquals(listOf("/", PAGE_3), history.entries)
        }

    @Test
    fun `the sort opens its orders and the one picked is opened`() =
        runDesktopComposeUiTest(WIDTH, 1_600) {
            answer("/ui/home", results)
            destinations(BY_PRICE)
            storefront()
            onNodeWithTag(LINK_MENU_TAG).assertDoesNotExist()
            onNodeWithText("Sort:").performClick()
            onNodeWithTag(LINK_MENU_TAG).assertExists()
            onNodeWithText("Price: low to high").performClick()
            onNodeWithText(NEXT).assertExists()
            assertEquals(listOf("/", BY_PRICE), history.entries)
        }

    @Test
    fun `clear all opens the page without its filters`() =
        runDesktopComposeUiTest(WIDTH, 1_600) {
            answer("/ui/home", results)
            destinations(CLEARED)
            storefront()
            onNodeWithText("Clear all").performClick()
            onNodeWithText(NEXT).assertExists()
            assertEquals(listOf("/", CLEARED), history.entries)
        }

    @Test
    fun `a category in the header's row opens its page`() =
        runDesktopComposeUiTest(WIDTH, 1_000) {
            answer("/ui/home", page(header))
            destinations("/c/electronics")
            storefront()
            onNodeWithText("Electronics").performClick()
            onNodeWithText(NEXT).assertExists()
            assertEquals(listOf("/", "/c/electronics"), history.entries)
        }

    @Test
    fun `catalog opens every category as a menu and the one picked is opened`() =
        runDesktopComposeUiTest(WIDTH, 1_000) {
            answer("/ui/home", page(header))
            destinations("/c/home-kitchen")
            storefront()
            onNodeWithText("Home & Kitchen").assertDoesNotExist()
            onNodeWithText("Catalog").performClick()
            onNodeWithText("Home & Kitchen").performClick()
            onNodeWithText(NEXT).assertExists()
            assertEquals(listOf("/", "/c/home-kitchen"), history.entries)
        }

    @Test
    fun `deals and the cart in the header open their pages`() =
        runDesktopComposeUiTest(WIDTH, 1_000) {
            answer("/ui/home", page(header))
            destinations("/deals", "/cart")
            storefront()
            onNodeWithText("Deals").performClick()
            onNodeWithText(NEXT).assertExists()
            assertEquals(listOf("/", "/deals"), history.entries)
            history.back()
            onNodeWithText("Cart").performClick()
            onNodeWithText(NEXT).assertExists()
            assertEquals(listOf("/", "/cart"), history.entries)
            assertEquals(listOf("/ui/home", "/ui/deals", "/ui/home", "/ui/cart"), requests)
        }

    /** «Orders» in the header (B-18) opens where its tree says: a customer's orders' history (B-19). */
    @Test
    fun `orders in the header opens the customer's orders`() =
        runDesktopComposeUiTest(WIDTH, 1_000) {
            answer("/ui/home", page(header))
            destinations("/account/orders")
            storefront()
            onNodeWithText("Orders").performClick()
            onNodeWithText(NEXT).assertExists()
            assertEquals(listOf("/", "/account/orders"), history.entries)
            assertEquals(listOf("/ui/home", "/ui/account/orders"), requests)
        }

    @Test
    fun `view all deals opens the deals page`() =
        runDesktopComposeUiTest(WIDTH, 1_000) {
            val section =
                SectionHeader(
                    "deals-title",
                    "Deals of the day",
                    linkLabel = "View all deals",
                    action = NavigateAction("/deals"),
                )
            answer("/ui/home", page(header, section))
            destinations("/deals")
            storefront()
            onNodeWithText("View all deals").performClick()
            onNodeWithText(NEXT).assertExists()
            assertEquals(listOf("/", "/deals"), history.entries)
        }

    @Test
    fun `a card's plus sends its line change and draws the page again`() =
        runDesktopComposeUiTest(WIDTH, 1_600) {
            val after = page(header.copy(cartCount = 1), TextComponent(id = "count", text = "One in the cart"))
            answer("/ui/home", results, after)
            storefront()
            onNodeWithContentDescription(ADD_TO_CART).performClick()
            onNodeWithText("One in the cart").assertExists()
            assertEquals(listOf(CartCommand.ChangeLine(MUG_LINE, LineChange(quantity = 2)).toString()), sent)
            assertEquals(listOf("/ui/home", "/ui/home"), requests, "the page was not drawn again")
            assertEquals(listOf("/"), history.entries, "«+» opened the product")
        }

    @Test
    fun `clear on recent searches sends its delete and asks for the panel again`() =
        runDesktopComposeUiTest(WIDTH, 1_000) {
            answer("/ui/home", page(header))
            answer("/ui/search/suggest?q=mu", recent(listOf("mugs"), RECENT), recent(emptyList(), null))
            storefront()
            onNodeWithTag(SEARCH_FIELD_TAG).performClick()
            onNodeWithTag(SEARCH_FIELD_TAG).performTextInput("mu")
            waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("mugs")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("CLEAR").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("mugs")).fetchSemanticsNodes().isEmpty() }
            assertEquals(listOf("DELETE $RECENT"), sent)
            assertEquals(listOf("/ui/home", "/ui/search/suggest?q=mu", "/ui/search/suggest?q=mu"), requests)
        }

    private companion object {
        const val WIDTH = 1440
        const val NEXT = "The next page"
        const val PAGE_2 = "/c/mugs?brand=Haul&page=2"
        const val PAGE_3 = "/c/mugs?brand=Haul&page=3"
        const val BY_PRICE = "/c/mugs?brand=Haul&sort=price-asc"
        const val CLEARED = "/c/mugs"
        const val MUG_LINE = "/api/v1/cart/lines/p-stoneware-mug-0"
        const val RECENT = "/api/v1/me/recent-searches"

        /** What a command answers (kompot's `refresh`); the storefront draws again on any answer. */
        const val REFRESH = """{"type":"refresh"}"""

        val header =
            HaulHeader(
                id = "header",
                deliverTo = "Brooklyn, NY 11211",
                deliveryPromise = "Free delivery over $35",
                customerName = "Maya",
                cartCount = 0,
                searchPlaceholder = "Search 2.4 million products",
                categories = listOf("Electronics"),
                catalog =
                    listOf(
                        Link("Electronics", NavigateAction("/c/electronics")),
                        Link("Home & Kitchen", NavigateAction("/c/home-kitchen")),
                    ),
                deals = NavigateAction("/deals"),
                cart = NavigateAction("/cart"),
                orders = NavigateAction("/account/orders"),
            )

        val mug =
            ProductCard(
                id = "card-mug",
                productId = "p-stoneware-mug",
                title = "Stoneware Mug, 12 oz",
                price = "$24",
                rating = "4.8",
                reviews = "420",
                delivery = "Tomorrow",
                tone = "#E3F5D8",
                label = "mug",
                action = NavigateAction("/p/p-stoneware-mug"),
                add = LineCommand(MUG_LINE, LineChange(quantity = 2)),
            )

        val results =
            page(
                header,
                FilteredResults(
                    id = "results",
                    facets = FacetPanel("facets", emptyList()),
                    applied =
                        AppliedFilters(
                            id = "applied",
                            chips = emptyList(),
                            clearLabel = "Clear all",
                            sortLabel = "Popular",
                            filterCount = 1,
                            clearAction = NavigateAction(CLEARED),
                            sorts =
                                listOf(
                                    Link("Popular", NavigateAction("/c/mugs?brand=Haul")),
                                    Link("Price: low to high", NavigateAction(BY_PRICE)),
                                ),
                        ),
                    showLabel = "Show 60 items",
                    grid = ProductGrid("grid", listOf(mug), columns = 4),
                    pagination =
                        HaulPagination(
                            id = "pagination",
                            current = 1,
                            pages = listOf("1", "2", "3"),
                            moreLabel = "Show 24 more",
                            moreAction = NavigateAction(PAGE_2),
                            links = listOf(Link("2", NavigateAction(PAGE_2)), Link("3", NavigateAction(PAGE_3))),
                        ),
                ),
            )

        fun recent(
            queries: List<String>,
            clearUrl: String?,
        ) = SearchSuggestPanel(
            id = "suggest",
            query = "mu",
            suggestions = emptyList(),
            categories = emptyList(),
            products = emptyList(),
            allResultsLabel = "All 3 results",
            recent = queries,
            clearUrl = clearUrl,
        )

        fun page(vararg children: KompotComponent): KompotComponent =
            ColumnComponent(id = "page", children = children.toList())
    }
}
