package io.github.youndie.haul.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import kotlin.math.roundToInt

/** The tag of an open [LinkMenu], for the tests that tell open from closed. */
public const val LINK_MENU_TAG: String = "link-menu"

/**
 * [control] opens [links] as a menu under it — «Catalog»'s categories, the sort's orders — and an entry
 * pressed follows its action. [current] is the entry drawn as chosen. With nobody to follow the
 * actions ([LocalHaulActions] `null`, every screenshot) [control] is drawn as it is and opens nothing;
 * no artboard draws a menu open, so closed is the only state the canvas has. A press outside, or Escape,
 * closes it.
 *
 * The box around [control] passes its constraints on unchanged, so wrapping a control moves no pixel.
 */
@Composable
internal fun LinkMenu(
    links: List<Link>,
    modifier: Modifier = Modifier,
    current: String? = null,
    alignEnd: Boolean = false,
    control: @Composable (Modifier) -> Unit,
) {
    val actions = LocalHaulActions.current
    val entries =
        if (actions == null) {
            emptyList()
        } else {
            links.map { link -> MenuEntry(link.label, link.action?.let { { actions.handle(it) } }) }
        }
    Menu(entries, modifier, current, alignEnd, control)
}

/** An entry of a [Menu]: what it reads, and what pressing it does — `null` draws it and does nothing. */
internal class MenuEntry(
    val label: String,
    val onPress: (() -> Unit)?,
)

/**
 * [control] opens [entries] as a menu under it, and an entry pressed closes it and runs its press — a
 * [LinkMenu]'s links, or a choice the client keeps itself (the search field's picker, B-72). [current] is the
 * entry drawn as chosen; pressed, it closes the menu even with nothing to run. With no entry to press, [control]
 * is drawn as it is and opens nothing.
 */
@Composable
internal fun Menu(
    entries: List<MenuEntry>,
    modifier: Modifier = Modifier,
    current: String? = null,
    alignEnd: Boolean = false,
    control: @Composable (Modifier) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    // Over the page, in the storefront's own layer (B-126): with none — a screenshot — the menu opens nothing.
    val usable = entries.any { it.onPress != null } && isOverPage()
    // Where the control is in the window, unclipped: the menu opens under it.
    var anchor by remember { mutableStateOf(IntRect.Zero) }
    Box(
        modifier.onGloballyPositioned {
            val at = it.positionInRoot()
            anchor = IntRect(IntOffset(at.x.roundToInt(), at.y.roundToInt()), it.size)
        },
        propagateMinConstraints = true,
    ) {
        control(Modifier.pressable(if (usable) ({ open = !open }) else null))
        if (open && usable) {
            val gap = with(LocalDensity.current) { MENU_GAP.roundToPx() }
            val origin = LocalOverlays.current?.origin ?: Offset.Zero
            val below = Below(gap, alignEnd)
            OverPage {
                // A press anywhere off the menu closes it, and goes no further: the page under it is not pressed.
                Layout(
                    content = { MenuColumn(entries, current) { open = false } },
                    modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { open = false } },
                ) { measurables, constraints ->
                    val menu = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
                    val window = IntSize(constraints.maxWidth, constraints.maxHeight)
                    val bounds = anchor.translate(-origin.x.roundToInt(), -origin.y.roundToInt())
                    val at = below.position(bounds, window, IntSize(menu.width, menu.height))
                    layout(window.width, window.height) { menu.place(at) }
                }
            }
        }
    }
}

/** The open menu: [entries] in a card, the [current] one in bold; an entry pressed, or Escape, is [onClose] first. */
@Composable
private fun MenuColumn(
    entries: List<MenuEntry>,
    current: String?,
    onClose: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    // Escape closes it (B-75). The menu holds the focus itself, and reads the key before its entries.
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    Column(
        Modifier
            .testTag(LINK_MENU_TAG)
            .focusRequester(focus)
            .onPreviewKeyEvent {
                val escape = it.key == Key.Escape
                if (escape && it.type == KeyEventType.KeyDown) onClose()
                escape
            }.focusable()
            // A press on the card between its entries is the card's: it does not close the menu.
            .swallowsPresses()
            .width(IntrinsicSize.Max)
            .widthIn(min = 200.dp)
            .heightIn(max = 440.dp)
            .background(HaulColors.surfaceContainerLowest, shape)
            .border(1.dp, HaulColors.outlineVariant, shape)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
    ) {
        entries.forEach { entry ->
            // The entry already chosen has nothing to open — the sort's order shown — and closes the menu, as any
            // pick does (B-121).
            val press = entry.onPress ?: {}.takeIf { entry.label == current }
            Text(
                entry.label,
                HaulType.text(15f, if (entry.label == current) 700 else 500),
                Modifier
                    .fillMaxWidth()
                    .pressable(
                        press?.let {
                            {
                                onClose()
                                it()
                            }
                        },
                    ).padding(horizontal = 16.dp, vertical = 10.dp),
                softWrap = false,
            )
        }
    }
}

private val MENU_GAP = 8.dp

/** Under the control, its start edge (or its end edge) lined up with the menu's, kept inside the storefront. */
private class Below(
    private val gap: Int,
    private val alignEnd: Boolean,
) {
    fun position(
        anchorBounds: IntRect,
        windowSize: IntSize,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = if (alignEnd) anchorBounds.right - popupContentSize.width else anchorBounds.left
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        return IntOffset(x.coerceIn(0, maxX), anchorBounds.bottom + gap)
    }
}
