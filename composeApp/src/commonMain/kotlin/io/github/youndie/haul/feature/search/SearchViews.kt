package io.github.youndie.haul.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.feature.catalog.ResultCount
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.HaulType.browserLeading
import io.github.youndie.haul.theme.LocalHaulCompact
import io.github.youndie.haul.ui.BalancedText
import io.github.youndie.haul.ui.CategorySuggestion
import io.github.youndie.haul.ui.HaulIcons
import io.github.youndie.haul.ui.Icon
import io.github.youndie.haul.ui.LocalSearchField
import io.github.youndie.haul.ui.ProductSuggestion
import io.github.youndie.haul.ui.QuerySuggestion
import io.github.youndie.haul.ui.SearchFieldState
import io.github.youndie.haul.ui.SearchNoResults
import io.github.youndie.haul.ui.SearchSuggestPanel
import io.github.youndie.haul.ui.Text
import io.github.youndie.haul.ui.accented
import io.github.youndie.haul.ui.gutter
import io.github.youndie.haul.ui.toneColor
import kotlin.math.roundToInt

// The Search screen's own components (screen-search): the page for a query that found nothing and the
// suggest panel over the page while the shopper types. The numbers are the artboards'
// (Search_NoResults, Search_Autocomplete and their _Phone twins).

/**
 * Nothing found: the count, the title with the quoted query in the italic, then the queries to try —
 * or, when there are none, the numbered tips (Search_NoResults). The popular categories after it are a
 * `SectionHeader` and a `CategoryGrid` of their own.
 */
@Composable
public fun SearchNoResultsView(none: SearchNoResults) {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    Column(
        Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = if (compact) 24.dp else 36.dp),
    ) {
        none.count?.let { ResultCount(it) }
        BalancedText(
            accented(none.title, none.accent),
            HaulType.display(
                if (compact) 56f else 112f,
                800,
                letterSpacing = if (compact) -0.01f else -0.03f,
                lineHeight = 0.9f,
            ),
            Modifier.padding(top = 14.dp, bottom = if (compact) 24.dp else 32.dp),
        )
        val pills: List<Pair<String?, AnnotatedString>> =
            if (none.suggestions.isNotEmpty()) {
                none.suggestions.map { null to suggestionText(it) }
            } else {
                none.tips.mapIndexed { index, tip -> (index + 1).toString().padStart(2, '0') to AnnotatedString(tip) }
            }
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                pills.forEach { (number, text) -> Tip(number, text, Modifier.fillMaxWidth()) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pills.forEach { (number, text) -> Tip(number, text) }
            }
        }
    }
}

/** A tip on a white pill: its number in Cobalt mono, or the search glyph for a query to try. */
@Composable
private fun Tip(
    number: String?,
    text: AnnotatedString,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(18.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) {
            Text(number, HaulType.label(12f, 600, 0f).copy(color = HaulColors.primary))
        } else {
            Icon(HaulIcons.search, 18.dp, HaulColors.outline)
        }
        Text(text, style = HaulType.text(16f, 500), softWrap = false)
    }
}

/**
 * The page with the suggest panel open over it (Search_Autocomplete): the header's field is focused,
 * a scrim covers the page from under the header (from under the field's row on a phone), and the
 * panel hangs 10 px under the field, as wide as it — on a phone 4 px under it, 8 px from either edge.
 */
@Composable
public fun SearchSuggestOverlay(
    panel: SearchSuggestPanel,
    modifier: Modifier = Modifier,
    highlighted: Int = 0,
    page: @Composable () -> Unit,
) {
    val compact = LocalHaulCompact.current
    val field = remember { SearchFieldState(focused = true) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        CompositionLocalProvider(LocalSearchField provides field) { page() }
        val bounds = field.bounds
        val scrimTop = field.scrimTop
        if (bounds == null || scrimTop == null) return@Box
        Box(
            Modifier.fillMaxSize().drawBehind {
                val top = scrimTop - origin.y
                drawRect(HaulColors.scrim, Offset(0f, top), Size(size.width, size.height - top))
            },
        )
        val density = LocalDensity.current
        val placed =
            if (compact) {
                Modifier
                    .offset { IntOffset(0, (bounds.bottom - origin.y + 4.dp.toPx()).roundToInt()) }
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
            } else {
                Modifier
                    .offset {
                        IntOffset(
                            (bounds.left - origin.x).roundToInt(),
                            (bounds.bottom - origin.y + 10.dp.toPx()).roundToInt(),
                        )
                    }.width(with(density) { bounds.width.toDp() })
            }
        SearchSuggestPanelView(panel, placed, highlighted)
    }
}

/**
 * The suggest panel (`SearchSuggestPanel`): queries, categories with counts, recent searches and, in a
 * column of their own at 1440 (under the rest on a phone), the top products and «All N results ↵».
 * [highlighted] is the query row the keyboard is on.
 */
@Composable
public fun SearchSuggestPanelView(
    panel: SearchSuggestPanel,
    modifier: Modifier = Modifier,
    highlighted: Int = 0,
) {
    val compact = LocalHaulCompact.current
    val shape = RoundedCornerShape(if (compact) 20.dp else 24.dp)
    val card =
        modifier
            .dropShadow(
                shape,
                Shadow(radius = 80.dp, spread = (-20).dp, offset = DpOffset(0.dp, 30.dp), color = HaulColors.shadow),
            ).clip(shape)
            .background(HaulColors.surfaceContainerLowest)
    if (compact) {
        Column(card) {
            Suggestions(panel, highlighted, compact = true)
            TopProducts(panel, compact = true, modifier = Modifier.fillMaxWidth())
        }
    } else {
        // A grid row: the products' column is as tall as the suggestions, its button at the bottom.
        Layout(
            content = {
                Suggestions(panel, highlighted, compact = false)
                TopProducts(panel, compact = false, modifier = Modifier.fillMaxSize())
            },
            modifier = card,
        ) { measurables, constraints ->
            val right = PRODUCTS_COLUMN.roundToPx()
            val leftWidth = (constraints.maxWidth - right).coerceAtLeast(0)
            val left = measurables[0].measure(Constraints.fixedWidth(leftWidth))
            val height = maxOf(left.height, measurables[1].minIntrinsicHeight(right))
            val column = measurables[1].measure(Constraints.fixed(right, height))
            layout(constraints.maxWidth, height) {
                left.place(0, 0)
                column.place(leftWidth, 0)
            }
        }
    }
}

