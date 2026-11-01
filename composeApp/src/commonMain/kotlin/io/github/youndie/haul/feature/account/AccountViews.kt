package io.github.youndie.haul.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.youndie.haul.feature.catalog.PaginationView
import io.github.youndie.haul.feature.order.StepColumns
import io.github.youndie.haul.feature.saved.SavedListView
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.LocalHaulCompact
import io.github.youndie.haul.theme.LocalHaulFonts
import io.github.youndie.haul.theme.LocalHaulMedium
import io.github.youndie.haul.ui.AccountBody
import io.github.youndie.haul.ui.AccountMenuItem
import io.github.youndie.haul.ui.AccountProfile
import io.github.youndie.haul.ui.AccountTile
import io.github.youndie.haul.ui.AccountTileKind
import io.github.youndie.haul.ui.ActiveOrder
import io.github.youndie.haul.ui.ActiveOrders
import io.github.youndie.haul.ui.EmptyStateView
import io.github.youndie.haul.ui.HaulButton
import io.github.youndie.haul.ui.HaulIcons
import io.github.youndie.haul.ui.HistoryFilter
import io.github.youndie.haul.ui.HistoryRow
import io.github.youndie.haul.ui.HistoryStatusKind
import io.github.youndie.haul.ui.Icon
import io.github.youndie.haul.ui.LineTile
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.OrderHistory
import io.github.youndie.haul.ui.Text
import io.github.youndie.haul.ui.accented
import io.github.youndie.haul.ui.following
import io.github.youndie.haul.ui.follows
import io.github.youndie.haul.ui.named
import io.github.youndie.haul.ui.normal
import io.github.youndie.haul.ui.pressable
import io.github.youndie.haul.ui.toneColor

// The Account screen (screen-account): the frame's header, then the profile and the menu in a column of
// their own at 1440 — a profile row and three tabs on a phone — beside the page: the overview's tiles,
// active orders and recent history, or the whole history under its filter. The numbers are the artboards'
// (Account_Content, _NotMember, _Orders, _NoOrders and their _Phone twins; the Saved list's, B-20, are
// `feature/saved/SavedViews.kt`'s). Links follow the tree's actions; «Reorder» on a row is a command for
// the renderer to send.

/** The account under the header. [onReorder] sends a row's «Reorder» (`HistoryRow.reorderUrl`). */
@Composable
public fun AccountBodyView(
    body: AccountBody,
    onReorder: (String) -> Unit = {},
) {
    val compact = LocalHaulCompact.current
    if (compact) {
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Profile(body.profile)
                Tabs(body.menu)
            }
            Main(body, onReorder, Modifier.fillMaxWidth())
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 40.dp, bottom = 96.dp)) {
            Column(Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Profile(body.profile)
                Menu(body.menu)
            }
            Spacer(Modifier.width(48.dp))
            Main(body, onReorder, Modifier.weight(1f))
        }
    }
}

/** The page beside the menu: the title, then the overview's sections or the history. */
@Composable
private fun Main(
    body: AccountBody,
    onReorder: (String) -> Unit,
    modifier: Modifier,
) {
    val compact = LocalHaulCompact.current
    val size = if (compact) 56f else 112f
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 32.dp else 40.dp)) {
        val heading =
            @Composable {
                Text(
                    accented(body.title, body.accent),
                    style =
                        HaulType.display(
                            size,
                            800,
                            letterSpacing = if (compact) -0.01f else -0.03f,
                            lineHeight = TITLE_LEADING,
                        ),
                    softWrap = false,
                )
            }
        val count = body.count
        if (count == null) {
            heading()
        } else {
            // «Saved 48»: the count in an Acid pill at the title's top, `margin-top: 10px` (6 on a phone).
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                heading()
                Text(
                    count,
                    HaulType.label(14f, 600, 0f).copy(color = HaulColors.onSecondaryContainer),
                    Modifier
                        .padding(top = if (compact) 6.dp else 10.dp)
                        .background(HaulColors.secondaryContainer, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    softWrap = false,
                )
            }
        }
        if (body.tiles.isNotEmpty()) Tiles(body.tiles)
        body.active?.let { Active(it) }
        body.history?.let { history ->
            if (history.title != null) {
                Recent(history, onReorder)
            } else {
                if (history.filters.isNotEmpty()) Filters(history.filters)
                history.empty?.let { EmptyStateView(it) }
                if (history.rows.isNotEmpty()) Rows(history.rows, onReorder)
                history.none?.let { Note(it) }
                history.pagination?.let { PaginationView(it) }
            }
        }
        body.saved?.let { SavedListView(it) }
    }
}

