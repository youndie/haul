package io.github.youndie.haul.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.theme.LocalHaulCompact

/**
 * How a grid draws a card in the place [Modifier] gives it. The renderers of a screen draw each card as a
 * node of the tree, so an `update` of one card by its id — what «+» is answered with (B-63) — redraws that
 * card; `null` — a screenshot of a view on its own — draws [ProductCardView] directly, the same pixels.
 */
public val LocalCardNodes: ProvidableCompositionLocal<(@Composable (card: ProductCard, modifier: Modifier) -> Unit)?> =
    staticCompositionLocalOf { null }

/**
 * Product cards (`ProductGrid` on the wire): [ProductGrid.columns] across at 1440 (24 apart, rows 40
 * apart), two across on a phone (12 and 28) — or, for a [ProductGrid.scroll] row, 160-wide cards in
 * one row that runs off the right edge. [gutter] is the page's side padding when the grid stands on
 * the page itself; a grid inside another component passes none.
 *
 * Narrower than [ProductGrid.columns] cards of [MIN_CARD] hold (B-82: six across at 1024 left a card 135 px, its
 * price and «+» crowding the old price into a column of figures), the grid takes as many as fit: four at 1024,
 * cards of 214 px, near 1440's 204. Evening the rows out (two rows of three) was tried and drew 293 px cards.
 */
@Composable
public fun ProductGridView(
    grid: ProductGrid,
    gutter: Dp = 0.dp,
) {
    val compact = LocalHaulCompact.current
    val nodes = LocalCardNodes.current
    val card: @Composable (ProductCard, Modifier) -> Unit = nodes ?: { it, modifier -> ProductCardView(it, modifier) }
    if (compact && grid.scroll) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = gutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            grid.cards.forEach { card(it, Modifier.width(160.dp)) }
        }
        return
    }
    val gap = if (compact) 12.dp else 24.dp
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = gutter)) {
        val columns = if (compact) 2 else columnsIn(maxWidth, gap, grid.columns)
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 28.dp else 40.dp)) {
            grid.cards.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { card(it, Modifier.weight(1f)) }
                    repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** The columns a grid draws in [width]: [wanted], or as many cards of [MIN_CARD] [gap] apart as fit there. */
internal fun columnsIn(
    width: Dp,
    gap: Dp,
    wanted: Int,
): Int = minOf(wanted, ((width + gap) / (MIN_CARD + gap)).toInt().coerceAtLeast(1))

/** The narrowest desktop card whose price, old price and «+» share a line: «$349 $449 +» takes 146 px. */
internal val MIN_CARD = 168.dp
