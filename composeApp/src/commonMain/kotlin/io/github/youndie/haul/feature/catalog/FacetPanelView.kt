package io.github.youndie.haul.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.groupedCount
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.ui.AppliedFilters
import io.github.youndie.haul.ui.Facet
import io.github.youndie.haul.ui.FacetOption
import io.github.youndie.haul.ui.FacetPanel
import io.github.youndie.haul.ui.HaulButton
import io.github.youndie.haul.ui.HaulIcons
import io.github.youndie.haul.ui.Icon
import io.github.youndie.haul.ui.Text
import io.github.youndie.haul.ui.follows
import io.github.youndie.haul.ui.pressable
import io.github.youndie.haul.ui.toneColor

/**
 * The facet column (`FacetPanel` on the wire): price, then one block per facet kind, hairlines
 * between. [sheet] is the phone's sheet: tighter blocks, rows a finger tall, larger swatches.
 */
@Composable
public fun FacetPanelView(
    panel: FacetPanel,
    modifier: Modifier = Modifier,
    sheet: Boolean,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (sheet) 24.dp else 32.dp)) {
        panel.facets.forEachIndexed { index, facet ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(HaulColors.outlineVariant))
            FacetBlock(facet, sheet)
        }
    }
}

@Composable
private fun FacetBlock(
    facet: Facet,
    sheet: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (facet.kind == "range") 16.dp else 13.dp)) {
        Text(facet.title, HaulType.text(16f, 700))
        when (facet.kind) {
            "range" -> {
                PriceRange(facet)
            }

            "swatch" -> {
                Swatches(facet.options, sheet)
            }

            "pills" -> {
                Pills(facet.options)
            }

            else -> {
                facet.options.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = if (sheet) 44.dp else 0.dp).follows(option.action),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when (facet.kind) {
                            "checkbox" -> Checkbox(option.selected)
                            "radio" -> Radio(option.selected)
                        }
                        Text(option.label, HaulType.text(15f), Modifier.weight(1f), softWrap = false)
                        when (facet.kind) {
                            "toggle" -> {
                                Switch(option.selected)
                            }

                            "checkbox" -> {
                                option.count?.let {
                                    Text(groupedCount(it), HaulType.text(15f).copy(color = HaulColors.outlineMuted))
                                }
                            }
                        }
                    }
                }
            }
        }
        // «Show N more» opens the page again with this block expanded (`Facet.moreAction`, B-49).
        facet.moreLabel?.let {
            Text(it, HaulType.text(15f, 600).copy(color = HaulColors.primary), Modifier.follows(facet.moreAction))
        }
    }
}

@Composable
private fun PriceRange(facet: Facet) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PriceField("from", facet.min.orEmpty(), Modifier.weight(1f))
        PriceField("to", facet.max.orEmpty(), Modifier.weight(1f))
    }
    val start = facet.rangeStart ?: 0f
    val end = facet.rangeEnd ?: 1f
    BoxWithConstraints(Modifier.fillMaxWidth().height(20.dp)) {
        val track = maxWidth
        Box(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(4.dp)
                .background(HaulColors.outlineVariant, RoundedCornerShape(2.dp)),
        )
        Box(
            Modifier
                .padding(top = 8.dp)
                .offset(x = track * start)
                .width(track * (end - start))
                .height(4.dp)
                .background(HaulColors.primary),
        )
        Thumb(Modifier.offset(x = track * start - 10.dp))
        Thumb(Modifier.offset(x = track * end - 10.dp))
    }
}

@Composable
private fun PriceField(
    prefix: String,
    value: String,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .height(48.dp)
            .background(HaulColors.surfaceContainerLowest, shape)
            .border(1.dp, HaulColors.outlineVariant, shape)
            .padding(horizontal = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(prefix, HaulType.text(15f).copy(color = HaulColors.outlineMuted), softWrap = false)
        Text(value, HaulType.text(15f, 600), softWrap = false)
    }
}

@Composable
private fun Thumb(modifier: Modifier) {
    Box(
        modifier
            .size(20.dp)
            .background(HaulColors.surfaceContainerLowest, CircleShape)
            .border(3.dp, HaulColors.primary, CircleShape),
    )
}

@Composable
private fun Checkbox(checked: Boolean) {
    val shape = RoundedCornerShape(6.dp)
    if (checked) {
        Box(Modifier.size(20.dp).background(HaulColors.primary, shape), contentAlignment = Alignment.Center) {
            Icon(HaulIcons.checkBold, 14.dp, HaulColors.onPrimary)
        }
    } else {
        Box(
            Modifier
                .size(20.dp)
                .background(HaulColors.surfaceContainerLowest, shape)
                .border(2.dp, HaulColors.outlineControl, shape),
        )
    }
}

