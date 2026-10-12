package io.github.youndie.haul

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.ui.ADD_TO_CART
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.HaulHeaderView
import io.github.youndie.haul.ui.LocalHaulNow
import io.github.youndie.haul.ui.PRICE_GAP
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.ProductGridView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-82: between a phone and 1440 the store reads — at 800, 1024 and 1280 no word of the header, the cart, the
 * product page or any other page is cut at an edge or broken between two letters onto two lines. Before, the
 * 1440 arrangement was drawn squeezed from 768 up: «HAUL PLUS» one letter a line at 1024, the search field's
 * placeholder cut to nothing, the cart's title one letter a line at 800, «WH-1000XM / 6» at 1280.
 *
 * Every text is read off its own layout: a line that ends inside a word (neither side of the break a space, nor
 * the end of the first a hyphen or a dash), a one-line text wider than its room, a text past the window's right
 * edge. A row that scrolls sideways — the phone's categories, a search's kinds below 1280 — runs past the edge
 * on purpose and is not read for that.
 */
@OptIn(ExperimentalTestApi::class)
class WidthsTest {
    @Test
    fun `the header reads at every width`() =
        WIDTHS.forEach { width ->
            HEADERS.forEach { (name, header, pending) ->
                runDesktopComposeUiTest(width, 400) {
                    atWidth { HaulHeaderView(header, pending = pending) }
                    assertReads("$name header at $width")
                }
            }
        }

    @Test
    fun `the cart reads at every width`() = pages(CARTS)

    @Test
    fun `the product page reads at every width`() = pages(PRODUCTS)

    /** The rest of the store, which the same rearrangement reaches: home, a category, a search, the account, an order. */
    @Test
    fun `every other page reads at every width`() = pages(OTHERS)

    /**
     * B-108: a deals row — six cards across, home's and the deals page's; at 1280 a card is 180 px — broke a struck-out
     * price with cents mid-number, «$432.9» over «9», where the price and the old price did not fit side by side. The old price goes
     * under the price whole; a card whose two fit keeps them side by side.
     */
    @Test
    fun `a deals row keeps its prices whole`() =
        runDesktopComposeUiTest(1_280, 600) {
            val cards =
                List(6) {
                    ProductCard(
                        id = "deal-$it",
                        productId = "p-$it",
                        title = "A deal of the day",
                        price = if (it == 0) "$12" else "$1,099.99",
                        oldPrice = if (it == 0) "$15" else "$1,432.99",
                        rating = "4.8",
                        reviews = "1,204",
                        delivery = "Tomorrow",
                        tone = "#E3F5D8",
                        label = "deal",
                    )
                }
            atWidth { ProductGridView(ProductGrid("today", cards, columns = 6), gutter = 40.dp) }
            assertReads("a deals row at 1280")
            val shown = onAllNodesWithText("$12").fetchSemanticsNodes().single().boundsInRoot
            val struck = onAllNodesWithText("$15").fetchSemanticsNodes().single().boundsInRoot
            assertTrue(struck.left > shown.right, "a short old price left the price's side")
            val wide = onAllNodesWithText("$1,099.99").fetchSemanticsNodes().first().boundsInRoot
            val under = onAllNodesWithText("$1,432.99").fetchSemanticsNodes().first().boundsInRoot
            assertTrue(
                under.top > wide.center.y && under.left < wide.right,
                "an old price that does not fit beside the price is not under it: $wide, $under",
            )
        }

    /**
     * B-136: on the 390 deals row a 160 px card's «$193.90 $277» fitted its room to the pixel, so the old price
     * touched «+». The prices keep [PRICE_GAP]'s distance from «+» as from each other; at 390 the old price goes
     * under.
     */
    @Test
    fun `a phone's deal card keeps its old price off the plus`() =
        runDesktopComposeUiTest(390, 600) {
            val card =
                ProductCard(
                    id = "deal",
                    productId = "p-002-08",
                    title = "Northwind Travel TVs",
                    price = "$193.90",
                    oldPrice = "$277",
                    rating = "4.8",
                    reviews = "1,204",
                    delivery = "Tomorrow",
                    tone = "#E3F5D8",
                    label = "deal",
                )
            atWidth { ProductGridView(ProductGrid("deals", listOf(card), columns = 6, scroll = true), gutter = 16.dp) }
            val struck = onAllNodesWithText("$277").fetchSemanticsNodes().single().boundsInRoot
            val plus = onAllNodesWithContentDescription(ADD_TO_CART).fetchSemanticsNodes().single().boundsInRoot
            assertTrue(
                plus.left - struck.right >= with(density) { PRICE_GAP.toPx() },
                "the old price ${struck.right} against «+» at ${plus.left}",
            )
        }

