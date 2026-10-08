package io.github.youndie.haul.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.LocalHaulCompact
import kotlin.math.sqrt

/** A product card (`ProductCard` on the wire), at the width its grid cell gives it. */
@Composable
public fun ProductCardView(
    card: ProductCard,
    modifier: Modifier = Modifier,
) {
    val compact = LocalHaulCompact.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PhotoTile(card, compact, Modifier.padding(bottom = 4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    card.price,
                    HaulType.display(if (compact) 22f else 26f, 800, letterSpacing = -0.01f),
                    Modifier.alignByBaseline(),
                )
                card.oldPrice?.let {
                    Text(
                        it,
                        HaulType
                            .text(
                                if (compact) 13f else 14f,
                            ).copy(color = HaulColors.outlineMuted, textDecoration = TextDecoration.LineThrough),
                        Modifier.alignByBaseline(),
                    )
                }
            }
            val button = if (compact) 36.dp else 38.dp
            Box(
                Modifier.size(button).background(HaulColors.primary, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(HaulIcons.plus, 18.dp, HaulColors.onPrimary)
            }
        }
        val titleSize = if (compact) 14f else 15f
        ClampedText(
            card.title,
            HaulType.text(titleSize, lineHeight = 1.35f),
            maxLines = 2,
            modifier = Modifier.heightIn(min = if (compact) 38.dp else 40.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(HaulIcons.star, 14.dp, HaulColors.onSurface)
            Text(card.rating, HaulType.text(13f, 600))
            Text("· ${card.reviews}", HaulType.text(13f).copy(color = HaulColors.outline))
        }
        Text(card.delivery.uppercase(), HaulType.label(11f, 600, 0.05f).copy(color = HaulColors.primary))
    }
}

@Composable
private fun PhotoTile(
    card: ProductCard,
    compact: Boolean,
    modifier: Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp)),
    ) {
        // The stored photo (B-30) over the canvas's placeholder tile, which stays while there is none.
        PhotoOrPlaceholder(card.image, Modifier.matchParentSize()) {
            Box(Modifier.fillMaxSize().background(toneColor(card.tone))) {
                Hatching(Modifier.fillMaxSize())
                Text(
                    card.label.uppercase(),
                    HaulType.label(10f, 500, 0.06f).copy(color = HaulColors.tileLabel),
                    Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 12.dp),
                )
            }
        }
        card.badge?.let {
            Text(
                it,
                HaulType.text(13f, 800, lineHeight = 1f),
                Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .background(HaulColors.secondaryContainer, CircleShape)
                    .padding(horizontal = 9.dp, vertical = 6.dp),
            )
        }
        val heart = if (compact) 40.dp else 36.dp
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(if (compact) 6.dp else 10.dp)
                .size(heart)
                .background(HaulColors.surfaceContainerLowest, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(HaulIcons.heart, 18.dp, if (card.saved) HaulColors.tertiaryContainer else HaulColors.onSurface) }
    }
}

/**
 * The canvas's `repeating-linear-gradient(135deg, ink 5 % 0 1px, transparent 1px 12px)`: 1 px lines
 * across the 135° axis, one every 12 px along it.
 */
@Composable
private fun Hatching(modifier: Modifier) {
    Canvas(modifier) {
        val step = HATCH_PERIOD * sqrt(2f)
        var c = 0f
        while (c < size.width + size.height) {
            drawLine(HaulColors.tileHatch, Offset(c, 0f), Offset(0f, c), strokeWidth = 1f)
            c += step
        }
    }
}

private const val HATCH_PERIOD = 12f

/** A tile tone as the server sends it, `#RRGGBB`; anything else is the canvas's stone tone. */
internal fun toneColor(tone: String): Color =
    tone
        .removePrefix("#")
        .takeIf { it.length == 6 }
        ?.toLongOrNull(16)
        ?.let { Color(0xFF000000 or it) }
        ?: Color(0xFFECE9E2)
