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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
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
    Column(
        Modifier.fillMaxWidth().padding(
            start = gutter(),
            end = gutter(),
            top = if (compact) 24.dp else 40.dp,
            bottom = if (compact) 64.dp else 96.dp,
        ),
    ) {
        Crumbs(body, Modifier.padding(bottom = if (compact) 14.dp else 20.dp))
        Text(body.meta, mono().copy(color = HaulColors.outline))
        BalancedText(
            accented(body.title.breakableHyphens(), body.accent?.breakableHyphens()),
            HaulType
                .display(
                    if (compact) 56f else 112f,
                    800,
                    letterSpacing = if (compact) -0.01f else -0.03f,
                    lineHeight = 0.9f,
                ).copy(lineBreak = LineBreak.Simple),
            // The title's bottom margin collapses into the grid's top one when no lead stands between them.
            Modifier
                .padding(
                    top = if (compact) 12.dp else 14.dp,
                    bottom =
                        if (body.lead == null) {
                            0.dp
                        } else if (compact) {
                            14.dp
                        } else {
                            18.dp
                        },
                ).widthIn(max = 1100.dp),
        )
        body.lead?.let {
            Text(
                it,
                HaulType
                    .text(if (compact) 17f else 20f, lineHeight = 1.4f)
                    .copy(color = HaulColors.onSurfaceVariant),
                Modifier.widthIn(max = 760.dp),
            )
        }
        val column: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                body.steps?.let { Steps(it) }
                body.notice?.let { Notice(it) }
                body.pickup?.let { Pickup(it) }
                body.shipments.forEach { Shipment(it) }
            }
        }
        val top = if (compact) 28.dp else 40.dp
        if (compact) {
            Column(Modifier.padding(top = top), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                column(Modifier.fillMaxWidth())
                Summary(body.summary, onReorder, Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.padding(top = top)) {
                column(Modifier.weight(1f))
                Spacer(Modifier.width(40.dp))
                Summary(body.summary, onReorder, Modifier.width(420.dp))
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
    val code: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(pickup.label.uppercase(), HaulType.label(11f, 600, 0.08f), softWrap = false)
            Text(
                pickup.code,
                HaulType.display(if (compact) 72f else 96f, 800, letterSpacing = 0.06f, lineHeight = 0.9f),
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
            style = HaulType.text(15f, lineHeight = 1.5f).browserLeading(),
        )
    }
    val box =
        Modifier
            .fillMaxWidth()
            .background(HaulColors.secondaryContainer, RoundedCornerShape(24.dp))
            .padding(horizontal = if (compact) 20.dp else 32.dp, vertical = if (compact) 24.dp else 28.dp)
    if (compact) {
        Column(box, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            code()
            text()
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
) {
    val compact = LocalHaulCompact.current
    Column(
        modifier
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(28.dp))
            .padding(if (compact) 24.dp else 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
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

/** A fact under a hairline: its icon, the title in semibold and the detail under it. */
@Composable
private fun Fact(fact: OrderFact) {
    Column {
        Hairline()
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val icon =
                when (fact.kind) {
                    OrderFactKind.Card -> HaulIcons.lock
                    OrderFactKind.Place -> HaulIcons.pin
                    OrderFactKind.Points -> HaulIcons.star
                }
            Icon(icon, 18.dp, HaulColors.outline, Modifier.padding(top = 1.dp))
            Column {
                Text(fact.title, HaulType.text(15f, 600, lineHeight = FACT_LEADING))
                Text(
                    fact.detail,
                    HaulType.text(14f, lineHeight = FACT_LEADING).copy(color = HaulColors.outline),
                    Modifier.padding(top = 2.dp),
                )
            }
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

/** The ring around the current step's dot: `box-shadow: 0 0 0 4px`. */
private val RING: Dp = 4.dp

/** «Reorder», for the tests that press it. */
internal const val REORDER_TAG: String = "order-reorder"
