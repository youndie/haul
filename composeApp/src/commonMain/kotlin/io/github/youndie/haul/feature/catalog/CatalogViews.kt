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
import io.github.youndie.haul.ui.FlexHalves
import io.github.youndie.haul.ui.HaulButton
import io.github.youndie.haul.ui.HaulIcons
import io.github.youndie.haul.ui.HaulPagination
import io.github.youndie.haul.ui.Icon
import io.github.youndie.haul.ui.PageTitle
import io.github.youndie.haul.ui.ProductGridView
import io.github.youndie.haul.ui.Text
import io.github.youndie.haul.ui.gutter

// The category page's components (screen-catalog), each with the margin the page gives it; the
// numbers are the artboards' (Catalog_Content, Catalog_Empty and their _Phone twins).

/** «Home / Electronics / Audio / Headphones», the last crumb in Ink. */
@Composable
public fun BreadcrumbsView(breadcrumbs: Breadcrumbs) {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    val crumbs = breadcrumbs.crumbs
    Text(
        buildAnnotatedString {
            crumbs.dropLast(1).forEach { append(it.label + " / ") }
            withStyle(SpanStyle(color = HaulColors.onSurface)) { append(crumbs.lastOrNull()?.label.orEmpty()) }
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

/** The page's title with the count beside it, on one baseline. */
@Composable
public fun PageTitleView(title: PageTitle) {
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

/** The kinds of a category as pills: one row at 1440, a row that scrolls off the right edge on a phone. */
@Composable
public fun FilterChipsView(chips: FilterChips) {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    val row =
        if (compact) {
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = gutter, bottom = 16.dp)
        } else {
            Modifier.fillMaxWidth().padding(start = gutter, end = gutter, bottom = 36.dp)
        }
    // A flex line stretches its items: the selected pill has no border and is as tall as the others.
    Row(row.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        chips.chips.forEach { KindChip(it, compact) }
    }
}

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
    Box(Modifier.fillMaxHeight().then(box)) {
        Text(
            chip.label,
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
        ClearAll(applied.clearLabel, Modifier.padding(start = 4.dp))
        Spacer(Modifier.weight(1f))
        Sort(applied.sortLabel, Modifier.height(44.dp), textSize = 14f)
    }
}

@Composable
private fun CompactApplied(
    applied: AppliedFilters,
    gutter: androidx.compose.ui.unit.Dp,
    onOpenFilters: () -> Unit,
) {
    FlexHalves(10.dp, Modifier.fillMaxWidth().padding(start = gutter, end = gutter, bottom = 14.dp)) {
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
        Sort(applied.sortLabel, Modifier.height(48.dp), textSize = 15f)
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
        ClearAll(applied.clearLabel)
    }
}

@Composable
private fun AppliedChip(chip: Chip) {
    Row(
        Modifier
            .background(HaulColors.inverseSurface, CircleShape)
            .padding(start = 14.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(chip.label, HaulType.text(14f, 500).copy(color = HaulColors.onPrimary), softWrap = false)
        Icon(HaulIcons.close, 14.dp, HaulColors.onPrimary)
    }
}

@Composable
private fun ClearAll(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(label, HaulType.text(14f, 600).copy(color = HaulColors.primary), modifier, softWrap = false)
}

@Composable
private fun Sort(
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
                HaulButton(it, m, height = 60.dp, radius = 16.dp, border = HaulColors.onSurface, textSize = 16f)
            }
        }
    val pages =
        @Composable {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pagination.pages.forEach { PageNumber(it, it == pagination.current.toString()) }
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
) {
    val fill =
        when {
            current -> HaulColors.inverseSurface
            label == "…" -> null
            else -> HaulColors.surfaceContainerLowest
        }
    Box(
        Modifier
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
