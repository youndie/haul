package io.github.youndie.haul.feature.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.feature.identity.SignInActions
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.LocalHaulCompact
import io.github.youndie.haul.ui.CartBody
import io.github.youndie.haul.ui.CartGroup
import io.github.youndie.haul.ui.CartLine
import io.github.youndie.haul.ui.CartSelection
import io.github.youndie.haul.ui.HaulButton
import io.github.youndie.haul.ui.HaulIcons
import io.github.youndie.haul.ui.Icon
import io.github.youndie.haul.ui.OrderSummary
import io.github.youndie.haul.ui.PromoField
import io.github.youndie.haul.ui.SummaryRow
import io.github.youndie.haul.ui.Text
import io.github.youndie.haul.ui.following
import io.github.youndie.haul.ui.follows
import io.github.youndie.haul.ui.gutter
import io.github.youndie.haul.ui.pressable
import io.github.youndie.haul.ui.toneColor
import io.github.youndie.kompot.standard.NavigateAction

// The Cart screen's body (screen-cart): the lines grouped by seller beside the order summary at 1440,
// the summary under them on a phone. The numbers are the artboards' (Cart_Content, Cart_ItemChanged,
// Cart_PromoApplied, Cart_PromoError, Cart_Guest and their _Phone twins). Every press is a command
// ([CartCommand]) for [onCommand], or a link the tree carries, followed through `follows`.

/** The cart with lines; [onCommand] sends the commands a press makes, in order, as one change. */
@Composable
public fun CartBodyView(
    body: CartBody,
    onCommand: (List<CartCommand>) -> Unit,
) {
    val compact = LocalHaulCompact.current
    val lines = body.groups.flatMap { it.lines }
    val main: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SelectionRow(body.selection, lines, onCommand)
            body.groups.forEach { GroupCard(it, body.selection, onCommand) }
        }
    }
    if (compact) {
        Column(
            Modifier.fillMaxWidth().padding(start = gutter(), end = gutter(), bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            main(Modifier.fillMaxWidth())
            SummaryCard(body.summary, onCommand, Modifier.fillMaxWidth())
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(start = gutter(), end = gutter(), bottom = 96.dp)) {
            main(Modifier.weight(1f))
            Spacer(Modifier.width(40.dp))
            SummaryCard(body.summary, onCommand, Modifier.width(420.dp))
        }
    }
}

/** «Select all», ticked, partly ticked (a line that cannot be selected is not) or empty; «Delete selected». */
@Composable
private fun SelectionRow(
    selection: CartSelection,
    lines: List<CartLine>,
    onCommand: (List<CartCommand>) -> Unit,
) {
    val selectedLines = lines.filter { it.selected }
    val box =
        when {
            selection.allSelected && selectedLines.size == lines.size -> Tick.Checked
            selection.selectedCount > 0 -> Tick.Partial
            else -> Tick.Empty
        }
    val style = HaulType.text(15f, 600)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .defaultMinSize(minHeight = if (LocalHaulCompact.current) 44.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.testTag(SELECT_ALL_TAG).pressable {
                // Every line that can be selected follows the box: ticked when it was not all ticked.
                val target = !selection.allSelected
                val changes =
                    lines
                        .filter { it.selectable && it.selected != target }
                        .map { CartCommand.ChangeLine(it.url, LineChange(selected = target)) }
                if (changes.isNotEmpty()) onCommand(changes)
            },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(box)
            Text(selection.selectAllLabel, style, softWrap = false)
        }
        Spacer(Modifier.weight(1f))
        Text(
            selection.deleteLabel,
            style.copy(color = HaulColors.primary),
            Modifier.testTag(DELETE_SELECTED_TAG).pressable(
                if (selectedLines.isEmpty()) {
                    null
                } else {
                    {
                        onCommand(
                            listOf(
                                CartCommand.RemoveLines(
                                    selection.linesUrl,
                                    LinesRemoval(selectedLines.map { it.skuId }),
                                ),
                            ),
                        )
                    }
                },
            ),
            softWrap = false,
        )
    }
}

private enum class Tick { Checked, Partial, Empty }

