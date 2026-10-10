package io.github.youndie.haul

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
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
import io.github.youndie.haul.ui.CATEGORY_ROW_TAG
import io.github.youndie.haul.ui.CampaignHero
import io.github.youndie.haul.ui.CampaignRow
import io.github.youndie.haul.ui.Facet
import io.github.youndie.haul.ui.FacetOption
import io.github.youndie.haul.ui.FacetPanel
import io.github.youndie.haul.ui.FilteredResults
import io.github.youndie.haul.ui.FooterColumn
import io.github.youndie.haul.ui.HEADER_MENU_TAG
import io.github.youndie.haul.ui.HaulFooter
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.HaulPagination
import io.github.youndie.haul.ui.LINK_MENU_TAG
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.OPEN_MENU
import io.github.youndie.haul.ui.PlusBenefit
import io.github.youndie.haul.ui.PlusTrialDialog
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.PromoBanner
import io.github.youndie.haul.ui.SEARCH_FIELD_TAG
import io.github.youndie.haul.ui.SearchSuggestPanel
import io.github.youndie.haul.ui.SectionHeader
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.commands.LoadAction
import io.github.youndie.kompot.commands.UpdateHistory
import io.github.youndie.kompot.commands.kompotUpdate
import io.github.youndie.kompot.standard.CloseAction
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.PresentAction
import io.github.youndie.kompot.standard.RefreshAction
import io.github.youndie.kompot.standard.TextComponent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.PolymorphicSerializer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-37, B-49, B-54 and B-59 in the client: each control that the trees used to draw with nothing to follow,
 * pressed, follows the action its tree now carries — a `navigate` opens its address, a command goes to the
 * server and the page (or the suggest panel) is drawn again. A fake transport serves the trees and records what
 * was fetched; fake commands record what was sent.
 */
@OptIn(ExperimentalTestApi::class)
class DrawnActionsTest {
    private val requests = CopyOnWriteArrayList<String>()
    private val answers = mutableMapOf<String, ArrayDeque<KompotComponent>>()
    private val sent = CopyOnWriteArrayList<String>()
    private val history = FakeHistory()

    /** Paths whose answer waits until the test completes the gate: a page still on its way. */
    private val gates = mutableMapOf<String, CompletableDeferred<Unit>>()

    /** `load` endpoints' answers by path (B-63). */
    private val parts = mutableMapOf<String, KompotAction>()

    /**
     * The category with the brand facet unticked, and the results ticking Ostra, then Lume, load in its place
     * (B-63): each tick a `load`, answered with an `update` of the results and the address they make.
     */
    private fun filtering() {
        history.entries[0] = CLEARED
        answer("/ui$CLEARED", filtered(applied = 0, show = "Show 60 items", card = "Stoneware Mug"))
        parts(BY_OSTRA, filtered(applied = 1, show = "Show 12 items", card = "Ostra Mug", ostra = true))
        parts(BY_BOTH, filtered(applied = 2, show = "Show 5 items", card = "Lume Mug", ostra = true, lume = true))
    }

    /** The parts of [address] — the results of [page] — as its `load` endpoint answers them. */
    private fun parts(
        address: String,
        page: KompotComponent,
    ) {
        val results = (page as ColumnComponent).children.single { it.id == "results" }
        parts[PARTS + address] = kompotUpdate(address, UpdateHistory.PUSH) { addComponent(results) }
    }