/** The avatar with the initials, the name and the line under it. */
@Composable
private fun Profile(profile: AccountProfile) {
    val compact = LocalHaulCompact.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(if (compact) 48.dp else 64.dp).background(toneColor(profile.tone), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                profile.initials,
                HaulType
                    .display(if (compact) 20f else 26f, 800, letterSpacing = -0.01f)
                    .copy(color = HaulColors.onTone(profile.tone)),
                softWrap = false,
            )
        }
        Column {
            // The name is a `<b>` in a 14 px block: its line is as tall as the larger of the two.
            Text(profile.name, normal(if (compact) 16f else 17f, 700), softWrap = false)
            Text(
                profile.subtitle,
                normal(14f).copy(color = HaulColors.outline),
                Modifier.padding(top = if (compact) 2.dp else 3.dp),
                softWrap = false,
            )
        }
    }
}

/** The side menu at 1440: the page shown on Ink, the active orders' count in a Cobalt badge. */
@Composable
private fun Menu(menu: List<AccountMenuItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        menu.forEach { item ->
            val content = if (item.selected) HaulColors.onPrimary else HaulColors.onSurface
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (item.selected) {
                            Modifier.background(
                                HaulColors.inverseSurface,
                                RoundedCornerShape(14.dp),
                            )
                        } else {
                            Modifier
                        },
                    ).follows(item.action, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.label, normal(16f, 500).copy(color = content), Modifier.weight(1f), softWrap = false)
                item.count?.let { count ->
                    if (item.badge) {
                        Badge(count, horizontal = 8.dp, vertical = 5.dp)
                    } else {
                        Text(count, normal(16f, 500).copy(color = HaulColors.outline), softWrap = false)
                    }
                }
            }
        }
    }
}

/**
 * The menu as three tabs on a phone: pills, the page shown on Ink, the counts in mono. A tab is `height:
 * 44px` in CSS's content box, so an outlined one is 46 with its border, and the row is as tall.
 */
@Composable
private fun Tabs(menu: List<AccountMenuItem>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        menu.forEach { item ->
            val shape = RoundedCornerShape(22.dp)
            Row(
                Modifier
                    .then(
                        if (item.selected) {
                            Modifier.height(44.dp).background(HaulColors.inverseSurface, shape)
                        } else {
                            Modifier
                                .height(46.dp)
                                .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(23.dp))
                                .border(1.dp, HaulColors.outlineVariant, RoundedCornerShape(23.dp))
                                .padding(1.dp)
                        },
                    ).follows(item.action, RoundedCornerShape(23.dp))
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.label,
                    normal(15f, 600).copy(color = if (item.selected) HaulColors.onPrimary else HaulColors.onSurface),
                    softWrap = false,
                )
                item.count?.let { count ->
                    if (item.badge) {
                        Badge(count, horizontal = 7.dp, vertical = 4.dp)
                    } else {
                        Text(count, HaulType.label(12f, 600, 0f).copy(color = HaulColors.outline), softWrap = false)
                    }
                }
            }
        }
    }
}

/** The active orders' count: JetBrains Mono 12 on a Cobalt pill. */
@Composable
private fun Badge(
    count: String,
    horizontal: Dp,
    vertical: Dp,
) {
    Text(
        count,
        HaulType.label(12f, 600, 0f).copy(color = HaulColors.onPrimary),
        Modifier
            .background(HaulColors.primary, CircleShape)
            .padding(horizontal = horizontal, vertical = vertical),
        softWrap = false,
    )
}

/**
 * The three tiles, as tall as the tallest at 1440 (a grid row stretches its cells), one under another on a
 * phone. A tile's fill is a child of its own, drawn at the row's height once the contents are measured: the
 * fill is what is pressed and named, its words are not (B-126), so the two are siblings, not a `Row` of boxes.
 */
