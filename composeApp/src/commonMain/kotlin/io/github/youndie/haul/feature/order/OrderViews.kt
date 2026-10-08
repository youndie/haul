package io.github.youndie.haul.feature.order

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.HaulType.browserLeading
import io.github.youndie.haul.theme.LocalHaulCompact
import io.github.youndie.haul.ui.BalancedText
import io.github.youndie.haul.ui.HaulButton
import io.github.youndie.haul.ui.HaulIcons
import io.github.youndie.haul.ui.Icon
import io.github.youndie.haul.ui.LocalHaulActions
import io.github.youndie.haul.ui.OrderBody
import io.github.youndie.haul.ui.OrderFact
import io.github.youndie.haul.ui.OrderFactKind
import io.github.youndie.haul.ui.OrderItem
import io.github.youndie.haul.ui.OrderNotice
import io.github.youndie.haul.ui.OrderShipment
import io.github.youndie.haul.ui.OrderSteps
import io.github.youndie.haul.ui.OrderTotals
import io.github.youndie.haul.ui.PickupCode
import io.github.youndie.haul.ui.SummaryRow
import io.github.youndie.haul.ui.Text
import io.github.youndie.haul.ui.accented
import io.github.youndie.haul.ui.appendLink
import io.github.youndie.haul.ui.following
import io.github.youndie.haul.ui.follows
import io.github.youndie.haul.ui.gutter
import io.github.youndie.haul.ui.normal
import io.github.youndie.haul.ui.toneColor
import kotlin.math.roundToInt

// The Order screen (screen-order): the frame's header, then the crumbs and the title, and the order —
// where it is, its shipments — in one column with the summary beside it at 1440 and under it on a phone.
// The numbers are the artboards' (Order_Placed, _InTransit, _ReadyForPickup, _Delivered, _Cancelled and
// their _Phone twins). Links follow the tree's actions; «Reorder» is a command for the renderer to send.

/** The order under the header. [onReorder] sends «Reorder» (`OrderTotals.reorderUrl`). */
@Composable
public fun OrderBodyView(
    body: OrderBody,
    onReorder: () -> Unit = {},
) {
    val compact = LocalHaulCompact.current
    val crumbs = remember { CssBox() }
    val meta = remember(compact) { CssBox(if (compact) 14.dp else 20.dp) }
    val title = remember(compact) { CssBox(if (compact) 12.dp else 14.dp) }
    val lead = remember(compact) { CssBox(if (compact) 14.dp else 18.dp) }
    // The title's bottom margin collapses into the grid's top one when no lead stands between them.
    val grid = remember(compact) { CssBox(if (compact) 28.dp else 40.dp) }
    val left = remember { CssBox() }
    val summary = remember { CssBox() }
    val titleSize = if (compact) 56f else 112f
    val leadSize = if (compact) 17f else 20f
    CssColumn(
        Modifier.fillMaxWidth().padding(horizontal = gutter()),
        top = if (compact) 24.dp else 40.dp,
        bottom = if (compact) 64.dp else 96.dp,
    ) {
        Crumbs(body, Modifier.cssLines(crumbs, MONO_LINE))
        Text(body.meta, mono().copy(color = HaulColors.outline), Modifier.cssLines(meta, MONO_LINE))
        BalancedText(
            accented(body.title.breakableHyphens(), body.accent?.breakableHyphens()),
            HaulType
                .display(
                    titleSize,
                    800,
                    letterSpacing = if (compact) -0.01f else -0.03f,
                    lineHeight = TITLE_LEADING,
                ).copy(lineBreak = LineBreak.Simple),
            Modifier.cssLines(title, (titleSize * TITLE_LEADING).dp).widthIn(max = 1100.dp),
        )
        body.lead?.let {
            Text(
                it,
                HaulType
                    .text(leadSize, lineHeight = LEAD_LEADING)
                    .copy(color = HaulColors.onSurfaceVariant),
                Modifier.cssLines(lead, (leadSize * LEAD_LEADING).dp).widthIn(max = 760.dp),
            )
        }
        val column: @Composable (Modifier) -> Unit = { modifier ->
            CssColumn(modifier, gap = 16.dp, box = left) {
                body.steps?.let { Steps(it) }
                body.notice?.let { Notice(it) }
                body.pickup?.let { Pickup(it) }
                body.shipments.forEach { Shipment(it) }
            }
        }
        if (compact) {
            CssColumn(Modifier.fillMaxWidth(), gap = 16.dp, box = grid) {
                column(Modifier.fillMaxWidth())
                Summary(body.summary, onReorder, Modifier.fillMaxWidth(), summary)
            }
        } else {
            Row(Modifier.css(grid)) {
                column(Modifier.weight(1f))
                Spacer(Modifier.width(40.dp))
                Summary(body.summary, onReorder, Modifier.width(420.dp), summary)
            }
        }
    }
}