/** The canvas's 22 px box: Cobalt with a tick or a bar, or white with a grey border. */
@Composable
private fun Checkbox(
    tick: Tick,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    when (tick) {
        Tick.Checked -> {
            Box(modifier.size(22.dp).background(HaulColors.primary, shape), contentAlignment = Alignment.Center) {
                Icon(HaulIcons.checkBold, 14.dp, HaulColors.onPrimary)
            }
        }

        Tick.Partial -> {
            Box(modifier.size(22.dp).background(HaulColors.primary, shape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(10.dp, 3.dp).background(HaulColors.onPrimary, RoundedCornerShape(2.dp)))
            }
        }

        Tick.Empty -> {
            Box(
                modifier
                    .size(22.dp)
                    .background(HaulColors.surfaceContainerLowest, shape)
                    .border(2.dp, HaulColors.outlineControl, shape),
            )
        }
    }
}

/** One seller's card: the name and how the lines arrive over a hairline, then the lines. */
@Composable
private fun GroupCard(
    group: CartGroup,
    selection: CartSelection,
    onCommand: (List<CartCommand>) -> Unit,
) {
    val compact = LocalHaulCompact.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(24.dp))
            .padding(horizontal = if (compact) 16.dp else 28.dp, vertical = if (compact) 4.dp else 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = if (compact) 16.dp else 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(group.seller, HaulType.text(15f, 700), Modifier.weight(1f), softWrap = false)
            Text(
                group.delivery.uppercase(),
                HaulType.label(11f, 600, 0.05f).copy(color = HaulColors.primary),
                softWrap = false,
            )
        }
        Hairline()
        group.lines.forEachIndexed { index, line ->
            if (compact) PhoneLine(line, selection, onCommand) else WideLine(line, selection, onCommand)
            if (index < group.lines.lastIndex) Hairline()
        }
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(HaulColors.outlineVariant))
}

