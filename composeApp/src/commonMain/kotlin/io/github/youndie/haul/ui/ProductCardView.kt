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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.feature.cart.CartCommand
import io.github.youndie.haul.feature.cart.LocalCartCommands
import io.github.youndie.haul.feature.cart.linePress
import io.github.youndie.haul.feature.cart.run
import io.github.youndie.haul.feature.saved.SaveCommand
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.LocalHaulCompact
import io.github.youndie.kompot.KompotAction
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/** What a card's «+» is named, by the card's product: «Add Sony WH-1000XM6 to cart» (B-151). */
public fun addToCart(title: String): String = "Add $title to cart"

/** A card's «+»: its test tag, and its id in the browser's mirror. */
public const val CARD_ADD_TAG: String = "card-add"

/** What a heart says to a screen reader — the press it makes — and what the tests find it by. */
public const val SAVE: String = "Save"
public const val UNSAVE: String = "Remove from Saved"

/** A product card (`ProductCard` on the wire), at the width its grid cell gives it. */
@Composable
public fun ProductCardView(
    card: ProductCard,
    modifier: Modifier = Modifier,
) {
    val compact = LocalHaulCompact.current
    Column(modifier.follows(card.action), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PhotoTile(card, compact, Modifier.padding(bottom = 4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Prices(
                price = {
                    Text(
                        card.price,
                        HaulType.display(if (compact) 22f else 26f, 800, letterSpacing = -0.01f),
                        softWrap = false,
                    )
                },
                old =
                    card.oldPrice?.let {
                        {
                            Text(
                                it,
                                HaulType
                                    .text(
                                        if (compact) 13f else 14f,
                                    ).copy(
                                        color = HaulColors.outlineMuted,
                                        textDecoration = TextDecoration.LineThrough,
                                    ),
                                softWrap = false,
                            )
                        }
                    },
                // As far from «+» as from each other (B-136): on the 390 deals row «$193.90 $277» fitted its room to
                // the pixel and the old price touched «+».
                modifier = Modifier.weight(1f).padding(end = PRICE_GAP),
            )
            val button = if (compact) 36.dp else 38.dp
            // «+» sends the card's line change as a cart command (B-37) and follows the answer, `update`;
            // the card's own press, under it, opens the product. With nothing to add — out of stock, or the
            // cart holds as many as can be bought — the tree gives no change: «+» is drawn as the buy box
            // draws its buttons then, greyed, and takes the press so that it opens nothing either (B-75).
            val add = card.add
            val press = linePress(add)
            Box(
                Modifier
                    .then(if (add == null) Modifier.swallowsPresses() else Modifier.pressable(press))
                    // Its own node, pressable or not: merged into the card's, a disabled «+» would read as the card.
                    // Named by the product, as six «Add to cart» in a row named nothing (B-151).
                    .testTag(CARD_ADD_TAG)
                    .semantics(mergeDescendants = true) {
                        contentDescription = addToCart(card.title)
                        if (add == null) disabled()
                    }.size(button)
                    .background(
                        if (add ==
                            null
                        ) {
                            HaulColors.outlineVariant
                        } else {
                            HaulColors.primary
                        },
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(HaulIcons.plus, 18.dp, if (add == null) HaulColors.outlineMuted else HaulColors.onPrimary)
            }
        }
        card.drop?.let { PriceDrop(it) }
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
        // Out of stock there is no day to promise (B-120): the card ends at its rating.
        card.delivery?.let { Text(it.uppercase(), HaulType.label(11f, 600, 0.05f).copy(color = HaulColors.primary)) }
    }
}

/**
 * The price and the old price struck through beside it on their baselines, 8 apart — or, where the two do not fit
 * beside each other, the old price under the price (B-108). Neither is ever broken: a deal's «$432.99» beside
 * «$618.56» in a card of six across broke the old price mid-number, «$432.9» over «9».
 */
@Composable
private fun Prices(
    price: @Composable () -> Unit,
    old: (@Composable () -> Unit)?,
    modifier: Modifier,
) {
    Layout(content = {
        price()
        old?.invoke()
    }, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxWidth = Constraints.Infinity)
        val shown = measurables[0].measure(loose)
        val struck = measurables.getOrNull(1)?.measure(loose)
        val room = constraints.maxWidth
        if (struck == null) {
            return@Layout layout(constraints.constrainWidth(shown.width), shown.height) { shown.place(0, 0) }
        }
        val gap = PRICE_GAP.roundToPx()
        if (shown.width + gap + struck.width <= room) {
            val top = maxOf(shown[FirstBaseline], struck[FirstBaseline])
            val shownY = top - shown[FirstBaseline]
            val struckY = top - struck[FirstBaseline]
            val height = maxOf(shownY + shown.height, struckY + struck.height)
            layout(constraints.constrainWidth(shown.width + gap + struck.width), height) {
                shown.place(0, shownY)
                struck.place(shown.width + gap, struckY)
            }
        } else {
            layout(constraints.constrainWidth(maxOf(shown.width, struck.width)), shown.height + struck.height) {
                shown.place(0, 0)
                struck.place(0, shown.height)
            }
        }
    }
}

/** Between the price and the old price, and between the prices and «+». */
internal val PRICE_GAP = 8.dp

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
        // The stored photo (B-30) or the product's picture (B-78) over the canvas's placeholder tile, which stays
        // while there is none; the tile says nothing, a caption only described the missing picture.
        PhotoOrPlaceholder(card.image, Modifier.matchParentSize()) {
            Box(Modifier.fillMaxSize().background(toneColor(card.tone))) { Hatching(Modifier.fillMaxSize()) }
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
                .pressable(rememberHeartPress(card.heartCommand, card.heartAction))
                .semantics { contentDescription = if (card.saved) UNSAVE else SAVE }
                .size(heart)
                .background(HaulColors.surfaceContainerLowest, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Heart(card.saved, 18.dp) }
    }
}

/** The heart: outlined in Ink, or — the product saved — filled in Hot (`Saved_*`). */
@Composable
internal fun Heart(
    saved: Boolean,
    size: Dp,
) {
    if (saved) {
        Icon(HaulIcons.heartFilled, size, HaulColors.tertiaryContainer)
    } else {
        Icon(HaulIcons.heart, size, HaulColors.onSurface)
    }
}

/**
 * What a press on a heart does (B-20): a customer's sends the tree's [command] as a cart command and
 * follows the answer, `refresh`, which draws the heart as it now is; a guest's follows [action], the way
 * to sign in. `null` — nothing to send, or nobody to send it (a screenshot) — is not pressable.
 */
@Composable
internal fun rememberHeartPress(
    command: SaveCommand?,
    action: KompotAction?,
): (() -> Unit)? {
    val cart = LocalCartCommands.current
    val actions = LocalHaulActions.current
    val scope = rememberCoroutineScope()
    if (actions == null) return null
    if (command != null && cart != null) {
        fun press() {
            scope.launch {
                cart.run(listOf(CartCommand.Heart(command.url, command.save)), again = ::press)?.let(actions::handle)
            }
        }
        return ::press
    }
    return action?.let { { actions.handle(it) } }
}

/** The Saved list's mark on a card that got cheaper: «PRICE DROPPED −$200», mono capitals on Hot. */
@Composable
private fun PriceDrop(text: String) {
    Text(
        text.uppercase(),
        HaulType.label(11f, 600, 0.04f),
        Modifier
            .background(HaulColors.tertiaryContainer, CircleShape)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        softWrap = false,
    )
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
