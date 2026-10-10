package io.github.youndie.haul.feature.product

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.feature.cart.linePress
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.LocalHaulCompact
import io.github.youndie.haul.ui.DeliveryLine
import io.github.youndie.haul.ui.HaulButton
import io.github.youndie.haul.ui.HaulIcons
import io.github.youndie.haul.ui.Heart
import io.github.youndie.haul.ui.Highlight
import io.github.youndie.haul.ui.Icon
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.LocalLinkCopier
import io.github.youndie.haul.ui.PhotoOrPlaceholder
import io.github.youndie.haul.ui.ProductDetails
import io.github.youndie.haul.ui.SAVE
import io.github.youndie.haul.ui.SellerSummary
import io.github.youndie.haul.ui.Text
import io.github.youndie.haul.ui.UNSAVE
import io.github.youndie.haul.ui.VariantGroup
import io.github.youndie.haul.ui.accented
import io.github.youndie.haul.ui.follows
import io.github.youndie.haul.ui.gutter
import io.github.youndie.haul.ui.hatching
import io.github.youndie.haul.ui.pressable
import io.github.youndie.haul.ui.rememberHeartPress
import io.github.youndie.haul.ui.toneColor
import io.github.youndie.kompot.KompotAction
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// The top of the product page (screen-product): the gallery, the identity and choices, the buy box.
// At 1440 the three stand side by side (1.3fr, 1fr, 384 px, 40 apart); on a phone they stack, the buy
// box between the rating and the choices. Numbers are the artboards' (Product_Description,
// Product_OutOfStock and their _Phone twins).

/** The details, with the space the page leaves under the breadcrumbs. */
@Composable
public fun ProductDetailsView(details: ProductDetails) {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    if (compact) {
        Column(
            Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Photo(details, Modifier.fillMaxWidth(), radius = 24.dp)
            Brand(details)
            Title(details, compact = true, Modifier.negativeTop(6.dp))
            Rating(details)
            BuyBox(details, compact = true, Modifier.fillMaxWidth())
            details.variants.forEach { Variants(it) }
            Highlights(details.highlights)
            AllSpecifications(details)
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            // The canvas's thumbnails beside the photo drew photos no product has; the product's one photo
            // takes the gallery's width (B-71).
            Photo(details, Modifier.weight(1.3f), radius = 28.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Brand(details)
                Title(details, compact = false, Modifier.negativeTop(8.dp))
                Rating(details)
                details.variants.forEach { Variants(it) }
                Highlights(details.highlights)
                AllSpecifications(details)
            }
            BuyBox(details, compact = false, Modifier.width(384.dp))
        }
    }
}

/** CSS's negative top margin: the item moves up by [by] and pulls everything under it along. */
private fun Modifier.negativeTop(by: Dp): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val shift = by.roundToPx()
        layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) { placeable.place(0, -shift) }
    }

@Composable
private fun Photo(
    details: ProductDetails,
    modifier: Modifier,
    radius: Dp,
) {
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(radius)),
    ) {
        // The stored photo (B-30) over the canvas's placeholder tile, which stays while there is none.
        PhotoOrPlaceholder(details.photo, Modifier.matchParentSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(toneColor(details.photoTone))
                    .hatching(period = 14f),
            ) {
                Text(
                    details.photoLabel.uppercase(),
                    HaulType.label(11f, 500, 0.06f).copy(color = HaulColors.tileLabel),
                    Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 20.dp),
                )
            }
        }
        details.badge?.let {
            Text(
                it,
                HaulType.text(14f, 800),
                Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(HaulColors.secondaryContainer, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Row(
            Modifier.align(Alignment.TopEnd).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The heart is the Saved list's (B-20): the customer's command, a guest's way to sign in.
            Box(
                Modifier
                    .pressable(rememberHeartPress(details.heartCommand, details.heartAction))
                    .semantics { contentDescription = if (details.saved) UNSAVE else SAVE }
                    .size(44.dp)
                    .background(HaulColors.surfaceContainerLowest, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Heart(details.saved, 20.dp) }
            details.share?.let { Share(it) }
        }
    }
}

/** The buy box's «Save» out of stock, for the tests that press it. */
internal const val SAVE_TAG: String = "buy-box-save"

/** What the share button says it does, and says once it has: its description to a screen reader and the tests. */
internal const val COPY_LINK: String = "Copy link"
internal const val LINK_COPIED: String = "Link copied"

/** How long the share button shows its check after a link was copied. */
private const val COPIED_MS = 2_000L

/**
 * The share button (B-71): copies the link to the product at its SKU — [path], on the page's own origin — through
 * [LocalLinkCopier], then shows a check for a moment. Nobody to copy it (a screenshot) presses nothing.
 */
@Composable
private fun Share(path: String) {
    val copier = LocalLinkCopier.current
    var copied by remember(path) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_MS)
            copied = false
        }
    }
    Box(
        Modifier
            .pressable(copier?.let { { it.copy(path) { done -> if (done) copied = true } } })
            .semantics { contentDescription = if (copied) LINK_COPIED else COPY_LINK }
            .size(44.dp)
            .background(HaulColors.surfaceContainerLowest, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (copied) HaulIcons.check else HaulIcons.share, 20.dp, HaulColors.onSurface)
    }
}