@Composable
private fun Radio(selected: Boolean) {
    Box(
        Modifier
            .size(20.dp)
            .background(HaulColors.surfaceContainerLowest, CircleShape)
            .border(
                if (selected) 6.dp else 2.dp,
                if (selected) HaulColors.primary else HaulColors.outlineControl,
                CircleShape,
            ),
    )
}

@Composable
private fun Switch(on: Boolean) {
    Box(
        Modifier
            .size(44.dp, 26.dp)
            .background(if (on) HaulColors.primary else HaulColors.surfaceContainerHighest, RoundedCornerShape(13.dp))
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(20.dp).background(HaulColors.surfaceContainerLowest, CircleShape))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Swatches(
    options: List<FacetOption>,
    sheet: Boolean,
) {
    val size: Dp = if (sheet) 40.dp else 32.dp
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(if (sheet) 14.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(if (sheet) 14.dp else 10.dp),
    ) {
        options.forEach { option ->
            val colour = option.swatch?.let(::toneColor) ?: HaulColors.outlineVariant
            // The selected ring is a box shadow on the canvas: 2 px of Paper, then 2 px of Cobalt,
            // drawn outside the swatch without moving its neighbours.
            Box(Modifier.size(size).follows(option.action), contentAlignment = Alignment.Center) {
                if (option.selected) {
                    Box(Modifier.requiredSize(size + 8.dp).background(HaulColors.primary, CircleShape))
                    Box(Modifier.requiredSize(size + 4.dp).background(HaulColors.background, CircleShape))
                }
                Box(
                    Modifier
                        .size(size)
                        .background(colour, CircleShape)
                        .then(
                            if (colour ==
                                WHITE
                            ) {
                                Modifier.border(1.dp, HaulColors.outlineControl, CircleShape)
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
    }
}

private val WHITE = Color(0xFFFFFFFF)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Pills(options: List<FacetOption>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val box =
                if (option.selected) {
                    Modifier.background(HaulColors.secondaryContainer, CircleShape)
                } else {
                    Modifier
                        .background(HaulColors.surfaceContainerLowest, CircleShape)
                        .border(1.dp, HaulColors.outlineVariant, CircleShape)
                        .padding(1.dp)
                }
            Text(
                option.label,
                HaulType.text(14f, if (option.selected) 600 else 500),
                box.follows(option.action).padding(horizontal = 13.dp, vertical = 9.dp),
                softWrap = false,
            )
        }
    }
}

/** What the filter sheet's «×» is called to a screen reader, and to the tests that press it. */
public const val CLOSE_FILTERS: String = "Close filters"

/**
 * The phone's filter sheet over the category page (Catalog_FiltersSheet_Phone): the title with how
 * many filters are applied and «Clear all», the facets scrolling between, the button that shows the
 * result pinned at the bottom. «×» is [onClose] (B-49): the sheet is the client's own, opened by
 * «Filters» without asking the server, so closing it asks nothing either. «Show N items» is [onShow]
 * (B-54): the page under the sheet already shows those results — each press in the sheet opened them —
 * so it only closes the sheet. `null` — a screenshot — leaves either unpressable.
 */
@Composable
public fun FiltersSheet(
    panel: FacetPanel,
    applied: AppliedFilters,
    showLabel: String,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    onShow: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxSize().background(HaulColors.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(68.dp)
                .background(HaulColors.surfaceContainerLowest)
                .padding(start = 16.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Filters", HaulType.display(30f, 800, letterSpacing = -0.01f), softWrap = false)
            Text(
                "${applied.filterCount} applied",
                HaulType.text(14f, 600).copy(color = HaulColors.outline),
                softWrap = false,
            )
            Spacer(Modifier.weight(1f))
            Text(
                applied.clearLabel,
                HaulType.text(15f, 600).copy(color = HaulColors.primary),
                Modifier.follows(applied.clearAction).padding(horizontal = 8.dp),
                softWrap = false,
            )
            Box(
                Modifier
                    .size(44.dp)
                    .background(HaulColors.background, CircleShape)
                    .semantics { contentDescription = CLOSE_FILTERS }
                    .pressable(onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(HaulIcons.close, 18.dp, HaulColors.onSurface)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(HaulColors.outlineVariant))
        Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            FacetPanelView(
                panel,
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 32.dp),
                sheet = true,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(HaulColors.outlineVariant))
        Box(Modifier.fillMaxWidth().background(HaulColors.surfaceContainerLowest).padding(16.dp)) {
            HaulButton(
                showLabel,
                Modifier.fillMaxWidth(),
                height = 56.dp,
                radius = 18.dp,
                fill = HaulColors.primary,
                content = HaulColors.onPrimary,
                onClick = onShow,
            )
        }
    }
}