/** «Account / Orders / #HL-48302», the order itself in ink; the rest follow their links. */
@Composable
private fun Crumbs(
    body: OrderBody,
    modifier: Modifier,
) {
    val actions = LocalHaulActions.current
    Text(
        buildAnnotatedString {
            body.crumbs.dropLast(1).forEach {
                appendLink(it.label, it.action, actions)
                append(" / ")
            }
            withStyle(SpanStyle(color = HaulColors.onSurface)) {
                append(
                    body.crumbs
                        .lastOrNull()
                        ?.label
                        .orEmpty(),
                )
            }
        },
        modifier,
        style = mono().copy(color = HaulColors.outline),
        softWrap = false,
        maxLines = 1,
    )
}

/** Four equal columns, each a dot and the line to the next step, its label under them. */
@Composable
private fun Steps(steps: OrderSteps) {
    val compact = LocalHaulCompact.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(24.dp))
            .padding(horizontal = if (compact) 20.dp else 32.dp, vertical = if (compact) 22.dp else 28.dp),
    ) {
        steps.labels.forEachIndexed { index, label ->
            val reached = index <= steps.current
            val current = index == steps.current && !steps.arrived
            val done = reached && !current
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(16.dp)
                            .drawBehind {
                                val radius = size.minDimension / 2
                                if (current) {
                                    // `box-shadow: 0 0 0 4px` Cobalt around Acid: a ring outside the dot's box.
                                    drawCircle(HaulColors.primary, radius + RING.toPx(), center)
                                    drawCircle(HaulColors.secondaryContainer, radius, center)
                                } else {
                                    drawCircle(
                                        if (done) HaulColors.primary else HaulColors.outlineVariant,
                                        radius,
                                        center,
                                    )
                                }
                            },
                    )
                    if (index < steps.labels.lastIndex) {
                        val ahead = index < steps.current || (steps.arrived && index < steps.labels.lastIndex)
                        Box(
                            Modifier
                                .weight(1f)
                                .height(4.dp)
                                .background(if (ahead) HaulColors.primary else HaulColors.outlineVariant),
                        )
                    }
                }
                Text(
                    label,
                    normal(
                        if (compact) 12f else 14f,
                        600,
                    ).copy(color = if (reached) HaulColors.onSurface else HaulColors.outline),
                    Modifier.padding(end = 6.dp),
                )
            }
        }
    }
}

/**
 * A cancelled order's banner: the alert, why, what it means, and the way back. On a phone the button
 * takes a line of its own, as the canvas's wrapping row puts it.
 */
