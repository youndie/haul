package io.github.youndie.haul.feature.catalog

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
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
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.LinkMenu
import io.github.youndie.haul.ui.LocalHaulActions
import io.github.youndie.haul.ui.LocalPageScroll
import io.github.youndie.haul.ui.PageTitle
import io.github.youndie.haul.ui.ProductGridView
import io.github.youndie.haul.ui.Text
import io.github.youndie.haul.ui.following
import io.github.youndie.haul.ui.follows
import io.github.youndie.haul.ui.gutter
import io.github.youndie.haul.ui.linkedText
import io.github.youndie.haul.ui.pressable
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
        linkedText {
            if (shown.size < crumbs.size) text.append("… / ")
            shown.dropLast(1).forEach {
                link(it.label, it.action, actions)
                text.append(" / ")
            }
            text.withStyle(SpanStyle(color = HaulColors.onSurface)) { append(shown.lastOrNull()?.label.orEmpty()) }
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

/**
 * The kinds of a category as pills: one row at 1440, a row that scrolls off the right edge on a phone. A
 * search's row ends in its sort control (B-77) — at the row's right edge on a desktop, over the row at the right
 * on a phone — and scrolls sideways at every width: a search's categories are as many as its results span, 111 for
 * `classic`, and each is a scope to press (B-142). The chosen one is scrolled into view when it arrives.
 */
@Composable
public fun FilterChipsView(chips: FilterChips) {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    val bottom = if (compact) (if (chips.counted()) 24.dp else 16.dp) else (if (chips.counted()) 32.dp else 36.dp)
    val sort = chips.sortLabel?.takeIf { chips.sorts.isNotEmpty() }
    when {
        compact -> {
            Column(Modifier.fillMaxWidth().padding(bottom = bottom)) {
                if (sort != null) {
                    Row(Modifier.fillMaxWidth().padding(start = gutter, end = gutter, bottom = 12.dp)) {
                        Spacer(Modifier.weight(1f))
                        SortMenu(sort, chips.sorts, Modifier.height(44.dp), textSize = 15f)
                    }
                }
                KindChips(chips.chips, compact, Modifier.fillMaxWidth(), rememberScrollState(), start = gutter)
            }
        }

        sort != null -> {
            Row(
                Modifier.fillMaxWidth().padding(start = gutter, end = gutter, bottom = bottom),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The pills run past the sort control at every desktop width, not only below 1280 (B-82): from
                // 1280 up they were one line cut under it, the chips past it out of reach (B-142). Scrolling
                // sideways as on a phone, not wrapping: a search's 111 categories wrapped would push its grid a
                // screen down, and the row is the one B-82 already made scroll below 1280.
                KindChips(chips.chips, compact, Modifier.weight(1f), rememberScrollState())
                SortMenu(sort, chips.sorts, Modifier.height(44.dp), textSize = 14f)
            }
        }

        else -> {
            KindChips(
                chips.chips,
                compact,
                Modifier.fillMaxWidth().padding(start = gutter, end = gutter, bottom = bottom),
            )
        }
    }
}

/**
 * The pills on one line. With [scroll] the line scrolls sideways, [start] inside what scrolls, and the chosen pill
 * is scrolled into view, centred, when it arrives outside the line's view — a search opened on a scope past the
 * line's end (B-142); a pill in view, pressed or arrived, moves nothing.
 */
@Composable
private fun KindChips(
    chips: List<Chip>,
    compact: Boolean,
    modifier: Modifier,
    scroll: ScrollState? = null,
    start: Dp = 0.dp,
) {
    // The scrolled content, which the chosen pill's place is measured in, whatever the scroll.
    val content = remember { arrayOfNulls<LayoutCoordinates>(1) }
    var chosen by remember { mutableStateOf<IntRange?>(null) }
    // A flex line stretches its items: the selected pill has no border and is as tall as the others.
    Row(
        modifier
            .then(
                if (scroll == null) {
                    Modifier
                } else {
                    Modifier
                        .draggedByMouse(scroll)
                        .horizontalScroll(scroll)
                        .onPlaced { content[0] = it }
                        .padding(start = start)
                },
            ).height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        chips.forEach { chip ->
            val placed =
                if (scroll != null && chip.selected) {
                    Modifier.onGloballyPositioned { pill ->
                        val left = content[0]?.localPositionOf(pill, Offset.Zero)?.x?.roundToInt()
                        chosen = left?.let { it until it + pill.size.width }
                    }
                } else {
                    Modifier
                }
            KindChip(chip, compact, placed)
        }
    }
    if (scroll == null) return
    LaunchedEffect(chosen, scroll.viewportSize) {
        val pill = chosen ?: return@LaunchedEffect
        val view = scroll.viewportSize
        if (view == 0 || (pill.first >= scroll.value && pill.last < scroll.value + view)) return@LaunchedEffect
        val width = pill.last - pill.first + 1
        scroll.scrollTo((pill.first - (view - width) / 2).coerceIn(0, scroll.maxValue))
    }
}

/**
 * A mouse dragged across the row scrolls it, as a finger does: Compose scrolls a row under a finger, a wheel and the
 * keyboard's focus, but not under a mouse held down and moved (B-142). The drag past the slop is consumed, so the pill
 * it started on is not pressed when the button comes up.
 */
private fun Modifier.draggedByMouse(scroll: ScrollState): Modifier =
    pointerInput(scroll) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.type != PointerType.Mouse) return@awaitEachGesture
            val dragging =
                awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                    change.consume()
                    scroll.dispatchRawDelta(-over)
                } ?: return@awaitEachGesture
            horizontalDrag(dragging.id) { change ->
                scroll.dispatchRawDelta(-change.positionChange().x)
                change.consume()
            }
        }
    }

