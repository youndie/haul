package io.github.youndie.haul.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.HaulThemeAtWidth
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.ui.Chip
import io.github.youndie.haul.ui.FilterChips
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.commands.LoadAction
import io.github.youndie.kompot.commands.UpdateHistory
import io.github.youndie.kompot.commands.kompotUpdate
import io.github.youndie.kompot.standard.ColumnComponent
import kotlinx.serialization.PolymorphicSerializer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-142: a search's category chips reach every scope at every width. From 1280 up the row was one line cut under the
 * sort control and did not move — `classic`'s 111 chips ended at «Books · Classics 1», a word cut under the sort, and
 * «Party · Classics» had nothing to press; a search opened on a scope past the line did not show its chip at all.
 * At each band the row scrolls by a sideways wheel, a drag and Tab, the chip past the line opens its scope, and a
 * page that arrives on a chosen chip past the line shows it in the row's view.
 */
@OptIn(ExperimentalTestApi::class)
class SearchChipsReachTest {
    @Test
    fun `at 1440 every chip is reachable and the chosen one arrives in view`() = reaches(1_440)

    @Test
    fun `at 1280 every chip is reachable and the chosen one arrives in view`() = reaches(1_280)

    @Test
    fun `at 1024 every chip is reachable and the chosen one arrives in view`() = reaches(1_024)

    @Test
    fun `at 390 every chip is reachable and the chosen one arrives in view`() = reaches(390)

    private fun reaches(width: Int) {
        arrivesChosen(width)
        reachesByHand(width)
    }

    /** A search opened on a scope past the line's end draws its chip in the row's view. */
    private fun arrivesChosen(width: Int) =
        runDesktopComposeUiTest(width, HEIGHT) {
            val stack = Stack(SCOPED)
            storefront(stack)
            waitForText(PARTY_CHIP)
            assertInView(PARTY_CHIP, "the chosen chip arrived out of view at $width")
        }

    /** The wheel, a drag and Tab move the row; Tab reaches «Party · Classics», and pressing it scopes the results. */
    private fun reachesByHand(width: Int) =
        runDesktopComposeUiTest(width, HEIGHT) {
            val stack = Stack(SEARCH)
            storefront(stack)
            waitForText(ALL_CHIP)
            assertEquals(0f, offset(), "the row arrived scrolled at $width")

            row().performMouseInput {
                moveTo(center)
                scroll(WHEEL, ScrollWheel.Horizontal)
            }
            waitForIdle()
            val wheeled = offset()
            assertTrue(wheeled > 0f, "a sideways wheel did not move the row at $width")

            row().performTouchInput { swipeLeft() }
            waitForIdle()
            assertTrue(offset() > wheeled, "a drag did not move the row at $width")

            chip(ALL_CHIP).performSemanticsAction(SemanticsActions.RequestFocus)
            repeat(PARTY_INDEX) { onRoot().performKeyInput { pressKey(Key.Tab) } }
            waitForIdle()
            chip(PARTY_CHIP).assertIsFocused()
            assertInView(PARTY_CHIP, "Tab did not bring «Party · Classics» into view at $width")

            chip(PARTY_CHIP).performClick()
            waitForText(SCOPED_CARD)
            assertEquals(listOf("/ui$SEARCH", PARTS + SCOPED), stack.requests.toList(), "at $width")
            assertEquals(listOf(SEARCH, SCOPED), stack.history.entries, "at $width")
            assertInView(PARTY_CHIP, "the pressed chip left the view at $width")
        }

    /** The fake server: the search and its scope as pages, and the scope as the part a chip loads. */
    private class Stack(
        address: String,
    ) {
        val requests = CopyOnWriteArrayList<String>()
        val history = FakeHistory(address)
        private val pages = mapOf("/ui$SEARCH" to page(null), "/ui$SCOPED" to page(PARTY))
        private val parts: Map<String, KompotAction> =
            mapOf(
                PARTS + SCOPED to
                    kompotUpdate(SCOPED, UpdateHistory.PUSH) {
                        addComponent(chips(PARTY))
                        addComponent(grid(PARTY))
                    },
            )

        val transport =
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
    }

    private fun ComposeUiTest.storefront(stack: Stack) =
        setContent {
            HaulThemeAtWidth(FixtureFonts.fonts, Modifier.fillMaxSize().background(HaulColors.background)) {
                Storefront(stack.transport, stack.history, signIn = {})
            }
        }

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.chip(label: String): SemanticsNodeInteraction = onNode(hasText(label))

