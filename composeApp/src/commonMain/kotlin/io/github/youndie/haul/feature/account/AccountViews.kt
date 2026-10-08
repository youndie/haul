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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
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
import io.github.youndie.haul.feature.order.StepColumns
import io.github.youndie.haul.feature.saved.SavedListView
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.LocalHaulCompact
import io.github.youndie.haul.theme.LocalHaulFonts
import io.github.youndie.haul.ui.AccountBody
import io.github.youndie.haul.ui.AccountMenuItem
import io.github.youndie.haul.ui.AccountProfile
import io.github.youndie.haul.ui.AccountTile
import io.github.youndie.haul.ui.AccountTileKind
import io.github.youndie.haul.ui.ActiveOrder
import io.github.youndie.haul.ui.ActiveOrders
import io.github.youndie.haul.ui.CssBox
import io.github.youndie.haul.ui.CssColumn
import io.github.youndie.haul.ui.EmptyStateView
import io.github.youndie.haul.ui.HaulButton
import io.github.youndie.haul.ui.HaulIcons
import io.github.youndie.haul.ui.HistoryFilter
import io.github.youndie.haul.ui.HistoryRow
import io.github.youndie.haul.ui.HistoryStatusKind
import io.github.youndie.haul.ui.Icon
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.OrderHistory
import io.github.youndie.haul.ui.Text
import io.github.youndie.haul.ui.accented
import io.github.youndie.haul.ui.css
import io.github.youndie.haul.ui.cssLines
import io.github.youndie.haul.ui.following
import io.github.youndie.haul.ui.follows
import io.github.youndie.haul.ui.normal
import io.github.youndie.haul.ui.pressable
import io.github.youndie.haul.ui.toneColor
import kotlin.math.roundToInt

// The Account screen (screen-account): the frame's header, then the profile and the menu in a column of
// their own at 1440 — a profile row and three tabs on a phone — beside the page: the overview's tiles,
// active orders and recent history, or the whole history under its filter. The numbers are the artboards'
// (Account_Content, _NotMember, _Orders, _NoOrders and their _Phone twins; the Saved list's, B-20, are
// `feature/saved/SavedViews.kt`'s). Links follow the tree's actions; «Reorder» on a row is a command for
// the renderer to send. The page lays out on the browser's fractional line boxes ([CssColumn]), as the
// order page does: a phone column is long enough to drift.

/** The account under the header. [onReorder] sends a row's «Reorder» (`HistoryRow.reorderUrl`). */
@Composable
public fun AccountBodyView(
    body: AccountBody,
    onReorder: (String) -> Unit = {},
) {
    val compact = LocalHaulCompact.current
    if (compact) {
        val head = remember { CssBox() }
        val main = remember { CssBox(24.dp, carry = true) }
        CssColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), top = 24.dp, bottom = 64.dp) {
            Column(Modifier.css(head), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Profile(body.profile)
                Tabs(body.menu)
            }
            Main(body, onReorder, Modifier.fillMaxWidth(), main)
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 40.dp, bottom = 96.dp)) {
            Column(Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Profile(body.profile)
                Menu(body.menu)
            }
            Spacer(Modifier.width(48.dp))
            Main(body, onReorder, Modifier.weight(1f), remember { CssBox(carry = true) })
        }
    }
}

