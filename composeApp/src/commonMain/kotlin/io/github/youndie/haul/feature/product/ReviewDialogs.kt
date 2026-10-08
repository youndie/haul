package io.github.youndie.haul.feature.product

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.FieldError
import io.github.youndie.haul.feature.reviews.QuestionEntry
import io.github.youndie.haul.feature.reviews.ReviewEntry
import io.github.youndie.haul.feature.reviews.ReviewRules
import io.github.youndie.haul.feature.reviews.groupedCount
import io.github.youndie.haul.feature.reviews.questionProblems
import io.github.youndie.haul.feature.reviews.reviewProblems
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.LocalHaulCompact
import io.github.youndie.haul.ui.FormProduct
import io.github.youndie.haul.ui.HaulButton
import io.github.youndie.haul.ui.HaulIcons
import io.github.youndie.haul.ui.Icon
import io.github.youndie.haul.ui.QuestionForm
import io.github.youndie.haul.ui.ReviewForm
import io.github.youndie.haul.ui.Text
import io.github.youndie.haul.ui.normal
import io.github.youndie.haul.ui.pressable
import io.github.youndie.haul.ui.toneColor
import io.github.youndie.kompot.KompotAction
import kotlinx.coroutines.launch

// The two dialogs over the product page (Product_ReviewDialog, Product_QuestionDialog and their _Phone
// twins): a white card 200 px down the page at 1440, 680 wide and centred, 120 down and 16 from each edge
// on a phone, over the page dimmed by the scrim. The card's content is the server's form; what is typed
// is the client's until it is sent.

/**
 * The scrim and the card over the page: [content] at the canvas's place, the rest of the page dimmed.
 * Pressing the scrim is [onDismiss]; a card taller than the window scrolls. [compactTop] is how far down
 * a phone's card starts: 120 px for the review dialogs, 140 for the Plus trial's (B-23).
 */
@Composable
public fun DialogOverlay(
    onDismiss: (() -> Unit)?,
    compactTop: Dp = 120.dp,
    content: @Composable () -> Unit,
) {
    val compact = LocalHaulCompact.current
    Box(Modifier.fillMaxSize().background(HaulColors.scrim).pressable(onDismiss)) {
        Box(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = if (compact) 16.dp else 0.dp,
                    end = if (compact) 16.dp else 0.dp,
                    top = if (compact) compactTop else 200.dp,
                    bottom = 40.dp,
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            // A press on the card is the card's: it never reaches the scrim under it.
            Box(Modifier.pointerInput(Unit) { detectTapGestures { } }) { content() }
        }
    }
}

/** What a dialog shows under its fields: each field's error by its name, and one for the form. */
public data class FormProblems(
    val fields: Map<String, String> = emptyMap(),
    val message: String? = null,
) {
    public companion object {
        public fun of(
            fields: List<FieldError>,
            message: String? = null,
        ): FormProblems = FormProblems(fields.associate { it.field to it.message }, message)
    }
}

/**
 * «Write a review», drawn: the stars, the title and the review as [draft] has them, [problems] under the
 * fields at fault. [onDraft] is every change, [onPost] «Post», [onClose] «×» and «Cancel».
 */
@Composable
public fun ReviewFormView(
    form: ReviewForm,
    draft: ReviewEntry,
    onDraft: (ReviewEntry) -> Unit = {},
    problems: FormProblems = FormProblems(),
    onPost: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
) {
    val compact = LocalHaulCompact.current
    DialogCard(form.title, form.product, onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FieldLabel(form.ratingLabel)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (ReviewRules.RATING_MIN..ReviewRules.RATING_MAX).forEach { stars ->
                    Box(
                        Modifier
                            .size(48.dp)
                            .testTag(starTag(stars))
                            .pressable { onDraft(draft.copy(rating = stars)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            HaulIcons.star,
                            36.dp,
                            if (stars <= draft.rating) HaulColors.onSurface else HaulColors.outlineVariant,
                        )
                    }
                }
            }
            problems.fields["rating"]?.let { FieldProblem(it) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(form.titleLabel)
            LineField(draft.title, { onDraft(draft.copy(title = it)) }, problems.fields["title"], TITLE_TAG)
            problems.fields["title"]?.let { FieldProblem(it) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(form.bodyLabel)
            AreaField(
                draft.body,
                { onDraft(draft.copy(body = it)) },
                minHeight = if (compact) 150.dp else 120.dp,
                hint = form.bodyHint,
                max = ReviewRules.BODY_MAX,
                error = problems.fields["body"],
                tag = BODY_TAG,
            )
        }
        Buttons(form.submitLabel, form.cancelLabel, problems.message, onPost, onClose)
    }
}

/** «Ask a question», drawn: the question as [draft] has it, who answers, «Send» and «Cancel». */
@Composable
public fun QuestionFormView(
    form: QuestionForm,
    draft: QuestionEntry,
    onDraft: (QuestionEntry) -> Unit = {},
    problems: FormProblems = FormProblems(),
    onSend: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
) {
    val compact = LocalHaulCompact.current
    DialogCard(form.title, form.product, onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(form.label)
            AreaField(
                draft.text,
                { onDraft(draft.copy(text = it)) },
                minHeight = if (compact) 140.dp else 120.dp,
                hint = form.hint,
                max = ReviewRules.QUESTION_MAX,
                error = problems.fields["text"],
                tag = QUESTION_TAG,
            )
        }
        Text(form.note, HaulType.text(14f, lineHeight = 1.5f).copy(color = HaulColors.outline))
        Buttons(form.submitLabel, form.cancelLabel, problems.message, onSend, onClose)
    }
}