/** A chip with a count is a search's category (Search_Results), and its row sits closer to the grid. */
private fun FilterChips.counted(): Boolean = chips.any { it.count != null }

@Composable
private fun KindChip(
    chip: Chip,
    compact: Boolean,
    modifier: Modifier = Modifier,
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
    Box(modifier.fillMaxHeight().then(box).follows(chip.action)) {
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
 * facets live in [FiltersSheet], which «Filters» opens ([onOpenFilters]; `null` — a screenshot — leaves
 * it unpressable).
 */
@Composable
public fun FilteredResultsView(
    results: FilteredResults,
    onOpenFilters: (() -> Unit)? = null,
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

/**
 * The filters applied and «Clear all» on as many lines as they take, the sort control at the first line's end
 * (B-82): in one row the filters took the width first, and four of them with «Clear all» cut the sort to «Sort:»
 * at 1024 and «Popul» at 1280. At 1440 they take one line, as the artboards draw them.
 */
@Composable
private fun WideApplied(applied: AppliedFilters) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlowRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            applied.chips.forEach { AppliedChip(it) }
            ClearAll(applied, Modifier.padding(start = 4.dp))
        }
        Sort(applied, Modifier.height(44.dp), textSize = 14f)
    }
}

@Composable
private fun CompactApplied(
    applied: AppliedFilters,
    gutter: androidx.compose.ui.unit.Dp,
    onOpenFilters: (() -> Unit)?,
) {
    FlexPair(
        10.dp,
        secondFrame = SORT_FRAME,
        modifier = Modifier.fillMaxWidth().padding(start = gutter, end = gutter, bottom = 14.dp),
    ) {
        // «Filters» opens the sheet: B-07 drew it and passed the opener here, but nothing pressed it, so the
        // sheet — and its «×» (B-49) — could not be reached.
        Row(
            Modifier
                .height(48.dp)
                .background(HaulColors.inverseSurface, RoundedCornerShape(12.dp))
                .pressable(onOpenFilters),
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

/**
 * «Clear all»: follows `AppliedFilters.clearAction`, the page without its filters — only while a filter is
 * applied (B-102): with none, a link-coloured word that clears nothing promised a press.
 */
@Composable
private fun ClearAll(
    applied: AppliedFilters,
    modifier: Modifier = Modifier,
) {
    if (applied.filterCount == 0) return
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
    SortMenu(applied.sortLabel, applied.sorts, modifier, textSize)
}

/** «Sort: [label]»; pressing it opens [sorts], the orders it offers, as a menu. */
@Composable
private fun SortMenu(
    label: String,
    sorts: List<Link>,
    modifier: Modifier,
    textSize: Float,
) {
    LinkMenu(sorts, current = label, alignEnd = true) { press ->
        SortControl(label, press.then(modifier), textSize)
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

/**
 * «Show 24 more» and the page numbers: side by side at 1440, stacked and centred on a phone. A grid of one page
 * has neither and draws nothing — not a lone «1» (B-108). A page number opens its page at the top, as a link to
 * another page would: the page is kept while it loads (B-62), but a press at the foot of a page left the shopper at
 * the foot of the next one. «Show N more» appends and keeps the scroll (B-77).
 */
@Composable
public fun PaginationView(
    pagination: HaulPagination,
    modifier: Modifier = Modifier,
) {
    if (pagination.pages.size <= 1 && pagination.moreLabel == null) return
    val compact = LocalHaulCompact.current
    val handler = LocalHaulActions.current
    val scroll = LocalPageScroll.current
    val scope = rememberCoroutineScope()
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
                    val action = pagination.links.firstOrNull { it.label == label }?.action
                    PageNumber(
                        label,
                        current = label == pagination.current.toString(),
                        onPress =
                            if (action == null || handler == null) {
                                null
                            } else {
                                {
                                    handler.handle(action)
                                    scroll?.let { scope.launch { it.scrollTo(0) } }
                                }
                            },
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
    onPress: (() -> Unit)?,
) {
    val fill =
        when {
            current -> HaulColors.inverseSurface
            label == "…" -> null
            else -> HaulColors.surfaceContainerLowest
        }
    Box(
        Modifier
            .pressable(onPress)
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