/** The page beside the menu: the title, then the overview's sections or the history. */
@Composable
private fun Main(
    body: AccountBody,
    onReorder: (String) -> Unit,
    modifier: Modifier,
    box: CssBox,
) {
    val compact = LocalHaulCompact.current
    val title = remember { CssBox() }
    val size = if (compact) 56f else 112f
    CssColumn(modifier, gap = if (compact) 32.dp else 40.dp, box = box) {
        val heading =
            @Composable { textModifier: Modifier ->
                Text(
                    accented(body.title, body.accent),
                    textModifier.cssLines(title, (size * TITLE_LEADING).dp),
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
            heading(Modifier)
        } else {
            // «Saved 48»: the count in an Acid pill at the title's top, `margin-top: 10px` (6 on a phone).
            Row(Modifier.css(title), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                heading(Modifier)
                Text(
                    count,
                    HaulType.label(14f, 600, 0f),
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
                if (history.rows.isNotEmpty()) Rows(history.rows, onReorder, remember { CssBox(carry = true) })
                history.none?.let { Note(it) }
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
                HaulType.display(if (compact) 20f else 26f, 800, letterSpacing = -0.01f),
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
                    ).follows(item.action)
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
                    ).follows(item.action)
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
 * phone. A tile's fill is a child of its own, drawn at the row's height once the contents are measured.
 */
@Composable
private fun Tiles(tiles: List<AccountTile>) {
    val compact = LocalHaulCompact.current
    val boxes = remember(tiles.size) { List(tiles.size) { CssBox(carry = true) } }
    val row = remember { CssBox(carry = true) }
    if (compact) {
        CssColumn(Modifier.fillMaxWidth(), gap = 12.dp, box = row) {
            tiles.forEachIndexed { index, tile ->
                TileContent(tile, Modifier.fillMaxWidth().background(tile.fill(), TILE_SHAPE), boxes[index])
            }
        }
    } else {
        Layout(
            content = {
                tiles.forEachIndexed { index, tile ->
                    Box(Modifier.background(tile.fill(), TILE_SHAPE))
                    TileContent(tile, Modifier, boxes[index])
                }
            },
            modifier = Modifier.fillMaxWidth().layoutId(row),
        ) { measurables, constraints ->
            val gap = 16.dp.roundToPx()
            val count = (measurables.size / 2).coerceAtLeast(1)
            val width = ((constraints.maxWidth - gap * (count - 1)) / count).coerceAtLeast(0)
            // Each tile starts where the row does, in its pixel too.
            boxes.forEach { it.fraction = row.fraction }
            val contents =
                measurables
                    .filterIndexed {
                        index,
                        _,
                        ->
                        index % 2 == 1
                    }.map { it.measure(Constraints.fixedWidth(width)) }
            // The row is as tall as the tallest tile in CSS — 176.6, not the 177 its layout rounds to.
            val css = boxes.maxOfOrNull { it.height.takeUnless(Float::isNaN) ?: 0f } ?: 0f
            row.height = css
            val height = (row.fraction + css).roundToInt()
            val fills =
                measurables
                    .filterIndexed {
                        index,
                        _,
                        ->
                        index % 2 == 0
                    }.map { it.measure(Constraints.fixed(width, height)) }
            layout(constraints.maxWidth, height) {
                fills.forEachIndexed { index, placeable -> placeable.place(index * (width + gap), 0) }
                contents.forEachIndexed { index, placeable -> placeable.place(index * (width + gap), 0) }
            }
        }
    }
}

/** A tile's fill: Acid for the points, Cobalt for the membership and its offer, White for the price drops. */
private fun AccountTile.fill(): Color =
    when (kind) {
        AccountTileKind.Points -> HaulColors.secondaryContainer
        AccountTileKind.Plus, AccountTileKind.PlusOffer -> HaulColors.primary
        AccountTileKind.PriceDrops -> HaulColors.surfaceContainerLowest
    }

/** A tile's content on its fill: the label, the figure, the sentence and, on the trial offer, its button. */
@Composable
private fun TileContent(
    tile: AccountTile,
    modifier: Modifier,
    box: CssBox,
) {
    val compact = LocalHaulCompact.current
    val (ink, label) =
        when (tile.kind) {
            AccountTileKind.Points -> HaulColors.onSurface to HaulColors.onSurface
            AccountTileKind.Plus, AccountTileKind.PlusOffer -> HaulColors.onPrimary to HaulColors.secondaryContainer
            AccountTileKind.PriceDrops -> HaulColors.onSurface to HaulColors.outline
        }
    val offer = tile.kind == AccountTileKind.PlusOffer
    val size =
        when {
            offer -> if (compact) 44f else 52f
            else -> if (compact) 52f else 64f
        }
    val figure = remember { CssBox() }
    val button = remember { CssBox(6.dp) }
    val padding = if (compact) 22.dp else 28.dp
    CssColumn(
        modifier.padding(horizontal = padding),
        gap = 10.dp,
        top = padding,
        bottom = padding,
        box = box,
    ) {
        Text(tile.label.uppercase(), HaulType.label(11f, 600, 0.08f).copy(color = label), softWrap = false)
        Text(
            accented(tile.figure, tile.accent, if (offer) HaulColors.secondaryContainer else Color.Unspecified),
            Modifier.cssLines(figure, (size * TITLE_LEADING).dp),
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
                Modifier.css(button),
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
    val cards = remember { CssBox(20.dp, carry = true) }
    CssColumn(Modifier.fillMaxWidth(), box = remember { CssBox(carry = true) }) {
        SectionHead(active.title, null)
        if (active.orders.isEmpty()) {
            active.empty?.let { Note(it, Modifier.css(cards)) }
        } else {
            CssColumn(Modifier.fillMaxWidth(), gap = 16.dp, box = cards) { active.orders.forEach { Card(it) } }
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
 * (a grid of two columns, the row centred), under each other on a phone; then the steps and «Details».
 */
@Composable
private fun Card(order: ActiveOrder) {
    val compact = LocalHaulCompact.current
    val text = remember { CssBox(carry = true) }
    val pair = remember { CssBox(carry = true) }
    val meta = remember { CssBox() }
    val title = remember { CssBox(12.dp) }
    val lead = remember { CssBox(10.dp) }
    val card = remember { CssBox(carry = true) }
    val titleSize = if (compact) 28f else 36f
    val heading: @Composable (Modifier) -> Unit = { modifier ->
        CssColumn(modifier, box = text) {
            Text(
                order.meta,
                HaulType.label(12f, 500, 0.04f).copy(color = HaulColors.outline, lineHeight = (12f * META_LEADING).sp),
                Modifier.cssLines(meta, (12f * META_LEADING).dp),
            )
            Text(
                accented(order.title, order.accent),
                Modifier.cssLines(title, titleSize.dp),
                style = HaulType.display(titleSize, 800, letterSpacing = -0.01f),
            )
            order.lead?.let {
                Text(
                    it,
                    HaulType.text(15f, lineHeight = LEAD_LEADING).copy(color = HaulColors.onSurfaceVariant),
                    Modifier.cssLines(lead, (15f * LEAD_LEADING).dp),
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
                Text(pickup.label.uppercase(), HaulType.label(11f, 600, 0.08f), softWrap = false)
                Text(pickup.code, HaulType.display(44f, 800, letterSpacing = 0.04f), softWrap = false)
            }
        } else {
            Tones(order.tones, if (compact) 56.dp else 64.dp, 14.dp, 8.dp, modifier)
        }
    }
    val details: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Row(
                Modifier.follows(order.details.action),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(order.details.label, normal(15f, 600).copy(color = HaulColors.primary), softWrap = false)
                Icon(HaulIcons.chevronRight, 16.dp, HaulColors.primary)
            }
        }
    }
    CssColumn(
        Modifier
            .fillMaxWidth()
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(24.dp))
            .padding(horizontal = if (compact) 20.dp else 32.dp),
        gap = 24.dp,
        top = if (compact) 22.dp else 28.dp,
        bottom = if (compact) 22.dp else 28.dp,
        box = card,
    ) {
        if (compact) {
            heading(Modifier.fillMaxWidth())
            aside(if (order.pickup != null) Modifier.fillMaxWidth() else Modifier)
        } else {
            GridPair(heading, aside, gap = 40.dp, firstBox = text, box = pair)
        }
        order.steps?.let { StepColumns(it, Modifier.fillMaxWidth()) }
        details()
    }
}

/** A line's tiles: one square of its product's tone per line. */
@Composable
private fun Tones(
    tones: List<String>,
    size: Dp,
    radius: Dp,
    gap: Dp,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        tones.forEach { Box(Modifier.size(size).background(toneColor(it), RoundedCornerShape(radius))) }
    }
}

/**
 * CSS's `grid-template-columns: minmax(0, 1fr) auto; align-items: center` for one row: [second] as wide
 * as it is, [first] the rest, both centred on the taller — counted in [first]'s CSS height ([firstBox]),
 * so a 64.8 px heading beside 64 px tiles makes a 64.8 px row.
 */
@Composable
private fun GridPair(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
    gap: Dp,
    firstBox: CssBox,
    box: CssBox,
) {
    Layout(
        content = {
            first(Modifier)
            second(Modifier)
        },
        modifier = Modifier.fillMaxWidth().layoutId(box),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val right = measurables[1].measure(loose)
        val gapPx = gap.roundToPx()
        // The heading's own column starts where the row does; centring it moves it by whole pixels only
        // when the tiles are the taller, which a heading of a meta line and a title never is.
        firstBox.fraction = box.fraction
        val left =
            measurables[0].measure(
                loose.copy(maxWidth = (constraints.maxWidth - right.width - gapPx).coerceAtLeast(0)),
            )
        val leftHeight = firstBox.height.takeUnless { it.isNaN() } ?: left.height.toFloat()
        val height = maxOf(leftHeight, right.height.toFloat())
        box.height = height
        val from = box.fraction
        layout(constraints.maxWidth, (from + height).roundToInt()) {
            left.place(0, (from + (height - leftHeight) / 2).roundToInt())
            right.place(constraints.maxWidth - right.width, (from + (height - right.height) / 2).roundToInt())
        }
    }
}

/** «Order history» with «All orders», and its rows. */
@Composable
private fun Recent(
    history: OrderHistory,
    onReorder: (String) -> Unit,
) {
    val rows = remember { CssBox(20.dp, carry = true) }
    CssColumn(Modifier.fillMaxWidth(), box = remember { CssBox(carry = true) }) {
        SectionHead(history.title.orEmpty(), history.all)
        Rows(history.rows, onReorder, rows)
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
                    color = if (filter.selected) HaulColors.secondaryContainer else HaulColors.outline,
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
            ).follows(filter.action)
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
    box: CssBox,
) {
    val compact = LocalHaulCompact.current
    CssColumn(
        Modifier
            .fillMaxWidth()
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(24.dp))
            .padding(horizontal = if (compact) 16.dp else 32.dp),
        top = if (compact) 4.dp else 8.dp,
        bottom = if (compact) 4.dp else 8.dp,
        box = box,
    ) {
        rows.forEachIndexed { index, row ->
            val last = index == rows.lastIndex
            if (compact) {
                PhoneRow(row, onReorder, last, remember { CssBox(carry = true) })
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
        Modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.date, normal(15f).copy(color = HaulColors.outline), Modifier.width(100.dp), softWrap = false)
        Text(row.number, HaulType.label(13f, 500, 0f), Modifier.width(150.dp), softWrap = false)
        Tones(row.tones, 44.dp, 10.dp, 6.dp, Modifier.weight(1f))
        Text(row.total, normal(15f, 600), Modifier.width(110.dp), softWrap = false)
        Box(Modifier.width(150.dp)) { Status(row) }
        Box(Modifier.width(80.dp), contentAlignment = Alignment.CenterEnd) { Action(row, onReorder) }
    }
}

/**
 * A row on a phone, a grid of two columns: the number over the day and the total, the status beside them;
 * the tiles, the way on beside them. The right column is as wide as the wider of the status and the way
 * on; the status starts it, the way on ends it.
 */
@Composable
private fun PhoneRow(
    row: HistoryRow,
    onReorder: (String) -> Unit,
    last: Boolean,
    box: CssBox,
) {
    val number = remember { CssBox() }
    val info = remember { CssBox() }
    val gap = 10.dp
    Layout(
        content = {
            CssColumn(gap = 6.dp, box = info) {
                Text(
                    row.number,
                    HaulType.label(12f, 500, 0f).copy(lineHeight = (12f * NUMBER_LEADING).sp),
                    Modifier.cssLines(number, (12f * NUMBER_LEADING).dp),
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
            Tones(row.tones, 44.dp, 10.dp, 6.dp)
            Action(row, onReorder)
            Hairline()
        },
        modifier = Modifier.fillMaxWidth().layoutId(box),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val status = measurables[1].measure(loose)
        val action = measurables[3].measure(loose)
        val right = maxOf(status.width, action.width)
        val leftWidth = (constraints.maxWidth - right - gap.roundToPx()).coerceAtLeast(0)
        val text = measurables[0].measure(loose.copy(maxWidth = leftWidth))
        val tiles = measurables[2].measure(loose.copy(maxWidth = leftWidth))
        val rule = measurables[4].measure(Constraints.fixedWidth(constraints.maxWidth))
        // `padding: 16px 0`, then the two grid rows `gap: 10px` apart, then the hairline under all but the last.
        val pad = 16.dp.toPx()
        val top = box.fraction + pad
        val textHeight = info.height.takeUnless { it.isNaN() } ?: text.height.toFloat()
        val first = maxOf(textHeight, status.height.toFloat())
        val secondTop = top + first + gap.toPx()
        val second = maxOf(tiles.height, action.height).toFloat()
        val bottom = secondTop + second + pad
        val height = bottom + if (last) 0 else rule.height
        box.height = height - box.fraction
        val start = constraints.maxWidth - right
        layout(constraints.maxWidth, height.roundToInt()) {
            text.place(0, (top + (first - textHeight) / 2).roundToInt())
            status.place(start, (top + (first - status.height) / 2).roundToInt())
            tiles.place(0, (secondTop + (second - tiles.height) / 2).roundToInt())
            action.place(constraints.maxWidth - action.width, (secondTop + (second - action.height) / 2).roundToInt())
            if (!last) rule.place(0, bottom.roundToInt())
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
    Text(
        row.status,
        normal(13f, 600),
        Modifier.background(fill, CircleShape).padding(horizontal = 10.dp, vertical = 6.dp),
        softWrap = false,
    )
}

/** «Track» or «Details» follow the row's action; «Reorder» sends its command. */
@Composable
private fun Action(
    row: HistoryRow,
    onReorder: (String) -> Unit,
) {
    val reorder = row.reorderUrl
    Text(
        row.actionLabel,
        normal(15f, 600).copy(color = HaulColors.primary),
        if (reorder != null) {
            Modifier.testTag(REORDER_TAG + row.id).pressable { onReorder(reorder) }
        } else {
            Modifier.follows(row.action)
        },
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