/**
 * The review dialog as the storefront presents it: the draft kept here, the rules checked before sending
 * (the server's own, `reviewProblems`), the command sent through [LocalReviewCommands], and its answer —
 * close, then refresh — handed to [handle]. A refusal draws its fields; no answer leaves the dialog open.
 */
@Composable
internal fun ReviewDialog(
    form: ReviewForm,
    handle: (KompotAction) -> Unit,
) {
    val commands = LocalReviewCommands.current
    val scope = rememberCoroutineScope()
    var draft by remember(form) { mutableStateOf(ReviewEntry()) }
    var problems by remember(form) { mutableStateOf(FormProblems()) }
    var sending by remember(form) { mutableStateOf(false) }
    ReviewFormView(
        form,
        draft,
        onDraft = { changed ->
            problems = problems.without(changedFields(draft, changed))
            draft = changed
        },
        problems = problems,
        onPost = {
            val found = reviewProblems(draft)
            when {
                found.isNotEmpty() -> {
                    problems = FormProblems.of(found)
                }

                commands != null && !sending -> {
                    sending = true
                    scope.launch {
                        problems = settle(commands.run(ReviewCommand.Post(form.url, draft)), handle)
                        sending = false
                    }
                }
            }
        },
        onClose = form.close?.let { close -> { handle(close) } },
    )
}

/** The question dialog as the storefront presents it; the same seam as [ReviewDialog]. */
@Composable
internal fun QuestionDialog(
    form: QuestionForm,
    handle: (KompotAction) -> Unit,
) {
    val commands = LocalReviewCommands.current
    val scope = rememberCoroutineScope()
    var draft by remember(form) { mutableStateOf(QuestionEntry()) }
    var problems by remember(form) { mutableStateOf(FormProblems()) }
    var sending by remember(form) { mutableStateOf(false) }
    QuestionFormView(
        form,
        draft,
        onDraft = { changed ->
            problems = problems.without(setOf("text"))
            draft = changed
        },
        problems = problems,
        onSend = {
            val found = questionProblems(draft)
            when {
                found.isNotEmpty() -> {
                    problems = FormProblems.of(found)
                }

                commands != null && !sending -> {
                    sending = true
                    scope.launch {
                        problems = settle(commands.run(ReviewCommand.Ask(form.url, draft)), handle)
                        sending = false
                    }
                }
            }
        },
        onClose = form.close?.let { close -> { handle(close) } },
    )
}

/** An answer handed on, or what the dialog draws instead. */
internal fun settle(
    outcome: ReviewOutcome,
    handle: (KompotAction) -> Unit,
): FormProblems =
    when (outcome) {
        is ReviewOutcome.Done -> {
            handle(outcome.action)
            FormProblems()
        }

        is ReviewOutcome.Refused -> {
            FormProblems.of(outcome.fields, outcome.message)
        }

        ReviewOutcome.NoAnswer -> {
            FormProblems(message = NOT_SENT)
        }
    }

private fun changedFields(
    before: ReviewEntry,
    after: ReviewEntry,
): Set<String> =
    buildSet {
        if (before.rating != after.rating) add("rating")
        if (before.title != after.title) add("title")
        if (before.body != after.body) add("body")
    }

/** The problems once [fields] were changed: their errors go, and the form's own message with them. */
internal fun FormProblems.without(fields: Set<String>): FormProblems =
    if (fields.isEmpty()) this else FormProblems(this.fields - fields, null)

@Composable
private fun DialogCard(
    title: String,
    product: FormProduct,
    onClose: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    DialogFrame(title, onClose) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(HaulColors.background, RoundedCornerShape(16.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(56.dp).background(toneColor(product.tone), RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f)) {
                Text(product.name, HaulType.text(14f, 600, lineHeight = 1.35f), softWrap = false, maxLines = 1)
                Text(
                    product.detail,
                    HaulType.text(14f, lineHeight = 1.35f).copy(color = HaulColors.outline),
                    softWrap = false,
                    maxLines = 1,
                )
            }
        }
        content()
    }
}

/**
 * The white card every dialog is (the review's, the question's, the return's, B-21): 680 wide at 1440 and
 * the width on a phone, the [title] in Bodoni with «×» ([onClose]) at its right, then [content], 22 apart.
 */
