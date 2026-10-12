package io.github.youndie.haul.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.launch

/**
 * The field a press on blank page space takes the focus off (B-118). A browser's input lets go of the focus when the
 * page is clicked anywhere else; a Compose field keeps it through a press nothing on the page takes, so a bound typed
 * into a price field and left with a click elsewhere stayed in the field, applied by nothing. A field that wants a
 * browser's behaviour [holds] this while it is focused — with its whole box, so a press on the box around the text
 * is still a press on the field — and the page under it is [releasingOnBlankPress].
 *
 * Only a field that asks: the header's search keeps its panel through such a press, and the checkout's address form
 * decides for itself which presses leave it.
 */
@Stable
public class BlankPress {
    private var held: LayoutCoordinates? = null

    /** The focused field, drawn at [box]: a blank press outside it takes the focus off it. */
    public fun holds(box: LayoutCoordinates) {
        held = box
    }

    /** The field at [box] lost the focus; a field that took it since is kept. */
    public fun lets(box: LayoutCoordinates) {
        if (held === box) held = null
    }

    /** The field a press at [position] on [page] takes the focus off, if one is held and the press is outside it. */
    internal fun outside(
        page: LayoutCoordinates,
        position: Offset,
    ): LayoutCoordinates? =
        held?.takeIf {
            it.isAttached && page.isAttached && !page.localBoundingBoxOf(it).contains(position)
        }

    internal fun stillHolds(box: LayoutCoordinates): Boolean = held === box
}

/** The page's [BlankPress]; none — a screenshot — and a field keeps its focus as Compose's does. */
public val LocalBlankPress: ProvidableCompositionLocal<BlankPress?> = staticCompositionLocalOf { null }

/**
 * A press on this page that nothing under it takes — no control's, no field's, not the start of a drag — and that
 * lands outside the field [LocalBlankPress] holds takes the focus off that field, as Tab would. The press is read on
 * the last pass, after everything under it had its turn, and is never consumed, so it goes on as it would have. The
 * focus goes once what was typed before the press is in the field (B-76): a field left without its last keys would
 * apply a bound the shopper did not type.
 */
@Composable
internal fun Modifier.releasingOnBlankPress(): Modifier {
    val blank = LocalBlankPress.current ?: return this
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val page = remember { arrayOfNulls<LayoutCoordinates>(1) }
    return onGloballyPositioned { page[0] = it }
        .pointerInput(blank, focus) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                var taken = down.isConsumed
                var dragged = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    taken = taken || event.changes.any { it.isConsumed }
                    dragged = dragged ||
                        event.changes.any { (it.position - down.position).getDistance() > viewConfiguration.touchSlop }
                } while (event.changes.any { it.pressed })
                val field = page[0]?.takeUnless { taken || dragged }?.let { blank.outside(it, down.position) }
                if (field != null) {
                    scope.launch {
                        awaitTypedInput()
                        if (blank.stillHolds(field)) focus.clearFocus()
                    }
                }
            }
        }
}