@Composable
private fun Tiles(tiles: List<AccountTile>) {
    val compact = LocalHaulCompact.current
    // Below 1280 three tiles side by side are 189 px at 1024, a figure broken between its digits and the trial's
    // button cut (B-82): one under another, as on a phone.
    if (compact || LocalHaulMedium.current) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tiles.forEach { tile ->
                TileContent(
                    tile,
                    Modifier.fillMaxWidth().background(tile.fill(), TILE_SHAPE).follows(tile.action, TILE_SHAPE),
                )
            }
        }
    } else {
        Layout(
            content = {
                tiles.forEach { tile ->
                    // The fill is the tile's whole height, so it is what is pressed; its content, drawn over
                    // it, takes no press and lets one through to it (but the trial's button). The words are not in
                    // the press, so it is named by the tile's label (B-126).
                    Box(Modifier.background(tile.fill(), TILE_SHAPE).named(tile.label).follows(tile.action, TILE_SHAPE))
                    TileContent(tile, Modifier)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { measurables, constraints ->
            val gap = 16.dp.roundToPx()
            val count = (measurables.size / 2).coerceAtLeast(1)
            val width = ((constraints.maxWidth - gap * (count - 1)) / count).coerceAtLeast(0)
            val contents =
                measurables
                    .filterIndexed { index, _ -> index % 2 == 1 }
                    .map { it.measure(Constraints.fixedWidth(width)) }
            val height = contents.maxOfOrNull { it.height } ?: 0
            val fills =
                measurables
                    .filterIndexed { index, _ -> index % 2 == 0 }
                    .map { it.measure(Constraints.fixed(width, height)) }
            layout(constraints.maxWidth, height) {
                fills.forEachIndexed { index, placeable -> placeable.place(index * (width + gap), 0) }
                contents.forEachIndexed { index, placeable -> placeable.place(index * (width + gap), 0) }
            }
        }
    }
}

/** A tile's fill: Acid for the points, Cobalt for the membership and its offer, White for the price drops. */
@Composable
@ReadOnlyComposable
private fun AccountTile.fill(): Color =
    when (kind) {
        AccountTileKind.Points -> HaulColors.secondaryContainer
        AccountTileKind.Plus, AccountTileKind.PlusOffer -> HaulColors.primaryFixed
        AccountTileKind.PriceDrops -> HaulColors.surfaceContainerLowest
    }

/** A tile's content on its fill: the label, the figure, the sentence and, on the trial offer, its button. */
@Composable
private fun TileContent(
    tile: AccountTile,
    modifier: Modifier,
) {
    val compact = LocalHaulCompact.current
    val (ink, label) =
        when (tile.kind) {
            AccountTileKind.Points -> HaulColors.onSecondaryContainer to HaulColors.onSecondaryContainer
            AccountTileKind.Plus, AccountTileKind.PlusOffer -> HaulColors.onPrimaryFixed to HaulColors.secondaryContainer
            AccountTileKind.PriceDrops -> HaulColors.onSurface to HaulColors.outline
        }
    val offer = tile.kind == AccountTileKind.PlusOffer
    val size =
        when {
            offer -> if (compact) 44f else 52f
            else -> if (compact) 52f else 64f
        }
    Column(modifier.padding(if (compact) 22.dp else 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tile.label.uppercase(), HaulType.label(11f, 600, 0.08f).copy(color = label), softWrap = false)
        Text(
            accented(tile.figure, tile.accent, if (offer) HaulColors.secondaryContainer else Color.Unspecified),
            style =
                HaulType
                    .display(
                        size,
                        800,
                        letterSpacing = if (compact || offer) -0.01f else -0.03f,
                        lineHeight = TITLE_LEADING,
                    ).copy(color = ink),
        )
        Text(tile.text, normal(15f).copy(color = ink))
        tile.button?.let {
            HaulButton(
                it.label,
                Modifier.padding(top = 6.dp),
                height = 48.dp,
                radius = 14.dp,
                horizontal = 20.dp,
                fill = HaulColors.secondaryContainer,
                content = HaulColors.onSecondaryContainer,
                textSize = 15f,
                onClick = following(it.action),
            )
        }
    }
}

/** A section's title in Bodoni and, at its right on the same bottom line, its link. */
@Composable
private fun SectionHead(
    title: String,
    link: Link?,
) {
    val compact = LocalHaulCompact.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(
            title,
            HaulType.display(if (compact) 30f else 40f, 800, letterSpacing = -0.01f),
            Modifier.weight(1f),
            softWrap = false,
        )
        link?.let {
            Text(
                it.label,
                normal(15f, 600).copy(color = HaulColors.primary),
                Modifier.follows(it.action),
                softWrap = false,
            )
        }
    }
}

/** «Active orders»: the cards, or the sentence that there are none. */
@Composable
private fun Active(active: ActiveOrders) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SectionHead(active.title, null)
        if (active.orders.isEmpty()) {
            active.empty?.let { Note(it) }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                active.orders.forEach { Card(it) }
            }
        }
    }
}

