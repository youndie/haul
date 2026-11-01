package io.github.youndie.haul.shell

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.feature.cart.CartCommand
import io.github.youndie.haul.feature.cart.CartCommands
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.cart.LineCommand
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.CARD_ADD_TAG
import io.github.youndie.haul.ui.DEMO_NOTE_TAG
import io.github.youndie.haul.ui.DemoNote
import io.github.youndie.haul.ui.HOOD_TOGGLE_TAG
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.LinkCopier
import io.github.youndie.haul.ui.LocalLinkCopier
import io.github.youndie.haul.ui.LocalMotion
import io.github.youndie.haul.ui.Motion
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.commands.LoadAction
import io.github.youndie.kompot.commands.kompotUpdate
import io.github.youndie.kompot.standard.ButtonComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.OpenUrlAction
import io.github.youndie.kompot.standard.TextComponent
import kotlinx.serialization.PolymorphicSerializer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.github.youndie.haul.ui.Link as HaulLink

/**
 * B-163: the demo strip's «Under the hood» opens a panel over the page — the screen's tree as an outline, the request
 * that brought it with the server's time, and, while it is open, each partial update the page takes, listed with its
 * request and flashed on the nodes it replaced. Closed, it costs the page nothing: no update listed, nothing flashed,
 * and the page pixel for pixel what it was. The storefront is assembled as `App` assembles it, over a fake network.
 */
@OptIn(ExperimentalTestApi::class)
class UnderTheHoodTest {
    private val requests = CopyOnWriteArrayList<String>()
    private val answers = mutableMapOf<String, HaulResponse>()
    private val sent = CopyOnWriteArrayList<CartCommand>()
    private val opened = CopyOnWriteArrayList<String>()

    private val transport =
        HaulTransport { path ->
            requests += path
            answers[path] ?: error("nothing answers $path")
        }

    private val cartCommands =
        CartCommands { command ->
            sent += command
            kompotUpdate {
                addComponent(header.copy(cartCount = 3))
                addComponent(card.copy(title = ADDED))
            }
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

    private fun ComposeUiTest.storefront(
        hood: UnderTheHood,
        motion: Motion = Motion.Lively,
    ) = setContent {
        HaulTheme(FixtureFonts.fonts, compact = false) {
            CompositionLocalProvider(LocalLinkCopier provides links, LocalMotion provides motion) {
                // As `App` wires it: the requests through the hood's transport, the cart's answers told to it.
                val heard = remember { hood.recording(transport) }
                val cart = remember { hood.watching(cartCommands) }
                DemoStripped(heard, MemoryDemoNoteMemory(), hood) {
                    Storefront(heard, FakeHistory(), signIn = {}, cartCommands = cart)
                }
            }
        }
    }

    /** The home page with a count, a button that loads its parts, and a card with «+»; the strip; the parts. */
    private fun store() {
        answers["/ui/home"] = ok(home, timing = "app;dur=12.3")
        answers[DEMO_NOTE_TREE] = ok(note)
        answers[PARTS] =
            ok(kompotUpdate { addComponent(TextComponent(id = "count", text = COUNTED)) }, timing = "app;dur=4.5")
    }

    private fun ComposeUiTest.exists(text: String) = onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.tagged(tag: String) = onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.ready() {
        waitUntil(timeoutMillis = 5_000) { tagged(HOOD_TOGGLE_TAG) }
        waitForIdle()
    }

    private fun ComposeUiTest.toggle() {
        onNodeWithTag(HOOD_TOGGLE_TAG).performClick()
        waitForIdle()
    }

    @Test
    fun `the toggle in the strip opens the panel and the toggle or its close closes it`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            store()
            val hood = UnderTheHood()
            storefront(hood)
            ready()
            onNodeWithTag(HOOD_PANEL_TAG).assertDoesNotExist()
            toggle()
            onNodeWithTag(HOOD_PANEL_TAG).assertExists()
            assertTrue(hood.open)
            onNodeWithTag(HOOD_CLOSE_TAG).performClick()
            waitForIdle()
            onNodeWithTag(HOOD_PANEL_TAG).assertDoesNotExist()
            toggle()
            onNodeWithTag(HOOD_PANEL_TAG).assertExists()
            toggle()
            onNodeWithTag(HOOD_PANEL_TAG).assertDoesNotExist()
            assertTrue(!hood.open)
        }