    /** The chips' row: the sideways scroll holding «All» — at 390 the header's categories scroll too. */
    private fun ComposeUiTest.row(): SemanticsNodeInteraction =
        onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) and
                hasAnyDescendant(hasText(ALL_CHIP)),
        )

    private fun ComposeUiTest.offset(): Float =
        row().fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()

    /** [label]'s chip lies whole inside the row's view, and inside the window. */
    private fun ComposeUiTest.assertInView(
        label: String,
        what: String,
    ) {
        waitForIdle()
        val view = row().fetchSemanticsNode().boundsInRoot
        val window = onRoot().fetchSemanticsNode().boundsInRoot
        val pill = chip(label).fetchSemanticsNode().boundsInRoot
        assertTrue(pill.width > 0f && pill.within(view) && pill.within(window), "$what: $pill in $view")
    }

    private fun Rect.within(outer: Rect): Boolean =
        left >= outer.left - 0.5f && right <= outer.right + 0.5f && top >= outer.top - 0.5f &&
            bottom <= outer.bottom + 0.5f

    private companion object {
        const val HEIGHT = 900
        const val WHEEL = 6f
        const val PARTS = "/ui/parts"
        const val SEARCH = "/search?q=classic"
        const val PARTY = "party-essentials-classics"
        const val SCOPED = "$SEARCH&category=$PARTY"
        const val ALL_CHIP = "All 600"
        const val PARTY_CHIP = "Party · Classics 15"
        const val SCOPED_CARD = "A party classic"

        /** The top-level categories, «Party» deep in the row, as `classic`'s are. */
        val TOPS =
            listOf("Appliances", "Auto", "Beauty", "Books", "Electronics", "Fashion", "Party", "Pets", "Sports", "Toys")

        /** Each top-level category's leaves: ten tops by eleven leaves are `classic`'s 110 chips after «All». */
        val LEAVES =
            listOf(
                "Classics",
                "Limited",
                "Basics",
                "Deluxe",
                "Essentials",
                "Kits",
                "Mini",
                "Plus",
                "Pro",
                "Sets",
                "Travel",
            )

        /** The slug of a chip, and its label without the count. */
        val SCOPES: List<Pair<String, String>> =
            TOPS.flatMap { top ->
                LEAVES.map { leaf ->
                    "${top.lowercase()}-essentials-${leaf.lowercase()}" to
                        "$top · $leaf"
                }
            }

        /** Where «Party · Classics» stands after «All»: the Tabs from «All» that reach it. */
        val PARTY_INDEX = SCOPES.indexOfFirst { it.first == PARTY } + 1

        val header =
            HaulHeader(
                id = "header",
                deliverTo = "Brooklyn, NY 11211",
                deliveryPromise = "Free delivery over $35",
                customerName = null,
                cartCount = 0,
                searchPlaceholder = "Search products",
                categories = listOf("Electronics", "Home & Kitchen", "Fashion", "Beauty", "Kids & Toys", "Sports"),
                query = "classic",
            )

        /** The row with [chosen] — a slug, `null` for «All» — chosen; every chip loads its scope as a part. */
        fun chips(chosen: String?) =
            FilterChips(
                id = "categories",
                chips =
                    listOf(Chip("All", chosen == null, LoadAction(PARTS + SEARCH), "600")) +
                        SCOPES.map { (slug, label) ->
                            Chip(
                                label,
                                slug == chosen,
                                LoadAction("$PARTS$SEARCH&category=$slug"),
                                if (slug ==
                                    PARTY
                                ) {
                                    "15"
                                } else {
                                    "5"
                                },
                            )
                        },
                sortLabel = "Relevance",
                sorts = listOf(Link("Relevance", LoadAction(PARTS + SEARCH))),
            )

        fun grid(chosen: String?) =
            ProductGrid(
                "grid",
                List(4) {
                    ProductCard(
                        id = "card-$it",
                        productId = "p-$it",
                        title =
                            if (chosen ==
                                null
                            ) {
                                "A classic $it"
                            } else if (it == 0) {
                                SCOPED_CARD
                            } else {
                                "A party classic $it"
                            },
                        price = "$24",
                        rating = "4.8",
                        reviews = "420",
                        delivery = "Tomorrow",
                        tone = "#E3F5D8",
                    )
                },
                columns = 4,
            )

        fun page(chosen: String?): KompotComponent =
            ColumnComponent(id = "page", children = listOf(header, chips(chosen), grid(chosen)))
    }
}
