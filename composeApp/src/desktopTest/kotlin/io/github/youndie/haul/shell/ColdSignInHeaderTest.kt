package io.github.youndie.haul.shell

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.CANVAS_NOW
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.CLOSE_MENU
import io.github.youndie.haul.ui.HEADER_MENU_TAG
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.LINK_MENU_TAG
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.OPEN_MENU
import io.github.youndie.haul.ui.SCOPE_PICKER_TAG
import io.github.youndie.haul.ui.SEARCH_FIELD_TAG
import io.github.youndie.haul.ui.SearchScope
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.TextComponent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.PolymorphicSerializer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * B-138: `/sign-in` landed on cold makes no request while it loads (B-66, B-105), so it draws the client's own
 * header — ten category words, each to the catalog's root, and a search picker with no scopes. A press on the phone
 * header's menu or on the picker asks for the header alone, once per page, and the menu lists the server's
 * categories with their addresses, the picker its scopes.
 */
@OptIn(ExperimentalTestApi::class)
class ColdSignInHeaderTest {
    private val requests = CopyOnWriteArrayList<String>()

    @Test
    fun `a cold sign-in page asks nothing until a press`() {
        listOf(false, true).forEach { compact ->
            requests.clear()
            runDesktopComposeUiTest(if (compact) PHONE else WIDTH, HEIGHT) {
                storefront(FakeHistory(START), compact) { path -> answer(path) }
                waitUntil(timeoutMillis = 5_000) { exists(hasText("Sign in to HAUL")) }
                waitForIdle()
                assertEquals(emptyList(), requests.toList(), "compact=$compact: the sign-in page asked on its load")
            }
        }
    }

    /** The phone menu pressed asks once, lists the 32 the server names, and each leads to its address. */
    @Test
    fun `the phone menu pressed lists the server's categories`() =
        runDesktopComposeUiTest(PHONE, HEIGHT) {
            val history = FakeHistory(START)
            storefront(history, compact = true) { path -> answer(path) }
            waitUntil(timeoutMillis = 5_000) { exists(hasText("Sign in to HAUL")) }

            openMenu()
            waitUntil(timeoutMillis = 5_000) { exists(hasText(CATEGORIES.last().first) and inMenu()) }
            assertEquals(listOf(HEADER_TREE), requests.toList())
            CATEGORIES.forEach { (label, _) ->
                assertTrue(exists(hasText(label) and inMenu()), "the menu did not list $label")
            }
            // The client's own words are gone: «Pets» was one of its ten, each to the catalog's root.
            assertTrue(!exists(hasText("Pets") and inMenu()), "the menu kept the client's own category words")

            val (label, address) = CATEGORIES[17]
            onNode(hasText(label) and inMenu()).performClick()
            waitUntil(timeoutMillis = 5_000) { exists(hasText(pageAt(address))) }
            assertEquals(listOf(START, address), history.entries)
        }

    /** The picker pressed asks once and opens with the scopes; the search Enter runs is in the one chosen. */
    @Test
    fun `the picker pressed offers the server's scopes`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val history = FakeHistory(START)
            storefront(history) { path -> answer(path) }
            waitUntil(timeoutMillis = 5_000) { exists(hasText("Sign in to HAUL")) }

            press(hasTestTag(SCOPE_PICKER_TAG) and hasClickAction())
            waitUntil(timeoutMillis = 5_000) { exists(hasText(CATEGORIES.last().first) and inPicker()) }
            assertEquals(listOf(HEADER_TREE), requests.toList())
            CATEGORIES.forEach { (label, _) ->
                assertTrue(exists(hasText(label) and inPicker()), "the picker did not offer $label")
            }

