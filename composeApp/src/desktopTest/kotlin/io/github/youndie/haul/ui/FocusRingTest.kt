package io.github.youndie.haul.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.read
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.kompot.standard.RefreshAction
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * B-167: the control the keyboard is on is ringed in the accent, and only when the keyboard put the focus there — a
 * browser's `:focus-visible`. Tab rings a control and Enter or Space presses it; a mouse press rings nothing, though
 * it focuses the control it lands on; the next key brings the ring back; a field's frame rings whenever the field
 * has the focus, as a browser's text field does; a card does not ring while its «+» has the focus. The ring is read
 * off the pixels, in the accent just outside the control's edge, where nothing else is drawn. Every key is sent to
 * the window, wherever the focus is.
 */
@OptIn(ExperimentalTestApi::class)
class FocusRingTest {
    private val pressed = mutableListOf<String>()

    private fun ComposeUiTest.controls() =
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                OverlayHost {
                    Column(
                        Modifier.fillMaxSize().background(HaulColors.background).padding(40.dp),
                        verticalArrangement = Arrangement.spacedBy(40.dp),
                    ) {
                        Box(Modifier.size(160.dp, 44.dp).testTag(FIRST).pressable(PILL) { pressed += FIRST })
                        Box(Modifier.size(160.dp, 44.dp).testTag(SECOND).pressable { pressed += SECOND })
                        var typed by remember { mutableStateOf("") }
                        val ring = rememberFieldRing()
                        Box(
                            Modifier
                                .size(240.dp, 44.dp)
                                .testTag(FRAME)
                                .ringsAround(ring, PILL)
                                .background(HaulColors.surfaceContainerLowest, PILL)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            HaulTextField(
                                typed,
                                { typed = it },
                                Modifier.testTag(FIELD).ringedBy(ring),
                                singleLine = true,
                            )
                        }
                        Box(Modifier.size(240.dp, 120.dp).testTag(CARD).pressable { pressed += CARD }) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(12.dp)
                                    .size(38.dp)
                                    .testTag(PLUS)
                                    .pressable(ROUND) { pressed += PLUS },
                            )
                        }
                    }
                }
            }
        }

    private fun ComposeUiTest.press(key: Key) {
        onRoot().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    /** Tab until [tag] has the focus, within a few. */
    private fun ComposeUiTest.tabTo(tag: String) {
        repeat(TABS) {
            if (onAllNodes(isFocused() and hasTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
                return
            }
            press(Key.Tab)
        }
        onNodeWithTag(tag).assertIsFocused()
    }

    private fun ComposeUiTest.ringed(tag: String): Boolean =
        ringed(onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot)

    /**
     * Whether the accent is drawn just outside [bounds]: on a line out from the middle of any of its edges, within
     * [reach] — the ring's own few pixels for a control, the way to its frame's edge for a field.
     */
    private fun ComposeUiTest.ringed(
        bounds: Rect,
        reach: Dp = 4.dp,
    ): Boolean {
        val pixels = onRoot().captureToImage().toPixelMap()
        val c = bounds.center
        val far = (reach.value * density.density).toInt()
        return (1..far).any { out ->
            listOf(
                Offset(bounds.left - out, c.y),
                Offset(bounds.right + out - 1, c.y),
                Offset(c.x, bounds.top - out),
                Offset(c.x, bounds.bottom + out - 1),
            ).any { at ->
                val x = at.x.toInt()
                val y = at.y.toInt()
                x in 0 until pixels.width && y in 0 until pixels.height && pixels[x, y].near(HaulColors.primary)
            }
        }
    }

    @Test
    fun `Tab rings the control it reaches, Enter and Space press it, Shift+Tab goes back`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            controls()
            assertFalse(ringed(FIRST), "a ring before anything has the focus")
            tabTo(FIRST)
            assertTrue(ringed(FIRST), "the control Tab reached has no ring")
            assertFalse(ringed(SECOND), "a control without the focus is ringed")
            press(Key.Enter)
            assertEquals(listOf(FIRST), pressed, "Enter did not press the control Tab reached")
            press(Key.Tab)
            onNodeWithTag(SECOND).assertIsFocused()
            assertTrue(ringed(SECOND), "the ring did not move on with Tab")
            assertFalse(ringed(FIRST), "the ring stayed on the control Tab left")
            press(Key.Spacebar)
            assertEquals(listOf(FIRST, SECOND), pressed, "Space did not press the control Tab reached")
            onRoot().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
            waitForIdle()
            onNodeWithTag(FIRST).assertIsFocused()
            assertTrue(ringed(FIRST), "Shift+Tab back drew no ring")
            assertFalse(ringed(SECOND), "the ring stayed on the control Shift+Tab left")
        }

    @Test
    fun `a mouse press rings nothing and the next key brings the ring back`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            controls()
            tabTo(SECOND)
            assertTrue(ringed(SECOND))
            onNodeWithTag(FIRST).performMouseInput { click() }
            waitForIdle()
            assertEquals(listOf(FIRST), pressed)
            assertFalse(ringed(FIRST), "a mouse press drew the ring on the control it pressed")
            assertFalse(ringed(SECOND), "a mouse press left the ring on the control Tab had reached")
            press(Key.Tab)
            val focused = onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().single()
            assertTrue(ringed(focused.boundsInRoot), "Tab after a mouse press drew no ring")
        }

    @Test
    fun `a field's frame rings whenever the field has the focus`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            controls()
            onNodeWithTag(FIELD).performMouseInput { click() }
            waitForIdle()
            onNodeWithTag(FIELD).assertIsFocused()
            assertTrue(ringed(FRAME), "a field focused by a press has no ring on its frame")
            press(Key.Tab)
            assertFalse(ringed(FRAME), "the field's frame kept its ring after Tab left it")
        }

    @Test
    fun `a card does not ring while its plus has the focus`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            controls()
            tabTo(CARD)
            assertTrue(ringed(CARD))
            press(Key.Tab)
            onNodeWithTag(PLUS).assertIsFocused()
            assertTrue(ringed(PLUS), "the card's plus has no ring")
            assertFalse(ringed(CARD), "the card is ringed while its plus has the focus")
        }

    /**
     * The cart as the storefront draws it at 1440, walked with Tab from the top: the header's controls, the category
     * row, the cart's boxes and lines, the promo field and Checkout, in reading order, each ringed as it is reached;
     * the walk comes round to the logo again.
     */
    @Test
    fun `Tab walks the cart in reading order and rings each control`() {
        val walk = walk("/cart", "cart_content.json", compact = false)
        assertEquals(emptyList(), walk.unringed, "controls Tab reached without a ring")
        val order = READING_ORDER.map { expected -> walk.names.indexOfFirst { it.startsWith(expected) } }
        assertTrue(order.none { it < 0 }, "controls Tab never reached: ${READING_ORDER.zip(order)} in ${walk.names}")
        assertEquals(order.sorted(), order, "Tab out of reading order: ${walk.names}")
    }

    /** Every control Tab reaches on the main screens with actions in their bodies, at both widths, draws its ring. */
    @Test
    fun `every control Tab reaches on the main screens is ringed`() {
        val unringed =
            SCREENS.flatMap { (address, body) ->
                listOf(false, true).flatMap { compact ->
                    val walk = walk(address, body, compact)
                    val where = "$address $body ${if (compact) "phone" else "wide"}"
                    val few = if (walk.names.size < FEW) listOf("$where: only ${walk.names}") else emptyList()
                    few + walk.unringed.map { "$where: $it" }
                }
            }
        assertEquals(emptyList(), unringed, "controls Tab reached without a ring, by screen")
    }

    /** What a walk with Tab over a page met: each control's name, in order, and those drawn with no ring. */
    private class Walk(
        val names: List<String>,
        val unringed: List<String>,
    )

    /**
     * The storefront at [address], drawn from [body], walked with Tab until it comes round to the first control again.
     * Each control is drawn twice where Tab left it — as the keyboard has it, and as after a pointer press, with no
     * ring — and is ringed when the accent is drawn near it in the first and not in the second. A field rings either
     * way, around its frame.
     */
    private fun walk(
        address: String,
        body: String,
        compact: Boolean,
    ): Walk {
        val tree = read(body)
        val keyboard = KeyboardFocus()
        val names = mutableListOf<String>()
        val unringed = mutableListOf<String>()
        runDesktopComposeUiTest(if (compact) PHONE else WIDTH, WALK_HEIGHT) {
            setContent {
                HaulTheme(FixtureFonts.fonts, compact = compact) {
                    CompositionLocalProvider(LocalKeyboardFocus provides keyboard) {
                        Storefront(
                            HaulTransport { HaulResponse(200, tree) },
                            FakeHistory(address),
                            signIn = {},
                            cartCommands = { RefreshAction },
                            commands = { _, _, _ -> HaulResponse(200, "{}") },
                            checkoutCommands = { RefreshAction },
                            treeCommands = { RefreshAction },
                        )
                    }
                }
            }
            waitUntil(timeoutMillis = 5_000) { onAllNodes(hasClickAction()).fetchSemanticsNodes().size > 5 }
            waitForIdle()
            for (step in 0 until WALK) {
                press(Key.Tab)
                val focused = onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().singleOrNull()
                // The page's selection is a stop of its own, with nothing of ours to ring (B-167's findings).
                if (focused == null || !focused.isControl()) continue
                val name = name(focused)
                if (names.isNotEmpty() && name == names.first()) break
                names += name
                // A field's frame rings however the field was focused: its ring is found around the field's frame.
                val shown =
                    if (hasSetTextAction().matches(focused)) {
                        ringed(focused.boundsInRoot, FRAME_REACH)
                    } else {
                        ringShown(focused.boundsInRoot, keyboard, 6.dp)
                    }
                if (!shown) unringed += "«$name» at ${focused.boundsInRoot}"
            }
        }
        return Walk(names, unringed)
    }

    /** Whether the accent appears within [reach] of [bounds] when [keyboard] has the focus, and goes after a press. */
    private fun ComposeUiTest.ringShown(
        bounds: Rect,
        keyboard: KeyboardFocus,
        reach: Dp,
    ): Boolean {
        val ringed = onRoot().captureToImage().toPixelMap()
        keyboard.pointerPressed()
        waitForIdle()
        val plain = onRoot().captureToImage().toPixelMap()
        keyboard.keyPressed()
        waitForIdle()
        val far = reach.value * density.density
        val xs =
            (bounds.left - far).toInt().coerceAtLeast(0) until (bounds.right + far).toInt().coerceAtMost(ringed.width)
        val ys =
            (bounds.top - far).toInt().coerceAtLeast(0) until (bounds.bottom + far).toInt().coerceAtMost(ringed.height)
        var drawn = 0
        for (x in xs) {
            for (y in ys) {
                if (ringed[x, y].near(HaulColors.primary) && !plain[x, y].near(HaulColors.primary)) drawn += 1
            }
        }
        return drawn >= RING_PIXELS
    }

    private fun SemanticsNode.isControl(): Boolean = hasClickAction().matches(this) || hasSetTextAction().matches(this)

    private fun hasTag(tag: String) =
        androidx.compose.ui.test.SemanticsMatcher
            .expectValue(SemanticsProperties.TestTag, tag)

    /** The words the mirror names a press by: its content description, else the text in it and under it. */
    private fun name(node: SemanticsNode): String {
        val own =
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
                ?: node.config
                    .getOrNull(SemanticsProperties.Text)
                    ?.joinToString(" ") { it.text }
                    .orEmpty()
        return (own + " " + node.children.joinToString(" ") { name(it) }).trim()
    }

    private fun Color.near(other: Color): Boolean =
        abs(red - other.red) < NEAR && abs(green - other.green) < NEAR && abs(blue - other.blue) < NEAR

    private companion object {
        const val WIDTH = 1440
        const val HEIGHT = 900
        const val TABS = 8
        const val WALK = 160
        const val WALK_HEIGHT = 1_200
        const val PHONE = 390

        /** Fewer controls than this on a screen: the body did not draw, or Tab stopped short. */
        const val FEW = 6

        /** A ring around the smallest control — a 22 px box — is well over this many pixels of the accent. */
        const val RING_PIXELS = 40
        const val NEAR = 0.08f
        const val FIRST = "first"
        const val SECOND = "second"
        const val FRAME = "frame"
        const val FIELD = "field"
        const val CARD = "card"
        const val PLUS = "plus"
        val PILL = RoundedCornerShape(22.dp)

        /** How far a field's frame is from the field inside it: the search box's 13 px above, the promo's 17 aside. */
        val FRAME_REACH = 40.dp
        val ROUND = RoundedCornerShape(50)

        /** The main screens whose bodies carry their actions, at their addresses: each control in them is a press. */
        val SCREENS =
            listOf(
                "/cart" to "cart_content.json",
                "/checkout" to "checkout_content.json",
                "/account" to "account_content.json",
                "/account/orders" to "account_orders.json",
                "/account/saved" to "saved_content.json",
                "/account/orders/HL-48302" to "order_in_transit.json",
                "/p/p-sonora-sh-718n?tab=reviews" to "product_reviews.json",
            )

        /** What a reader of the cart meets, top to bottom: the header, the category row, the lines, the summary. */
        val READING_ORDER =
            listOf(
                "Haul",
                "Catalog",
                "Search products",
                "Maya",
                "Cart",
                "Electronics",
                "Select all",
                "Select Sonora",
                "Save for later",
                "Select Linen",
                "Select Stoneware",
                "Promo code",
                "Apply",
                "Checkout",
            )
    }
}