/** A sentence on a white card: «No active orders right now.» */
@Composable
internal fun Note(
    text: String,
    modifier: Modifier = Modifier,
) {
    val compact = LocalHaulCompact.current
    Text(
        text,
        normal(16f).copy(color = HaulColors.onSurfaceVariant),
        modifier
            .fillMaxWidth()
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(24.dp))
            .padding(horizontal = if (compact) 20.dp else 32.dp, vertical = if (compact) 22.dp else 28.dp),
    )
}

/**
 * An order on its way: the meta line, the title and the lead beside the tiles or the pickup code at 1440
 * (a grid of two columns, the row centred), under each other on a phone; then the steps and «Details». A press
 * anywhere on the card opens the order's page, as a press on a history row does (B-108).
 */
@Composable
private fun Card(order: ActiveOrder) {
    val compact = LocalHaulCompact.current
    val titleSize = if (compact) 28f else 36f
    val heading: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier) {
            Text(
                order.meta,
                HaulType.label(12f, 500, 0.04f).copy(color = HaulColors.outline, lineHeight = (12f * META_LEADING).sp),
            )
            Text(
                accented(order.title, order.accent),
                Modifier.padding(top = 12.dp),
                style = HaulType.display(titleSize, 800, letterSpacing = -0.01f),
            )
            order.lead?.let {
                Text(
                    it,
                    HaulType.text(15f, lineHeight = LEAD_LEADING).copy(color = HaulColors.onSurfaceVariant),
                    Modifier.padding(top = 10.dp),
                )
            }
        }
    }
    val aside: @Composable (Modifier) -> Unit = { modifier ->
        val pickup = order.pickup
        if (pickup != null) {
            Column(
                modifier
                    .background(HaulColors.secondaryContainer, RoundedCornerShape(18.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val ink = HaulColors.onSecondaryContainer
                Text(pickup.label.uppercase(), HaulType.label(11f, 600, 0.08f).copy(color = ink), softWrap = false)
                Text(
                    pickup.code,
                    HaulType.display(44f, 800, letterSpacing = 0.04f).copy(color = ink),
                    softWrap = false,
                )
            }
        } else {
            Tones(order.tones, order.images, if (compact) 56.dp else 64.dp, 14.dp, 8.dp, modifier)
        }
    }
    val details: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(order.details.label, normal(15f, 600).copy(color = HaulColors.primary), softWrap = false)
                Icon(HaulIcons.chevronRight, 16.dp, HaulColors.primary)
            }
        }
    }
    // The whole card opens the order, as a history row does (B-74, B-108); «Details ›» says what the press does.
    Column(
        Modifier
            .fillMaxWidth()
            .follows(order.details.action, RoundedCornerShape(24.dp))
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(24.dp))
            .padding(horizontal = if (compact) 20.dp else 32.dp, vertical = if (compact) 22.dp else 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (compact) {
            heading(Modifier.fillMaxWidth())
            aside(if (order.pickup != null) Modifier.fillMaxWidth() else Modifier)
        } else {
            // CSS's `grid-template-columns: minmax(0, 1fr) auto; align-items: center`: the aside as wide as it
            // is, the heading the rest, both centred on the taller.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                heading(Modifier.weight(1f))
                Spacer(Modifier.width(40.dp))
                aside(Modifier)
            }
        }
        order.steps?.let { StepColumns(it, Modifier.fillMaxWidth()) }
        details()
    }
}

/**
 * An order's tiles: one square per line, its product's photo or picture ([images], B-149) over its tone, as the
 * order's page draws its lines.
 */
@Composable
private fun Tones(
    tones: List<String>,
    images: List<String?>,
    size: Dp,
    radius: Dp,
    gap: Dp,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        tones.forEachIndexed { index, tone -> LineTile(images.getOrNull(index), tone, radius, Modifier.size(size)) }
    }
}

/** «Order history» with «All orders», and its rows. */
@Composable
private fun Recent(
    history: OrderHistory,
    onReorder: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SectionHead(history.title.orEmpty(), history.all)
        Rows(history.rows, onReorder)
    }
}

