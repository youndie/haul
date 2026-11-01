package io.github.youndie.haul.shell

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.Body
import io.github.youndie.haul.CANVAS_NOW
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.Fixture
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.Page
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.AboutBody
import io.github.youndie.haul.ui.DEMO_NOTE_CLOSE_TAG
import io.github.youndie.haul.ui.DEMO_NOTE_TAG
import io.github.youndie.haul.ui.DemoNote
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.LinkCopier
import io.github.youndie.haul.ui.LocalLinkCopier
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.ButtonComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.OpenUrlAction
import io.github.youndie.kompot.standard.TextComponent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.PolymorphicSerializer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import io.github.youndie.haul.ui.Link as HaulLink

/**
 * B-160: every page of the storefront says, in one quiet line above it, that the stand is a demo built with Kotlin and
 * where its code is, until the visitor closes it. The line is a tree of its own the root asks for once the first page
 * has drawn ([DemoStripped]); closed, it stays closed for the session; a view drawn on its own has none.
 */
@OptIn(ExperimentalTestApi::class)
class DemoStripTest {
    private val requests = CopyOnWriteArrayList<String>()
    private val answers = mutableMapOf<String, ArrayDeque<suspend () -> HaulResponse>>()
    private val opened = CopyOnWriteArrayList<String>()
    private val memory = MemoryDemoNoteMemory()

    private fun answer(
        path: String,
        vararg responses: suspend () -> HaulResponse,
    ) {
        answers[path] = ArrayDeque(responses.toList())
    }

    private val transport =
        HaulTransport { path ->
            if (path != HEADER_TREE) requests += path
            val queue = answers[path] ?: error("nothing answers $path")
            val next = if (queue.size > 1) queue.removeFirst() else queue.first()
            next()
        }

    private val links =
        object : LinkCopier {
            override fun copy(
                path: String,
                done: (Boolean) -> Unit,
            ) = done(true)

            override fun openUrl(url: String) {
                opened += url
            }
        }

    private val clock =
        object : Clock {
            override fun now(): Instant = CANVAS_NOW
        }

    private fun ComposeUiTest.storefront(
        history: FakeHistory = FakeHistory(),
        demo: Boolean = true,
    ) = setContent {
        HaulTheme(FixtureFonts.fonts, compact = false) {
            CompositionLocalProvider(LocalLinkCopier provides links) {
                if (demo) {
                    DemoStripped(transport, memory) { Storefront(transport, history, signIn = {}, clock = clock) }
                } else {
                    Storefront(transport, history, signIn = {}, clock = clock)
                }
            }
        }
    }

