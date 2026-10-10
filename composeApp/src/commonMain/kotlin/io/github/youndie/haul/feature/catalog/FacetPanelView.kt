package io.github.youndie.haul.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.groupedCount
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.ui.AppliedFilters
import io.github.youndie.haul.ui.Facet
import io.github.youndie.haul.ui.FacetOption
import io.github.youndie.haul.ui.FacetPanel
import io.github.youndie.haul.ui.FacetRange
import io.github.youndie.haul.ui.HaulButton
import io.github.youndie.haul.ui.HaulIcons
import io.github.youndie.haul.ui.Icon
import io.github.youndie.haul.ui.LocalHaulActions
import io.github.youndie.haul.ui.Text
import io.github.youndie.haul.ui.awaitTypedInput
import io.github.youndie.haul.ui.follows
import io.github.youndie.haul.ui.pressable
import io.github.youndie.haul.ui.toneColor
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

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
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = if (sheet) 44.dp else 0.dp)
                            .muted(option)
                            .follows(option.action),
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

/** The tags of the price facet's fields and slider, for the tests that type into and drag them (B-69). */
public const val PRICE_FROM_TAG: String = "price-from"
public const val PRICE_TO_TAG: String = "price-to"
public const val PRICE_SLIDER_TAG: String = "price-slider"

/**
 * The price facet: «from» and «to», and the slider under them (B-69). A bound typed and confirmed — Enter,
 * the keyboard's «Done», or leaving the field — or a thumb released applies the range: the `load` the
 * facet's [FacetRange] makes of the two bounds, unless they are the ones applied. An empty field, or a
 * thumb taken to its end of the track, is no bound. A facet without a [FacetRange] (a screenshot of an
 * older body) is drawn as it is, with nothing to edit.
 */
