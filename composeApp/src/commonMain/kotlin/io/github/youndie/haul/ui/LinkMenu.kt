package io.github.youndie.haul.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
 * no artboard draws a menu open, so closed is the only state the canvas has.
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
    var open by remember { mutableStateOf(false) }
    val usable = actions != null && links.any { it.action != null }
    Box(modifier, propagateMinConstraints = true) {
        control(Modifier.pressable(if (usable) ({ open = !open }) else null))
        if (open && actions != null) {
            val gap = with(LocalDensity.current) { MENU_GAP.roundToPx() }
            Popup(
                popupPositionProvider = Below(gap, alignEnd),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                val shape = RoundedCornerShape(12.dp)
                Column(
                    Modifier
                        .testTag(LINK_MENU_TAG)
                        .width(IntrinsicSize.Max)
                        .widthIn(min = 200.dp)
                        .heightIn(max = 440.dp)
                        .background(HaulColors.surfaceContainerLowest, shape)
                        .border(1.dp, HaulColors.outlineVariant, shape)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 6.dp),
                ) {
                    links.forEach { link ->
                        val action = link.action
                        Text(
                            link.label,
                            HaulType.text(15f, if (link.label == current) 700 else 500),
                            Modifier
                                .fillMaxWidth()
                                .pressable(
                                    action?.let {
                                        {
                                            open = false
                                            actions.handle(it)
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
