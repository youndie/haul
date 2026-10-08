package io.github.youndie.haul.feature.order

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.feature.product.Buttons
import io.github.youndie.haul.feature.product.DialogFrame
import io.github.youndie.haul.feature.product.FieldLabel
import io.github.youndie.haul.feature.product.FieldProblem
import io.github.youndie.haul.feature.product.FormProblems
import io.github.youndie.haul.feature.product.LocalReviewCommands
import io.github.youndie.haul.feature.product.ReviewCommand
import io.github.youndie.haul.feature.product.run
import io.github.youndie.haul.feature.product.settle
import io.github.youndie.haul.feature.product.without
import io.github.youndie.haul.feature.returns.ReturnEntry
import io.github.youndie.haul.feature.returns.exactDollars
import io.github.youndie.haul.feature.returns.returnProblems
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.LocalHaulCompact
import io.github.youndie.haul.ui.HaulIcons
import io.github.youndie.haul.ui.Icon
import io.github.youndie.haul.ui.ReturnForm
import io.github.youndie.haul.ui.ReturnLine
import io.github.youndie.haul.ui.Text
import io.github.youndie.haul.ui.normal
import io.github.youndie.haul.ui.pressable
import io.github.youndie.haul.ui.toneColor
import io.github.youndie.kompot.KompotAction
import kotlinx.coroutines.launch

// «Return items» (Order_ReturnDialog and its _Phone twin, B-21): the card of the review dialogs (B-22) over
// the delivered order, holding the server's form — the lines that can go back, each with a checkbox, the
// reason, and what the ticked lines give back. What is ticked and chosen is the client's until it is sent.

/**
 * The return dialog, drawn: the lines with [draft]'s ticked, the reason it chose (or the form's hint), the
 * refund the ticked lines come to, and [problems] under the fields at fault. [onDraft] is every change,
 * [onSend] «Request return», [onClose] «×» and «Cancel».
 */
