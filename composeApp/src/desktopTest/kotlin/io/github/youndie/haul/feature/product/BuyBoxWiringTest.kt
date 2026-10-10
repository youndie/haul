package io.github.youndie.haul.feature.product

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.decode
import io.github.youndie.haul.feature.cart.CartCommand
import io.github.youndie.haul.feature.cart.CartCommands
import io.github.youndie.haul.feature.cart.CartRefused
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.cart.LineCommand
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.MESSAGE_MS
import io.github.youndie.haul.shell.MESSAGE_TAG
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.ProductDetails
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.commands.kompotUpdate
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.RefreshAction
import io.github.youndie.kompot.standard.SequenceAction
import io.github.youndie.kompot.standard.ShowMessageAction
import io.github.youndie.kompot.standard.TextComponent
import kotlinx.serialization.PolymorphicSerializer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-48 in the client: the product page's «Add to cart» and «Buy now», pressed in the storefront, send
 * the line change their tree carries to the cart commands, then follow the answer — «Add to cart» the
 * server's `refresh`, which fetches the page again; «Buy now» its own `next`, to checkout or to sign-in
 * on the way there — and a refused «Buy now» goes nowhere. Out of stock the tree carries no command and
 * a press sends nothing. The page is the server's own body for Product_Description and
 * Product_OutOfStock (`resources/bodies/`), with the commands B-48 gives it.
 */
@OptIn(ExperimentalTestApi::class)
class BuyBoxWiringTest {
    private val requests = CopyOnWriteArrayList<String>()
    private val sent = CopyOnWriteArrayList<CartCommand>()
    private val signIns = AtomicInteger()
    private val history = FakeHistory(ADDRESS)

    /** The server's answer to a cart command: accepted with [answer] unless a test refuses it. */
    private var refuse = false
    private var answer: KompotAction = RefreshAction

    private val cartCommands =
        CartCommands { command ->
            sent += command
            if (refuse) throw CartRefused(409, null, "Only 1 left in stock")
            answer
        }