private val PRODUCTS_COLUMN = 270.dp

@Composable
private fun Suggestions(
    panel: SearchSuggestPanel,
    highlighted: Int,
    compact: Boolean,
) {
    val side = if (compact) 16.dp else 24.dp
    val rowHeight = if (compact) 48.dp else 44.dp
    Column(Modifier.fillMaxWidth().padding(top = if (compact) 0.dp else 4.dp, bottom = if (compact) 12.dp else 16.dp)) {
        if (panel.suggestions.isNotEmpty()) {
            Heading("Suggestions", side, compact)
            panel.suggestions.forEachIndexed { index, suggestion ->
                Line(side, rowHeight, if (index == highlighted) HaulColors.background else null) {
                    Icon(HaulIcons.search, 18.dp, HaulColors.outline)
                    Text(suggestionText(suggestion), style = HaulType.text(16f), softWrap = false)
                }
            }
        }
        if (panel.categories.isNotEmpty()) {
            Heading("In categories", side, compact)
            panel.categories.forEach { Category(it, side, rowHeight) }
        }
        if (panel.recent.isNotEmpty()) {
            // «Clear» empties them (`clearAction`, the customer tier's, arriving with sign-in).
            Heading("Recent", side, compact, action = "Clear")
            panel.recent.forEach { query ->
                Line(side, rowHeight) {
                    Icon(HaulIcons.clock, 18.dp, HaulColors.outline)
                    Text(query, HaulType.text(16f), softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun Heading(
    label: String,
    side: androidx.compose.ui.unit.Dp,
    compact: Boolean,
    action: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = side,
                end = side,
                top = if (compact) 18.dp else 20.dp,
                bottom = if (compact) 8.dp else 10.dp,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label.uppercase(), HaulType.label(11f, 600, 0.08f).copy(color = HaulColors.outline))
        action?.let { Text(it.uppercase(), HaulType.label(11f, 600, 0.08f).copy(color = HaulColors.primary)) }
    }
}

@Composable
private fun Line(
    side: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    fill: androidx.compose.ui.graphics.Color? = null,
    content: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .then(if (fill != null) Modifier.background(fill) else Modifier)
            .padding(horizontal = side),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** «Sports › Running shoes» with the separator in the muted ink, and the count in mono at the end. */
@Composable
private fun Category(
    category: CategorySuggestion,
    side: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = height).padding(horizontal = side),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildAnnotatedString {
                category.label.split(SEPARATOR).forEachIndexed { index, part ->
                    if (index > 0) withStyle(SpanStyle(color = HaulColors.outline)) { append(SEPARATOR) }
                    append(part)
                }
            },
            style = HaulType.text(16f),
            softWrap = false,
        )
        Text(category.count, HaulType.label(12f, 500, 0f).copy(color = HaulColors.outline))
    }
}

private const val SEPARATOR = "›"

/** The top products on Paper — a tone tile, the title and the price — and the way to every result. */
@Composable
private fun TopProducts(
    panel: SearchSuggestPanel,
    compact: Boolean,
    modifier: Modifier,
) {
    Column(
        modifier
            .background(HaulColors.background)
            .padding(
                start = if (compact) 16.dp else 24.dp,
                end = if (compact) 16.dp else 24.dp,
                top = if (compact) 18.dp else 24.dp,
                bottom = if (compact) 16.dp else 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Top products".uppercase(), HaulType.label(11f, 600, 0.08f).copy(color = HaulColors.outline))
        panel.products.forEach { TopProduct(it, if (compact) 56.dp else 64.dp) }
        if (!compact) Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(HaulColors.inverseSurface, RoundedCornerShape(14.dp)),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(panel.allResultsLabel, HaulType.text(15f, 600).copy(color = HaulColors.onPrimary), softWrap = false)
            Text("↵", HaulType.label(12f, 500, 0f).copy(color = HaulColors.secondaryContainer))
        }
    }
}

@Composable
private fun TopProduct(
    product: ProductSuggestion,
    tile: androidx.compose.ui.unit.Dp,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(tile).background(toneColor(product.tone), RoundedCornerShape(14.dp)))
        Column {
            Text(product.title, HaulType.text(14f, lineHeight = 1.3f).browserLeading())
            Text(
                product.price,
                HaulType.display(20f, 800, letterSpacing = -0.01f),
                Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * A query suggestion as the panel draws it: what the shopper typed in the regular weight, what the
 * suggestion adds before and after it in bold — the spaces between them regular, as the canvas's
 * `running shoes <b>women</b>` has them.
 */
internal fun suggestionText(suggestion: QuerySuggestion): AnnotatedString =
    buildAnnotatedString {
        fun bold(part: String) {
            val core = part.trim()
            if (core.isEmpty()) {
                append(part)
                return
            }
            val start = part.indexOf(core)
            append(part.substring(0, start))
            withStyle(SpanStyle(fontWeight = FontWeight(700))) { append(core) }
            append(part.substring(start + core.length))
        }
        bold(suggestion.prefix)
        append(suggestion.typed)
        bold(suggestion.completion)
    }
