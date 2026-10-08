package io.github.youndie.haul.feature.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.registry.haulJson
import io.github.youndie.haul.shell.HaulCommands
import io.github.youndie.haul.shell.LocalHaulCommands
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.LocalHaulCompact
import io.github.youndie.haul.ui.HaulButton
import io.github.youndie.haul.ui.HaulIcons
import io.github.youndie.haul.ui.Icon
import io.github.youndie.haul.ui.InlineLine
import io.github.youndie.haul.ui.PlusBenefit
import io.github.youndie.haul.ui.PlusTrialDialog
import io.github.youndie.haul.ui.Text
import io.github.youndie.haul.ui.accented
import io.github.youndie.haul.ui.normal
import io.github.youndie.haul.ui.pressable
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.decodeKompotAction
import io.github.youndie.kompot.standard.RefreshAction
import io.github.youndie.kompot.standard.SequenceAction
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

// The Haul Plus trial's dialog over the page (Home_PlusTrialDialog and its _Phone twin, B-23): a white card
// 640 wide, 200 px down the page at 1440 and 140 down, 16 from each edge, on a phone, its head on black —
// «HAUL PLUS», «30 days *free*», «×» — then the four benefits, two by two at 1440 and one under another on
// a phone, a hairline, the terms and «Start trial» / «Not now».

/** Where the trial's dialog sits on a phone: lower than the review dialogs' 120 px (`DialogOverlay`). */
public val PLUS_DIALOG_COMPACT_TOP: Dp = 140.dp

/** The trial's dialog, drawn: [onStart] is «Start trial», [onClose] «Not now» and «×»; without them, nothing to press. */
@Composable
public fun PlusTrialDialogView(
    dialog: PlusTrialDialog,
    onStart: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
) {
    val compact = LocalHaulCompact.current
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .then(if (compact) Modifier.fillMaxWidth() else Modifier.width(640.dp))
            .dropShadow(
                shape,
                Shadow(radius = 100.dp, spread = (-20).dp, offset = DpOffset(0.dp, 40.dp), color = CARD_SHADOW),
            ).clip(shape)
            .background(HaulColors.surfaceContainerLowest),
    ) {
        Head(dialog, compact, onClose)
        Column(
            Modifier.padding(
                start = if (compact) 24.dp else 40.dp,
                end = if (compact) 24.dp else 40.dp,
                top = if (compact) 24.dp else 36.dp,
                bottom = if (compact) 24.dp else 40.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 20.dp else 24.dp),
        ) {
            Benefits(dialog.benefits, compact)
            Box(Modifier.fillMaxWidth().height(1.dp).background(HaulColors.outlineVariant))
            Text(dialog.terms, normal(16f, 600))
            Buttons(dialog, compact, onStart, onClose)
        }
    }
}