/** The filter chips: wrapping at 1440; on a phone one row running off the screen's edge, cut there. */
@Composable
private fun Filters(filters: List<HistoryFilter>) {
    val compact = LocalHaulCompact.current
    if (compact) {
        Row(
            Modifier
                // `margin-right: -16px`: the row reaches the screen's edge, past the page's gutter.
                .layout { measurable, constraints ->
                    val bleed = 16.dp.roundToPx()
                    val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + bleed))
                    layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
                }.horizontalScroll(rememberScrollState(), enabled = false),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) { filters.forEach { Chip(it) } }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { filters.forEach { Chip(it) } }
    }
}

/**
 * A chip: the label and its count in mono; the one shown on Ink with an Acid count, the others outlined.
 * The flex row stretches the one without a border to its neighbours' height: its text keeps its padding
 * at the top, and the two pixels the borders add go under it. The history's chips are smaller on a phone
 * ([horizontal], [vertical]); the Saved list's keep the desktop's padding.
 */
@Composable
internal fun Chip(
    filter: HistoryFilter,
    horizontal: Dp = if (LocalHaulCompact.current) 16.dp else 18.dp,
    vertical: Dp = if (LocalHaulCompact.current) 10.dp else 11.dp,
) {
    val compact = LocalHaulCompact.current
    val shape = CircleShape
    val size = if (compact) 14f else 15f
    val mono = LocalHaulFonts.current.mono
    Text(
        buildAnnotatedString {
            append(filter.label)
            append(" ")
            withStyle(
                SpanStyle(
                    fontFamily = mono,
                    fontSize = 12.sp,
                    fontWeight = FontWeight(500),
                    letterSpacing = 0.em,
                    color = if (filter.selected) HaulColors.inverseAccent else HaulColors.outline,
                ),
            ) { append(filter.count) }
        },
        Modifier
            .then(
                if (filter.selected) {
                    Modifier.background(HaulColors.inverseSurface, shape)
                } else {
                    Modifier
                        .background(HaulColors.surfaceContainerLowest, shape)
                        .border(1.dp, HaulColors.outlineVariant, shape)
                        .padding(1.dp)
                },
            ).follows(filter.action, shape)
            .padding(
                start = horizontal,
                end = horizontal,
                top = vertical,
                bottom = vertical + (if (filter.selected) 2.dp else 0.dp),
            ),
        style =
            normal(size, if (filter.selected) 600 else 500)
                .copy(color = if (filter.selected) HaulColors.onPrimary else HaulColors.onSurface),
        softWrap = false,
    )
}

/** The rows on one white card, a hairline under each but the last. */
@Composable
private fun Rows(
    rows: List<HistoryRow>,
    onReorder: (String) -> Unit,
) {
    val compact = LocalHaulCompact.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(24.dp))
            .padding(horizontal = if (compact) 16.dp else 32.dp, vertical = if (compact) 4.dp else 8.dp),
    ) {
        // Below 1280 the fixed columns of a wide row are wider than the card and cut «Reorder» (B-82): the phone's
        // row, a grid of two columns, fits any width.
        val narrow = compact || LocalHaulMedium.current
        rows.forEachIndexed { index, row ->
            val last = index == rows.lastIndex
            if (narrow) {
                PhoneRow(row, onReorder, last)
            } else {
                Column(Modifier.fillMaxWidth()) {
                    WideRow(row, onReorder)
                    if (!last) Hairline()
                }
            }
        }
    }
}

/** A row at 1440: the day, the number, the tiles, the total, the status and the way on, in fixed columns. */
@Composable
private fun WideRow(
    row: HistoryRow,
    onReorder: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().follows(row.action).padding(vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.date, normal(15f).copy(color = HaulColors.outline), Modifier.width(100.dp), softWrap = false)
        Text(row.number, HaulType.label(13f, 500, 0f), Modifier.width(150.dp), softWrap = false)
        Tones(row.tones, row.images, 44.dp, 10.dp, 6.dp, Modifier.weight(1f))
        Text(row.total, normal(15f, 600), Modifier.width(110.dp), softWrap = false)
        Box(Modifier.width(150.dp)) { Status(row) }
        Box(Modifier.width(80.dp), contentAlignment = Alignment.CenterEnd) { Action(row, onReorder) }
    }
}

/**
 * A row on a phone, a grid of two columns: the number over the day and the total, the status beside them;
 * the tiles, the way on beside them. The right column is as wide as the wider of the status and the way
 * on; the status starts it, the way on ends it — a column two rows share, which no `Row` of `Column`s says,
 * hence a layout of its own.
 */
