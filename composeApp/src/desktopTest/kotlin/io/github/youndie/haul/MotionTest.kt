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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.ui.LocalMotion
import io.github.youndie.haul.ui.Motion
import io.github.youndie.haul.ui.RollingText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
