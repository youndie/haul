package io.github.youndie.haul.shell

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.feature.cart.CartCommands
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.cart.LineCommand
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.CARD_ADD_TAG
import io.github.youndie.haul.ui.CART_BUTTON_TAG
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.LINK_MENU_TAG
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.commands.kompotUpdate
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.TextComponent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.PolymorphicSerializer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-170: the header keeps its content while the next page loads. A visit to another path draws that page's
 * placeholder until its tree arrives, and the placeholder's header was drawn as a placeholder too — the account slot
 * a grey bar, the cart with no count — on every page visited, though the header the last tree drew was there to
 * draw. It is drawn as it was now, «Sign in» and the count included, the count an `update` wrote too; only the first
 * page on its way, before the server has drawn any header, draws the client's own as a placeholder. A fake transport
 * serves the trees, each held back until the test lets it through: a load still on its way.
 */
@OptIn(ExperimentalTestApi::class)
class KeptHeaderTest {
    private val requests = CopyOnWriteArrayList<String>()

    /** The next answer to each path waits for its gate: the test opens it once it has read the page on its way. */
    private val gates = ConcurrentHashMap<String, ArrayDeque<CompletableDeferred<Unit>>>()

    private var cartAnswer: KompotAction? = null

    private fun gate(path: String) =
        CompletableDeferred<Unit>().also { gates.getOrPut(path) { ArrayDeque() }.addLast(it) }

    @Test
    fun `a page on its way keeps the header the last tree drew`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val history = FakeHistory("/")
            val firstHome = gate("/ui/home")
            storefront(history) { path -> page(path, HEADER) }

            // The first page on its way: no header has been drawn, so the client's own is a placeholder.
            waitUntil(timeoutMillis = 5_000) { tagged(LOADING_TAG) }
            assertTrue(!exists(signIn()), "the first page on its way drew an account before any header arrived")
            assertTrue(!exists(count(2)), "the first page on its way drew a count before any header arrived")
            firstHome.complete(Unit)
            waitUntil(timeoutMillis = 5_000) { exists(hasText(pageAt("/"))) }
            assertTrue(exists(signIn()) && exists(count(2)), "the home page's tree did not draw its header")

            // A category pressed in the header: its placeholder is drawn under the header the home page drew.
            val category = gate("/ui/c/electronics")
            press(hasText("Electronics") and !hasAnyAncestor(hasTestTag(LINK_MENU_TAG)))
            waitUntil(timeoutMillis = 5_000) { "/ui/c/electronics" in requests && tagged(LOADING_TAG) }
            keptWhileLoading(count = 2)
            category.complete(Unit)
            waitUntil(timeoutMillis = 5_000) { exists(hasText(pageAt("/c/electronics"))) }

            // A product opened from there by its card, then back: each on its way under the header last drawn.
            val product = gate("/ui/p/p-1")
            press(hasText("Stoneware Mug"))
            waitUntil(timeoutMillis = 5_000) { "/ui/p/p-1" in requests && tagged(LOADING_TAG) }
            keptWhileLoading(count = 2)
            product.complete(Unit)
            waitUntil(timeoutMillis = 5_000) { exists(hasText(pageAt("/p/p-1"))) }