    private fun ComposeUiTest.openSheet() {
        onNodeWithText("Filters").performClick()
        waitUntil(
            timeoutMillis = 5_000,
        ) { onAllNodes(hasContentDescription(CLOSE_FILTERS)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

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
            gates[path]?.await()
            parts[path]?.let { action ->
                return@HaulTransport HaulResponse(
                    200,
                    haulWireJson.encodeToString(PolymorphicSerializer(KompotAction::class), action),
                )
            }
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

    /** B-59: each of home's banners opens the address it carries — the deals page — as «Shop the sale» does. */
    @Test
    fun `the tech week banner opens the deals page`() = bannerOpensTheDealsPage("Laptops from $399")

    /** B-59: the second banner, the free-delivery weekend's, the same. */
    @Test
    fun `the free delivery banner opens the deals page`() = bannerOpensTheDealsPage("Free delivery on everything")

    private fun bannerOpensTheDealsPage(title: String) =
        runDesktopComposeUiTest(WIDTH, 1_000) {
            answer("/ui/home", page(header, campaigns))
            destinations("/deals")
            storefront()
            onNodeWithText(title).performClick()
            onNodeWithText(NEXT).assertExists()
            assertEquals(listOf("/", "/deals"), history.entries, title)
            assertEquals(listOf("/ui/home", "/ui/deals"), requests, title)
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

    /**
     * B-54: a sheet the page remembered closed after each tick, so choosing two filters meant opening it
     * twice. Since B-63 a tick is a `load` answered with an `update` of the results: the page is not left,
     * the sheet stays open and is drawn from the updated results — «N applied» and «Show N items» are the
     * new ones — while the results under it are the new ones too, and the address is each tick's.
     */
    @Test
    fun `ticking two facets keeps the filter sheet open over the new results`() =
        runDesktopComposeUiTest(PHONE, 1_600) {
            filtering()
            storefront(compact = true)
            openSheet()
            onNodeWithText("Ostra").performClick()
            waitForText("1 applied")
            onNodeWithContentDescription(CLOSE_FILTERS).assertExists()
            onNodeWithText("Show 12 items").assertExists()
            onNodeWithText("Ostra Mug").assertExists()

            onNodeWithText("Lume").performClick()
            waitForText("2 applied")
            onNodeWithContentDescription(CLOSE_FILTERS).assertExists()
            onNodeWithText("Show 5 items").assertExists()
            onNodeWithText("Lume Mug").assertExists()
            assertEquals(listOf(CLEARED, BY_OSTRA, BY_BOTH), history.entries)
            assertEquals(
                listOf("/ui$CLEARED", "$PARTS$BY_OSTRA", "$PARTS$BY_BOTH"),
                requests,
                "a tick asked for a page",
            )
        }

    /** B-54: «Show N items» did nothing; the page under the sheet already has those results, so it closes the sheet. */
    @Test
    fun `show N items closes the filter sheet over its results`() =
        runDesktopComposeUiTest(PHONE, 1_600) {
            filtering()
            storefront(compact = true)
            openSheet()
            onNodeWithText("Ostra").performClick()
            waitForText("1 applied")
            onNodeWithText("Show 12 items").performClick()
            waitUntil(
                timeoutMillis = 5_000,
            ) { onAllNodes(hasContentDescription(CLOSE_FILTERS)).fetchSemanticsNodes().isEmpty() }
            onNodeWithText("Ostra Mug").assertExists()
            assertEquals(listOf("/ui$CLEARED", "$PARTS$BY_OSTRA"), requests, "showing the results asked the server")
            assertEquals(listOf(CLEARED, BY_OSTRA), history.entries)
        }

    /**
     * B-54: only the sheet's own presses keep it open. Back, with the sheet open, closes it — and draws the
     * page before the tick, not the tick's update over it (B-63) — and a page opened from the page itself
     * («Show 24 more» under the results) does not open a sheet closed by «×».
     */
    @Test
    fun `a page not opened from the filter sheet leaves it closed`() =
        runDesktopComposeUiTest(PHONE, 1_600) {
            filtering()
            answer("/ui$PAGE_2", filtered(applied = 0, show = "Show 60 items", card = "Second Mug"))
            storefront(compact = true)
            openSheet()
            onNodeWithText("Ostra").performClick()
            waitForText("1 applied")
            history.back()
            waitUntil(
                timeoutMillis = 5_000,
            ) { onAllNodes(hasContentDescription(CLOSE_FILTERS)).fetchSemanticsNodes().isEmpty() }
            waitForText("Stoneware Mug")
            onNodeWithText("Ostra Mug").assertDoesNotExist()

            openSheet()
            onNodeWithContentDescription(CLOSE_FILTERS).performClick()
            onNodeWithText("Show 24 more").performClick()
            waitForText("Second Mug")
            onNodeWithContentDescription(CLOSE_FILTERS).assertDoesNotExist()
            assertEquals(listOf(CLEARED, PAGE_2), history.entries)
        }

    /**
     * B-54: until a tick's answer arrives, the sheet still shows the facets before it, whose addresses lack
     * that tick — a second tick then would load the results without the first, and win (B-63: the last
     * press does). So the facets follow nothing while the `load` is on its way, and follow the updated
     * results once they are drawn.
     */
    @Test
    fun `the filter sheet follows nothing while the page its tick opened is on its way`() =
        runDesktopComposeUiTest(PHONE, 1_600) {
            filtering()
            val arriving = CompletableDeferred<Unit>().also { gates["$PARTS$BY_OSTRA"] = it }
            storefront(compact = true)
            openSheet()
            onNodeWithText("Ostra").performClick()
            onNodeWithText("Lume").assertHasNoClickAction()
            onNodeWithContentDescription(CLOSE_FILTERS).assertHasClickAction()

            arriving.complete(Unit)
            waitForText("1 applied")
            onNodeWithText("Lume").assertHasClickAction()
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

    /**
     * B-73: on a phone the header's menu reaches what the 1440 header does — every category the catalog
     * carries, not only the row's — and an entry pressed opens its page with the menu closed.
     */
    @Test
    fun `on a phone the menu opens a category the row does not show`() =
        runDesktopComposeUiTest(PHONE, 1_000) {
            answer("/ui/home", page(phoneHeader))
            destinations("/c/home-kitchen")
            storefront(compact = true)
            onNodeWithText("Home & Kitchen").assertDoesNotExist()
            openMenu()
            inMenu("Home & Kitchen").performClick()
            onNodeWithText(NEXT).assertExists()
            onNodeWithTag(HEADER_MENU_TAG).assertDoesNotExist()
            assertEquals(listOf("/", "/c/home-kitchen"), history.entries)
        }

    /** B-73: the account, «Orders», «Saved» and «Deals» in the phone's menu each open where the header says. */
    @Test
    fun `on a phone the menu opens the account orders saved and deals`() =
        runDesktopComposeUiTest(PHONE, 1_000) {
            answer("/ui/home", page(phoneHeader))
            val entries =
                listOf(
                    "Maya" to "/account",
                    "Orders" to "/account/orders",
                    "Saved" to "/account/saved",
                    "Deals" to "/deals",
                )
            destinations(*entries.map { it.second }.toTypedArray())
            storefront(compact = true)
            entries.forEach { (entry, address) ->
                openMenu()
                inMenu(entry).performClick()
                onNodeWithText(NEXT).assertExists()
                onNodeWithTag(HEADER_MENU_TAG).assertDoesNotExist()
                assertEquals(listOf("/", address), history.entries, entry)
                history.back()
                waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText(NEXT)).fetchSemanticsNodes().isEmpty() }
            }
        }

    /** B-73: «HAUL PLUS» in the phone's menu presents the trial's dialog, over the page, with the menu closed. */
    @Test
    fun `on a phone the menu's haul plus presents the trial's dialog`() =
        runDesktopComposeUiTest(PHONE, 1_000) {
            answer("/ui/home", page(phoneHeader.copy(plus = PresentAction(trial, "dialog"))))
            storefront(compact = true)
            openMenu()
            inMenu("HAUL PLUS").performClick()
            waitUntil(
                timeoutMillis = 5_000,
            ) { onAllNodes(hasTestTag(PLUS_START_TAG)).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag(HEADER_MENU_TAG).assertDoesNotExist()
            assertEquals(listOf("/"), history.entries, "the dialog opened a page")
        }

    /** B-73: like the filter sheet, the menu is the shell's — back, with the menu open, closes it. */
    @Test
    fun `on a phone a page visited closes the menu`() =
        runDesktopComposeUiTest(PHONE, 1_000) {
            answer("/ui/home", page(phoneHeader))
            destinations("/c/electronics")
            storefront(compact = true)
            onNodeWithText("Electronics").performClick()
            onNodeWithText(NEXT).assertExists()
            openMenu()
            history.back()
            waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText(NEXT)).fetchSemanticsNodes().isEmpty() }
            waitForIdle()
            onNodeWithTag(HEADER_MENU_TAG).assertDoesNotExist()
            assertEquals(listOf("/ui/home", "/ui/c/electronics", "/ui/home"), requests)
        }

    /**
     * B-73: at 375 px the category row runs past the edge, and it scrolls: swiped to its end, its last
     * category is under the finger and opens its page.
     */
    @Test
    fun `on a phone the category row scrolls to its last category`() =
        runDesktopComposeUiTest(NARROW_PHONE, 1_000) {
            val names =
                listOf(
                    "Electronics",
                    "Home & Kitchen",
                    "Fashion",
                    "Beauty",
                    "Kids & Toys",
                    "Sports",
                    "Grocery",
                    "Auto",
                    "Books",
                    "Pets",
                )
            val row =
                phoneHeader.copy(
                    categories = names,
                    catalog = names.map { Link(it, NavigateAction("/c/${it.lowercase()}")) },
                )
            answer("/ui/home", page(row))
            destinations("/c/pets")
            storefront(compact = true)
            onNodeWithTag(CATEGORY_ROW_TAG).performTouchInput { repeat(3) { swipeLeft() } }
            // «Pets» ends 16 px short of the edge: the row's padding, scrolled into view with the rest.
            onNodeWithTag(CATEGORY_ROW_TAG).performTouchInput { click(Offset(width - 26.dp.toPx(), centerY)) }
            onNodeWithText(NEXT).assertExists()
            assertEquals(listOf("/", "/c/pets"), history.entries)
        }

    private fun ComposeUiTest.openMenu() {
        onNodeWithTag(HEADER_MENU_TAG).assertDoesNotExist()
        onNodeWithContentDescription(OPEN_MENU).performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag(HEADER_MENU_TAG)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.inMenu(text: String) =
        onNode(hasText(text) and hasAnyAncestor(hasTestTag(HEADER_MENU_TAG)))

    private companion object {
        const val WIDTH = 1440
        const val PHONE = 390
        const val NARROW_PHONE = 375
        const val EXPANDED = "/c/mugs?brand=Haul&expand=brand"
        const val MUGS = "/search?q=mugs"
        const val NEXT = "The next page"
        const val PAGE_2 = "/c/mugs?brand=Haul&page=2"
        const val PAGE_3 = "/c/mugs?brand=Haul&page=3"
        const val BY_PRICE = "/c/mugs?brand=Haul&sort=price-asc"
        const val CLEARED = "/c/mugs"
        const val PARTS = "/ui/parts"
        const val BY_OSTRA = "/c/mugs?brand=Ostra"
        const val BY_BOTH = "/c/mugs?brand=Lume&brand=Ostra"
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

        /** The header as a customer's page carries it on a phone (B-73): every entry of the menu leads somewhere. */
        val phoneHeader =
            header.copy(
                account = NavigateAction("/account"),
                saved = NavigateAction("/account/saved"),
                plus = NavigateAction("/account"),
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

        /** Home's campaign row as the server draws it at the canvas's «now» (B-59): each banner to the deals page. */
        val campaigns =
            CampaignRow(
                "campaigns",
                CampaignHero(
                    id = "campaign-autumn-mega-sale",
                    eyebrow = "Autumn mega sale · Oct 7 — 14",
                    title = "Up to −70%",
                    subtitle = "1.2 million items marked down across 32 categories",
                    actionLabel = "Shop the sale",
                    tone = "#2F2BFF",
                    label = "campaign image",
                    action = NavigateAction("/deals"),
                ),
                listOf(
                    PromoBanner(
                        id = "banner-tech-week",
                        eyebrow = "Tech week",
                        title = "Laptops from $399",
                        tone = "#E6E4FF",
                        action = NavigateAction("/deals"),
                    ),
                    PromoBanner(
                        id = "banner-free-delivery-weekend",
                        eyebrow = "600K items from local sellers",
                        title = "Free delivery on everything",
                        tone = "#DFFF3A",
                        action = NavigateAction("/deals"),
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

        /**
         * A phone's category page: the brand facet with Ostra and Lume, each ticked or not, each leading to
         * the page with it toggled; [applied] filters, the sheet's «Show N items» and one card under it.
         */
        fun filtered(
            applied: Int,
            show: String,
            card: String,
            ostra: Boolean = false,
            lume: Boolean = false,
        ): KompotComponent {
            fun address(
                ostra: Boolean,
                lume: Boolean,
            ) = listOfNotNull("brand=Lume".takeIf { lume }, "brand=Ostra".takeIf { ostra })
                .joinToString("&")
                .let { if (it.isEmpty()) CLEARED else "$CLEARED?$it" }
            val brand =
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
                                action = LoadAction(PARTS + address(!ostra, lume)),
                            ),
                            FacetOption("Lume", 9, selected = lume, action = LoadAction(PARTS + address(ostra, !lume))),
                        ),
                )
            return page(
                header,
                FilteredResults(
                    id = "results",
                    facets = FacetPanel("facets", listOf(brand)),
                    applied =
                        AppliedFilters(
                            id = "applied",
                            chips = emptyList(),
                            clearLabel = "Clear all",
                            sortLabel = "Popular",
                            filterCount = applied,
                            clearAction = NavigateAction(CLEARED),
                        ),
                    showLabel = show,
                    grid = ProductGrid("grid", listOf(mug.copy(title = card)), columns = 2),
                    pagination =
                        HaulPagination(
                            id = "pagination",
                            current = 1,
                            pages = listOf("1", "2"),
                            moreLabel = "Show 24 more",
                            moreAction = NavigateAction(PAGE_2),
                        ),
                ),
            )
        }

        fun page(vararg children: KompotComponent): KompotComponent =
            ColumnComponent(id = "page", children = children.toList())
    }
}