@Composable
private fun Notice(notice: OrderNotice) {
    val compact = LocalHaulCompact.current
    val text: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier) {
            Text(notice.title, normal(16f, 700))
            Text(notice.text, normal(14f).copy(color = HaulColors.onSurfaceVariant), Modifier.padding(top = 3.dp))
        }
    }
    val button: @Composable (Modifier) -> Unit = { modifier ->
        notice.action?.let { action ->
            HaulButton(
                action.label,
                modifier,
                height = 52.dp,
                radius = 14.dp,
                horizontal = 22.dp,
                fill = HaulColors.inverseSurface,
                content = HaulColors.onPrimary,
                textSize = 15f,
                onClick = following(action.action),
            )
        }
    }
    val box =
        Modifier
            .fillMaxWidth()
            .background(HaulColors.errorContainer, RoundedCornerShape(20.dp))
            .padding(horizontal = if (compact) 16.dp else 24.dp, vertical = if (compact) 16.dp else 20.dp)
    if (compact) {
        Column(box, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(HaulIcons.alert, 22.dp, HaulColors.error)
                text(Modifier.weight(1f))
            }
            button(Modifier.fillMaxWidth())
        }
    } else {
        // `align-items: center` with the button `align-self: flex-start`: the row is the button's height,
        // the icon and the text centred in it.
        Row(box, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(HaulIcons.alert, 22.dp, HaulColors.error)
            text(Modifier.weight(1f))
            button(Modifier.align(Alignment.Top))
        }
    }
}

/** The code on Acid: the label, the code in Bodoni, and how long the point keeps the parcel. */
@Composable
private fun Pickup(pickup: PickupCode) {
    val compact = LocalHaulCompact.current
    val codeSize = if (compact) 72f else 96f
    val column = remember { CssBox() }
    val digits = remember { CssBox(8.dp) }
    val code: @Composable () -> Unit = {
        CssColumn(box = column) {
            Text(pickup.label.uppercase(), HaulType.label(11f, 600, 0.08f), softWrap = false)
            Text(
                pickup.code,
                HaulType.display(codeSize, 800, letterSpacing = 0.06f, lineHeight = TITLE_LEADING),
                Modifier.cssLines(digits, (codeSize * TITLE_LEADING).dp),
                softWrap = false,
            )
        }
    }
    val text: @Composable () -> Unit = {
        Text(
            buildAnnotatedString {
                val at = pickup.highlight?.let { pickup.text.indexOf(it) } ?: -1
                if (at < 0) {
                    append(pickup.text)
                } else {
                    append(pickup.text.substring(0, at))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(pickup.highlight) }
                    append(pickup.text.substring(at + pickup.highlight.orEmpty().length))
                }
            },
            Modifier.widthIn(max = 380.dp),
            style = HaulType.text(15f, lineHeight = PICKUP_LEADING).browserLeading(),
        )
    }
    val box =
        Modifier
            .fillMaxWidth()
            .background(HaulColors.secondaryContainer, RoundedCornerShape(24.dp))
            .padding(horizontal = if (compact) 20.dp else 32.dp, vertical = if (compact) 24.dp else 28.dp)
    if (compact) {
        val card = remember { CssBox() }
        val lines = remember { CssBox(12.dp) }
        CssColumn(
            Modifier
                .fillMaxWidth()
                .background(HaulColors.secondaryContainer, RoundedCornerShape(24.dp))
                .padding(horizontal = 20.dp),
            top = 24.dp,
            bottom = 24.dp,
            box = card,
        ) {
            code()
            Box(Modifier.cssLines(lines, (15f * PICKUP_LEADING).dp)) { text() }
        }
    } else {
        Row(box, horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
            code()
            text()
        }
    }
}

/** One seller's card: the seller, the status and when, over a hairline, then its lines. */
@Composable
private fun Shipment(shipment: OrderShipment) {
    val compact = LocalHaulCompact.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(24.dp))
            .padding(
                start = if (compact) 16.dp else 28.dp,
                end = if (compact) 16.dp else 28.dp,
                top = if (compact) 4.dp else 8.dp,
                bottom = if (compact) 8.dp else 12.dp,
            ),
    ) {
        ShipmentHead(shipment, Modifier.padding(vertical = 18.dp))
        Hairline()
        shipment.items.forEachIndexed { index, item ->
            if (index > 0) Hairline()
            Item(item, Modifier.alpha(if (shipment.cancelled) 0.55f else 1f))
        }
    }
}

