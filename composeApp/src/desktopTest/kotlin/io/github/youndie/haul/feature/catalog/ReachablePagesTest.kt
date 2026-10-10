package io.github.youndie.haul.feature.catalog

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.LOADING_TAG
import io.github.youndie.haul.shell.PAGE_SCROLL_TAG
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.AppliedFilters
import io.github.youndie.haul.ui.Chip
import io.github.youndie.haul.ui.FacetPanel
import io.github.youndie.haul.ui.FilterChips
import io.github.youndie.haul.ui.FilteredResults
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.HaulPagination
import io.github.youndie.haul.ui.LINK_MENU_TAG
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.commands.LoadAction
import io.github.youndie.kompot.commands.UpdateHistory
import io.github.youndie.kompot.commands.kompotUpdate
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import kotlinx.serialization.PolymorphicSerializer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-77: every page of a listing can be reached. Page 7 of a long listing is drawn with its neighbours and the
 * ends, each a press away; «Show 24 more» appends the next page under the cards already drawn, where the
 * shopper is; a search's sort opens its orders and loads the one picked. A fake transport serves the pages
 * and their parts as the server draws them (`PaginationTest`, `DrawnActionsTest` on the server).
 */
@OptIn(ExperimentalTestApi::class)
class ReachablePagesTest {
    private val requests = CopyOnWriteArrayList<String>()
    private var history = FakeHistory(SEVENTH)
    private val pages = mutableMapOf<String, KompotComponent>()
    private val parts = mutableMapOf<String, KompotAction>()

    private val transport =
        HaulTransport { path ->
            requests += path
            val body =
                parts[path]?.let { haulWireJson.encodeToString(PolymorphicSerializer(KompotAction::class), it) }
                    ?: haulWireJson.encodeToString(
                        PolymorphicSerializer(KompotComponent::class),
                        pages[path] ?: error("nothing answers $path"),
                    )
            HaulResponse(200, body)
        }

