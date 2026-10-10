package io.github.youndie.haul.feature.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.feature.account.Chip
import io.github.youndie.haul.feature.account.Note
import io.github.youndie.haul.feature.catalog.PaginationView
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.LocalHaulCompact
import io.github.youndie.haul.ui.EmptyStateView
import io.github.youndie.haul.ui.Heart
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.ProductGridView
import io.github.youndie.haul.ui.SavedList
import io.github.youndie.haul.ui.SavedStep
import io.github.youndie.haul.ui.Text
import io.github.youndie.haul.ui.follows
import io.github.youndie.haul.ui.hatching
import io.github.youndie.haul.ui.normal
import io.github.youndie.haul.ui.toneColor
import io.github.youndie.kompot.KompotAction

// The Saved list (screen-saved, B-20) under the account's title «Saved 48»: the filter, four cards to a row
// at 1440 and two on a phone, the page numbers; or nothing saved, the empty state and how the heart works.
// The numbers are the artboards' (Saved_Content, _PriceDrops, _Empty and their _Phone twins). The cards
// are the product cards of every grid, their heart filled and «Price dropped» marked by the tree; the
// chips are the orders' history's, with the desktop's padding at both widths.

/** The list's part of the account's page: each piece one block of the page's column, 40 apart (32 on a phone). */
@Composable
internal fun SavedListView(list: SavedList) {
    if (list.filters.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            list.filters.forEach { Chip(it, horizontal = 18.dp, vertical = 11.dp) }
        }
    }
    if (list.cards.isNotEmpty()) ProductGridView(ProductGrid("saved", list.cards, columns = COLUMNS))
    list.none?.let { Note(it) }
    list.pagination?.let { PaginationView(it) }
    list.empty?.let { EmptyStateView(it) }
    if (list.steps.isNotEmpty()) Steps(list.steps, list.empty?.action)
}

/**
 * How the heart works: a tile with a saved heart beside three cards at 1440, as tall as the tile; on a
 * phone the tile and the cards one under another. The tile's heart is drawn as a card's, so it is pressed
 * like one would be: it goes where the empty state's own way on does ([browse], «Browse deals»), to the
 * products whose hearts fill this list (B-74).
 */
@Composable
private fun Steps(
    steps: List<SavedStep>,
    browse: KompotAction?,
) {
    val compact = LocalHaulCompact.current
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Illustration(Modifier.width(160.dp).testTag(SAVED_ILLUSTRATION_TAG).follows(browse))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                steps.forEach { Step(it, Modifier.fillMaxWidth()) }
            }
        }
    } else {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Illustration(Modifier.width(200.dp).testTag(SAVED_ILLUSTRATION_TAG).follows(browse))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                steps.forEach { Step(it, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

/** A product tile with its heart saved, ringed in Cobalt: what a press on a card's heart leaves. */
@Composable
private fun Illustration(modifier: Modifier) {
    Box(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(toneColor(ILLUSTRATION_TONE))
                .hatching(),
        )
        // `box-shadow: 0 0 0 3px` Cobalt: a ring outside the 40 px circle, which sits 10 px from the corner.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp - RING)
                .size(40.dp + RING * 2)
                .background(HaulColors.primary, CircleShape)
                .padding(RING)
                .background(HaulColors.surfaceContainerLowest, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Heart(saved = true, size = 20.dp) }
    }
}

/** A white card: the step's number in Cobalt mono, its title, and the sentence under it. */
@Composable
private fun Step(
    step: SavedStep,
    modifier: Modifier,
) {
    Column(
        modifier
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(20.dp))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(step.number, HaulType.label(12f, 600, 0f).copy(color = HaulColors.primary), softWrap = false)
        // The browser gives a card 258.67 px and its title 214.67 of them; Compose's whole-pixel weights
        // give the first card 258, and «Tap the heart on any product» — 214.5 px — broke onto two lines.
        Text(step.title, normal(16f, 700), Modifier.subPixelSlack())
        Text(step.text, HaulType.text(14f, lineHeight = 1.45f).copy(color = HaulColors.outline))
    }
}

/** Measured one pixel wider than it is given, as the browser's fractional box allows; never drawn wider. */
private fun Modifier.subPixelSlack(): Modifier =
    layout { measurable, constraints ->
        val loose =
            if (constraints.hasBoundedWidth) constraints.copy(maxWidth = constraints.maxWidth + 1) else constraints
        val placeable = measurable.measure(loose)
        val width = placeable.width.coerceAtMost(constraints.maxWidth)
        layout(width, placeable.height) { placeable.place(0, 0) }
    }

/** Four cards to a row at 1440; the grid draws two on a phone. */
private const val COLUMNS = 4

/** The illustration's tile: the canvas's lavender tile tone (canvas.json `tileTones`). */
private const val ILLUSTRATION_TONE = "#E6E4FF"

private val RING = 3.dp

/** The empty list's illustration, the tile with the saved heart, for the tests that press it. */
internal const val SAVED_ILLUSTRATION_TAG: String = "saved-illustration"