/**
 * The seller in bold, the status in a chip, and when — pushed to the right (`margin-left: auto`); when
 * the three do not fit one line, when goes to the next, still at the right, 10 px under (`flex-wrap`).
 */
@Composable
private fun ShipmentHead(
    shipment: OrderShipment,
    modifier: Modifier,
) {
    Layout(
        content = {
            Text(shipment.seller, normal(15f, 700), softWrap = false)
            Text(
                shipment.status,
                normal(13f, 600),
                Modifier
                    .background(HaulColors.background, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                softWrap = false,
            )
            Text(
                shipment.eta.uppercase(),
                HaulType.label(11f, 600, 0.05f).copy(color = HaulColors.primary),
                softWrap = false,
            )
        },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val (seller, chip, eta) = measurables.map { it.measure(loose) }
        val gap = 10.dp.roundToPx()
        val width = constraints.maxWidth
        val first = seller.width + gap + chip.width
        val oneLine = first + gap + eta.width <= width
        val lead = maxOf(seller.height, chip.height)
        val height = if (oneLine) maxOf(lead, eta.height) else lead + gap + eta.height
        layout(width, height) {
            val line = if (oneLine) height else lead
            seller.place(0, (line - seller.height) / 2)
            chip.place(seller.width + gap, (line - chip.height) / 2)
            if (oneLine) {
                eta.place(width - eta.width, (line - eta.height) / 2)
            } else {
                eta.place(width - eta.width, lead + gap)
            }
        }
    }
}

/** A line as bought: the tile, the title and its details, and «Write a review» once it has arrived. */
@Composable
private fun Item(
    item: OrderItem,
    modifier: Modifier,
) {
    val compact = LocalHaulCompact.current
    val review: @Composable (Modifier) -> Unit = { reviewModifier ->
        item.review?.let {
            Text(it.label, normal(15f, 600).copy(color = HaulColors.primary), reviewModifier.follows(it.action))
        }
    }
    Row(
        modifier.fillMaxWidth().padding(vertical = if (compact) 14.dp else 18.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tile = if (compact) 72.dp else 88.dp
        Box(Modifier.size(tile).background(toneColor(item.tone), RoundedCornerShape(14.dp)).follows(item.action))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                item.title,
                HaulType.text(if (compact) 15f else 16f, 600, lineHeight = 1.3f),
                Modifier.follows(item.action),
            )
            Text(item.details, normal(14f).copy(color = HaulColors.outline))
            if (compact) review(Modifier.padding(top = 4.dp))
        }
        if (!compact) review(Modifier)
    }
}

