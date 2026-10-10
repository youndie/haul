package io.github.youndie.haul.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType

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
 * entry drawn as chosen. With no entry to press, [control] is drawn as it is and opens nothing.
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
    val usable = entries.any { it.onPress != null }
    Box(modifier, propagateMinConstraints = true) {
        control(Modifier.pressable(if (usable) ({ open = !open }) else null))
        if (open && usable) {
            val gap = with(LocalDensity.current) { MENU_GAP.roundToPx() }
            Popup(
                popupPositionProvider = Below(gap, alignEnd),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                val shape = RoundedCornerShape(12.dp)
                // Escape closes it (B-75). The popup takes the focus but gives it to none of its nodes, so the
                // key reached nothing: the menu holds the focus itself, and reads the key before its entries.
                val focus = remember { FocusRequester() }
                LaunchedEffect(focus) { focus.requestFocus() }
                Column(
                    Modifier
                        .testTag(LINK_MENU_TAG)
                        .focusRequester(focus)
                        .onPreviewKeyEvent {
                            val escape = it.key == Key.Escape
                            if (escape && it.type == KeyEventType.KeyDown) open = false
                            escape
                        }.focusable()
                        .width(IntrinsicSize.Max)
                        .widthIn(min = 200.dp)
                        .heightIn(max = 440.dp)
                        .background(HaulColors.surfaceContainerLowest, shape)
                        .border(1.dp, HaulColors.outlineVariant, shape)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 6.dp),
                ) {
                    entries.forEach { entry ->
                        val press = entry.onPress
                        Text(
                            entry.label,
                            HaulType.text(15f, if (entry.label == current) 700 else 500),
                            Modifier
                                .fillMaxWidth()
                                .pressable(
                                    press?.let {
                                        {
                                            open = false
                                            it()
                                        }
                                    },
                                ).padding(horizontal = 16.dp, vertical = 10.dp),
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

private val MENU_GAP = 8.dp

/** Under the control, its start edge (or its end edge) lined up with the menu's, kept inside the window. */
private class Below(
    private val gap: Int,
    private val alignEnd: Boolean,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = if (alignEnd) anchorBounds.right - popupContentSize.width else anchorBounds.left
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        return IntOffset(x.coerceIn(0, maxX), anchorBounds.bottom + gap)
    }
}