            val (label, address) = CATEGORIES[5]
            press(hasText(label) and inPicker())
            waitUntil(timeoutMillis = 5_000) { exists(hasTestTag(SCOPE_PICKER_TAG) and hasText(label)) }
            onNodeWithTag(SEARCH_FIELD_TAG).performClick()
            onNodeWithTag(SEARCH_FIELD_TAG).performTextInput("tv")
            onNodeWithTag(SEARCH_FIELD_TAG).performKeyInput { pressKey(Key.Enter) }
            val search = "/search?q=tv&category=${address.removePrefix("/c/")}"
            waitUntil(timeoutMillis = 5_000) { exists(hasText(pageAt(search))) }
            assertEquals(listOf(START, search), history.entries)
            assertEquals(1, requests.count { it == HEADER_TREE })
        }

    /** Pressed again and again while the header is on its way, and after: one request. */
    @Test
    fun `one header request per page however often they are pressed`() =
        runDesktopComposeUiTest(PHONE, HEIGHT) {
            val answered = CompletableDeferred<Unit>()
            storefront(FakeHistory(START), compact = true) { path ->
                if (path == HEADER_TREE) answered.await()
                answer(path)
            }
            waitUntil(timeoutMillis = 5_000) { exists(hasText("Sign in to HAUL")) }

            repeat(3) {
                openMenu()
                closeMenu()
            }
            answered.complete(Unit)
            openMenu()
            waitUntil(timeoutMillis = 5_000) { exists(hasText(CATEGORIES.last().first) and inMenu()) }
            closeMenu()
            openMenu()
            waitForIdle()
            assertEquals(listOf(HEADER_TREE), requests.toList())
        }

    /** A header that does not arrive leaves the client's own, and the page does not ask again. */
    @Test
    fun `a header that does not arrive is not asked for again on the page`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            storefront(FakeHistory(START)) { path ->
                if (path == HEADER_TREE) HaulResponse(500, """{"code":"internal","message":"boom"}""") else answer(path)
            }
            waitUntil(timeoutMillis = 5_000) { exists(hasText("Sign in to HAUL")) }

            repeat(3) { press(hasTestTag(SCOPE_PICKER_TAG) and hasClickAction()) }
            waitForIdle()
            assertEquals(listOf(HEADER_TREE), requests.toList())
            assertTrue(!exists(hasTestTag(LINK_MENU_TAG)), "the picker opened with no scopes")
        }

    private fun ComposeUiTest.openMenu() {
        onNodeWithContentDescription(OPEN_MENU).performClick()
        waitUntil(timeoutMillis = 5_000) { exists(hasTestTag(HEADER_MENU_TAG)) }
    }

    private fun ComposeUiTest.closeMenu() {
        onNode(hasContentDescription(CLOSE_MENU) and hasClickAction()).performClick()
        waitUntil(timeoutMillis = 5_000) { !exists(hasTestTag(HEADER_MENU_TAG)) }
    }

    private fun ComposeUiTest.press(matcher: SemanticsMatcher) {
        waitUntil(timeoutMillis = 5_000) { exists(matcher) }
        onAllNodes(matcher)[0].performClick()
    }

    private fun ComposeUiTest.exists(matcher: SemanticsMatcher): Boolean =
        onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun inMenu(): SemanticsMatcher = hasAnyAncestor(hasTestTag(HEADER_MENU_TAG))

    private fun inPicker(): SemanticsMatcher = hasAnyAncestor(hasTestTag(LINK_MENU_TAG))

    private fun ComposeUiTest.storefront(
        history: FakeHistory,
        compact: Boolean = false,
        answer: suspend (path: String) -> HaulResponse,
    ) {
        val transport =
            HaulTransport { path ->
                requests += path
                answer(path)
            }
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = compact) {
                Storefront(transport, history, signIn = {}, clock = FixedClock)
            }
        }
    }

    private object FixedClock : Clock {
        override fun now(): Instant = CANVAS_NOW
    }

    private companion object {
        const val WIDTH = 1440
        const val PHONE = 390
        const val HEIGHT = 1400
        const val START = "/sign-in?next=%2Fdeals"

        /** The server's 32 top-level categories, none of them one of the client's ten words. */
        val CATEGORIES = (1..32).map { "Aisle $it" to "/c/aisle-$it" }

        /** The server's header for a guest: the 32 in the menu, the row's ten, and the 32 as the picker's scopes. */
        val GUEST =
            HaulHeader(
                id = "header",
                deliverTo = "Brooklyn, NY 11211",
                deliveryPromise = "Free delivery over $35",
                cartCount = 0,
                searchPlaceholder = "Search products",
                categories = CATEGORIES.take(10).map { it.first },
                account = NavigateAction("/sign-in"),
                catalog = CATEGORIES.map { (label, address) -> Link(label, NavigateAction(address)) },
                deals = NavigateAction("/deals"),
                cart = NavigateAction("/cart"),
                orders = NavigateAction("/sign-in?next=%2Faccount%2Forders"),
                saved = NavigateAction("/sign-in?next=%2Faccount%2Fsaved"),
                plus = NavigateAction("/sign-in"),
                scopes = CATEGORIES.map { (label, address) -> SearchScope(label, address.removePrefix("/c/")) },
            )

        fun pageAt(address: String) = "The page at $address"

        fun tree(component: KompotComponent) =
            HaulResponse(200, haulWireJson.encodeToString(PolymorphicSerializer(KompotComponent::class), component))

        /** The header alone, or any page a control leads to: a tree saying where it is. */
        fun answer(path: String): HaulResponse =
            if (path == HEADER_TREE) {
                tree(GUEST)
            } else {
                val address = if (path == "/ui/home") "/" else path.removePrefix("/ui")
                tree(
                    ColumnComponent(id = "page", children = listOf(TextComponent(id = "body", text = pageAt(address)))),
                )
            }
    }
}