@Composable
public fun ReturnFormView(
    form: ReturnForm,
    draft: ReturnEntry,
    onDraft: (ReturnEntry) -> Unit = {},
    problems: FormProblems = FormProblems(),
    onSend: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
) {
    var choosing by remember(form) { mutableStateOf(false) }
    DialogFrame(form.title, onClose) {
        // `margin-top: -10px` under the title: 12 px apart where the card's other blocks stand 22.
        Text(
            form.meta,
            HaulType.label(12f, 500, 0.04f).copy(color = HaulColors.outline),
            Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val lift = META_LIFT.roundToPx()
                layout(placeable.width, placeable.height - lift) { placeable.place(0, -lift) }
            },
        )
        Column {
            form.lines.forEachIndexed { index, line ->
                val ticked = line.position in draft.lines
                Line(
                    line,
                    ticked,
                    last = index == form.lines.lastIndex,
                    onTick = {
                        val lines = if (ticked) draft.lines - line.position else draft.lines + line.position
                        onDraft(draft.copy(lines = lines.sorted()))
                    },
                )
            }
            problems.fields["lines"]?.let { FieldProblem(it) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(form.reasonLabel)
            val chosen = form.reasons.firstOrNull { it.id == draft.reason }
            ReasonField(chosen?.label ?: form.reasonHint, chosen != null, problems.fields["reason"]) {
                choosing = !choosing
            }
            if (choosing) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, HaulColors.outlineVariant, RoundedCornerShape(14.dp))
                        .padding(vertical = 6.dp),
                ) {
                    form.reasons.forEach { reason ->
                        Text(
                            reason.label,
                            normal(16f, if (reason.id == draft.reason) 600 else 400),
                            Modifier
                                .fillMaxWidth()
                                .testTag(reasonTag(reason.id))
                                .pressable {
                                    choosing = false
                                    onDraft(draft.copy(reason = reason.id))
                                }.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            problems.fields["reason"]?.let { FieldProblem(it) }
        }
        Refund(form, draft)
        Buttons(form.submitLabel, form.cancelLabel, problems.message, onSend, onClose)
    }
}

/**
 * The return dialog as the storefront presents it: the draft kept here, the rules checked before sending
 * (the server's own, `returnProblems`), the command sent through the dialogs' seam ([LocalReviewCommands]),
 * and its answer — close, then refresh — handed to [handle]. A refusal (the window closed since the page was
 * drawn: `422 return_window_closed`) is drawn over the buttons; no answer leaves the dialog open.
 */
@Composable
internal fun ReturnDialog(
    form: ReturnForm,
    handle: (KompotAction) -> Unit,
) {
    val commands = LocalReviewCommands.current
    val scope = rememberCoroutineScope()
    var draft by remember(form) { mutableStateOf(ReturnEntry()) }
    var problems by remember(form) { mutableStateOf(FormProblems()) }
    var sending by remember(form) { mutableStateOf(false) }
    ReturnFormView(
        form,
        draft,
        onDraft = { changed ->
            val fields =
                buildSet {
                    if (changed.lines != draft.lines) add("lines")
                    if (changed.reason != draft.reason) add("reason")
                }
            problems = problems.without(fields)
            draft = changed
        },
        problems = problems,
        onSend = {
            val found = returnProblems(draft)
            when {
                found.isNotEmpty() -> {
                    problems = FormProblems.of(found)
                }

                commands != null && !sending -> {
                    sending = true
                    scope.launch {
                        problems = settle(commands.run(ReviewCommand.Return(form.url, draft)), handle)
                        sending = false
                    }
                }
            }
        },
        onClose = form.close?.let { close -> { handle(close) } },
    )
}

/** A line that can go back: the checkbox, the tile, the title and its details, over a hairline unless [last]. */
@Composable
private fun Line(
    line: ReturnLine,
    ticked: Boolean,
    last: Boolean,
    onTick: () -> Unit,
) {
    val compact = LocalHaulCompact.current
    Column(Modifier.fillMaxWidth().testTag(lineTag(line.position)).pressable(onTick)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = if (compact) 14.dp else 18.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(ticked)
            val tile = if (compact) 72.dp else 88.dp
            Box(Modifier.size(tile).background(toneColor(line.tone), RoundedCornerShape(14.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(line.title, HaulType.text(if (compact) 15f else 16f, 600, lineHeight = 1.3f))
                Text(line.details, normal(14f).copy(color = HaulColors.outline))
            }
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(HaulColors.outlineVariant))
    }
}

/** The canvas's 22 px box: Cobalt with a tick, or white with a grey border. */
@Composable
private fun Checkbox(ticked: Boolean) {
    val shape = RoundedCornerShape(6.dp)
    if (ticked) {
        Box(Modifier.size(22.dp).background(HaulColors.primary, shape), contentAlignment = Alignment.Center) {
            Icon(HaulIcons.checkBold, 14.dp, HaulColors.onPrimary)
        }
    } else {
        Box(
            Modifier
                .size(22.dp)
                .background(HaulColors.surfaceContainerLowest, shape)
                .border(2.dp, HaulColors.outlineControl, shape),
        )
    }
}

/** The reason, closed: what was chosen in ink inside an ink border, or the hint; sale red at fault. */
@Composable
private fun ReasonField(
    text: String,
    chosen: Boolean,
    error: String?,
    onPress: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val border =
        when {
            error != null -> HaulColors.error
            chosen -> HaulColors.onSurface
            else -> HaulColors.outlineVariant
        }
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(HaulColors.surfaceContainerLowest, shape)
            .border(if (chosen || error != null) 2.dp else 1.dp, border, shape)
            .testTag(REASON_TAG)
            .pressable(onPress)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            normal(16f).copy(color = if (chosen) HaulColors.onSurface else HaulColors.outline),
            Modifier.weight(1f),
        )
        Icon(HaulIcons.chevronDown, 18.dp, HaulColors.onSurface)
    }
}

/**
 * What the ticked lines give back, on Paper: «Refund $80.00 to card ···· 4821», then how it goes back and,
 * when the order earned points, that they are taken back. Nothing ticked, nothing to say.
 */
@Composable
private fun Refund(
    form: ReturnForm,
    draft: ReturnEntry,
) {
    val ticked = form.lines.filter { it.position in draft.lines }
    if (ticked.isEmpty()) return
    val amount = exactDollars(ticked.sumOf { it.refundCents })
    val points = if (ticked.size == 1) form.pointsOne else form.pointsMany
    Column(
        Modifier
            .fillMaxWidth()
            .background(HaulColors.background, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            form.refund.replace("{amount}", amount),
            HaulType.text(15f, 600, lineHeight = REFUND_LEADING),
            Modifier.testTag(REFUND_TAG),
        )
        Text(
            listOfNotNull(form.note, points).joinToString(" "),
            HaulType.text(14f, lineHeight = REFUND_LEADING).copy(color = HaulColors.outline),
        )
    }
}

/** The refund box's `line-height: 1.5`, a number its 15 px title inherits as it is. */
private const val REFUND_LEADING = 1.5f

/** The meta line's `margin-top: -10px`. */
private val META_LIFT = 10.dp

internal fun lineTag(position: Int): String = "return-line:$position"

internal fun reasonTag(id: String): String = "return-reason:$id"

internal const val REASON_TAG: String = "return-reason"
internal const val REFUND_TAG: String = "return-refund"