/** «Summary»: the rows, the total, the facts and the ways on. */
@Composable
private fun Summary(
    totals: OrderTotals,
    onReorder: () -> Unit,
    modifier: Modifier,
    box: CssBox,
) {
    val compact = LocalHaulCompact.current
    val padding = if (compact) 24.dp else 32.dp
    CssColumn(
        modifier
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(28.dp))
            .padding(horizontal = padding),
        gap = 18.dp,
        top = padding,
        bottom = padding,
        box = box,
    ) {
        Text(totals.title, HaulType.display(32f, 800, letterSpacing = -0.01f), softWrap = false)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { totals.rows.forEach { SummaryLine(it) } }
        Hairline()
        Row(Modifier.fillMaxWidth()) {
            Text(totals.totalLabel, normal(17f, 700), Modifier.alignByBaseline(), softWrap = false)
            Spacer(Modifier.weight(1f))
            Text(
                totals.total,
                HaulType
                    .display(48f, 800, letterSpacing = -0.01f)
                    .let {
                        if (totals.voided) {
                            it.copy(
                                color = HaulColors.outlineMuted,
                                textDecoration = TextDecoration.LineThrough,
                            )
                        } else {
                            it
                        }
                    },
                Modifier.alignByBaseline(),
                softWrap = false,
            )
        }
        totals.facts.forEach { Fact(it) }
        val reorder = totals.reorderLabel
        if (reorder != null || totals.returnLabel != null) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                reorder?.let {
                    HaulButton(
                        it,
                        Modifier.fillMaxWidth().testTag(REORDER_TAG),
                        height = 64.dp,
                        radius = 18.dp,
                        fill = HaulColors.primary,
                        content = HaulColors.onPrimary,
                        onClick = onReorder,
                    )
                }
                // «Return items»: drawn; its dialog is the returns' (B-21).
                totals.returnLabel?.let {
                    HaulButton(
                        it,
                        Modifier.fillMaxWidth(),
                        height = 56.dp,
                        radius = 18.dp,
                        border = HaulColors.onSurface,
                        textSize = 16f,
                    )
                }
            }
        }
        totals.back?.let {
            HaulButton(
                it.label,
                Modifier.fillMaxWidth(),
                height = 64.dp,
                radius = 18.dp,
                fill = HaulColors.primary,
                content = HaulColors.onPrimary,
                icon = HaulIcons.arrowRight,
                onClick = following(it.action),
            )
        }
    }
}

@Composable
private fun SummaryLine(row: SummaryRow) {
    val style = normal(15f)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(row.label, style.copy(color = HaulColors.outline), Modifier.weight(1f))
        Text(row.value, if (row.saving) normal(15f, 600).copy(color = HaulColors.error) else style, softWrap = false)
    }
}

/**
 * A fact under a hairline: its icon, the title in semibold and the detail under it, on lines of
 * `line-height: 1.45` — 21.75 and 20.3 px, which the summary adds up as they are ([CssBox]).
 */
@Composable
private fun Fact(fact: OrderFact) {
    val box = remember { CssBox() }
    val icon =
        when (fact.kind) {
            OrderFactKind.Card -> HaulIcons.lock
            OrderFactKind.Place -> HaulIcons.pin
            OrderFactKind.Points -> HaulIcons.star
        }
    Layout(
        content = {
            Hairline()
            Icon(icon, 18.dp, HaulColors.outline)
            Text(fact.title, HaulType.text(15f, 600, lineHeight = FACT_LEADING))
            Text(fact.detail, HaulType.text(14f, lineHeight = FACT_LEADING).copy(color = HaulColors.outline))
        },
        modifier = Modifier.fillMaxWidth().css(box),
    ) { measurables, constraints ->
        val (line, glyph, title, detail) = measurables
        val rule = line.measure(constraints.copy(minHeight = 0))
        val iconPlaceable = glyph.measure(Constraints())
        val start = iconPlaceable.width + 14.dp.roundToPx()
        val text = Constraints(maxWidth = (constraints.maxWidth - start).coerceAtLeast(0))
        val titlePlaceable = title.measure(text)
        val detailPlaceable = detail.measure(text)
        // `border-top: 1px; padding-top: 16px`, then the title's lines, `margin-top: 2px` and the detail's.
        val top = rule.height + 16.dp.toPx()
        val detailTop = top + lines(titlePlaceable.height, 15f * FACT_LEADING) + 2.dp.toPx()
        val height = detailTop + lines(detailPlaceable.height, 14f * FACT_LEADING)
        box.height = height
        layout(constraints.maxWidth, height.roundToInt()) {
            rule.place(0, 0)
            iconPlaceable.place(0, (top + 1.dp.toPx()).roundToInt())
            titlePlaceable.place(start, top.roundToInt())
            detailPlaceable.place(start, detailTop.roundToInt())
        }
    }
}