@Composable
internal fun DialogFrame(
    title: String,
    onClose: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val compact = LocalHaulCompact.current
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .then(if (compact) Modifier.fillMaxWidth() else Modifier.width(680.dp))
            .dropShadow(
                shape,
                Shadow(radius = 100.dp, spread = (-20).dp, offset = DpOffset(0.dp, 40.dp), color = CARD_SHADOW),
            ).background(HaulColors.surfaceContainerLowest, shape)
            .padding(if (compact) 24.dp else 40.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                HaulType.display(if (compact) 32f else 44f, 800, letterSpacing = -0.01f),
                Modifier.weight(1f),
                softWrap = false,
            )
            Box(
                Modifier
                    .size(44.dp)
                    .background(HaulColors.background, CircleShape)
                    .testTag(CLOSE_TAG)
                    .pressable(onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(HaulIcons.close, 18.dp, HaulColors.onSurface)
            }
        }
        content()
    }
}

@Composable
internal fun FieldLabel(label: String) {
    Text(label.uppercase(), HaulType.label(11f, 600, 0.08f).copy(color = HaulColors.outline), softWrap = false)
}

/** A one-line field: a hairline box, ink while typed in, sale red at fault. */
@Composable
private fun LineField(
    value: String,
    onValue: (String) -> Unit,
    error: String?,
    tag: String,
) {
    var focused by remember { mutableStateOf(false) }
    val border = borderOf(error, focused)
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(HaulColors.surfaceContainerLowest, shape)
            .border(border.first, border.second, shape)
            .padding(horizontal = 16.dp + border.first),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value,
            onValue,
            Modifier.fillMaxWidth().testTag(tag).onFocusChanged { focused = it.isFocused },
            textStyle = normal(16f),
            singleLine = true,
            cursorBrush = SolidColor(HaulColors.primary),
        )
    }
}

/** A text area with its hint (or its error) and how much of [max] is used under it. */
@Composable
private fun AreaField(
    value: String,
    onValue: (String) -> Unit,
    minHeight: Dp,
    hint: String,
    max: Int,
    error: String?,
    tag: String,
) {
    var focused by remember { mutableStateOf(false) }
    val border = borderOf(error, focused)
    val shape = RoundedCornerShape(14.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .border(border.first, border.second, shape)
                // CSS's 14 / 16 inside a 2 px border, kept while the border is thinner so the text never moves.
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            BasicTextField(
                value,
                onValue,
                Modifier.fillMaxWidth().testTag(tag).onFocusChanged { focused = it.isFocused },
                textStyle = HaulType.text(16f, lineHeight = 1.5f),
                cursorBrush = SolidColor(HaulColors.primary),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (error != null) {
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(HaulIcons.alert, 14.dp, HaulColors.error)
                    Text(error, normal(13f, 600).copy(color = HaulColors.error))
                }
            } else {
                Text(hint, normal(13f).copy(color = HaulColors.outline), Modifier.weight(1f))
            }
            Text(
                "${groupedCount(value.length)} / ${groupedCount(max)}",
                normal(13f).copy(color = if (value.trim().length > max) HaulColors.error else HaulColors.outline),
                softWrap = false,
            )
        }
    }
}

@Composable
internal fun FieldProblem(message: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(HaulIcons.alert, 14.dp, HaulColors.error)
        Text(message, normal(13f, 600).copy(color = HaulColors.error))
    }
}

/** The submit button filled and Cancel outlined: side by side at 1440, stacked full width on a phone. */
@Composable
internal fun Buttons(
    submit: String,
    cancel: String,
    message: String?,
    onSubmit: (() -> Unit)?,
    onCancel: (() -> Unit)?,
) {
    val compact = LocalHaulCompact.current
    message?.let {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(HaulIcons.alert, 16.dp, HaulColors.error)
            Text(it, normal(14f, 600).copy(color = HaulColors.error))
        }
    }
    val primary =
        @Composable { modifier: Modifier ->
            HaulButton(
                submit,
                modifier.testTag(SUBMIT_TAG),
                height = 60.dp,
                radius = 18.dp,
                fill = HaulColors.primary,
                content = HaulColors.onPrimary,
                onClick = onSubmit,
            )
        }
    val secondary =
        @Composable { modifier: Modifier ->
            HaulButton(
                cancel,
                modifier.testTag(CANCEL_TAG),
                height = 60.dp,
                radius = 18.dp,
                border = HaulColors.onSurface,
                onClick = onCancel,
            )
        }
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            primary(Modifier.fillMaxWidth())
            secondary(Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            primary(Modifier.weight(1f))
            secondary(Modifier)
        }
    }
}

private fun borderOf(
    error: String?,
    focused: Boolean,
): Pair<Dp, Color> =
    when {
        error != null -> 2.dp to HaulColors.error
        focused -> 2.dp to HaulColors.onSurface
        else -> 1.dp to HaulColors.outlineVariant
    }

/** The card's `box-shadow: 0 40px 100px -20px rgba(0,0,0,.5)`. */
private val CARD_SHADOW = Color(0x80000000)

internal fun starTag(stars: Int): String = "review-star:$stars"

internal const val TITLE_TAG: String = "review-title"
internal const val BODY_TAG: String = "review-body"
internal const val QUESTION_TAG: String = "question-text"
internal const val SUBMIT_TAG: String = "dialog-submit"
internal const val CANCEL_TAG: String = "dialog-cancel"
internal const val CLOSE_TAG: String = "dialog-close"