/** A line at 1440: box, tile, title and actions, stepper, price — the change notice under all but the box. */
@Composable
private fun WideLine(
    line: CartLine,
    selection: CartSelection,
    onCommand: (List<CartCommand>) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            LineBox(line, onCommand)
            Tile(line, 128.dp, 16.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(line.title, HaulType.text(17f, 600, lineHeight = 1.3f), Modifier.follows(line.action))
                Text(line.options, HaulType.text(14f).copy(color = HaulColors.outline))
                LineActions(line, selection, onCommand, Modifier.padding(top = 8.dp))
            }
            Stepper(line, onCommand)
            Column(Modifier.width(140.dp), horizontalAlignment = Alignment.End) {
                Text(line.price, HaulType.display(30f, 800, letterSpacing = -0.01f), softWrap = false)
                line.oldPrice?.let {
                    Text(
                        it,
                        HaulType
                            .text(
                                14f,
                            ).copy(color = HaulColors.outlineMuted, textDecoration = TextDecoration.LineThrough),
                        Modifier.padding(top = 6.dp),
                        softWrap = false,
                    )
                }
                line.each?.let {
                    Text(
                        it,
                        HaulType.text(13f).copy(color = HaulColors.outlineMuted),
                        Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        // The notice spans the grid from the tile's column: the box and its gap stay clear.
        ChangeNotice(line, onCommand, Modifier.padding(start = 22.dp + 24.dp, top = 20.dp))
    }
}

/** A line on a phone: box, tile and title with the price; the actions and the stepper on a row of their own. */
@Composable
private fun PhoneLine(
    line: CartLine,
    selection: CartSelection,
    onCommand: (List<CartCommand>) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LineBox(line, onCommand)
            Tile(line, 88.dp, 14.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(line.title, HaulType.text(15f, 600, lineHeight = 1.3f), Modifier.follows(line.action))
                Text(line.options, HaulType.text(14f).copy(color = HaulColors.outline))
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        line.price,
                        HaulType.display(24f, 800, letterSpacing = -0.01f),
                        Modifier.alignByBaseline(),
                        softWrap = false,
                    )
                    line.oldPrice?.let {
                        Text(
                            it,
                            HaulType
                                .text(
                                    13f,
                                ).copy(color = HaulColors.outlineMuted, textDecoration = TextDecoration.LineThrough),
                            Modifier.alignByBaseline(),
                            softWrap = false,
                        )
                    }
                    line.each?.let {
                        Text(
                            it,
                            HaulType.text(13f).copy(color = HaulColors.outlineMuted),
                            Modifier.alignByBaseline(),
                            softWrap = false,
                        )
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineActions(line, selection, onCommand, Modifier.weight(1f))
            Stepper(line, onCommand)
        }
        ChangeNotice(line, onCommand)
    }
}

/** The line's box: pressing it ticks or unticks the line, while the line can be selected. */
@Composable
private fun LineBox(
    line: CartLine,
    onCommand: (List<CartCommand>) -> Unit,
) {
    Checkbox(
        if (line.selected) Tick.Checked else Tick.Empty,
        Modifier.testTag(lineTag("box", line)).pressable(
            if (line.selectable) {
                { onCommand(listOf(CartCommand.ChangeLine(line.url, LineChange(selected = !line.selected)))) }
            } else {
                null
            },
        ),
    )
}

/** The product's tile in its tone; pressing it opens the product. */
@Composable
private fun Tile(
    line: CartLine,
    size: Dp,
    radius: Dp,
) {
    Box(Modifier.size(size).background(toneColor(line.tone), RoundedCornerShape(radius)).follows(line.action))
}

/** «Save for later» (B-20: drawn, no command yet) and «Remove». */
@Composable
private fun LineActions(
    line: CartLine,
    selection: CartSelection,
    onCommand: (List<CartCommand>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = HaulType.text(14f, 500).copy(color = HaulColors.outline)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(line.saveLabel, style, softWrap = false)
        Text(
            line.removeLabel,
            style,
            Modifier.testTag(lineTag("remove", line)).pressable {
                onCommand(listOf(CartCommand.RemoveLines(selection.linesUrl, LinesRemoval(listOf(line.skuId)))))
            },
            softWrap = false,
        )
    }
}

/** − quantity +: one less down to one, one more up to [CartLine.maxQuantity]. */
@Composable
private fun Stepper(
    line: CartLine,
    onCommand: (List<CartCommand>) -> Unit,
) {
    val style = HaulType.text(16f, 700).copy(textAlign = TextAlign.Center)

    fun to(quantity: Int): (() -> Unit)? =
        if (quantity in 1..line.maxQuantity && quantity != line.quantity) {
            { onCommand(listOf(CartCommand.ChangeLine(line.url, LineChange(quantity = quantity)))) }
        } else {
            null
        }
    Row(
        Modifier.height(48.dp).background(HaulColors.background, RoundedCornerShape(14.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("−", style, Modifier.width(44.dp).testTag(lineTag("less", line)).pressable(to(line.quantity - 1)))
        Text(line.quantity.toString(), style, Modifier.width(24.dp))
        Text("+", style, Modifier.width(44.dp).testTag(lineTag("more", line)).pressable(to(line.quantity + 1)))
    }
}

/** «Price changed: now $26 · It was $24 when you added it · OK» on the error container; nothing when unchanged. */
@Composable
private fun ChangeNotice(
    line: CartLine,
    onCommand: (List<CartCommand>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val change = line.change ?: return
    val style = HaulType.text(14f)
    val acknowledge = line.acknowledgeUrl?.takeIf { line.acknowledgeLabel != null }
    WrapRow(
        gap = 12.dp,
        pushLast = acknowledge != null,
        modifier =
            modifier
                .fillMaxWidth()
                .background(HaulColors.errorContainer, RoundedCornerShape(14.dp))
                .padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
    ) {
        Icon(HaulIcons.alert, 18.dp, HaulColors.error)
        Text(change, HaulType.text(14f, 700), softWrap = false)
        line.changeDetail?.let { Text(it, style.copy(color = HaulColors.onSurfaceVariant), softWrap = false) }
        if (acknowledge != null) {
            HaulButton(
                line.acknowledgeLabel.orEmpty(),
                Modifier.testTag(lineTag("ok", line)),
                height = 40.dp,
                radius = 12.dp,
                horizontal = 18.dp,
                fill = HaulColors.inverseSurface,
                content = HaulColors.onPrimary,
                textSize = 14f,
                onClick = { onCommand(listOf(CartCommand.Acknowledge(acknowledge))) },
            )
        }
    }
}

/**
 * CSS's `flex-wrap: wrap; align-items: center`: items [gap] apart on a line while they fit, the rest on
 * the next, [gap] under; with [pushLast] the last item goes to the right edge (`margin-left: auto`).
 */
@Composable
private fun WrapRow(
    gap: Dp,
    pushLast: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val items = measurables.map { it.measure(loose) }
        val gapPx = gap.roundToPx()
        val width = constraints.maxWidth
        val lines = mutableListOf(mutableListOf<androidx.compose.ui.layout.Placeable>())
        var x = 0
        items.forEach { item ->
            val line = lines.last()
            if (line.isNotEmpty() && x + gapPx + item.width > width) {
                lines += mutableListOf(item)
                x = item.width
            } else {
                x += (if (line.isEmpty()) 0 else gapPx) + item.width
                line += item
            }
        }
        val heights = lines.map { line -> line.maxOf { it.height } }
        val height = heights.sum() + gapPx * (lines.size - 1)
        layout(width, height) {
            var y = 0
            lines.forEachIndexed { index, line ->
                var left = 0
                line.forEachIndexed { at, item ->
                    val pushed = pushLast && index == lines.lastIndex && at == line.lastIndex
                    val itemX = if (pushed) width - item.width else left
                    item.place(itemX, y + (heights[index] - item.height) / 2)
                    left += item.width + gapPx
                }
                y += heights[index] + gapPx
            }
        }
    }
}

/** The order summary: the title, the rows, the total, the promo field, checkout and the points. */
@Composable
private fun SummaryCard(
    summary: OrderSummary,
    onCommand: (List<CartCommand>) -> Unit,
    modifier: Modifier,
) {
    val compact = LocalHaulCompact.current
    Column(
        modifier
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(28.dp))
            .padding(if (compact) 24.dp else 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(summary.title, HaulType.display(32f, 800, letterSpacing = -0.01f), softWrap = false)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { summary.rows.forEach { SummaryLine(it) } }
        Hairline()
        Row(Modifier.fillMaxWidth()) {
            Text(summary.totalLabel, HaulType.text(17f, 700), Modifier.alignByBaseline(), softWrap = false)
            Spacer(Modifier.weight(1f))
            Text(
                summary.total,
                HaulType.display(56f, 800, letterSpacing = -0.01f),
                Modifier.alignByBaseline(),
                softWrap = false,
            )
        }
        summary.promo?.let { PromoFieldView(it, onCommand) }
        // A button that opens sign-in has no arrow: it does not go on to checkout (Cart_Guest).
        val signIn =
            (summary.checkoutAction as? NavigateAction)?.deeplink?.substringBefore('?') == SignInActions.SIGN_IN
        HaulButton(
            summary.checkoutLabel,
            Modifier.fillMaxWidth().testTag(CHECKOUT_TAG),
            height = 64.dp,
            radius = 18.dp,
            fill = HaulColors.primary,
            content = HaulColors.onPrimary,
            textSize = 18f,
            icon = if (signIn) null else HaulIcons.arrowRight,
            onClick = if (summary.checkoutEnabled) following(summary.checkoutAction) else null,
        )
        summary.points?.let { Points(it, summary.pointsAccent) }
    }
}

@Composable
private fun SummaryLine(row: SummaryRow) {
    val style = HaulType.text(15f)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(row.label, style.copy(color = HaulColors.outline), Modifier.weight(1f))
        Text(
            row.value,
            if (row.saving) HaulType.text(15f, 600).copy(color = HaulColors.error) else style,
            softWrap = false,
        )
    }
}

/** «You'll earn **1,024 points** on this order» on Acid: the bold part between the rest, 10 px apart as flex items. */
@Composable
private fun Points(
    sentence: String,
    accent: String?,
) {
    val at = accent?.let { sentence.indexOf(it) } ?: -1
    val style = HaulType.text(14f, 500)
    Row(
        Modifier
            .fillMaxWidth()
            .background(HaulColors.secondaryContainer, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (at < 0) {
            Text(sentence, style)
        } else {
            sentence
                .substring(0, at)
                .trim()
                .takeIf { it.isNotEmpty() }
                ?.let { Text(it, style, softWrap = false) }
            Text(accent.orEmpty(), HaulType.text(14f, 800), softWrap = false)
            sentence
                .substring(at + accent.orEmpty().length)
                .trim()
                .takeIf {
                    it.isNotEmpty()
                }?.let { Text(it, style, softWrap = false) }
        }
    }
}

/**
 * The promo field: the code with its terms and «Remove» once applied; otherwise the field and «Apply»,
 * outlined in the sale red with the reason under it when the server refused the code it holds.
 */
@Composable
private fun PromoFieldView(
    field: PromoField,
    onCommand: (List<CartCommand>) -> Unit,
) {
    if (field.applied) {
        AppliedCode(field, onCommand)
        return
    }
    var typed by remember(field.code, field.error) { mutableStateOf(field.code.orEmpty()) }
    val refused = field.error != null
    val shape = RoundedCornerShape(14.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // A refused field's border is inside its 52 px (`box-sizing: border-box`); an empty one's 1 px
            // is outside them, as the canvas draws it.
            Box(
                Modifier
                    .weight(1f)
                    .height(if (refused) 52.dp else 54.dp)
                    .border(
                        if (refused) 2.dp else 1.dp,
                        if (refused) HaulColors.error else HaulColors.outlineVariant,
                        shape,
                    ).padding(horizontal = if (refused) 18.dp else 17.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (typed.isEmpty()) {
                    Text(field.placeholder, HaulType.text(15f).copy(color = HaulColors.outlineMuted), softWrap = false)
                }
                BasicTextField(
                    typed,
                    { typed = it },
                    Modifier.fillMaxWidth().testTag(PROMO_INPUT_TAG),
                    // The field's own line, centred in the box: CSS's `line-height: 1` sits the code a pixel
                    // higher than a 15 px line box does here.
                    textStyle = HaulType.label(15f, 600, 0f).copy(lineHeight = TextUnit.Unspecified),
                    singleLine = true,
                )
            }
            // Not a `HaulButton`: «Apply» is set in 600, the canvas's buttons in 700.
            Box(
                Modifier
                    .testTag(PROMO_APPLY_TAG)
                    .pressable {
                        val code = typed.trim()
                        if (code.isNotEmpty()) onCommand(listOf(CartCommand.ApplyPromo(field.url, PromoEntry(code))))
                    }.height(52.dp)
                    .background(HaulColors.inverseSurface, shape)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(field.applyLabel, HaulType.text(15f, 600).copy(color = HaulColors.onPrimary), softWrap = false)
            }
        }
        field.error?.let { error ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(HaulIcons.alert, 14.dp, HaulColors.error)
                Text(error, HaulType.text(13f, 600).copy(color = HaulColors.error))
            }
        }
    }
}

/** The applied code in its pill, what it takes off, and «Remove». */
@Composable
private fun AppliedCode(
    field: PromoField,
    onCommand: (List<CartCommand>) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(2.dp, HaulColors.onSurface, RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            field.code.orEmpty(),
            HaulType.label(13f, 600, 0f),
            Modifier
                .background(HaulColors.secondaryContainer, RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            softWrap = false,
        )
        Text(field.terms.orEmpty(), HaulType.text(14f).copy(color = HaulColors.onSurfaceVariant), Modifier.weight(1f))
        Text(
            field.removeLabel,
            HaulType.text(14f, 600).copy(color = HaulColors.primary),
            Modifier.testTag(PROMO_REMOVE_TAG).pressable { onCommand(listOf(CartCommand.RemovePromo(field.url))) },
            softWrap = false,
        )
    }
}

internal fun lineTag(
    what: String,
    line: CartLine,
): String = "cart-line-$what:${line.skuId}"

internal const val SELECT_ALL_TAG: String = "cart-select-all"
internal const val DELETE_SELECTED_TAG: String = "cart-delete-selected"
internal const val CHECKOUT_TAG: String = "cart-checkout"
internal const val PROMO_INPUT_TAG: String = "cart-promo-input"
internal const val PROMO_APPLY_TAG: String = "cart-promo-apply"
internal const val PROMO_REMOVE_TAG: String = "cart-promo-remove"