/** A text's height in CSS: its lines, counted off its measured [height], times the [line] in dp, unrounded. */
private fun Density.lines(
    height: Int,
    line: Float,
): Float {
    val px = line.dp.toPx()
    return (height / px).roundToInt() * px
}

/**
 * A block's height as the browser lays it out, finer than the whole pixel its layout reports — a line
 * box of 23.8 px is 23.8, and a column of them drifts a pixel every few lines when each is rounded.
 * [top] is the block's margin above it in its [CssColumn].
 */
private class CssBox(
    val top: Dp = 0.dp,
) {
    var height: Float = Float.NaN
}

/** This block stands in a [CssColumn] with [box]'s margin; its height is what [box] holds once measured, else its own. */
private fun Modifier.css(box: CssBox): Modifier = layoutId(box)

/** Text on lines of [lineHeight]: its height in CSS is its line count times the line, unrounded. */
private fun Modifier.cssLines(
    box: CssBox,
    lineHeight: Dp,
): Modifier =
    layoutId(box).layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        box.height = lines(placeable.height, lineHeight.value)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

/**
 * CSS's block flow: its children one under another, [gap] apart and each its own margin ([CssBox.top])
 * further, at positions summed from their CSS heights and rounded only where each is placed — as the
 * browser draws a box whose top falls at 631.2 at 631. [box], when given, receives the column's own CSS
 * height, [top] and [bottom] included, for the column it stands in.
 */
@Composable
private fun CssColumn(
    modifier: Modifier = Modifier,
    gap: Dp = 0.dp,
    top: Dp = 0.dp,
    bottom: Dp = 0.dp,
    box: CssBox? = null,
    content: @Composable () -> Unit,
) {
    Layout(content, if (box == null) modifier else modifier.css(box)) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        var y = top.toPx()
        val placed =
            measurables.mapIndexed { index, measurable ->
                val child = measurable.layoutId as? CssBox
                val placeable = measurable.measure(loose)
                if (index > 0) y += gap.toPx()
                y += child?.top?.toPx() ?: 0f
                val at = y.roundToInt()
                y += child?.height?.takeUnless { it.isNaN() } ?: placeable.height.toFloat()
                placeable to at
            }
        y += bottom.toPx()
        box?.height = y
        // As wide as its widest child, as a column is: a child that fills the width makes it fill too.
        val width = (placed.maxOfOrNull { it.first.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        layout(width, y.roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)) {
            placed.forEach { (placeable, at) -> placeable.place(0, at) }
        }
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(HaulColors.outlineVariant))
}

/**
 * A line may end after a hyphen, as the browser ends one inside «#HL-48302» (`Order_Placed_Phone`): the
 * line breaker here keeps a hyphen with the digits after it, so a zero-width space marks the break.
 */
private fun String.breakableHyphens(): String = replace("-", "-\u200B")

/** JetBrains Mono 12 on 1.4, the crumbs' and the meta line's. */
@Composable
private fun mono(): TextStyle = HaulType.label(12f, 500, 0.04f).copy(lineHeight = (12f * 1.4f).sp).browserLeading()

/** The facts' `line-height: 1.45`, set on their box and inherited as a number by the title and the detail. */
private const val FACT_LEADING = 1.45f

/** The title's and the pickup code's `line-height: .9`. */
private const val TITLE_LEADING = 0.9f

/** The lead's `line-height: 1.4`. */
private const val LEAD_LEADING = 1.4f

/** The pickup card's sentence, `line-height: 1.5`. */
private const val PICKUP_LEADING = 1.5f

/** The crumbs' and the meta line's line box: JetBrains Mono 12 on 1.4. */
private val MONO_LINE: Dp = (12f * 1.4f).dp

/** The ring around the current step's dot: `box-shadow: 0 0 0 4px`. */
private val RING: Dp = 4.dp

/** «Reorder», for the tests that press it. */
internal const val REORDER_TAG: String = "order-reorder"
