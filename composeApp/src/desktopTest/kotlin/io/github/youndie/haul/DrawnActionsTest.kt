package io.github.youndie.haul

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
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.feature.cart.CartCommand
import io.github.youndie.haul.feature.cart.CartCommands
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.cart.LineCommand
import io.github.youndie.haul.feature.catalog.CLOSE_FILTERS
import io.github.youndie.haul.feature.home.PLUS_START_TAG
import io.github.youndie.haul.shell.HaulCommands
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.ADD_TO_CART
import io.github.youndie.haul.ui.AppliedFilters
import io.github.youndie.haul.ui.Facet
import io.github.youndie.haul.ui.FacetOption
import io.github.youndie.haul.ui.FacetPanel
import io.github.youndie.haul.ui.FilteredResults
import io.github.youndie.haul.ui.FooterColumn
import io.github.youndie.haul.ui.HaulFooter
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.HaulPagination
import io.github.youndie.haul.ui.LINK_MENU_TAG
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.PlusBenefit
import io.github.youndie.haul.ui.PlusTrialDialog
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.SEARCH_FIELD_TAG
import io.github.youndie.haul.ui.SearchSuggestPanel
import io.github.youndie.haul.ui.SectionHeader
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.CloseAction
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.PresentAction
import io.github.youndie.kompot.standard.RefreshAction
import io.github.youndie.kompot.standard.TextComponent
import kotlinx.serialization.PolymorphicSerializer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-37 and B-49 in the client: each control that the trees used to draw with nothing to follow, pressed, follows
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

    private fun ComposeUiTest.storefront(compact: Boolean = false) =
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = compact) {
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
            answer(
                "/ui/search/suggest?q=mu",
                recent(listOf(Link("mugs", NavigateAction(MUGS))), RECENT),
                recent(emptyList(), null),
            )
            storefront()
            onNodeWithTag(SEARCH_FIELD_TAG).performClick()
            onNodeWithTag(SEARCH_FIELD_TAG).performTextInput("mu")
            waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("mugs")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("CLEAR").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("mugs")).fetchSemanticsNodes().isEmpty() }
            assertEquals(listOf("DELETE $RECENT"), sent)
            assertEquals(listOf("/ui/home", "/ui/search/suggest?q=mu", "/ui/search/suggest?q=mu"), requests)
        }

    /** B-49: home's «All N categories» opens the catalog's root, `/c`, which the server answers at `/ui/c`. */
    @Test
    fun `all categories opens the catalog root`() =
        runDesktopComposeUiTest(WIDTH, 1_000) {
            val section =
                SectionHeader(
                    "categories-title",
                    "Shop by category",
                    linkLabel = "All 32 categories",
                    action = NavigateAction("/c"),
                )
            answer("/ui/home", page(header, section))
            destinations("/c")
            storefront()
            onNodeWithText("All 32 categories").performClick()
            onNodeWithText(NEXT).assertExists()
            assertEquals(listOf("/", "/c"), history.entries)
            assertEquals(listOf("/ui/home", "/ui/c"), requests)
        }

    /** B-49: the brand facet's «Show N more» opens the address its facet carries, the facet expanded. */
    @Test
    fun `show more on the brand facet opens the page with the facet expanded`() =
        runDesktopComposeUiTest(WIDTH, 1_600) {
            answer("/ui/home", results)
            destinations(EXPANDED)
            storefront()
            onNodeWithText("Show 3 more").performClick()
            onNodeWithText(NEXT).assertExists()
            assertEquals(listOf("/", EXPANDED), history.entries)
        }

    /**
     * B-49: on a phone «Filters» opens the facets as a sheet and its «×» closes it. Both are the client's
     * own — the sheet is not a page — so neither asks the server for anything nor moves the address.
     */
    @Test
    fun `the filter sheet's close closes it without asking the server`() =
        runDesktopComposeUiTest(PHONE, 1_600) {
            answer("/ui/home", results)
            storefront(compact = true)
            onNodeWithContentDescription(CLOSE_FILTERS).assertDoesNotExist()
            onNodeWithText("Filters").performClick()
            waitUntil(
                timeoutMillis = 5_000,
            ) { onAllNodes(hasContentDescription(CLOSE_FILTERS)).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithContentDescription(CLOSE_FILTERS).performClick()
            waitUntil(
                timeoutMillis = 5_000,
            ) { onAllNodes(hasContentDescription(CLOSE_FILTERS)).fetchSemanticsNodes().isEmpty() }
            assertEquals(listOf("/ui/home"), requests, "closing the sheet asked the server")
            assertEquals(listOf("/"), history.entries)
        }

    /** B-49: a recent search's row opens the search its tree carries; the client builds no address of its own. */
    @Test
    fun `a recent search's row runs that search`() =
        runDesktopComposeUiTest(WIDTH, 1_000) {
            answer("/ui/home", page(header))
            answer("/ui/search/suggest?q=mu", recent(listOf(Link("mugs", NavigateAction(MUGS))), RECENT))
            destinations(MUGS)
            storefront()
            onNodeWithTag(SEARCH_FIELD_TAG).performClick()
            onNodeWithTag(SEARCH_FIELD_TAG).performTextInput("mu")
            waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("mugs")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("mugs").performClick()
            onNodeWithText(NEXT).assertExists()
            assertEquals(listOf("/", MUGS), history.entries)
            assertEquals(emptyList(), sent, "a recent search's row sent a command")
        }

    /** B-49: «HAUL PLUS» presents what the header carries for a customer who is not a member: the trial's dialog. */
    @Test
    fun `haul plus presents the trial's dialog`() =
        runDesktopComposeUiTest(WIDTH, 1_000) {
            answer("/ui/home", page(header.copy(plus = PresentAction(trial, "dialog"))))
            storefront()
            onNodeWithTag(PLUS_START_TAG).assertDoesNotExist()
            onNodeWithText("HAUL PLUS").performClick()
            waitUntil(
                timeoutMillis = 5_000,
            ) { onAllNodes(hasTestTag(PLUS_START_TAG)).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(listOf("/"), history.entries, "the dialog opened a page")
            assertEquals(listOf("/ui/home"), requests)
        }

    /** B-49: a member's «HAUL PLUS» opens the account, where the membership is drawn. */
    @Test
    fun `haul plus opens a member's account`() =
        runDesktopComposeUiTest(WIDTH, 1_000) {
            answer("/ui/home", page(header.copy(plus = NavigateAction("/account"))))
            destinations("/account")
            storefront()
            onNodeWithText("HAUL PLUS").performClick()
            onNodeWithText(NEXT).assertExists()
            assertEquals(listOf("/", "/account"), history.entries)
        }

    /**
     * B-49: «Sell on HAUL», «Help», the language and the footer's links have no page to open, so they are
     * drawn as plain text — nothing to press, not links that do nothing.
     */
    @Test
    fun `help sell on haul the language and the footer are plain text`() =
        runDesktopComposeUiTest(WIDTH, 1_600) {
            answer("/ui/home", page(header.copy(plus = NavigateAction("/account")), footer))
            storefront()
            listOf("SELL ON HAUL", "HELP", "EN · USD").forEach { onNodeWithText(it).assertHasNoClickAction() }
            footer.columns.flatMap { it.links }.forEach {
                onNodeWithText(it, substring = true).assertHasNoClickAction()
            }
            onNodeWithText("HAUL PLUS").assertHasClickAction()
        }

    private companion object {
        const val WIDTH = 1440
        const val PHONE = 390
        const val EXPANDED = "/c/mugs?brand=Haul&expand=brand"
        const val MUGS = "/search?q=mugs"
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
                    facets =
                        FacetPanel(
                            "facets",
                            listOf(
                                Facet(
                                    "brand",
                                    "Brand",
                                    "checkbox",
                                    options = listOf(FacetOption("Haul", 12, selected = true)),
                                    moreLabel = "Show 3 more",
                                    moreAction = NavigateAction(EXPANDED),
                                ),
                            ),
                        ),
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

        val trial =
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

        val footer =
            HaulFooter(
                id = "footer",
                columns = listOf(FooterColumn("Company", listOf("About", "Careers"))),
                appTitle = "Get the app",
                appText = "Order tracking.",
            )

        fun recent(
            queries: List<Link>,
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
