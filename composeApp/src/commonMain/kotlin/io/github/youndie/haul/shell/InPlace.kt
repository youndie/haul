package io.github.youndie.haul.shell

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.ui.HaulButton
import io.github.youndie.haul.ui.HaulIcons
import io.github.youndie.haul.ui.Icon
import io.github.youndie.haul.ui.Text
import io.github.youndie.haul.ui.normal

// What the shell draws over a page it keeps (B-62): a new address of the same screen loads behind the
// page that is drawn, so the shopper is told a load is on its way — a line under the header — and, when
// it does not arrive, told so over the page that stayed, with Retry. No artboard draws either; both are
// drawn from the theme's tokens.

/** The tag on the line drawn under the header while the page's next tree loads behind it. */
public const val LOADING_LINE_TAG: String = "page-loading-line"

/** The tag on the notice a load that did not arrive leaves over the page it kept. */
public const val NOT_UPDATED_TAG: String = "page-not-updated"

/** The notice's title: the page shown is the one before the press, not the one asked for. */
public const val NOT_UPDATED_TITLE: String = "The page didn’t update"

/** A thin line sweeping across the page's width: the next tree of this screen is on its way. */
@Composable
internal fun LoadingLine(modifier: Modifier = Modifier) {
    val sweep = rememberInfiniteTransition()
    val at by sweep.animateFloat(0f, 1f, infiniteRepeatable(tween(SWEEP_MS, easing = LinearEasing)))
    Box(
        modifier
            .testTag(LOADING_LINE_TAG)
            .fillMaxWidth()
            .height(3.dp)
            .clipToBounds()
            .background(HaulColors.primary.copy(alpha = 0.16f))
            .drawBehind {
                val width = size.width * SWEEP_SHARE
                drawRect(
                    HaulColors.primary,
                    topLeft = Offset((size.width + width) * at - width, 0f),
                    size = Size(width, size.height),
                )
            },
    )
}

/**
 * A load of this screen that did not arrive, over the page that stayed: what happened and Retry, which
 * asks for the same address again. The page under it still answers its presses.
 */
@Composable
internal fun NotUpdatedNotice(
    cause: Throwable,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .testTag(NOT_UPDATED_TAG)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .background(HaulColors.inverseSurface, RoundedCornerShape(16.dp))
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(HaulIcons.alert, 20.dp, HaulColors.secondaryContainer)
        Column(Modifier.weight(1f)) {
            Text(NOT_UPDATED_TITLE, normal(15f, 700).copy(color = HaulColors.inverseOnSurface))
            Text(cause.notUpdatedReason, normal(14f).copy(color = HaulColors.inverseOnSurfaceVariant))
        }
        HaulButton(
            "Retry",
            height = 44.dp,
            radius = 12.dp,
            horizontal = 18.dp,
            fill = HaulColors.secondaryContainer,
            content = HaulColors.onSecondaryContainer,
            textSize = 15f,
            onClick = onRetry,
        )
    }
}

/** Why the page asked for is not the one shown, in the words the error pages use. */
private val Throwable.notUpdatedReason: String
    get() =
        when (this) {
            is ScreenFailed.Unreachable -> ShellFailure.Unreachable.message
            is ScreenFailed.NotFound -> "The link may be old, or the page has moved."
            else -> ShellFailure.Server.message
        }

private const val SWEEP_MS = 1_200
private const val SWEEP_SHARE = 0.3f
