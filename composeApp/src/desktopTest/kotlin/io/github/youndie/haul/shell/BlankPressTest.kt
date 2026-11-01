package io.github.youndie.haul.shell

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.read
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.COPY
import io.github.youndie.haul.ui.LINK_MENU_TAG
import io.github.youndie.haul.ui.LinkCopier
import io.github.youndie.haul.ui.LocalLinkCopier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-179: a press on the page where no words are drawn leaves the page where it is. The page's selection (B-154)
 * starts at any press and brings the point it was dragged to into view, next to the nearest text; the nearest one
 * under «Every order.» was a line's strut (`InlineLine`), a space drawn zero pixels wide, and a request to show a
 * point next to a text of no width reached the page's scroll as a point above its top — the page scrolled after it
 * all the way up. The home tree is the server's (`bodies/home_guest.json`), its header too.
 */
@OptIn(ExperimentalTestApi::class)
class BlankPressTest {
    private val copied = mutableListOf<String>()

    private val links =
        object : LinkCopier {
            override fun copy(
                path: String,
                done: (Boolean) -> Unit,
            ) = done(true)

            override fun openApart(path: String) = Unit

            override fun copyText(
                text: String,
                done: (Boolean) -> Unit,
            ) = done(copied.add(text))

            override fun pasteText(done: (String?) -> Unit) = done(null)
        }

    private fun ComposeUiTest.home() {
        val home = read("home_guest.json")
        val header = read("haul_header_guest.json")
        val transport = HaulTransport { path -> HaulResponse(200, if (path == HEADER_TREE) header else home) }
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                CompositionLocalProvider(LocalLinkCopier provides links) {
                    Storefront(transport, FakeHistory("/"), signIn = {})
                }
            }
        }
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(
                hasText(EVERY_ORDER, substring = true),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty()
        }
        waitForIdle()
    }

    private fun ComposeUiTest.scrolled(): Float =
        onNodeWithTag(PAGE_SCROLL_TAG).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    /** The page scrolled to its foot, and a double press's time later, so the next press counts as a first. */
    private fun ComposeUiTest.toTheFoot(): Float {
        onNodeWithTag(PAGE_SCROLL_TAG).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        return scrolled()
    }

    /** Where words or a control are drawn in the window: a press there is not a press on blank space. */
    private fun ComposeUiTest.taken(): List<Rect> =
        onAllNodes(hasText("", substring = true) or hasClickAction(), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }

    private fun hasClick() =
        androidx.compose.ui.test
            .hasClickAction()

    private fun ComposeUiTest.bounds(text: String): Rect =
        onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot

    /** Whether the store's menu over [at] offers Copy — whether any words are selected. Closes the menu. */
    private fun ComposeUiTest.offersCopy(at: Offset): Boolean {
        onRoot().performMouseInput { rightClick(at) }
        waitForIdle()
        val offers = onAllNodes(hasText(COPY)).fetchSemanticsNodes().isNotEmpty()
        assertTrue(
            onAllNodes(
                androidx.compose.ui.test
                    .hasTestTag(LINK_MENU_TAG),
            ).fetchSemanticsNodes().isNotEmpty(),
        )
        mainClock.advanceTimeBy(1_000)
        onRoot().performMouseInput { click(Offset(WIDTH - 2f, 2f)) }
        waitForIdle()
        return offers
    }

    @Test
    fun `a press on blank space low on the home page keeps its scroll`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            home()
            val foot = toTheFoot()
            assertTrue(foot > HEIGHT, "the home page is taller than the window: scrolled to $foot")
            val every = bounds(EVERY_ORDER)
            val taken = taken()
            // Every point of the column through the page's middle, under «Every order.», that holds no words and no
            // control — the gap above the footer the owner pressed among them.
            val blank =
                (every.bottom.toInt() + 4 until HEIGHT - 4 step 12)
                    .map { Offset(WIDTH / 2f, it.toFloat()) }
                    .filter { point -> taken.none { it.contains(point) } }
            assertTrue(blank.size >= 5, "blank points under «Every order.»: $blank")
            val moved =
                blank.mapNotNull { point ->
                    val before = toTheFoot()
                    onRoot().performMouseInput { click(point) }
                    waitForIdle()
                    scrolled().takeIf { it != before }?.let { "a press at $point scrolled $before → $it" }
                }
            assertEquals(emptyList(), moved)
        }

    @Test
    fun `a press on blank space still clears the words selected`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            home()
            toTheFoot()
            val every = bounds(EVERY_ORDER)
            onRoot().performMouseInput {
                moveTo(Offset(every.left + 1f, every.center.y))
                press()
                moveTo(Offset(every.right - 1f, every.center.y))
                release()
            }
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            assertTrue(offersCopy(every.center), "a drag over «Every order.» selects it")
            val before = scrolled()
            onRoot().performMouseInput { click(Offset(WIDTH / 2f, every.bottom + 24f)) }
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            assertEquals(before, scrolled())
            assertTrue(!offersCopy(every.center), "a press on blank space clears the selection")
        }

    @Test
    fun `a drag past the window's foot scrolls the page with the selection`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            home()
            assertEquals(0f, scrolled())
            val every = bounds(EVERY_ORDER)
            val start = taken().first { it.top > 80f && it.height in 10f..60f && it.width > 40f }
            mainClock.autoAdvance = false
            onRoot().performMouseInput {
                moveTo(Offset(start.left + 2f, start.center.y))
                press()
                moveTo(Offset(WIDTH / 2f, HEIGHT - 20f))
                moveTo(Offset(WIDTH / 2f, HEIGHT + 30f))
            }
            repeat(60) { mainClock.advanceTimeByFrame() }
            val during = scrolled()
            onRoot().performMouseInput { release() }
            mainClock.autoAdvance = true
            waitForIdle()
            assertTrue(during > 0f, "a drag past the foot of the window scrolls: $during (from ${every.top})")
        }

    private companion object {
        const val WIDTH = 800
        const val HEIGHT = 600
        const val EVERY_ORDER = "Every order."
    }
}
