package io.github.youndie.haul.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// CSS's block flow, for pages whose long phone column drifts when each line box is rounded to a pixel on
// its own (B-18's order page, B-19's account): heights are summed as the browser sums them, unrounded,
// and rounded only where a block is placed.

/** A text's height in CSS: its lines, counted off its measured [height], times the [line] in dp, unrounded. */
internal fun Density.lines(
    height: Int,
    line: Float,
): Float {
    val px = line.dp.toPx()
    return (height / px).roundToInt() * px
}

/**
 * A block's height as the browser lays it out, finer than the whole pixel its layout reports — a line
 * box of 23.8 px is 23.8, and a column of them drifts a pixel every few lines when each is rounded.
 * [top] is the block's margin above it in its [CssColumn].
 */
internal class CssBox(
    val top: Dp = 0.dp,
    val carry: Boolean = false,
) {
    var height: Float = Float.NaN

    /**
     * Where in its first pixel a [carry] block starts — 0.4 for a block at 446.4, which its column draws
     * at 446 — set by the [CssColumn] it stands in before it is measured. A column or a layout of its own
     * places its children from it, so a tile at 446.4 + 372.4 is drawn at 819 as the browser draws it, not
     * at 446 + 372: rounding twice, once per level, loses up to a pixel a level (B-19's phone tiles).
     */
    var fraction: Float = 0f
}

/** This block stands in a [CssColumn] with [box]'s margin; its height is what [box] holds once measured, else its own. */
internal fun Modifier.css(box: CssBox): Modifier = layoutId(box)

/** Text on lines of [lineHeight]: its height in CSS is its line count times the line, unrounded. */
internal fun Modifier.cssLines(
    box: CssBox,
    lineHeight: Dp,
): Modifier =
    layoutId(box).layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        box.height = lines(placeable.height, lineHeight.value)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

/**
 * CSS's block flow: its children one under another, [gap] apart and each its own margin ([CssBox.top])
 * further, at positions summed from their CSS heights and rounded only where each is placed — as the
 * browser draws a box whose top falls at 631.2 at 631. [box], when given, receives the column's own CSS
 * height, [top] and [bottom] included, for the column it stands in.
 */
@Composable
internal fun CssColumn(
    modifier: Modifier = Modifier,
    gap: Dp = 0.dp,
    top: Dp = 0.dp,
    bottom: Dp = 0.dp,
    box: CssBox? = null,
    content: @Composable () -> Unit,
) {
    Layout(content, if (box == null) modifier else modifier.css(box)) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        // A carrying column starts where in its pixel it was placed, and keeps that out of its own height.
        val from = box?.takeIf { it.carry }?.fraction ?: 0f
        var y = top.toPx() + from
        val placed =
            measurables.mapIndexed { index, measurable ->
                val child = measurable.layoutId as? CssBox
                if (index > 0) y += gap.toPx()
                y += child?.top?.toPx() ?: 0f
                val at = y.roundToInt()
                if (child?.carry == true) child.fraction = y - at
                val placeable = measurable.measure(loose)
                y += child?.height?.takeUnless { it.isNaN() } ?: placeable.height.toFloat()
                placeable to at
            }
        y += bottom.toPx()
        box?.height = y - from
        // As wide as its widest child, as a column is: a child that fills the width makes it fill too.
        val width = (placed.maxOfOrNull { it.first.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        layout(width, y.roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)) {
            placed.forEach { (placeable, at) -> placeable.place(0, at) }
        }
    }
}