@Composable
private fun Head(
    dialog: PlusTrialDialog,
    compact: Boolean,
    onClose: (() -> Unit)?,
) {
    Box(Modifier.fillMaxWidth().background(HaulColors.inverseSurface)) {
        Column(Modifier.padding(horizontal = if (compact) 24.dp else 40.dp, vertical = if (compact) 28.dp else 40.dp)) {
            InlineLine(
                dialog.eyebrow.uppercase(),
                HaulType.label(12f, 600, 0.08f).copy(color = HaulColors.secondaryContainer),
                strut = HaulType.text(16f),
            )
            Text(
                accented(dialog.title, dialog.accent, HaulColors.secondaryContainer),
                Modifier.padding(top = if (compact) 16.dp else 20.dp),
                style =
                    HaulType
                        .display(
                            if (compact) 56f else 80f,
                            800,
                            letterSpacing = if (compact) -0.01f else -0.03f,
                            lineHeight = 0.9f,
                        ).copy(color = HaulColors.onPrimary),
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = if (compact) 16.dp else 24.dp, end = if (compact) 16.dp else 24.dp)
                .size(44.dp)
                .background(HaulColors.inverseControl, CircleShape)
                .testTag(PLUS_CLOSE_TAG)
                .pressable(onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(HaulIcons.close, 18.dp, HaulColors.onPrimary)
        }
    }
}

/**
 * Two columns 24 apart with rows 22 apart at 1440; one column on a phone, where the canvas's `gap:16` has no
 * unit and so no gap at all.
 */
@Composable
private fun Benefits(
    benefits: List<PlusBenefit>,
    compact: Boolean,
) {
    if (compact) {
        Column { benefits.forEach { Benefit(it, Modifier.fillMaxWidth()) } }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
            benefits.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    pair.forEach { Benefit(it, Modifier.weight(1f)) }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Benefit(
    benefit: PlusBenefit,
    modifier: Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier.size(32.dp).background(HaulColors.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(HaulIcons.checkBold, 16.dp, HaulColors.onSecondaryContainer)
        }
        Column {
            Text(benefit.title, normal(16f, 700))
            Text(
                benefit.detail,
                normal(14f).copy(color = HaulColors.outline),
                Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun Buttons(
    dialog: PlusTrialDialog,
    compact: Boolean,
    onStart: (() -> Unit)?,
    onClose: (() -> Unit)?,
) {
    @Composable
    fun Start(modifier: Modifier) =
        HaulButton(
            dialog.startLabel,
            modifier.testTag(PLUS_START_TAG),
            height = 60.dp,
            radius = 18.dp,
            fill = HaulColors.primary,
            content = HaulColors.onPrimary,
            onClick = onStart,
        )

    @Composable
    fun Dismiss(modifier: Modifier) =
        HaulButton(
            dialog.dismissLabel,
            modifier.testTag(PLUS_DISMISS_TAG),
            height = 60.dp,
            radius = 18.dp,
            border = HaulColors.onSurface,
            onClick = onClose,
        )
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Start(Modifier.fillMaxWidth())
            Dismiss(Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Start(Modifier.weight(1f))
            Dismiss(Modifier)
        }
    }
}

/**
 * The trial's dialog as the storefront presents it: «Start trial» sends `POST` [PlusTrialDialog.url]
 * through [LocalHaulCommands] and hands the answer — close, then refresh — to [handle]. A refusal
 * (`409 already_member`: a second tab started it first) closes the dialog and draws the page again, which
 * then shows the membership; no answer leaves the dialog open for another press.
 */
@Composable
internal fun PlusTrialDialogPresented(
    dialog: PlusTrialDialog,
    handle: (KompotAction) -> Unit,
) {
    val commands = LocalHaulCommands.current
    val scope = rememberCoroutineScope()
    var sending by remember(dialog) { mutableStateOf(false) }
    val close = dialog.close
    PlusTrialDialogView(
        dialog,
        onStart = {
            if (commands != null && !sending) {
                sending = true
                scope.launch {
                    commands.startTrial(dialog.url, close)?.let(handle)
                    sending = false
                }
            }
        },
        onClose = close?.let { { handle(it) } },
    )
}

/**
 * What the trial's `POST` was answered with, as an action to follow: the server's own on success, the
 * dialog closed and the page drawn again on a refusal; `null` for no answer at all.
 */
@Suppress(
    "ktlint:kapkan:swallowed-failure",
    "A trial that got no answer started nothing the shopper can see; the dialog stays and the next press tries again.",
)
internal suspend fun HaulCommands.startTrial(
    url: String,
    close: KompotAction?,
): KompotAction? =
    try {
        val answer = send("POST", url, null)
        if (answer.status in SUCCESS) {
            haulJson.decodeKompotAction(answer.body)
        } else {
            SequenceAction(listOfNotNull(close, RefreshAction))
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        // Throwable: in the browser a failed fetch is a JavaScript error, which is no `Exception` on Wasm.
        null
    }

private val SUCCESS = 200..299

/** The card's shadow, black at 50 % (`box-shadow: 0 40px 100px -20px rgba(0,0,0,.5)`), as the review dialogs'. */
private val CARD_SHADOW = Color(0x80000000)

public const val PLUS_START_TAG: String = "plus-trial-start"
public const val PLUS_DISMISS_TAG: String = "plus-trial-dismiss"
public const val PLUS_CLOSE_TAG: String = "plus-trial-close"