    private fun pages(bodies: List<String>) =
        WIDTHS.forEach { width ->
            bodies.forEach { body ->
                runDesktopComposeUiTest(width, 1_000) {
                    atWidth { Page { Body(body) } }
                    assertReads("$body at $width")
                }
            }
        }

    private fun ComposeUiTest.atWidth(content: @Composable () -> Unit) =
        setContent {
            HaulThemeAtWidth(FixtureFonts.fonts, Modifier.fillMaxSize().background(HaulColors.background)) {
                CompositionLocalProvider(LocalHaulNow provides CANVAS_NOW) { Box(Modifier.fillMaxSize()) { content() } }
            }
        }

    private fun ComposeUiTest.assertReads(what: String) {
        waitForIdle()
        val texts =
            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        val edge = onRoot().fetchSemanticsNode().boundsInRoot.right
        val unread =
            texts.fetchSemanticsNodes().mapNotNull { node ->
                if (node.scrollsSideways()) return@mapNotNull null
                val layout = node.layout() ?: return@mapNotNull null
                val text = layout.layoutInput.text.text
                when {
                    layout.didOverflowWidth -> "«$text» cut at its edge"
                    layout.brokenWord() != null -> "«$text» broken inside «${layout.brokenWord()}»"
                    node.boundsInRoot.right > edge + 1f -> "«$text» past the window's edge"
                    else -> null
                }
            }
        assertEquals(emptyList(), unread, what)
    }

    private fun SemanticsNode.layout(): TextLayoutResult? {
        val results = mutableListOf<TextLayoutResult>()
        config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.firstOrNull()
    }

    private fun SemanticsNode.scrollsSideways(): Boolean =
        generateSequence(this) { it.parent }.any { SemanticsProperties.HorizontalScrollAxisRange in it.config }

    /** The word a line break falls inside, or none. */
    private fun TextLayoutResult.brokenWord(): String? {
        val text = layoutInput.text.text
        for (line in 0 until lineCount - 1) {
            val end = getLineEnd(line)
            if (end <= 0 || end >= text.length) continue
            val before = text[end - 1]
            val after = text[end]
            if (!before.isWhitespace() && !after.isWhitespace() && before !in BREAKS_AFTER) {
                val start = text.lastIndexOfAny(charArrayOf(' ', '\n'), end - 1) + 1
                val stop = text.indexOfAny(charArrayOf(' ', '\n'), end).let { if (it < 0) text.length else it }
                return text.substring(start, stop)
            }
        }
        return null
    }

    private companion object {
        /** A phone held sideways or a tablet upright, a tablet sideways or a small laptop, a laptop. */
        val WIDTHS = listOf(800, 1_024, 1_280)

        // The dashes, and the zero-width space an order number carries after its «HL-» to be broken there.
        const val BREAKS_AFTER = "-–—\u200B"

        val CUSTOMER =
            HaulHeader(
                id = "header",
                deliverTo = "Brooklyn, NY 11211",
                deliveryPromise = "Free delivery over $35",
                customerName = "Maya",
                cartCount = 12,
                searchPlaceholder = "Search products",
                categories =
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
                    ),
            )

        /** A customer's, a guest's, the placeholder before any tree, and one with a query in its field. */
        val HEADERS =
            listOf(
                Triple("customer", CUSTOMER, false),
                Triple("guest", CUSTOMER.copy(customerName = null, cartCount = 0), false),
                Triple("placeholder", CUSTOMER, true),
                Triple("search", CUSTOMER.copy(query = "noise cancelling headphones"), false),
            )

        val CARTS =
            listOf(
                "cart_content.json",
                "cart_guest.json",
                "cart_item_changed.json",
                "cart_promo_applied.json",
                "cart_promo_error.json",
                "cart_nothing_selected.json",
                "cart_empty.json",
            )

        val OTHERS =
            listOf(
                "home_content.json",
                "home_guest.json",
                "catalog_content.json",
                "catalog_empty.json",
                "search_results.json",
                "search_no_results.json",
                "account_content.json",
                "account_orders.json",
                "account_not_member.json",
                "account_no_orders.json",
                "order_placed.json",
                "order_in_transit.json",
                "order_delivered.json",
                "saved_content.json",
                "saved_price_drops.json",
                "checkout_content.json",
                "checkout_validation.json",
                "checkout_place_error.json",
            )

        val PRODUCTS =
            listOf(
                "product_description.json",
                "product_specifications.json",
                "product_reviews.json",
                "product_questions.json",
                "product_out_of_stock.json",
            )
    }
}