@Composable
private fun PriceRange(facet: Facet) {
    val range = facet.range
    val handler by rememberUpdatedState(LocalHaulActions.current)
    val scope = rememberCoroutineScope()
    var low by remember(range) { mutableStateOf(range?.low?.toString().orEmpty()) }
    var high by remember(range) { mutableStateOf(range?.high?.toString().orEmpty()) }
    // The thumbs while one is held, as fractions of the track; `null` draws them where the tree puts them.
    var held by remember(facet) { mutableStateOf<Pair<Float, Float>?>(null) }

    // Whether a `load` went: not for the bounds already applied, nor while the sheet follows nothing (B-54).
    fun apply(
        from: Int?,
        to: Int?,
    ): Boolean {
        val actions = handler
        if (range == null || actions == null || (from == range.low && to == range.high)) return false
        actions.handle(range.applying(from, to))
        return true
    }

    // What was typed just before Enter or a press elsewhere may not be in the field yet (B-76).
    val confirm: () -> Unit = {
        scope.launch {
            awaitTypedInput()
            apply(low.toIntOrNull(), high.toIntOrNull())
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (range == null) {
            PriceField("from", facet.min.orEmpty(), Modifier.weight(1f))
            PriceField("to", facet.max.orEmpty(), Modifier.weight(1f))
        } else {
            PriceInput("from", low, "0", PRICE_FROM_TAG, { low = it }, confirm, Modifier.weight(1f))
            PriceInput("to", high, range.top.toString(), PRICE_TO_TAG, { high = it }, confirm, Modifier.weight(1f))
        }
    }
    val (start, end) = held ?: ((facet.rangeStart ?: 0f) to (facet.rangeEnd ?: 1f))
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(20.dp)
            .testTag(PRICE_SLIDER_TAG)
            .then(
                if (range == null) {
                    Modifier
                } else {
                    Modifier.pointerInput(range) {
                        val track = size.width.toFloat()
                        // The thumb nearer the press is the one dragged, and it does not pass the other.
                        var lower = true
                        detectHorizontalDragGestures(
                            onDragStart = { at ->
                                val (s, e) = held ?: ((facet.rangeStart ?: 0f) to (facet.rangeEnd ?: 1f))
                                val x = at.x / track
                                lower = abs(x - s) < abs(x - e) || (x < s && s == e)
                                held = s to e
                            },
                            // The held thumb is where the pointer is, within its end of the track.
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                val (s, e) = held ?: return@detectHorizontalDragGestures
                                val x = change.position.x / track
                                held = if (lower) x.coerceIn(0f, e) to e else s to x.coerceIn(s, 1f)
                                held?.let { (ns, ne) ->
                                    low = bound(ns, range.top, atEnd = ns <= 0f)
                                    high = bound(ne, range.top, atEnd = ne >= 1f)
                                }
                            },
                            onDragEnd = {
                                val applied =
                                    held?.let { (s, e) ->
                                        apply(dollars(s, range.top, s <= 0f), dollars(e, range.top, e >= 1f))
                                    } ?: false
                                // Nothing went: the thumbs and the fields go back to the range applied.
                                if (!applied) {
                                    held = null
                                    low = range.low?.toString().orEmpty()
                                    high = range.high?.toString().orEmpty()
                                }
                            },
                            onDragCancel = {
                                held = null
                                low = range.low?.toString().orEmpty()
                                high = range.high?.toString().orEmpty()
                            },
                        )
                    }
                },
            ),
    ) {
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

/** The whole dollars at [fraction] of a track that ends at [top]; `null` — no bound — at the track's end. */
private fun dollars(
    fraction: Float,
    top: Int,
    atEnd: Boolean,
): Int? = if (atEnd) null else (fraction * top).roundToInt()

private fun bound(
    fraction: Float,
    top: Int,
    atEnd: Boolean,
): String = dollars(fraction, top, atEnd)?.toString().orEmpty()

@Composable
private fun PriceField(
    prefix: String,
    value: String,
    modifier: Modifier,
) {
    PriceBox(prefix, modifier) { Text(value, HaulType.text(15f, 600), softWrap = false) }
}

/**
 * A bound the shopper types: whole dollars, drawn after a «$» ([DOLLARS]); [placeholder] in the muted ink
 * while it is empty. [onConfirm] on Enter, «Done», and on leaving the field.
 */
@Composable
private fun PriceInput(
    prefix: String,
    value: String,
    placeholder: String,
    tag: String,
    onValue: (String) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    PriceBox(prefix, modifier) {
        BasicTextField(
            value,
            { typed -> onValue(typed.filter(Char::isDigit).take(MAX_DIGITS)) },
            Modifier
                .weight(1f)
                .testTag(tag)
                .onFocusChanged {
                    if (focused && !it.isFocused) onConfirm()
                    focused = it.isFocused
                }.onPreviewKeyEvent {
                    val enter = it.key == Key.Enter || it.key == Key.NumPadEnter
                    if (enter && it.type == KeyEventType.KeyDown) onConfirm()
                    enter
                },
            textStyle = HaulType.text(15f, 600),
            singleLine = true,
            cursorBrush = SolidColor(HaulColors.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConfirm() }),
            visualTransformation = DOLLARS,
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            "$$placeholder",
                            HaulType.text(15f).copy(color = HaulColors.outlineMuted),
                            softWrap = false,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun PriceBox(
    prefix: String,
    modifier: Modifier,
    value: @Composable RowScope.() -> Unit,
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
        value()
    }
}

/** A bound is typed as digits and drawn as dollars: «80» reads «$80»; an empty field stays empty. */
private val DOLLARS =
    VisualTransformation { text ->
        if (text.isEmpty()) {
            TransformedText(text, OffsetMapping.Identity)
        } else {
            TransformedText(
                AnnotatedString("$") + text,
                object : OffsetMapping {
                    override fun originalToTransformed(offset: Int): Int = offset + 1

                    override fun transformedToOriginal(offset: Int): Int = (offset - 1).coerceIn(0, text.length)
                },
            )
        }
    }

/** Six digits: a bound past $999,999 is no bound the store's prices reach. */
private const val MAX_DIGITS = 6

@Composable
private fun Thumb(modifier: Modifier) {
    Box(
        modifier
            .size(20.dp)
            .background(HaulColors.surfaceContainerLowest, CircleShape)
            .border(3.dp, HaulColors.primary, CircleShape),
    )
}

/**
 * An option that would show nothing — a count of 0, not ticked (B-75) — is drawn faded; the server sends it
 * with no action, so it presses nothing. A ticked one keeps its look and its press: that is how it is unticked.
 */
private fun Modifier.muted(option: FacetOption): Modifier =
    if (option.count == 0 && !option.selected) alpha(MUTED_ALPHA) else this

private const val MUTED_ALPHA = 0.4f

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
            Box(Modifier.size(size).muted(option).follows(option.action), contentAlignment = Alignment.Center) {
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
                box.muted(option).follows(option.action).padding(horizontal = 13.dp, vertical = 9.dp),
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
