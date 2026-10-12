package io.github.youndie.haul.feature.search

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.feature.identity.SessionControls
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.shell.HaulCommands
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.SUGGEST_DELAY_MS
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.SEARCH_FIELD_TAG
import io.github.youndie.haul.ui.SearchScope
import io.github.youndie.haul.ui.SearchSuggestPanel
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.TextComponent
import kotlinx.serialization.PolymorphicSerializer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-124: the empty search field pressed. A customer is offered their recent searches at once — the panel asked
 * for with no query, in the picker's scope — and a row runs its search; a guest's empty field asks nothing. Found
 * by the v0.4.0 run (TC-SRCH-10): the press drew a caret and no panel, and the recent searches showed only once
 * two letters were typed.
 */
@OptIn(ExperimentalTestApi::class)
class EmptyFieldRecallTest {
    private val requests = CopyOnWriteArrayList<String>()
    private val sent = CopyOnWriteArrayList<String>()
    private val answers = mutableMapOf<String, ArrayDeque<KompotComponent>>()
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
        HaulCommands { method, path, _ ->
            sent += "$method $path"
            HaulResponse(200, """{"type":"refresh"}""")
        }

    private fun ComposeUiTest.storefront(session: SessionControls?) =
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                Storefront(transport, history, signIn = {}, commands = commands, session = session)
            }
        }

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.top(text: String): Float =
        onNodeWithText(text)
            .fetchSemanticsNode()
            .boundsInRoot.top

    @Test
    fun `a customer pressing the empty field sees their recent searches and a row runs its search in the scope`() =
        runDesktopComposeUiTest(WIDTH, 1_000) {
            answer("/ui/home", page(header))
            answer(RECALL_IN_SCOPE, recall(RECENT_URL, "mug", "duvet", "sony"))
            answer("/ui$DUVET", page(header, TextComponent(id = "next", text = NEXT)))
            storefront(Customer)
            onNodeWithTag(SEARCH_FIELD_TAG).performClick()
            waitForText("duvet")
            assertEquals(listOf("/ui/home", RECALL_IN_SCOPE), requests, "the press did not ask for the panel")
            assertTrue(top("mug") < top("duvet") && top("duvet") < top("sony"), "not in the server's order")
            onNodeWithText("CLEAR").assertExists()
            onNodeWithText("TOP PRODUCTS").assertDoesNotExist()

            onNodeWithText("duvet").performClick()
            onNodeWithText(NEXT).assertExists()
            assertEquals(listOf("/", DUVET), history.entries)
            assertEquals(emptyList(), sent, "a recent search's row sent a command")
        }

    @Test
    fun `a guest's empty field asks for nothing`() =
        listOf(Guest, null).forEach { session ->
            requests.clear()
            runDesktopComposeUiTest(WIDTH, 1_000) {
                answer("/ui/home", page(header))
                storefront(session)
                mainClock.autoAdvance = false
                onNodeWithTag(SEARCH_FIELD_TAG).performClick()
                mainClock.advanceTimeBy(SUGGEST_DELAY_MS * 4)
                mainClock.autoAdvance = true
                waitForIdle()
                assertEquals(listOf("/ui/home"), requests, "a guest's empty field asked for a panel")
                onNodeWithTag(SUGGEST_PANEL_TAG).assertDoesNotExist()
            }
        }

    @Test
    fun `clear empties the empty field's panel and it closes`() =
        runDesktopComposeUiTest(WIDTH, 1_000) {
            answer("/ui/home", page(header.copy(scope = null)))
            answer(RECALL, recall(RECENT_URL, "mug"), recall(null))
            storefront(Customer)
            onNodeWithTag(SEARCH_FIELD_TAG).performClick()
            waitForText("mug")
            onNodeWithText("CLEAR").performClick()
            // Nothing recent left: no panel at all, rather than an empty card under the field.
            waitUntil(
                timeoutMillis = 5_000,
            ) { onAllNodes(hasTestTag(SUGGEST_PANEL_TAG)).fetchSemanticsNodes().isEmpty() }
            assertEquals(listOf("DELETE $RECENT_URL"), sent)
            assertEquals(listOf("/ui/home", RECALL, RECALL), requests)
        }

    @Test
    fun `typing leaves the recall for suggestions and emptying the field recalls again`() =
        runDesktopComposeUiTest(WIDTH, 1_000) {
            answer("/ui/home", page(header.copy(scope = null)))
            answer(RECALL, recall(RECENT_URL, "mug"))
            answer("/ui/search/suggest?q=du", typed("du"))
            storefront(Customer)
            val field = onNodeWithTag(SEARCH_FIELD_TAG)
            field.performClick()
            waitForText("mug")
            field.performTextInput("du")
            waitForText("All 2 results")
            onNodeWithText("mug").assertDoesNotExist()
            field.performTextClearance()
            waitForText("mug")
            assertEquals(listOf("/ui/home", RECALL, "/ui/search/suggest?q=du", RECALL), requests)
        }

    private abstract class FakeSession(
        override val signedIn: Boolean,
    ) : SessionControls {
        override fun signOut() = Unit

        override val canSignInHere: Boolean = false

        override suspend fun signInHere(next: String?) = Unit

        override val returnedFromSignIn: Boolean = false

        override fun forgetUnansweredSignIn(): Boolean = false

        override suspend fun finishSignInHere(): String? = null
    }

    private object Customer : FakeSession(signedIn = true)

    private object Guest : FakeSession(signedIn = false)

    private companion object {
        const val WIDTH = 1440
        const val NEXT = "The next page"
        const val RECENT_URL = "/api/v1/me/recent-searches"
        const val RECALL = "/ui/search/suggest?q="
        const val RECALL_IN_SCOPE = "/ui/search/suggest?q=&category=home-kitchen"
        const val DUVET = "/search?q=duvet&category=home-kitchen"

        /** A customer's header, searching in Home & Kitchen (B-72). */
        val header =
            HaulHeader(
                id = "header",
                deliverTo = "Brooklyn, NY 11211",
                deliveryPromise = "Free delivery over $35",
                customerName = "Maya",
                cartCount = 0,
                searchPlaceholder = "Search products",
                categories = listOf("Electronics"),
                scopes =
                    listOf(
                        SearchScope("Electronics", "electronics"),
                        SearchScope("Home & Kitchen", "home-kitchen"),
                    ),
                scope = "home-kitchen",
            )

        /** The empty field's panel as the server draws it: the recent searches alone, each in the scope. */
        fun recall(
            clearUrl: String?,
            vararg queries: String,
        ) = SearchSuggestPanel(
            id = "suggest",
            query = "",
            suggestions = emptyList(),
            categories = emptyList(),
            products = emptyList(),
            allResultsLabel = "",
            recent = queries.map { Link(it, NavigateAction("/search?q=$it&category=home-kitchen")) },
            clearUrl = clearUrl,
        )

        /** The panel for [query] typed, with nothing recent in it. */
        fun typed(query: String) =
            SearchSuggestPanel(
                id = "suggest",
                query = query,
                suggestions = emptyList(),
                categories = emptyList(),
                products = emptyList(),
                allResultsLabel = "All 2 results",
                allResultsAction = NavigateAction("/search?q=$query"),
            )

        fun page(vararg children: KompotComponent): KompotComponent =
            ColumnComponent(id = "page", children = children.toList())
    }
}
