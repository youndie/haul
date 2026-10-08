package io.github.youndie.haul.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.HaulType.browserLeading
import io.github.youndie.haul.theme.LocalHaulCompact
import io.github.youndie.haul.ui.AppliedFilters
import io.github.youndie.haul.ui.BaselineWrapRow
import io.github.youndie.haul.ui.Breadcrumbs
import io.github.youndie.haul.ui.Chip
import io.github.youndie.haul.ui.EmptyStateView
import io.github.youndie.haul.ui.FilterChips
import io.github.youndie.haul.ui.FilteredResults
import io.github.youndie.haul.ui.FlexPair
import io.github.youndie.haul.ui.HaulButton
import io.github.youndie.haul.ui.HaulIcons
import io.github.youndie.haul.ui.HaulPagination
import io.github.youndie.haul.ui.Icon
import io.github.youndie.haul.ui.LinkMenu
import io.github.youndie.haul.ui.LocalHaulActions
import io.github.youndie.haul.ui.PageTitle
import io.github.youndie.haul.ui.ProductGridView
import io.github.youndie.haul.ui.Text
import io.github.youndie.haul.ui.appendLink
import io.github.youndie.haul.ui.following
import io.github.youndie.haul.ui.follows
import io.github.youndie.haul.ui.gutter
import io.github.youndie.kompot.KompotAction

// The category page's components (screen-catalog), each with the margin the page gives it; the
// numbers are the artboards' (Catalog_Content, Catalog_Empty and their _Phone twins).

/** «Home / Electronics / Audio / Headphones», the last crumb in Ink. */
@Composable
public fun BreadcrumbsView(breadcrumbs: Breadcrumbs) {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    // A phone shortens a deep path to its last two crumbs («… / Headphones / Sony WH-1000XM6»,
    // Product_Description_Phone); a category's four still fit.
    val crumbs = breadcrumbs.crumbs
    val shown = if (compact && crumbs.size > COMPACT_CRUMBS) crumbs.takeLast(2) else crumbs
    val actions = LocalHaulActions.current
    Text(
        buildAnnotatedString {
            if (shown.size < crumbs.size) append("… / ")
            shown.dropLast(1).forEach {
                appendLink(it.label, it.action, actions)
                append(" / ")
            }
            withStyle(SpanStyle(color = HaulColors.onSurface)) { append(shown.lastOrNull()?.label.orEmpty()) }
        },
        Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = if (compact) 20.dp else 28.dp),
        style =
            HaulType
                .label(
                    12f,
                    500,
                    0.04f,
                ).copy(color = HaulColors.outline, lineHeight = (12f * 1.4f).sp)
                .browserLeading(),
        softWrap = false,
        maxLines = 1,
    )
}

private const val COMPACT_CRUMBS = 4

/**
 * The page's title with the count beside it, on one baseline; a search's ([PageTitle.quoted]) has it
 * above, the cart's ([PageTitle.badge]) in a pill at its top.
 */
@Composable
public fun PageTitleView(title: PageTitle) {
    if (title.quoted) {
        QueryTitle(title)
        return
    }
    if (title.badge) {
        BadgeTitle(title)
        return
    }
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    BaselineWrapRow(
        gap = if (compact) 14.dp else 20.dp,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = gutter,
                    end = gutter,
                    top = if (compact) 14.dp else 18.dp,
                    bottom = if (compact) 20.dp else 24.dp,
                ),
    ) {
        Text(
            title.title,
            HaulType.display(
                if (compact) 56f else 112f,
                800,
                letterSpacing = if (compact) -0.01f else -0.03f,
                lineHeight = 0.9f,
            ),
            softWrap = false,
        )
        Text(title.count.orEmpty(), HaulType.label(14f, 600, 0f).copy(color = HaulColors.outline), softWrap = false)
    }
}

/**
 * A search's title (Search_Results): the count in mono, then the query in quotes, the quotes in the
 * italic. The page's top padding is the title's, as on the category page the crumbs carry it.
 */
@Composable
private fun QueryTitle(title: PageTitle) {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    Column(
        Modifier.fillMaxWidth().padding(
            start = gutter,
            end = gutter,
            top = if (compact) 24.dp else 36.dp,
            bottom = if (compact) 20.dp else 24.dp,
        ),
    ) {
        title.count?.let { ResultCount(it) }
        Text(
            buildAnnotatedString {
                withStyle(QUOTE) { append("“") }
                append(title.title)
                withStyle(QUOTE) { append("”") }
            },
            Modifier.padding(top = 14.dp),
            style =
                HaulType.display(
                    if (compact) 56f else 112f,
                    800,
                    letterSpacing = if (compact) -0.01f else -0.03f,
                    lineHeight = 0.9f,
                ),
        )
    }
}