            val back = gate("/ui/c/electronics")
            history.back()
            waitUntil(
                timeoutMillis = 5_000,
            ) { requests.count { it == "/ui/c/electronics" } == 2 && tagged(LOADING_TAG) }
            keptWhileLoading(count = 2)
            back.complete(Unit)
            waitUntil(timeoutMillis = 5_000) { exists(hasText(pageAt("/c/electronics"))) }
        }

    /**
     * «Add to cart» answers the header's count in place (B-63): the next page on its way draws that count, not the one
     * the tree arrived with, and the tree that arrives then draws its own.
     */
    @Test
    fun `the count an update wrote is the one a page on its way draws`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val history = FakeHistory("/")
            cartAnswer = kompotUpdate { addComponent(HEADER.copy(cartCount = 7)) }
            storefront(history) { path -> page(path, HEADER.copy(cartCount = if (path == "/ui/home") 2 else 7)) }
            waitUntil(timeoutMillis = 5_000) { exists(count(2)) }
            onAllNodes(hasTestTag(CARD_ADD_TAG))[0].performClick()
            waitUntil(timeoutMillis = 5_000) { exists(count(7)) }

            val category = gate("/ui/c/electronics")
            press(hasText("Electronics") and !hasAnyAncestor(hasTestTag(LINK_MENU_TAG)))
            waitUntil(timeoutMillis = 5_000) { "/ui/c/electronics" in requests && tagged(LOADING_TAG) }
            keptWhileLoading(count = 7)
            assertTrue(!exists(count(2)), "the page on its way drew the count the tree arrived with, not the update's")
            category.complete(Unit)
            waitUntil(timeoutMillis = 5_000) { exists(hasText(pageAt("/c/electronics"))) }
            assertEquals(listOf("/ui/home", "/ui/c/electronics"), requests.toList())
        }

    /** The page's placeholder is drawn, and over it the header as it was: «Sign in» to press, and the cart's count. */
    private fun ComposeUiTest.keptWhileLoading(count: Int) {
        waitForIdle()
        assertTrue(tagged(LOADING_TAG), "the page is not on its way")
        assertTrue(exists(signIn()), "the header over a page on its way went back to its account placeholder")
        assertTrue(exists(count(count)), "the header over a page on its way lost the cart's count")
    }

    private fun ComposeUiTest.storefront(
        history: FakeHistory,
        answer: (path: String) -> KompotComponent,
    ) {
        val transport =
            HaulTransport { path ->
                requests += path
                gates[path]?.removeFirstOrNull()?.await()
                ok(answer(path))
            }
        val cartCommands = CartCommands { checkNotNull(cartAnswer) { "nothing answers ${it.url}" } }
        setContent {
            HaulTheme(FixtureFonts.fonts) {
                Storefront(transport, history, signIn = {}, cartCommands = cartCommands)
            }
        }
    }

    private fun ComposeUiTest.press(matcher: SemanticsMatcher) {
        waitUntil(timeoutMillis = 5_000) { exists(matcher) }
        onNode(matcher).performClick()
    }

    private fun ComposeUiTest.exists(matcher: SemanticsMatcher): Boolean =
        onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.tagged(tag: String) = exists(hasTestTag(tag))

    private companion object {
        const val WIDTH = 1440
        const val HEIGHT = 1000

        val HEADER =
            HaulHeader(
                id = "header",
                deliverTo = "Brooklyn, NY 11211",
                deliveryPromise = "Free delivery over $35",
                customerName = null,
                cartCount = 2,
                searchPlaceholder = "Search products",
                categories = listOf("Electronics"),
                account = NavigateAction("/sign-in"),
                catalog = listOf(Link("Electronics", NavigateAction("/c/electronics"))),
                deals = NavigateAction("/deals"),
                cart = NavigateAction("/cart"),
            )

        /** The guest's «Sign in», which the header's placeholder draws as a grey bar. */
        fun signIn() = hasText("Sign in") and hasClickAction()

        /** The cart button's count. */
        fun count(n: Int) = hasText("$n") and hasAnyAncestor(hasTestTag(CART_BUTTON_TAG))

        fun pageAt(address: String) = "The page at $address"

        /** Any page: [header], a card whose «+» is a cart command, and where it is. */
        fun page(
            path: String,
            header: HaulHeader,
        ): KompotComponent {
            val address = if (path == "/ui/home") "/" else path.removePrefix("/ui")
            val card =
                ProductCard(
                    id = "card",
                    productId = "p-1",
                    title = "Stoneware Mug",
                    price = "$24",
                    rating = "4.8",
                    reviews = "420",
                    delivery = "Tomorrow",
                    tone = "#E3F5D8",
                    action = NavigateAction("/p/p-1"),
                    add = LineCommand("/api/v1/cart/lines/p-1-0?answer=card", LineChange(quantity = 1)),
                )
            return ColumnComponent(
                id = "page",
                children =
                    listOf(
                        header,
                        TextComponent(id = "where", text = pageAt(address)),
                        ProductGrid("grid", listOf(card), columns = 4),
                    ),
            )
        }

        fun ok(tree: KompotComponent) =
            HaulResponse(200, haulWireJson.encodeToString(PolymorphicSerializer(KompotComponent::class), tree))
    }
}