    private fun ComposeUiTest.storefront() =
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                Storefront(transport, history, signIn = {})
            }
        }

    private fun ComposeUiTest.exists(text: String) = onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.waitForText(text: String) = waitUntil(timeoutMillis = 5_000) { exists(text) }

    private fun ComposeUiTest.scrolled(): Float =
        onNodeWithTag(PAGE_SCROLL_TAG).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    /** The mugs at [address], their grid holding the pages [shown] of 517; its parts at `/ui/parts<address>`. */
    private fun serve(
        address: String,
        shown: IntRange,
    ) {
        val page = mugs(shown)
        pages["/ui$address"] = page
        parts[PARTS + address] =
            kompotUpdate(address, UpdateHistory.PUSH) {
                addComponent((page as ColumnComponent).children.single { it.id == "results" })
            }
    }

    @Test
    fun `page seven of a long listing draws its neighbours and both ends and each opens its page`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            serve(SEVENTH, 7..7)
            serve(EIGHTH, 8..8)
            storefront()
            waitForText(card(7, 0))
            listOf("1", "6", "7", "8", "517").forEach { onNodeWithText(it).assertExists() }
            assertEquals(2, onAllNodes(hasText("…")).fetchSemanticsNodes().size, "one «…» on either side of 6 7 8")

            onNodeWithText("8").performScrollTo().performClick()
            waitForText(card(8, 0))
            assertEquals(listOf("/ui$SEVENTH", PARTS + EIGHTH), requests.toList())
            assertEquals(listOf(SEVENTH, EIGHTH), history.entries)
        }

    @Test
    fun `show more appends the next page under the cards already drawn`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            serve(SEVENTH, 7..7)
            serve(MORE, 7..8)
            storefront()
            waitForText(card(7, 0))
            onNodeWithTag(PAGE_SCROLL_TAG).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, SCROLL) }
            waitForIdle()
            val before = scrolled()

            onNodeWithText("Show 24 more").performClick()
            waitForText(card(8, 0))
            // The cards of page 7 are still drawn, where they were; page 8's follow them.
            onNodeWithText(card(7, 0)).assertExists()
            onNodeWithText(card(7, CARDS - 1)).assertExists()
            onNodeWithTag(LOADING_TAG).assertDoesNotExist()
            assertEquals(before, scrolled(), "the scroll moved when page 8 was appended")
            assertEquals(listOf("/ui$SEVENTH", PARTS + MORE), requests.toList(), "«Show 24 more» asked for a page")
            assertEquals(listOf(SEVENTH, MORE), history.entries)
        }

    @Test
    fun `a search's sort opens its orders and loads the one picked`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            history = FakeHistory(SEARCH)
            pages["/ui$SEARCH"] = search("Popular")
            parts[PARTS + CHEAPEST] =
                kompotUpdate(CHEAPEST, UpdateHistory.PUSH) { addComponent(chips("Price: low to high")) }
            storefront()
            waitForText("Popular")
            onNodeWithTag(LINK_MENU_TAG).assertDoesNotExist()
            onNodeWithText("Popular").performClick()
            onNodeWithTag(LINK_MENU_TAG).assertExists()
            onNodeWithText("Price: low to high").performClick()
            waitUntil(timeoutMillis = 5_000) { history.entries.size == 2 }
            waitForIdle()
            onNodeWithTag(LINK_MENU_TAG).assertDoesNotExist()
            onNodeWithText("Price: low to high").assertExists()
            onNodeWithText("Popular").assertDoesNotExist()
            assertEquals(listOf("/ui$SEARCH", PARTS + CHEAPEST), requests.toList())
            assertEquals(listOf(SEARCH, CHEAPEST), history.entries)
        }

    private companion object {
        const val WIDTH = 1440
        const val HEIGHT = 1000
        const val SCROLL = 240f
        const val CARDS = 8
        const val PARTS = "/ui/parts"
        const val SEVENTH = "/c/mugs?page=7"
        const val EIGHTH = "/c/mugs?page=8"
        const val MORE = "/c/mugs?page=8&from=7"
        const val SEARCH = "/search?q=mug"
        const val CHEAPEST = "/search?q=mug&sort=price-asc"
        const val TOTAL = 517

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
            page: Int,
            index: Int,
        ) = "Page $page mug $index"

        fun cards(page: Int) =
            List(CARDS) { index ->
                ProductCard(
                    id = "card-$page-$index",
                    productId = "p-$page-$index",
                    title = card(page, index),
                    price = "$24",
                    rating = "4.8",
                    reviews = "420",
                    delivery = "Tomorrow",
                    tone = "#E3F5D8",
                    label = "mug",
                    action = NavigateAction("/p/p-$page-$index"),
                )
            }

        /** The address of the mugs holding [pages], as the server writes it. */
        fun address(pages: IntRange) =
            "/c/mugs" + (if (pages.last > 1) "?page=${pages.last}" else "") +
                if (pages.first < pages.last) "&from=${pages.first}" else ""

        /** The page numbers the server draws around [current] of 517 (`pageNumbers`). */
        fun numbers(current: Int): List<String> =
            when (current) {
                7 -> listOf("1", "…", "6", "7", "8", "…", "$TOTAL")
                8 -> listOf("1", "…", "7", "8", "9", "…", "$TOTAL")
                else -> error("no numbers for page $current")
            }

        /** The mugs whose grid holds the pages [shown]: «Show 24 more» appends the next to them. */
        fun mugs(shown: IntRange): KompotComponent {
            val current = shown.last
            val numbers = numbers(current)
            return ColumnComponent(
                id = "page",
                children =
                    listOf(
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
                                    filterCount = 0,
                                ),
                            showLabel = "Show 12,408 items",
                            grid = ProductGrid("grid", shown.flatMap(::cards), columns = 4),
                            pagination =
                                HaulPagination(
                                    id = "pagination",
                                    current = current,
                                    pages = numbers,
                                    moreLabel = "Show 24 more",
                                    moreAction = LoadAction(PARTS + address(shown.first..current + 1)),
                                    links =
                                        numbers.mapNotNull { label ->
                                            label.toIntOrNull()?.takeIf { it != current }?.let {
                                                Link(label, LoadAction(PARTS + address(it..it)))
                                            }
                                        },
                                ),
                        ),
                    ),
            )
        }

        fun chips(sort: String) =
            FilterChips(
                id = "categories",
                chips = listOf(Chip("All", true, LoadAction("$PARTS$SEARCH"), "12")),
                sortLabel = sort,
                sorts =
                    listOf(
                        Link("Popular", LoadAction("$PARTS$SEARCH")),
                        Link("Price: low to high", LoadAction("$PARTS$CHEAPEST")),
                    ),
            )

        fun search(sort: String): KompotComponent =
            ColumnComponent(
                id = "page",
                children = listOf(header, chips(sort), ProductGrid("grid", cards(1), columns = 5)),
            )
    }
}