    /** The request is the one the transport made for the page, its time the server's header; the tree its outline. */
    @Test
    fun `the panel shows the screen's tree and the request that brought it`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            store()
            storefront(UnderTheHood())
            ready()
            toggle()
            onNodeWithText("GET /ui/home").assertExists()
            val size = home.encoded().encodeToByteArray().size
            onNode(hasText("200 · ${size.said()} · server 12.3 ms · total ", substring = true)).assertExists()
            // The root open, its children listed; a node opens to its fields.
            onNodeWithText("column").assertExists()
            onNodeWithText("haul_header").assertExists()
            onNodeWithText(" #count").assertExists()
            assertTrue(!exists("text: \"$COUNT\""), "a closed node shows its fields")
            onNode(hasText(" #count")).performClick()
            waitForIdle()
            onNodeWithText("text: ").assertExists()
            onNodeWithText("\"$COUNT\"").assertExists()
            // A Haul type links to where it is drawn and built.
            onAllNodes(hasText(VIEW))[0].performClick()
            waitForIdle()
            assertEquals(listOf(HoodSources.url(HoodSources.of("haul_header")?.view!!)), opened.toList())
        }

    /**
     * The acceptance: with the panel open, a `load` and a card's «+» each replace their parts, which flash on the page
     * and are listed — the request, and the nodes replaced; then the flash fades.
     */
    @Test
    fun `an update while the panel is open is listed and the nodes it replaced flash`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            store()
            val hood = UnderTheHood(Motion.Lively)
            storefront(hood)
            ready()
            toggle()
            onNodeWithText(HOOD_NO_UPDATES).assertExists()

            mainClock.autoAdvance = false
            onNodeWithText(LOAD).performClick()
            settle { exists(COUNTED) }
            val load = hood.updates.single()
            assertEquals("GET", load.method)
            assertEquals(PARTS, load.url)
            assertEquals(listOf("count"), load.replaced)
            assertEquals(4.5, load.request?.serverMillis)
            assertEquals(1f, hood.glow("count"), "the node replaced does not flash")
            assertTrue(flashedAt(COUNTED), "no outline is drawn around the node replaced")
            onNodeWithText("GET $PARTS").assertExists()
            mainClock.advanceTimeBy((FLASH_HOLD_MS + FLASH_FADE_MS + 200).toLong())
            waitForIdle()
            assertEquals(0f, hood.glow("count"), "the flash did not fade")
            assertTrue(!flashedAt(COUNTED), "the outline stayed")

            onNodeWithTag(CARD_ADD_TAG).performClick()
            settle { exists(ADDED) }
            val add = hood.updates.first()
            assertEquals("PUT", add.method)
            assertEquals(LINE, add.url)
            assertEquals(listOf("header", "card"), add.replaced)
            assertTrue(hood.glow("header") > 0f && hood.glow("card") > 0f, "the badge and the card do not flash")
            assertEquals(2, hood.updates.size)
            onNodeWithText("PUT $LINE").assertExists()
            // The outline is the page as it is now: the card's new title in it.
            assertTrue(
                hood.screen
                    ?.outline()
                    ?.fields("card")
                    ?.any { ADDED in it.value } == true,
            )
            mainClock.autoAdvance = true
        }

    /** Under [Motion.Still] nothing moves, the flash included; the update is listed all the same. */
    @Test
    fun `on a still page an update is listed and nothing flashes`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            store()
            val hood = UnderTheHood(Motion.Still)
            storefront(hood, Motion.Still)
            ready()
            toggle()
            mainClock.autoAdvance = false
            onNodeWithText(LOAD).performClick()
            settle { exists(COUNTED) }
            assertEquals(listOf("count"), hood.updates.single().replaced)
            assertEquals(0f, hood.glow("count"))
            assertTrue(!flashedAt(COUNTED), "a still page flashed")
            mainClock.autoAdvance = true
        }

    /** Closed, the panel lists nothing and draws nothing; the update still reaches the page. */
    @Test
    fun `closed nothing is listed and nothing flashes`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            store()
            val hood = UnderTheHood(Motion.Lively)
            storefront(hood)
            ready()
            mainClock.autoAdvance = false
            onNodeWithText(LOAD).performClick()
            settle { exists(COUNTED) }
            onNodeWithTag(CARD_ADD_TAG).performClick()
            settle { exists(ADDED) }
            assertTrue(hood.updates.isEmpty(), "a closed panel listed updates")
            assertTrue(hood.flashes.isEmpty() && hood.flashing == null, "a closed panel flashed")
            assertTrue(!flashedAt(COUNTED))
            onNodeWithTag(HOOD_PANEL_TAG).assertDoesNotExist()
            mainClock.autoAdvance = true
            // Opened now, the outline is the page as drawn — what the updates replaced included — with nothing listed.
            toggle()
            onNodeWithText(HOOD_NO_UPDATES).assertExists()
            assertTrue(
                hood.screen
                    ?.outline()
                    ?.fields("card")
                    ?.any { ADDED in it.value } == true,
            )
        }

    @Test
    fun `closing the panel leaves the page pixel for pixel as it was`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            store()
            // Still, so the pointer the presses leave over the page moves nothing under it.
            storefront(UnderTheHood(Motion.Still), Motion.Still)
            ready()
            val before = onRoot().captureToImage().toPixelMap().buffer
            toggle()
            onNodeWithTag(HOOD_PANEL_TAG).assertExists()
            onNode(hasText(" #count")).performClick()
            waitForIdle()
            toggle()
            assertContentEquals(before, onRoot().captureToImage().toPixelMap().buffer, "the page moved")
        }

    /** The goldens and the shell's own tests: a strip without a hood has no toggle, and a page drawn alone no strip. */
    @Test
    fun `a storefront drawn without its root's hood has no toggle`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            store()
            setContent {
                HaulTheme(FixtureFonts.fonts, compact = false) {
                    DemoStripped(
                        transport,
                        MemoryDemoNoteMemory(),
                    ) { Storefront(transport, FakeHistory(), signIn = {}) }
                }
            }
            waitUntil(timeoutMillis = 5_000) { tagged(DEMO_NOTE_TAG) }
            waitForIdle()
            onNodeWithTag(HOOD_TOGGLE_TAG).assertDoesNotExist()
        }

    /** Frames until [done], the clock held: a flash is looked at before it has faded. */
    private fun ComposeUiTest.settle(done: () -> Boolean) {
        repeat(SETTLE_FRAMES) {
            if (done()) return
            mainClock.advanceTimeByFrame()
            waitForIdle()
        }
        assertTrue(done(), "the page did not take the update")
    }

    /** Whether the left edge of the node showing [text] is drawn in the flash's blue. */
    private fun ComposeUiTest.flashedAt(text: String): Boolean {
        val bounds = onNodeWithText(text).fetchSemanticsNode().boundsInRoot
        val pixels = onRoot().captureToImage().toPixelMap()
        val pixel = pixels[(bounds.left + 1).toInt(), bounds.center.y.toInt()]
        return pixel.blue > BLUE && pixel.red < RED
    }

    private fun HoodNode.fields(id: String): List<HoodField>? =
        if (this.id == id) fields else children.firstNotNullOfOrNull { it.fields(id) }

    private companion object {
        const val WIDTH = 1440
        const val HEIGHT = 900
        const val SETTLE_FRAMES = 40
        const val BLUE = 0.8f
        const val RED = 0.5f
        const val PARTS = "/ui/parts/home?counted=1"
        const val LINE = "/api/v1/cart/lines/p-0-0?answer=card"
        const val COUNT = "Counted 0 times"
        const val COUNTED = "Counted 1 time"
        const val LOAD = "Count"
        const val ADDED = "Added mug"

        val header =
            HaulHeader(
                id = "header",
                deliverTo = "Brooklyn, NY 11211",
                deliveryPromise = "Free delivery over $35",
                cartCount = 0,
                searchPlaceholder = "Search products",
                categories = listOf("Electronics"),
            )

        val card =
            ProductCard(
                id = "card",
                productId = "p-0",
                title = "A mug",
                price = "$24",
                rating = "4.8",
                reviews = "420",
                delivery = "Tomorrow",
                tone = "#E3F5D8",
                action = NavigateAction("/p/p-0"),
                add = LineCommand(LINE, LineChange(quantity = 1)),
            )

        val home: KompotComponent =
            ColumnComponent(
                id = "page",
                children =
                    listOf(
                        header,
                        TextComponent(id = "count", text = COUNT),
                        ButtonComponent(id = "load", text = LOAD, action = LoadAction(PARTS)),
                        ProductGrid("grid", listOf(card), columns = 4),
                    ),
            )

        val note =
            DemoNote(
                id = "demo-note",
                text = "A demo store built with Kotlin.",
                links = listOf(HaulLink("Source on GitHub", OpenUrlAction("https://github.com/youndie/haul"))),
                close = "Close the demo note",
                hood = "Under the hood",
            )

        fun KompotComponent.encoded(): String =
            haulWireJson.encodeToString(PolymorphicSerializer(KompotComponent::class), this)

        fun ok(
            tree: KompotComponent,
            timing: String? = null,
        ) = HaulResponse(200, tree.encoded(), timing)

        fun ok(
            action: KompotAction,
            timing: String? = null,
        ) = HaulResponse(200, haulWireJson.encodeToString(PolymorphicSerializer(KompotAction::class), action), timing)
    }
}