/** The brand, which opens its products in this product's category (B-71). */
@Composable
private fun Brand(details: ProductDetails) {
    Text(
        details.brand.uppercase(),
        HaulType.label(13f, 600, 0.08f).copy(color = linkColour(details.brandAction)),
        Modifier.follows(details.brandAction),
    )
}

/** A link's words are the theme's link colour only while there is somewhere to go (B-71). */
private fun linkColour(action: KompotAction?): Color =
    if (action !=
        null
    ) {
        HaulColors.primary
    } else {
        HaulColors.onSurfaceVariant
    }

@Composable
private fun Title(
    details: ProductDetails,
    compact: Boolean,
    modifier: Modifier,
) {
    Text(
        accented(details.title, details.accent),
        modifier,
        style = HaulType.display(if (compact) 34f else 48f, 800, letterSpacing = -0.02f),
    )
}

/** The stars, the rating and «2,341 reviews», which open the reviews tab (B-71); then «12K bought this month». */
@Composable
private fun Rating(details: ProductDetails) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.follows(details.ratingAction),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val filled = details.rating.toFloatOrNull()?.roundToInt() ?: STARS
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(STARS) {
                    Icon(
                        HaulIcons.star,
                        16.dp,
                        if (it <
                            filled
                        ) {
                            HaulColors.onSurface
                        } else {
                            HaulColors.outlineControl
                        },
                    )
                }
            }
            Text(details.rating, HaulType.text(15f, 700), softWrap = false)
            Text(
                details.reviews,
                HaulType.text(15f, 500).copy(color = linkColour(details.ratingAction)),
                softWrap = false,
            )
        }
        details.bought?.let { Text(it.uppercase(), HaulType.label(11f, 600, 0.08f).copy(color = HaulColors.outline)) }
    }
}

private const val STARS = 5

/** A choice: colour swatches («Color: Midnight Black»), or pills under the group's name. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Variants(group: VariantGroup) {
    val swatches = group.options.any { it.swatch != null }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (swatches) {
            val selected = group.options.firstOrNull { it.selected }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = HaulColors.outline)) { append("${group.name}:") }
                    append(" ")
                    withStyle(SpanStyle(fontWeight = FontWeight(600))) { append(group.selectedLabel) }
                    if (selected != null && !selected.available) {
                        append(" ")
                        withStyle(
                            SpanStyle(color = HaulColors.error, fontWeight = FontWeight(600)),
                        ) { append("· Out of stock") }
                    }
                },
                style = HaulType.text(15f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                group.options.forEach { option ->
                    // The selected ring is CSS's box-shadow: 2 px of Paper, then 2 px of Cobalt, outside.
                    Box(Modifier.size(64.dp).follows(option.action), contentAlignment = Alignment.Center) {
                        if (option.selected) {
                            Box(Modifier.requiredSize(72.dp).background(HaulColors.primary, RoundedCornerShape(18.dp)))
                            Box(
                                Modifier
                                    .requiredSize(
                                        68.dp,
                                    ).background(HaulColors.background, RoundedCornerShape(16.dp)),
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                                .background(toneColor(option.swatch.orEmpty())),
                        ) {
                            // An option that cannot be had is struck through, corner to corner.
                            if (!option.available) {
                                Box(
                                    Modifier
                                        .align(Alignment.Center)
                                        .requiredSize(84.dp, 2.dp)
                                        .rotate(-45f)
                                        .background(HaulColors.outlineMuted),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Text(group.name, HaulType.text(15f).copy(color = HaulColors.outline))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                group.options.forEach { option ->
                    val shape = RoundedCornerShape(12.dp)
                    val box =
                        if (option.selected) {
                            Modifier.border(2.dp, HaulColors.onSurface, shape).padding(2.dp)
                        } else {
                            Modifier
                                .background(HaulColors.surfaceContainerLowest, shape)
                                .border(1.dp, HaulColors.outlineVariant, shape)
                                .padding(1.dp)
                        }
                    Text(
                        option.label,
                        HaulType.text(15f, 500),
                        box.follows(option.action).padding(horizontal = 16.dp, vertical = 12.dp),
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/** The key facts as a two-column table under a hairline. */
@Composable
private fun Highlights(rows: List<Highlight>) {
    Column(
        Modifier.fillMaxWidth().topRule().padding(top = 20.dp),
    ) {
        LabelledRows(
            rows,
            rowGap = 12.dp,
            columnGap = 24.dp,
            label = { Text(it, HaulType.text(15f).copy(color = HaulColors.outline)) },
            value = { Text(it, HaulType.text(15f)) },
        )
    }
}

