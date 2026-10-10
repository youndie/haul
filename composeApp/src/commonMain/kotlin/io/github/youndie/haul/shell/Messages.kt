package io.github.youndie.haul.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.ui.HaulButton
import io.github.youndie.haul.ui.HaulIcons
import io.github.youndie.haul.ui.Icon
import io.github.youndie.haul.ui.Text
import io.github.youndie.haul.ui.normal
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.standard.MessageLevel
import io.github.youndie.kompot.standard.ShowMessageAction
import kotlinx.coroutines.delay

// kompot's `show_message` (SPEC §16.4) as the storefront draws it (B-75): an answer in words to what was just
// pressed — «Added to your cart» — over the page, at the bottom, for a few seconds. kompot leaves the look of a
// message to the client and no artboard draws one, so it is drawn in the frame the shell already has for
// what happens over a page, the notice a load that did not arrive leaves (`NotUpdatedNotice`, B-62): the
// inverse surface, the Acid accent, the button on the right. The Material snackbar kompot ships
// (`kompot-ds-material-compose`) is not taken: the app has no Material theme, only Haul's tokens.

/** The tag on the message drawn over the page, for the tests that wait for it. */
public const val MESSAGE_TAG: String = "page-message"

/** How long a message stays; one with a button stays longer, for the time to reach it. */
internal const val MESSAGE_MS: Long = 4_000
internal const val MESSAGE_WITH_ACTION_MS: Long = 6_000

/**
 * The message shown over the storefront's pages, if any: held above them, so a message outlives the page that
 * raised it — its own button may open another one. A new message replaces the one shown.
 */
internal class MessageState {
    var shown: Shown? by mutableStateOf(null)
        private set

    private var raised = 0

    /** [message], whose own button follows its action through [follow]. */
    fun show(
        message: ShowMessageAction,
        follow: (KompotAction) -> Unit,
    ) {
        raised += 1
        shown = Shown(message, follow, raised)
    }

    /** Takes [message] away, unless another one has replaced it since. */
    fun dismiss(message: Shown) {
        if (shown == message) shown = null
    }

    /** One message raised; [number] tells two raised with the same words apart, so each gets its full time. */
    class Shown(
        val message: ShowMessageAction,
        val follow: (KompotAction) -> Unit,
        val number: Int,
    ) {
        /** The button, when the message has both its halves: words without an action press nothing. */
        val button: Pair<String, KompotAction>? =
            message.actionLabel?.let { label -> message.action?.let { label to it } }
    }
}

/** The message [state] holds, at the bottom of the page, until its time is up or its button is pressed. */
@Composable
internal fun MessageHost(state: MessageState) {
    val shown = state.shown ?: return
    LaunchedEffect(shown) {
        delay(if (shown.button != null) MESSAGE_WITH_ACTION_MS else MESSAGE_MS)
        state.dismiss(shown)
    }
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        MessageNotice(
            shown.message,
            onAction =
                shown.button?.let { (_, action) ->
                    {
                        state.dismiss(shown)
                        shown.follow(action)
                    }
                },
        )
    }
}

/**
 * One message: what happened and, when it has one, its button. An `error` message carries the alert mark the
 * load notice does; any other level is `info`, with a check.
 */
@Composable
internal fun MessageNotice(
    message: ShowMessageAction,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .testTag(MESSAGE_TAG)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .background(HaulColors.inverseSurface, RoundedCornerShape(16.dp))
            .padding(start = 20.dp, end = if (onAction != null) 12.dp else 20.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val error = message.level == MessageLevel.ERROR
        Icon(if (error) HaulIcons.alert else HaulIcons.checkBold, 20.dp, HaulColors.secondaryContainer)
        Text(
            message.text,
            normal(15f, 700).copy(color = HaulColors.inverseOnSurface),
            Modifier.weight(1f).padding(vertical = if (onAction != null) 0.dp else 11.dp),
        )
        val label = message.actionLabel
        if (onAction != null && label != null) {
            HaulButton(
                label,
                height = 44.dp,
                radius = 12.dp,
                horizontal = 18.dp,
                fill = HaulColors.secondaryContainer,
                content = HaulColors.onSecondaryContainer,
                textSize = 15f,
                onClick = onAction,
            )
        }
    }
}