/**
 * The cart's title (Cart_Content): «Cart» with the count in an Acid pill level with its top, 14 px
 * apart. The page has no crumbs, so the title carries the page's top padding; an empty cart's has no
 * count and leaves the gap under it to what follows (Cart_Empty).
 */
@Composable
private fun BadgeTitle(title: PageTitle) {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    Row(
        Modifier.fillMaxWidth().padding(
            start = gutter,
            end = gutter,
            top = if (compact) 24.dp else 40.dp,
            bottom =
                if (title.count == null) {
                    0.dp
                } else if (compact) {
                    24.dp
                } else {
                    36.dp
                },
        ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            title.title,
            HaulType.display(
                if (compact) 56f else 112f,
                800,
                letterSpacing = if (compact) -0.01f else -0.03f,
                lineHeight = 0.9f,
            ),
            softWrap = false,
        )
        title.count?.let {
            Text(
                it,
                HaulType.label(14f, 600, 0f),
                Modifier
                    .padding(top = if (compact) 6.dp else 10.dp)
                    .background(HaulColors.secondaryContainer, RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                softWrap = false,
            )
        }
    }
}

private val QUOTE = SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight(500))

/** «14,870 results» over a search's title. */
@Composable
internal fun ResultCount(count: String) {
    Text(count, HaulType.label(13f, 600, 0.04f).copy(color = HaulColors.outline))
}

/** The kinds of a category as pills: one row at 1440, a row that scrolls off the right edge on a phone. */
@Composable
public fun FilterChipsView(chips: FilterChips) {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    val row =
        if (compact) {
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = gutter, bottom = if (chips.counted()) 24.dp else 16.dp)
        } else {
            Modifier.fillMaxWidth().padding(
                start = gutter,
                end = gutter,
                bottom = if (chips.counted()) 32.dp else 36.dp,
            )
        }
    // A flex line stretches its items: the selected pill has no border and is as tall as the others.
    Row(row.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        chips.chips.forEach { KindChip(it, compact) }
    }
}

/** A chip with a count is a search's category (Search_Results), and its row sits closer to the grid. */
private fun FilterChips.counted(): Boolean = chips.any { it.count != null }

@Composable
private fun KindChip(
    chip: Chip,
    compact: Boolean,
) {
    val style =
        HaulType
            .text(if (compact) 14f else 15f, if (chip.selected) 600 else 500)
            .copy(color = if (chip.selected) HaulColors.onPrimary else HaulColors.onSurface)
    val box =
        if (chip.selected) {
            Modifier.background(HaulColors.inverseSurface, CircleShape)
        } else {
            Modifier
                .background(HaulColors.surfaceContainerLowest, CircleShape)
                .border(1.dp, HaulColors.outlineVariant, CircleShape)
                .padding(1.dp)
        }
    Box(Modifier.fillMaxHeight().then(box).follows(chip.action)) {
        Text(
            chip.count?.let { "${chip.label} $it" } ?: chip.label,
            style,
            Modifier.padding(horizontal = if (compact) 16.dp else 18.dp, vertical = if (compact) 10.dp else 11.dp),
            softWrap = false,
        )
    }
}

/**
 * The results and their filters. At 1440 the 280-wide facet column stands beside the applied chips,
 * the grid and the pagination; on a phone «Filters» and the sort lead, the chips scroll, and the
 * facets live in [FiltersSheet].
 */
@Composable
public fun FilteredResultsView(
    results: FilteredResults,
    onOpenFilters: () -> Unit = {},
) {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    if (compact) {
        Column(Modifier.fillMaxWidth().padding(bottom = 64.dp)) {
            CompactApplied(results.applied, gutter, onOpenFilters)
            Column(Modifier.padding(horizontal = gutter)) { Results(results, compact = true) }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(start = gutter, end = gutter, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            FacetPanelView(results.facets, Modifier.width(280.dp), sheet = false)
            Column(Modifier.weight(1f)) {
                WideApplied(results.applied)
                Results(results, compact = false)
            }
        }
    }
}

@Composable
private fun Results(
    results: FilteredResults,
    compact: Boolean,
) {
    results.grid?.let { ProductGridView(it) }
    results.pagination?.let { PaginationView(it, Modifier.padding(top = if (compact) 36.dp else 48.dp)) }
    results.empty?.let {
        EmptyStateView(it, Modifier.padding(top = if (compact) 28.dp else 40.dp, bottom = if (compact) 8.dp else 0.dp))
    }
}

@Composable
private fun WideApplied(applied: AppliedFilters) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        applied.chips.forEach { AppliedChip(it) }
        ClearAll(applied, Modifier.padding(start = 4.dp))
        Spacer(Modifier.weight(1f))
        Sort(applied, Modifier.height(44.dp), textSize = 14f)
    }
}