/** «All specifications», which opens the specifications tab (B-71). */
@Composable
private fun AllSpecifications(details: ProductDetails) {
    Text(
        "All specifications",
        HaulType.text(15f, 600).copy(color = linkColour(details.specificationsAction)),
        Modifier.follows(details.specificationsAction),
    )
}

/** A hairline along the top edge, inside the box: CSS's `border-top: 1px solid`. */
internal fun Modifier.topRule(): Modifier =
    drawBehind {
        drawRect(
            HaulColors.outlineVariant,
            size =
                androidx.compose.ui.geometry
                    .Size(size.width, 1f),
        )
    }.padding(top = 1.dp)

/**
 * CSS's `grid-template-columns: auto 1fr`: the labels as wide as the widest of them, the values in the
 * rest, each row as tall as its taller cell.
 */
@Composable
internal fun LabelledRows(
    rows: List<Highlight>,
    rowGap: Dp,
    columnGap: Dp,
    label: @Composable (String) -> Unit,
    value: @Composable (String) -> Unit,
) {
    androidx.compose.ui.layout.Layout(
        content = {
            rows.forEach {
                label(it.title)
                value(it.text)
            }
        },
    ) { measurables, constraints ->
        val gapX = columnGap.roundToPx()
        val gapY = rowGap.roundToPx()
        val labels = measurables.filterIndexed { i, _ -> i % 2 == 0 }
        val values = measurables.filterIndexed { i, _ -> i % 2 == 1 }
        val labelWidth = labels.maxOfOrNull { it.maxIntrinsicWidth(constraints.maxHeight) } ?: 0
        val valueWidth = (constraints.maxWidth - labelWidth - gapX).coerceAtLeast(0)
        val placedLabels =
            labels.map {
                it.measure(
                    androidx.compose.ui.unit
                        .Constraints(maxWidth = labelWidth),
                )
            }
        val placedValues =
            values.map {
                it.measure(
                    androidx.compose.ui.unit
                        .Constraints(maxWidth = valueWidth),
                )
            }
        val heights = placedLabels.indices.map { maxOf(placedLabels[it].height, placedValues[it].height) }
        val height = heights.sum() + gapY * (heights.size - 1).coerceAtLeast(0)
        layout(constraints.maxWidth, height) {
            var y = 0
            placedLabels.indices.forEach {
                placedLabels[it].place(0, y)
                placedValues[it].place(labelWidth + gapX, y)
                y += heights[it] + gapY
            }
        }
    }
}

/**
 * The buy box: the price for the chosen SKU, the buttons, then the delivery lines — or, out of stock,
 * «Save» and where to turn — and the seller, drawn as words: there is no seller's page to open (B-71).
 * «Add to cart» and «Buy now» send the line changes the tree gave them (B-48); out of stock the tree gives
 * none and they are drawn greyed, pressing nothing, and so is «Add to cart» alone at the line's limit (B-75).
 * Under them, once the cart holds the SKU shown, how many and the way to the cart ([ProductDetails.inCart],
 * B-75): what an «Add to cart» answered with an `update` changes here.
 */
@Composable
private fun BuyBox(
    details: ProductDetails,
    compact: Boolean,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(if (compact) 24.dp else 28.dp)
    Box(modifier) {
        // `box-shadow: 0 1px 0` — a hairline following the box's lower edge.
        Box(Modifier.matchParentSize().offset(y = 1.dp).background(HaulColors.outlineVariant, shape))
        Column(
            Modifier
                .fillMaxWidth()
                .background(HaulColors.surfaceContainerLowest, shape)
                .padding(if (compact) 20.dp else 28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Price(details, compact)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val live = details.inStock
                val adds = live && details.add != null
                HaulButton(
                    "Add to cart",
                    Modifier.fillMaxWidth(),
                    height = 64.dp,
                    radius = 18.dp,
                    fill = if (adds) HaulColors.primary else HaulColors.outlineVariant,
                    content = if (adds) HaulColors.onPrimary else HaulColors.outlineMuted,
                    textSize = 18f,
                    icon = HaulIcons.bag,
                    iconSize = 22.dp,
                    iconFirst = true,
                    onClick = linePress(details.add),
                )
                HaulButton(
                    "Buy now",
                    Modifier.fillMaxWidth(),
                    height = 56.dp,
                    radius = 18.dp,
                    fill = if (live) null else HaulColors.outlineVariant,
                    content = if (live) HaulColors.onSurface else HaulColors.outlineMuted,
                    border = if (live) HaulColors.onSurface else null,
                    textSize = 16f,
                    onClick = linePress(details.buy),
                )
                details.inCart?.let { InCart(it) }
                if (!live) {
                    // The heart's twin (B-71): «Saved» once the product is on the Saved list, and pressing it
                    // takes it off, as the heart over the photo does.
                    HaulButton(
                        if (details.saved) "Saved" else "Save",
                        Modifier.fillMaxWidth().testTag(SAVE_TAG),
                        height = 56.dp,
                        radius = 18.dp,
                        border = HaulColors.onSurface,
                        textSize = 16f,
                        icon = if (details.saved) HaulIcons.heartFilled else HaulIcons.heart,
                        iconFirst = true,
                        onClick = rememberHeartPress(details.heartCommand, details.heartAction),
                    )
                }
            }
            val advice = details.stockAdvice
            if (!details.inStock && advice != null) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight(600))) { append(advice.title) }
                        if (advice.text.isNotEmpty()) append(" " + advice.text)
                    },
                    Modifier
                        .fillMaxWidth()
                        .background(HaulColors.background, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    style = HaulType.text(14f, lineHeight = 1.5f),
                )
            } else {
                Column(
                    Modifier.fillMaxWidth().topRule().padding(top = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    details.delivery.forEach { Delivery(it) }
                }
            }
            Seller(details.seller)
        }
    }
}