    private fun ComposeUiTest.storefront(page: KompotComponent) {
        val transport =
            HaulTransport { path ->
                requests += path
                val tree = if (path == "/ui$ADDRESS") page else NEXT_PAGE
                HaulResponse(200, haulWireJson.encodeToString(PolymorphicSerializer(KompotComponent::class), tree))
            }
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                Storefront(
                    transport,
                    history,
                    signIn = { signIns.incrementAndGet() },
                    cartCommands = cartCommands,
                )
            }
        }
        waitUntil(timeoutMillis = 5_000) { requests.isNotEmpty() }
        onNodeWithText("Add to cart").assertExists()
    }

    @Test
    fun `add to cart sends its line change and draws the page again`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            storefront(inStock(BUY_AS_CUSTOMER))
            onNodeWithText("Add to cart").performClick()
            waitUntil(timeoutMillis = 5_000) { requests.size == 2 }
            assertEquals(listOf<CartCommand>(CartCommand.ChangeLine(LINE, ADD.change)), sent.toList())
            assertEquals(listOf("/ui$ADDRESS", "/ui$ADDRESS"), requests.toList(), "the page was not drawn again")
            assertEquals(listOf(ADDRESS), history.entries, "«Add to cart» left the page")
        }

    /**
     * B-75: «Add to cart» answered as the server answers it — `update` of the header and the buy box, then
     * `show_message` — draws the count, the buy box's «1 in your cart» and the message, with no page asked
     * for; the message's own button opens the cart and takes the message away.
     */
    @Test
    fun `add to cart shows its message and the buy box says the line is in the cart`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val page = inStock(BUY_AS_CUSTOMER) as ColumnComponent
            val header = page.children.filterIsInstance<HaulHeader>().single()
            val details = page.children.filterIsInstance<ProductDetails>().single()
            answer =
                SequenceAction(
                    listOf(
                        kompotUpdate {
                            addComponent(header.copy(cartCount = 1))
                            addComponent(
                                details.copy(
                                    add = LineCommand(LINE, LineChange(quantity = 2)),
                                    inCart = Link("1 in your cart", NavigateAction("/cart")),
                                ),
                            )
                        },
                        ShowMessageAction(
                            "Added to your cart",
                            actionLabel = "View cart",
                            action = NavigateAction("/cart"),
                        ),
                    ),
                )
            storefront(page)
            onNodeWithTag(IN_CART_TAG).assertDoesNotExist()

            onNodeWithText("Add to cart").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(MESSAGE_TAG).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Added to your cart").assertExists()
            onNodeWithTag(IN_CART_TAG).assertExists()
            onNodeWithText("1 in your cart").assertExists()
            assertEquals(listOf("/ui$ADDRESS"), requests.toList(), "«Add to cart» asked for a page")

            onNode(hasText("View cart") and hasAnyAncestor(hasTestTag(MESSAGE_TAG))).performClick()
            onNodeWithText(NEXT).assertExists()
            onNodeWithTag(MESSAGE_TAG).assertDoesNotExist()
            assertEquals(listOf(ADDRESS, "/cart"), history.entries)
        }

    /** B-75: a message with nothing pressed goes away on its own once its time is up. */
    @Test
    fun `a message goes away on its own`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            answer = SequenceAction(listOf(ShowMessageAction("Added to your cart")))
            storefront(inStock(BUY_AS_CUSTOMER))
            onNodeWithText("Add to cart").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(MESSAGE_TAG).fetchSemanticsNodes().isNotEmpty() }
            mainClock.advanceTimeBy(MESSAGE_MS - 500)
            onNodeWithTag(MESSAGE_TAG).assertExists()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            onNodeWithTag(MESSAGE_TAG).assertDoesNotExist()
        }

    @Test
    fun `a customer's buy now sends its line change and opens checkout`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            storefront(inStock(BUY_AS_CUSTOMER))
            onNodeWithText("Buy now").performClick()
            onNodeWithText(NEXT).assertExists()
            assertEquals(listOf<CartCommand>(CartCommand.ChangeLine(LINE, BUY_AS_CUSTOMER.change)), sent.toList())
            assertEquals(listOf(ADDRESS, "/checkout"), history.entries)
            assertEquals(listOf("/ui$ADDRESS", "/ui/checkout"), requests.toList())
            assertEquals(0, signIns.get(), "a customer was asked to sign in")
        }

    /** A guest's «Buy now» goes through sign-in (B-41's `next`), as the cart's «Sign in to check out» does. */
    @Test
    fun `a guest's buy now signs in and then opens checkout`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            storefront(inStock(BUY_AS_CUSTOMER.copy(next = NavigateAction("/sign-in?next=%2Fcheckout"))))
            onNodeWithText("Buy now").performClick()
            onNodeWithText(NEXT).assertExists()
            assertEquals(1, signIns.get(), "the guest was not asked to sign in")
            assertEquals(listOf<CartCommand>(CartCommand.ChangeLine(LINE, BUY_AS_CUSTOMER.change)), sent.toList())
            assertEquals(listOf(ADDRESS, "/checkout"), history.entries)
        }

    /** A refused «Buy now» put nothing in the cart, so it does not go to checkout: the page is drawn again. */
    @Test
    fun `a refused buy now stays on the page and draws it again`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            refuse = true
            storefront(inStock(BUY_AS_CUSTOMER))
            onNodeWithText("Buy now").performClick()
            waitUntil(timeoutMillis = 5_000) { requests.size == 2 }
            assertEquals(1, sent.size)
            assertEquals(listOf(ADDRESS), history.entries, "a refused «Buy now» went to checkout")
            assertEquals(listOf("/ui$ADDRESS", "/ui$ADDRESS"), requests.toList())
        }

    @Test
    fun `out of stock neither button sends anything`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            storefront(decode("product_out_of_stock.json"))
            onNodeWithText("Add to cart").performClick()
            onNodeWithText("Buy now").performClick()
            waitForIdle()
            assertEquals(emptyList(), sent.toList())
            assertEquals(listOf(ADDRESS), history.entries)
            assertEquals(listOf("/ui$ADDRESS"), requests.toList())
        }

    private companion object {
        const val WIDTH = 1440
        const val HEIGHT = 1_500
        const val ADDRESS = "/p/p-sony-wh-1000xm6"
        const val LINE = "/api/v1/cart/lines/p-sony-wh-1000xm6-0"
        const val NEXT = "The next page"

        val ADD = LineCommand(LINE, LineChange(quantity = 1))
        val BUY_AS_CUSTOMER = LineCommand(LINE, LineChange(quantity = 1, selected = true), NavigateAction("/checkout"))

        val NEXT_PAGE: KompotComponent =
            ColumnComponent(id = "page", children = listOf(TextComponent(id = "next", text = NEXT)))

        /** Product_Description as the server draws it, with the commands B-48 gives its buttons. */
        fun inStock(buy: LineCommand): KompotComponent {
            val page = decode("product_description.json") as ColumnComponent
            return page.copy(
                children =
                    page.children.map {
                        if (it is ProductDetails) it.copy(add = ADD, buy = buy) else it
                    },
            )
        }
    }
}