@Composable
private fun CompactApplied(
    applied: AppliedFilters,
    gutter: androidx.compose.ui.unit.Dp,
    onOpenFilters: () -> Unit,
) {
    FlexPair(
        10.dp,
        secondFrame = SORT_FRAME,
        modifier = Modifier.fillMaxWidth().padding(start = gutter, end = gutter, bottom = 14.dp),
    ) {
        Row(
            Modifier
                .height(48.dp)
                .background(HaulColors.inverseSurface, RoundedCornerShape(12.dp)),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(HaulIcons.filters, 18.dp, HaulColors.onPrimary)
            Text("Filters", HaulType.text(15f, 600).copy(color = HaulColors.onPrimary))
            if (applied.filterCount > 0) {
                Box(
                    Modifier
                        .defaultMinSize(minWidth = 22.dp)
                        .height(22.dp)
                        .background(HaulColors.secondaryContainer, RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text(applied.filterCount.toString(), HaulType.text(12f, 800)) }
            }
        }
        Sort(applied, Modifier.height(48.dp), textSize = 15f)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = gutter, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        applied.chips.forEach { AppliedChip(it) }
        ClearAll(applied)
    }
}

@Composable
private fun AppliedChip(chip: Chip) {
    Row(
        Modifier
            .background(HaulColors.inverseSurface, CircleShape)
            .follows(chip.action)
            .padding(start = 14.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(chip.label, HaulType.text(14f, 500).copy(color = HaulColors.onPrimary), softWrap = false)
        Icon(HaulIcons.close, 14.dp, HaulColors.onPrimary)
    }
}

/** «Clear all»: follows `AppliedFilters.clearAction`, the page without its filters. */
@Composable
private fun ClearAll(
    applied: AppliedFilters,
    modifier: Modifier = Modifier,
) {
    Text(
        applied.clearLabel,
        HaulType.text(14f, 600).copy(color = HaulColors.primary),
        modifier.follows(applied.clearAction),
        softWrap = false,
    )
}

/** The sort control's padding (16 each side) and border (1 each side). */
private val SORT_FRAME = 34.dp

/** The sort control; pressing it opens `AppliedFilters.sorts`, the orders it offers, as a menu. */
@Composable
private fun Sort(
    applied: AppliedFilters,
    modifier: Modifier,
    textSize: Float,
) {
    LinkMenu(applied.sorts, current = applied.sortLabel, alignEnd = true) { press ->
        SortControl(applied.sortLabel, press.then(modifier), textSize)
    }
}

@Composable
private fun SortControl(
    label: String,
    modifier: Modifier,
    textSize: Float,
) {
    Row(
        modifier
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(12.dp))
            .border(1.dp, HaulColors.outlineVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Sort:", HaulType.text(textSize).copy(color = HaulColors.outlineMuted), softWrap = false)
        Text(label, HaulType.text(textSize, 600), softWrap = false)
        Icon(HaulIcons.chevronDown, 16.dp, HaulColors.onSurface)
    }
}

/** «Show 24 more» and the page numbers: side by side at 1440, stacked and centred on a phone. */
@Composable
public fun PaginationView(
    pagination: HaulPagination,
    modifier: Modifier = Modifier,
) {
    val compact = LocalHaulCompact.current
    val more =
        @Composable { m: Modifier ->
            pagination.moreLabel?.let {
                HaulButton(
                    it,
                    m,
                    height = 60.dp,
                    radius = 16.dp,
                    border = HaulColors.onSurface,
                    textSize = 16f,
                    onClick = following(pagination.moreAction),
                )
            }
        }
    val pages =
        @Composable {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pagination.pages.forEach { label ->
                    PageNumber(
                        label,
                        current = label == pagination.current.toString(),
                        action = pagination.links.firstOrNull { it.label == label }?.action,
                    )
                }
            }
        }
    if (compact) {
        Column(
            modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            more(Modifier.fillMaxWidth())
            pages()
        }
    } else {
        Row(
            modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            more(Modifier)
            pages()
        }
    }
}

@Composable
private fun PageNumber(
    label: String,
    current: Boolean,
    action: KompotAction?,
) {
    val fill =
        when {
            current -> HaulColors.inverseSurface
            label == "…" -> null
            else -> HaulColors.surfaceContainerLowest
        }
    Box(
        Modifier
            .follows(action)
            .defaultMinSize(minWidth = 44.dp)
            .height(44.dp)
            .then(if (fill != null) Modifier.background(fill, RoundedCornerShape(12.dp)) else Modifier)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            HaulType.label(14f, 600, 0f).copy(color = if (current) HaulColors.onPrimary else HaulColors.onSurface),
        )
    }
}