/** The tag of the buy box's «in your cart» line, for the tests that wait for it. */
public const val IN_CART_TAG: String = "in-cart"

/** «2 in your cart», and «View cart» where it leads (B-75). No artboard draws it: the theme's tokens. */
@Composable
private fun InCart(note: Link) {
    Row(
        Modifier.fillMaxWidth().testTag(IN_CART_TAG).padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(HaulIcons.checkBold, 18.dp, HaulColors.primary)
        Text(note.label, HaulType.text(15f, 600), Modifier.weight(1f))
        note.action?.let {
            Text("View cart", HaulType.text(15f, 700).copy(color = HaulColors.primary), Modifier.follows(it))
        }
    }
}

@Composable
private fun Price(
    details: ProductDetails,
    compact: Boolean,
) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                details.price,
                HaulType
                    .display(
                        if (compact) 60f else 72f,
                        800,
                        letterSpacing = if (compact) -0.01f else -0.03f,
                        lineHeight = 0.9f,
                    ).copy(color = if (details.inStock) HaulColors.onSurface else HaulColors.outlineMuted),
                softWrap = false,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    !details.inStock -> {
                        Pill(
                            details.stockNote ?: "Out of stock",
                            HaulColors.inverseSurface,
                            HaulType.text(13f, 700).copy(color = HaulColors.onPrimary),
                        )
                    }

                    details.discount != null -> {
                        Pill(details.discount.orEmpty(), HaulColors.secondaryContainer, HaulType.text(14f, 800))
                    }
                }
                details.oldPrice?.let {
                    Text(
                        it,
                        HaulType
                            .text(
                                16f,
                            ).copy(color = HaulColors.outlineMuted, textDecoration = TextDecoration.LineThrough),
                    )
                }
            }
        }
        val pay = details.haulPay
        if (details.inStock && pay != null) {
            val strong = details.haulPayStrong
            val at = strong?.let { pay.indexOf(it) } ?: -1
            Text(
                buildAnnotatedString {
                    if (at < 0) {
                        append(pay)
                    } else {
                        append(pay.substring(0, at))
                        withStyle(SpanStyle(fontWeight = FontWeight(700))) { append(strong) }
                        append(pay.substring(at + strong!!.length))
                    }
                },
                Modifier.padding(top = 12.dp),
                style = HaulType.text(14f).copy(color = HaulColors.onSurfaceVariant),
            )
        }
    }
}

@Composable
private fun Pill(
    text: String,
    fill: androidx.compose.ui.graphics.Color,
    style: androidx.compose.ui.text.TextStyle,
) {
    Text(
        text,
        style,
        Modifier.background(fill, CircleShape).padding(horizontal = 9.dp, vertical = 5.dp),
        softWrap = false,
    )
}

@Composable
private fun Delivery(line: DeliveryLine) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(line.title, HaulType.text(15f, 600))
            Text(line.detail, HaulType.text(14f).copy(color = HaulColors.outline), Modifier.padding(top = 3.dp))
        }
        line.price?.let { Text(it, HaulType.text(14f, 600), softWrap = false) }
    }
}

@Composable
private fun Seller(seller: SellerSummary) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(HaulColors.background, RoundedCornerShape(18.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).background(HaulColors.inverseSurface, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                seller.initial,
                HaulType.display(22f, 800, letterSpacing = -0.01f).copy(color = HaulColors.secondaryContainer),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(seller.name, HaulType.text(15f, 600))
            Text(seller.meta, HaulType.text(14f).copy(color = HaulColors.outline), Modifier.padding(top = 3.dp))
        }
    }
}
