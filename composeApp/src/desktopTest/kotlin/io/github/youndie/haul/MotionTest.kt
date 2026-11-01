package io.github.youndie.haul

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.ui.LocalMotion
import io.github.youndie.haul.ui.Motion
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.RollingText
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.ButtonComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.TextComponent
import kotlinx.serialization.PolymorphicSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * B-158: the storefront moves, and the screenshots do not. Every golden is a page at rest, drawn still; what moves on
 * a lively page has to come to rest exactly there — a card revealed at its place and fully drawn, a control that
 * reads hover and presses at its size — or the goldens stop describing the store a shopper sees once the page has
 * settled. A revealed block left a few pixels low, or a layer that drew its words a shade differently, would pass
 * every golden (drawn still) and be wrong on the stand.
 */
@OptIn(ExperimentalTestApi::class)
class MotionTest {
    private fun ComposeUiTest.home(motion: Motion) =
        setContent {
            CompositionLocalProvider(LocalMotion provides motion) {
                Fixture(compact = false) { Page { Body("home_content.json") } }
            }
        }

    private fun ComposeUiTest.drawn(): IntArray = onRoot().captureToImage().pixels()

    /**
     * What [motion]'s home draws: settled, by default; [frozen], with the clock held from the start and moved only
     * by [frames].
     */
    private fun capture(
        motion: Motion,
        frozen: Boolean = false,
        // Long enough for every stagger and spring: a reveal's wait is a delay, which the clock does not wait for.
        frames: ComposeUiTest.() -> Unit = {
            mainClock.advanceTimeBy(SETTLE_MS)
            waitForIdle()
        },
    ): IntArray {
        var pixels = IntArray(0)
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            mainClock.autoAdvance = !frozen
            home(motion)
            frames()
            pixels = drawn()
        }
        return pixels
    }

    @Test
    fun `a lively home at rest draws what the still home draws`() {
        val still = capture(Motion.Still)
        val lively = capture(Motion.Lively)
        assertEquals(still.size, lively.size)
        val differ = still.indices.filter { still[it] != lively[it] }
        assertEquals(
            0,
            differ.size,
            "pixels a lively home left different from the still one once it settled, " +
                "x ${differ.minOfOrNull { it % WIDTH }}..${differ.maxOfOrNull { it % WIDTH }}, " +
                "y ${differ.minOfOrNull { it / WIDTH }}..${differ.maxOfOrNull { it / WIDTH }}",
        )
    }

    /** The control for the test above: while it arrives, the lively home is not yet the still one. */
    @Test
    fun `a lively home is still arriving a few frames in`() {
        val still = capture(Motion.Still)
        val arriving =
            capture(Motion.Lively, frozen = true) {
                mainClock.advanceTimeByFrame()
                mainClock.advanceTimeBy(FEW_FRAMES_MS)
            }
        assertTrue(!still.contentEquals(arriving), "nothing moved: the lively home arrived already drawn")
    }

    /**
     * B-168: the first window arrives quickly — the cascade over it took about 1.4 s before its last block settled
     * (620 ms reveals, 70 ms apart, up to 260 ms down the window). Counted on the held clock, frame by frame, from
     * the frame the home's tree is first drawn to the first frame that is the still home.
     */
    @Test
    fun `the last block above the fold settles within 600 ms of the page arriving`() {
        val still = capture(Motion.Still)
        var settled = -1L
        capture(Motion.Lively, frozen = true) {
            mainClock.advanceTimeByFrame()
            val arrived = mainClock.currentTime
            while (mainClock.currentTime - arrived < LOOK_MS) {
                mainClock.advanceTimeByFrame()
                if (drawn().contentEquals(still)) {
                    settled = mainClock.currentTime - arrived
                    break
                }
            }
        }
        println("B-168: the lively home's first window settled $settled ms after it arrived")
        assertTrue(settled in 0..SETTLED_WITHIN_MS, "the first window settled $settled ms after it arrived")
    }

    /** The storefront at `/`, [motion], with a transport that answers the home and a product page. */
    private fun ComposeUiTest.storefront(
        motion: Motion,
        history: FakeHistory,
    ) {
        val transport =
            HaulTransport { path ->
                val tree = if (path == "/ui/home") goingHome else productPage
                HaulResponse(200, haulWireJson.encodeToString(PolymorphicSerializer(KompotComponent::class), tree))
            }
        setContent {
            CompositionLocalProvider(LocalMotion provides motion) {
                Fixture(compact = false) { Storefront(transport, history, signIn = {}, clock = canvasClock) }
            }
        }
    }

    /**
     * What the storefront draws a few frames after the home arrives, [motion]: the first time it is drawn, or [back]
     * to it from a product page opened from it.
     */
    private fun arriving(
        motion: Motion,
        back: Boolean,
    ): IntArray {
        var pixels = IntArray(0)
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val history = FakeHistory()
            mainClock.autoAdvance = back
            storefront(motion, history)
            if (back) {
                mainClock.advanceTimeBy(SETTLE_MS)
                waitForIdle()
                onNodeWithText(GO).performClick()
                mainClock.advanceTimeBy(SETTLE_MS)
                waitForIdle()
                onNodeWithText(PRODUCT_TEXT).assertExists()
                mainClock.autoAdvance = false
                history.back()
            }
            repeat(ARRIVAL_FRAMES) { mainClock.advanceTimeByFrame() }
            onNodeWithText(GO).assertExists()
            onNodeWithText(PRODUCT_TEXT).assertDoesNotExist()
            pixels = drawn()
        }
        return pixels
    }

    /** B-168: going back to a page brings it at rest, where it played its arrival again. */
    @Test
    fun `back to a page shows it at rest at once`() {
        val still = arriving(Motion.Still, back = true)
        val lively = arriving(Motion.Lively, back = true)
        val differ = still.indices.count { still[it] != lively[it] }
        assertEquals(0, differ, "pixels the home back from a product page drew still arriving")
    }

    /** The control for the test above: the first time it is drawn, the same home is arriving those frames in. */
    @Test
    fun `the first time a page is drawn it is arriving`() {
        val still = arriving(Motion.Still, back = false)
        val lively = arriving(Motion.Lively, back = false)
        assertTrue(!still.contentEquals(lively), "the home drawn for the first time arrived already at rest")
    }

    /** A card in a row, [motion], settled after [input]. */
    private fun card(
        motion: Motion,
        input: ComposeUiTest.() -> Unit,
    ): IntArray {
        var pixels = IntArray(0)
        runDesktopComposeUiTest(CARD_WIDTH, CARD_HEIGHT) {
            setContent {
                CompositionLocalProvider(LocalMotion provides motion) {
                    Fixture(compact = false) { Render(cardRow) }
                }
            }
            mainClock.advanceTimeBy(SETTLE_MS)
            waitForIdle()
            input()
            mainClock.advanceTimeBy(SETTLE_MS)
            waitForIdle()
            pixels = drawn()
        }
        return pixels
    }

    /** B-168: a finger has no hover — a tap left the card lifted as if the mouse were over it. */
    @Test
    fun `a touch press leaves no hover lift`() {
        val still = card(Motion.Still) {}
        val tapped = card(Motion.Lively) { onNodeWithText(CARD_TITLE).performTouchInput { click() } }
        val differ = still.indices.count { still[it] != tapped[it] }
        assertEquals(0, differ, "pixels a card tapped and released drew off its place at rest")
    }

    /** The control for the test above: the mouse over the same card lifts it. */
    @Test
    fun `a mouse over a card lifts it`() {
        val still = card(Motion.Still) {}
        val hovered = card(Motion.Lively) { onNodeWithText(CARD_TITLE).performMouseInput { moveTo(center) } }
        assertTrue(!still.contentEquals(hovered), "the mouse over the card left it where it rests")
    }

    /** A countdown's pill drawn [motion] with [text], the clock held: [frames] runs once it is first drawn. */
    private fun ticking(
        motion: Motion,
        frames: ComposeUiTest.(tick: () -> Unit) -> Unit,
    ) = runDesktopComposeUiTest(PILL_WIDTH, PILL_HEIGHT) {
        var text by mutableStateOf(BEFORE)
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalMotion provides motion) {
                Fixture(compact = false) { RollingText(text, HaulType.label(18f, 600, 0.04f)) }
            }
        }
        mainClock.advanceTimeByFrame()
        frames { text = AFTER }
    }

    /**
     * The countdown's figures roll as they tick, and come to rest as the still text: half-way through a tick the pill
     * is neither the second before nor the one after, and once the roll is over it is exactly the still pill of the
     * second after. A roll that left its characters a pixel off would tick the whole countdown sideways every second.
     */
    @Test
    fun `a ticking figure rolls and comes to rest as the still text`() {
        var still = IntArray(0)
        ticking(Motion.Still) { tick ->
            tick()
            mainClock.advanceTimeByFrame()
            still = drawn()
        }
        var before = IntArray(0)
        var rolling = IntArray(0)
        var after = IntArray(0)
        ticking(Motion.Lively) { tick ->
            before = drawn()
            tick()
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeBy(HALF_A_ROLL_MS)
            rolling = drawn()
            mainClock.advanceTimeBy(SETTLE_MS)
            after = drawn()
        }
        assertTrue(!before.contentEquals(rolling), "half-way through the tick the pill still read $BEFORE")
        assertTrue(!after.contentEquals(rolling), "half-way through the tick the pill already read $AFTER")
        assertTrue(still.contentEquals(after), "the pill at rest after the tick is not the still $AFTER")
    }

    @Test
    fun `a browser that prefers reduced motion gets a still page`() {
        assertEquals(Motion.Still, Motion.preferring(reducedMotion = true))
        assertEquals(Motion.Lively, Motion.preferring(reducedMotion = false))
    }

    private fun ImageBitmap.pixels(): IntArray {
        val map = toPixelMap()
        return IntArray(width * height) { map[it % width, it / width].toArgb() }
    }

    private companion object {
        const val GO = "Open the product"
        const val PRODUCT_TEXT = "The product page"
        const val CARD_TITLE = "Sonora SH-718N Wireless Noise Cancelling Headphones"

        /** The home, with a way to a product page above it. */
        val goingHome: KompotComponent =
            (decode("home_content.json") as ColumnComponent).let { home ->
                home.copy(
                    children =
                        listOf(
                            ButtonComponent(id = "go", text = GO, action = NavigateAction("/p/p-1")),
                        ) + home.children,
                )
            }
        val productPage: KompotComponent =
            ColumnComponent(id = "page", children = listOf(TextComponent(id = "next", text = PRODUCT_TEXT)))

        val cardRow: KompotComponent =
            ProductGrid(
                id = "grid",
                cards =
                    listOf(
                        ProductCard(
                            id = "card-p-1",
                            productId = "p-1",
                            title = CARD_TITLE,
                            price = "$349",
                            rating = "4.8",
                            reviews = "2,341",
                            delivery = "Tomorrow",
                            tone = "#E6E4FF",
                        ),
                    ),
                columns = 6,
            )

        val canvasClock =
            object : Clock {
                override fun now(): Instant = CANVAS_NOW
            }

        /** How long the settle test looks before it gives up: past the 1.4 s the cascade took before B-168. */
        const val LOOK_MS = 2_400L
        const val SETTLED_WITHIN_MS = 600L
        const val ARRIVAL_FRAMES = 4
        const val CARD_WIDTH = 1_440
        const val CARD_HEIGHT = 520
        const val WIDTH = 1_440
        const val HEIGHT = 1_600
        const val FEW_FRAMES_MS = 80L
        const val SETTLE_MS = 3_000L
        const val HALF_A_ROLL_MS = 140L
        const val PILL_WIDTH = 200
        const val PILL_HEIGHT = 60
        const val BEFORE = "04:12:37"
        const val AFTER = "04:12:36"
    }
}
