package io.github.youndie.haul.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.theme.HaulColors

// The control the keyboard is on is marked (B-167): every press of the store's is drawn with no indication — the
// canvas draws none — so Tab moved through the page and nothing showed where it was. A ring in the accent is drawn
// around the control Tab brought the focus to, as a browser's `:focus-visible` draws its outline: when the focus came
// from the keyboard, and not after a press of the pointer (a mouse press focuses the control it lands on, B-144). A
// field's frame shows it whenever the field has the focus ([ringsAround]), as a browser's text field does.

/**
 * Whether the page's last input was the keyboard's, as a browser decides `:focus-visible`: a key pressed — one with no
 * ⌘, Ctrl or Alt held, a shortcut being no move through the page — makes it so, a press of the pointer takes it away.
 * Told by the storefront's root ([watchesInput]), and in the browser by the document itself, before Compose sees the
 * key: a Tab pressed while nothing has the focus — after a press that followed a link, whose page took the focused
 * control away — reaches no node of the page, and the control it focuses has to know it came from the keyboard.
 */
@Stable
public class KeyboardFocus {
    /** Whether a focused control draws its ring now. Before any input, a Tab is the first thing that can focus. */
    public var byKeyboard: Boolean by mutableStateOf(true)
        private set

    /** A key went down; [shortcut] — with ⌘, Ctrl or Alt held — is not a move through the page and changes nothing. */
    public fun keyPressed(shortcut: Boolean = false) {
        if (!shortcut) byKeyboard = true
    }

    /** A pointer went down: a mouse button, a finger, a pen. */
    public fun pointerPressed() {
        byKeyboard = false
    }
}

/**
 * The page's [KeyboardFocus]; `null` — a screenshot, a view drawn on its own — draws a focused control's ring
 * whatever focused it.
 */
public val LocalKeyboardFocus: ProvidableCompositionLocal<KeyboardFocus?> = staticCompositionLocalOf { null }

/**
 * Tells [focus] what this reads before anything inside it: each key going down, and each press of a pointer. It takes
 * neither. A key pressed with nothing focused reaches no node; the browser's document tells it ([KeyboardFocus]).
 */
internal fun Modifier.watchesInput(focus: KeyboardFocus): Modifier =
    onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.key !in MODIFIER_KEYS) {
            focus.keyPressed(shortcut = it.isMetaPressed || it.isCtrlPressed || it.isAltPressed)
        }
        false
    }.pointerInput(focus) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press) focus.pointerPressed()
            }
        }
    }

/** Keys that are held for another key: pressed alone, they move nothing, as a browser has them. */
private val MODIFIER_KEYS =
    setOf(
        Key.ShiftLeft,
        Key.ShiftRight,
        Key.CtrlLeft,
        Key.CtrlRight,
        Key.AltLeft,
        Key.AltRight,
        Key.MetaLeft,
        Key.MetaRight,
    )

/** The ring's shape around a control that has none of its own to follow: a word, an icon, a row. */
internal val FOCUS_RING_DEFAULT: Shape = RoundedCornerShape(6.dp)

/**
 * The ring around this control while the keyboard brought the focus to it, following [shape] — the control's own:
 * a pill's, a card's, a circle's. It goes before the control's focus — `clickable`, `toggleable` — in the chain, and
 * reads that one's focus alone: a card does not ring while its «+» has the focus. Nothing is drawn while nothing is
 * focused, so a screenshot is drawn as before.
 */
internal fun Modifier.ringsWhenFocused(shape: Shape = FOCUS_RING_DEFAULT): Modifier = this then FocusRingElement(shape)

private data class FocusRingElement(
    val shape: Shape,
) : ModifierNodeElement<FocusRingNode>() {
    override fun create(): FocusRingNode = FocusRingNode(shape)

    override fun update(node: FocusRingNode) {
        node.shape = shape
        node.invalidateDraw()
    }
}

private class FocusRingNode(
    var shape: Shape,
) : Modifier.Node(),
    FocusEventModifierNode,
    DrawModifierNode,
    CompositionLocalConsumerModifierNode {
    private var focused = false

    override fun onFocusEvent(focusState: FocusState) {
        if (focusState.isFocused == focused) return
        focused = focusState.isFocused
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        // Read in the draw, so that a press of the pointer takes the ring away and a key brings it back.
        if (focused && currentValueOf(LocalKeyboardFocus)?.byKeyboard != false) drawFocusRing(shape)
    }
}

/**
 * Whether a field has the focus, for the ring around its frame ([ringsAround]): the frame is drawn around the field
 * and more — the search box holds its scope picker and its button too — so it rings for the field ([ringedBy]) alone.
 */
@Stable
internal class FieldRing {
    internal var focused: Boolean by mutableStateOf(false)
}

@Composable
internal fun rememberFieldRing(): FieldRing = remember { FieldRing() }

/**
 * The ring around a field's frame, following [shape], whenever the field [ring] watches has the focus: as a browser's
 * text field matches `:focus-visible` however it was focused. Typed into after a press, it would otherwise ring in
 * the middle of the typing, at the first key.
 */
internal fun Modifier.ringsAround(
    ring: FieldRing,
    shape: Shape,
): Modifier =
    drawWithContent {
        drawContent()
        if (ring.focused) drawFocusRing(shape)
    }

/** The field whose focus [ring] tells its frame. */
internal fun Modifier.ringedBy(ring: FieldRing): Modifier = onFocusChanged { ring.focused = it.isFocused }

/** The ring itself, just outside what is drawn: the accent over a halo of the page's paper. */
private fun DrawScope.drawFocusRing(shape: Shape) {
    val out = RING_OUT.toPx()
    val ringSize = Size(size.width + out * 2, size.height + out * 2)
    translate(-out, -out) {
        val outline = shape.createOutline(ringSize, layoutDirection, this)
        // The halo keeps the ring seen on a dark bar — the demo strip, «Under the hood» — as on the page.
        drawOutline(outline, HaulColors.background, style = Stroke(RING_HALO.toPx()))
        drawOutline(outline, HaulColors.primary, style = Stroke(RING_WIDTH.toPx()))
    }
}

/** How far outside the control the middle of its ring is drawn: the accent from 1 to 3 px out, the halo from 0 to 4. */
private val RING_OUT = 2.dp
private val RING_WIDTH = 2.dp
private val RING_HALO = 4.dp
