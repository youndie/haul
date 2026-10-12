package io.github.youndie.haul.feature.product

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.LocalHaulCompact
import io.github.youndie.haul.ui.HaulIcons
import io.github.youndie.haul.ui.Icon
import io.github.youndie.haul.ui.Review
import io.github.youndie.haul.ui.SheetOverPage
import io.github.youndie.haul.ui.Text
import io.github.youndie.haul.ui.inert
import io.github.youndie.haul.ui.isOverPage
import io.github.youndie.haul.ui.named
import io.github.youndie.haul.ui.pressable
import io.github.youndie.haul.ui.swallowsPresses
import io.github.youndie.haul.ui.toneColor

// A review's photos (B-86): the tiles under its words, and the viewer a tile opens over the page. The photos are
// the tones of their placeholders until B-78 decides where pictures come from (research D8), so the viewer shows
// the tile large; what it adds is the way through a review's photos one at a time.

/**
 * [review]'s photo tiles, 72 px each; inside the storefront each opens [ReviewPhotoViewer] at its photo, and is named
 * for a reader of the page («Photo 1 of 2 by Daniel R.»). Outside it — a screenshot — nothing opens over the page, so
 * the tiles are pictures and press nothing.
 */
@Composable
internal fun ReviewPhotoTiles(review: Review) {
    var open by remember(review) { mutableStateOf<Int?>(null) }
    val opens = isOverPage()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        review.photos.forEachIndexed { index, tone ->
            Box(
                Modifier
                    .size(72.dp)
                    .background(toneColor(tone), RoundedCornerShape(12.dp))
                    .testTag(reviewPhotoTag(index))
                    .then(
                        if (opens) {
                            Modifier.named(photoName(review, index)).pressable { open = index }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
    open?.let { shown -> ReviewPhotoViewer(review, shown, onShow = { open = it }, onClose = { open = null }) }
}

/**
 * [review]'s photo [shown] over the whole page, one at a time: «‹» and «›» (and the arrow keys) step through the
 * review's photos, «×», Escape or a press outside the photo's card close it ([onClose]). Drawn in the page's own
 * layer ([SheetOverPage]), not in a `Dialog`: Compose 1.12's browser mirror would keep a closed dialog's nodes
 * (B-126, CMP-9368). Every press in it is named.
 */
@Composable
internal fun ReviewPhotoViewer(
    review: Review,
    shown: Int,
    onShow: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val count = review.photos.size
    val previous = (shown - 1).takeIf { it >= 0 }?.let { { onShow(it) } }
    val next = (shown + 1).takeIf { it < count }?.let { { onShow(it) } }
    SheetOverPage(
        onDismiss = onClose,
        onKey = { key ->
            val step =
                when (key) {
                    Key.DirectionLeft -> previous
                    Key.DirectionRight -> next
                    else -> null
                }
            step?.invoke()
            step != null
        },
    ) {
        val compact = LocalHaulCompact.current
        // A press on the dimmed page closes the viewer. Read as a gesture, not as a button: a button here would
        // take the card's words into its own name, and «×» and Escape are the ways a reader of the page closes it.
        Box(
            Modifier
                .fillMaxSize()
                .background(HaulColors.scrim)
                .testTag(PHOTO_SCRIM_TAG)
                .pointerInput(onClose) { detectTapGestures { onClose() } },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .then(if (compact) Modifier.fillMaxWidth().padding(horizontal = 16.dp) else Modifier.width(640.dp))
                    .swallowsPresses()
                    .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(28.dp))
                    .padding(if (compact) 20.dp else 28.dp)
                    .testTag(PHOTO_VIEWER_TAG),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(review.author, HaulType.text(15f, 600))
                        Text(
                            "Photo ${shown + 1} of $count",
                            HaulType.text(14f).copy(color = HaulColors.outline),
                            Modifier.testTag(PHOTO_COUNTER_TAG),
                        )
                    }
                    RoundButton(HaulIcons.close, CLOSE_PHOTOS, onClose, Modifier.testTag(PHOTO_CLOSE_TAG))
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(toneColor(review.photos[shown]), RoundedCornerShape(20.dp))
                        .named(photoName(review, shown, opens = false)),
                )
                if (count > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f))
                        RoundButton(
                            HaulIcons.chevronLeft,
                            PREVIOUS_PHOTO,
                            previous,
                            Modifier.testTag(PHOTO_PREVIOUS_TAG),
                        )
                        RoundButton(HaulIcons.chevronRight, NEXT_PHOTO, next, Modifier.testTag(PHOTO_NEXT_TAG))
                    }
                }
            }
        }
    }
}

/**
 * A round 44 px button with [icon], named [name]; without [onPress] — the first photo's «‹», the last one's «›» — it
 * is drawn faded, presses nothing and is disabled for a reader of the page.
 */
@Composable
private fun RoundButton(
    icon: ImageVector,
    name: String,
    onPress: (() -> Unit)?,
    modifier: Modifier,
) {
    Box(
        modifier
            .size(44.dp)
            .background(HaulColors.background, CircleShape)
            .named(name)
            .then(if (onPress == null) Modifier.inert() else Modifier.pressable(onPress)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, 18.dp, if (onPress == null) HaulColors.outlineControl else HaulColors.onSurface)
    }
}

/** What a reader hears for [review]'s photo [index]: which of how many, and whose — to open it on a tile. */
private fun photoName(
    review: Review,
    index: Int,
    opens: Boolean = true,
): String = (if (opens) "Open photo" else "Photo") + " ${index + 1} of ${review.photos.size} by ${review.author}"

internal fun reviewPhotoTag(index: Int): String = "review-photo:$index"

internal const val PHOTO_VIEWER_TAG = "review-photo-viewer"
internal const val PHOTO_SCRIM_TAG = "review-photo-scrim"
internal const val PHOTO_COUNTER_TAG = "review-photo-counter"
internal const val PHOTO_CLOSE_TAG = "review-photo-close"
internal const val PHOTO_PREVIOUS_TAG = "review-photo-previous"
internal const val PHOTO_NEXT_TAG = "review-photo-next"

private const val CLOSE_PHOTOS = "Close photos"
private const val PREVIOUS_PHOTO = "Previous photo"
private const val NEXT_PHOTO = "Next photo"