    @Test
    fun `the strip is asked for once the first page has drawn and says the stand is a Kotlin demo`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val home = CompletableDeferred<HaulResponse>()
            answer("/ui/home", { home.await() })
            answer(DEMO_NOTE_TREE, ok(note))
            storefront()
            waitForIdle()
            assertEquals(listOf("/ui/home"), requests.toList(), "the strip went before the first page")
            onNodeWithTag(DEMO_NOTE_TAG).assertDoesNotExist()
            home.complete(ok(page(TextComponent(id = "home", text = HOME)))())
            waitUntil(timeoutMillis = 5_000) { exists(NOTE_TEXT) }
            assertEquals(listOf("/ui/home", DEMO_NOTE_TREE), requests.toList())
            onNodeWithText(HOME).assertExists()
            onNodeWithText(ABOUT).assertExists()
            onNodeWithText(SOURCE).assertExists()
        }

    @Test
    fun `about this demo opens the about page and source on github opens the repository apart`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val history = FakeHistory()
            answer("/ui/home", ok(page(TextComponent(id = "home", text = HOME))))
            answer("/ui/about", ok(page(TextComponent(id = "about", text = ABOUT_PAGE))))
            answer(DEMO_NOTE_TREE, ok(note))
            storefront(history)
            waitUntil(timeoutMillis = 5_000) { exists(NOTE_TEXT) }
            onNodeWithText(ABOUT).performClick()
            waitUntil(timeoutMillis = 5_000) { exists(ABOUT_PAGE) }
            assertEquals("/about", history.location)
            // Still above the page it led to, and asked for once.
            onNodeWithText(NOTE_TEXT).assertExists()
            onNodeWithText(SOURCE).performClick()
            waitForIdle()
            assertEquals(listOf(REPOSITORY), opened.toList(), "the repository did not open apart")
            assertEquals("/about", history.location, "the store left its page for the repository")
            assertEquals(1, requests.count { it == DEMO_NOTE_TREE })
        }

    @Test
    fun `the close hides the strip on every page for the rest of the session`() {
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            answer(
                "/ui/home",
                ok(page(ButtonComponent(id = "next", text = NEXT, action = NavigateAction("/deals")))),
            )
            answer("/ui/deals", ok(page(TextComponent(id = "deals", text = DEALS))))
            answer(DEMO_NOTE_TREE, ok(note))
            storefront()
            waitUntil(timeoutMillis = 5_000) { exists(NOTE_TEXT) }
            onNodeWithTag(DEMO_NOTE_CLOSE_TAG).performClick()
            waitForIdle()
            onNodeWithTag(DEMO_NOTE_TAG).assertDoesNotExist()
            assertTrue(memory.closed, "the close was not remembered")
            onNodeWithText(NEXT).performClick()
            waitUntil(timeoutMillis = 5_000) { exists(DEALS) }
            onNodeWithTag(DEMO_NOTE_TAG).assertDoesNotExist()
            assertEquals(1, requests.count { it == DEMO_NOTE_TREE }, "a closed strip was asked for again")
        }
        // The next load of the session — a reload, a new storefront over the same memory — asks for none.
        requests.clear()
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            answer("/ui/home", ok(page(TextComponent(id = "home", text = HOME))))
            storefront()
            waitUntil(timeoutMillis = 5_000) { exists(HOME) }
            waitForIdle()
            onNodeWithTag(DEMO_NOTE_TAG).assertDoesNotExist()
            assertEquals(listOf("/ui/home"), requests.toList())
        }
    }

    @Test
    fun `a strip that did not arrive is asked for again after the next page`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            answer(
                "/ui/home",
                ok(page(ButtonComponent(id = "next", text = NEXT, action = NavigateAction("/deals")))),
            )
            answer("/ui/deals", ok(page(TextComponent(id = "deals", text = DEALS))))
            answer(DEMO_NOTE_TREE, { HaulResponse(503, "") }, ok(note))
            storefront()
            waitUntil(timeoutMillis = 5_000) { requests.contains(DEMO_NOTE_TREE) }
            waitForIdle()
            onNodeWithTag(DEMO_NOTE_TAG).assertDoesNotExist()
            onNodeWithText(NEXT).performClick()
            waitUntil(timeoutMillis = 5_000) { exists(NOTE_TEXT) }
            onNodeWithText(DEALS).assertExists()
        }

    /** The About page's buttons lead off the store, each in a tab of its own; the page stays where it was. */
    @Test
    fun `the about page's links open the repository and its readme apart`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val history = FakeHistory("/about")
            answer(
                "/ui/about",
                ok(
                    page(
                        AboutBody(
                            id = "about",
                            eyebrow = ABOUT,
                            title = ABOUT_PAGE,
                            lead = "A store that sells nothing.",
                            sections = emptyList(),
                            links =
                                listOf(
                                    HaulLink(SOURCE, OpenUrlAction(REPOSITORY)),
                                    HaulLink(README, OpenUrlAction("$REPOSITORY#readme")),
                                ),
                        ),
                    ),
                ),
            )
            storefront(history, demo = false)
            waitUntil(timeoutMillis = 5_000) { exists(README) }
            onNodeWithText(SOURCE).performClick()
            onNodeWithText(README).performClick()
            waitForIdle()
            assertEquals(listOf(REPOSITORY, "$REPOSITORY#readme"), opened.toList())
            assertEquals("/about", history.location)
        }

    /** The goldens and the shell's own tests draw no strip: only the storefront's root has one. */
    @Test
    fun `a page drawn outside the storefront's root has no strip and asks for none`() {
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            setContent { Fixture(compact = false) { Page { Body("home_guest.json") } } }
            waitForIdle()
            onNodeWithTag(DEMO_NOTE_TAG).assertDoesNotExist()
        }
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            answer("/ui/home", ok(page(TextComponent(id = "home", text = HOME))))
            storefront(demo = false)
            waitUntil(timeoutMillis = 5_000) { exists(HOME) }
            waitForIdle()
            onNodeWithTag(DEMO_NOTE_TAG).assertDoesNotExist()
            assertEquals(listOf("/ui/home"), requests.toList())
        }
    }

    private fun ComposeUiTest.exists(text: String): Boolean =
        onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val WIDTH = 1440
        const val HEIGHT = 1000
        const val HOME = "The home page"
        const val DEALS = "Today’s deals"
        const val NEXT = "Next page"
        const val ABOUT_PAGE = "What this demo is"
        const val NOTE_TEXT = "A demo store built with Kotlin: Ktor, kompot, Compose Multiplatform in the browser."
        const val ABOUT = "About this demo"
        const val SOURCE = "Source on GitHub"
        const val README = "Read the README"
        const val REPOSITORY = "https://github.com/youndie/haul"

        val note =
            DemoNote(
                id = "demo-note",
                text = NOTE_TEXT,
                links =
                    listOf(
                        HaulLink(ABOUT, NavigateAction("/about")),
                        HaulLink(SOURCE, OpenUrlAction(REPOSITORY)),
                    ),
                close = "Close the demo note",
            )

        val header =
            HaulHeader(
                id = "header",
                deliverTo = "Brooklyn, NY 11211",
                deliveryPromise = "Free delivery over $35",
                cartCount = 0,
                searchPlaceholder = "Search products",
                categories = listOf("Electronics"),
            )

        fun page(vararg children: KompotComponent): KompotComponent =
            ColumnComponent(id = "page", children = listOf(header) + children)

        fun ok(tree: KompotComponent): suspend () -> HaulResponse =
            { HaulResponse(200, haulWireJson.encodeToString(PolymorphicSerializer(KompotComponent::class), tree)) }
    }
}