@Composable
private fun PhoneRow(
    row: HistoryRow,
    onReorder: (String) -> Unit,
    last: Boolean,
) {
    Layout(
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    row.number,
                    HaulType.label(12f, 500, 0f).copy(lineHeight = (12f * NUMBER_LEADING).sp),
                    softWrap = false,
                )
                Text(
                    buildAnnotatedString {
                        append(row.date + " · ")
                        withStyle(
                            SpanStyle(color = HaulColors.onSurface, fontWeight = FontWeight(600)),
                        ) { append(row.total) }
                    },
                    style = normal(14f).copy(color = HaulColors.outline),
                    softWrap = false,
                )
            }
            Status(row)
            Tones(row.tones, row.images, 44.dp, 10.dp, 6.dp)
            Action(row, onReorder)
            Hairline()
        },
        modifier = Modifier.fillMaxWidth().follows(row.action),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = 10.dp.roundToPx()
        val status = measurables[1].measure(loose)
        val action = measurables[3].measure(loose)
        val right = maxOf(status.width, action.width)
        val leftWidth = (constraints.maxWidth - right - gap).coerceAtLeast(0)
        val text = measurables[0].measure(loose.copy(maxWidth = leftWidth))
        val tiles = measurables[2].measure(loose.copy(maxWidth = leftWidth))
        val rule = measurables[4].measure(Constraints.fixedWidth(constraints.maxWidth))
        // `padding: 16px 0`, then the two grid rows `gap: 10px` apart, then the hairline under all but the last.
        val top = 16.dp.roundToPx()
        val first = maxOf(text.height, status.height)
        val secondTop = top + first + gap
        val second = maxOf(tiles.height, action.height)
        val bottom = secondTop + second + top
        val height = bottom + if (last) 0 else rule.height
        // Each cell centred in its grid row.
        val centred = Alignment.CenterVertically
        layout(constraints.maxWidth, height) {
            text.place(0, top + centred.align(text.height, first))
            status.place(constraints.maxWidth - right, top + centred.align(status.height, first))
            tiles.place(0, secondTop + centred.align(tiles.height, second))
            action.place(constraints.maxWidth - action.width, secondTop + centred.align(action.height, second))
            if (!last) rule.place(0, bottom)
        }
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(HaulColors.outlineVariant))
}

/** The status in a chip: Acid while it is on its way, Blush once returned, Paper otherwise. */
@Composable
private fun Status(row: HistoryRow) {
    val fill =
        when (row.statusKind) {
            HistoryStatusKind.Active -> HaulColors.secondaryContainer
            HistoryStatusKind.Returned -> HaulColors.errorContainer
            HistoryStatusKind.Done -> HaulColors.background
        }
    val ink = if (row.statusKind == HistoryStatusKind.Active) HaulColors.onSecondaryContainer else HaulColors.onSurface
    Text(
        row.status,
        normal(13f, 600).copy(color = ink),
        Modifier.background(fill, CircleShape).padding(horizontal = 10.dp, vertical = 6.dp),
        softWrap = false,
    )
}

/**
 * «Track» or «Details» say what a press on the row does — open the order's page, the row's action; «Reorder»
 * sends its command instead, and a press on it goes no further than it.
 */
@Composable
private fun Action(
    row: HistoryRow,
    onReorder: (String) -> Unit,
) {
    val reorder = row.reorderUrl
    Text(
        row.actionLabel,
        normal(15f, 600).copy(color = HaulColors.primary),
        if (reorder != null) Modifier.testTag(REORDER_TAG + row.id).pressable { onReorder(reorder) } else Modifier,
        softWrap = false,
    )
}

/** The titles' and the tiles' figures' `line-height: .9`. */
private const val TITLE_LEADING = 0.9f

/** An order card's meta line: JetBrains Mono 12 on 1.4. */
private const val META_LEADING = 1.4f

/** The pickup card's lead: `line-height: 1.45`. */
private const val LEAD_LEADING = 1.45f

/** A phone row's order number: JetBrains Mono 12 on 1.3. */
private const val NUMBER_LEADING = 1.3f

private val TILE_SHAPE = RoundedCornerShape(24.dp)

/** «Reorder» on a row, followed by the row's id, for the tests that press it. */
internal const val REORDER_TAG: String = "account-reorder-"
