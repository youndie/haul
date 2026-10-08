package io.github.youndie.haul.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.LocalHaulCompact
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The instant the page is drawn at: whoever hosts the page provides it — a screenshot the canvas's
 * «now», the app's root a ticking one once it draws screens. A composable reads this, never the clock.
 */
public val LocalHaulNow: ProvidableCompositionLocal<Instant> =
    compositionLocalOf { error("no «now» provided: the app root or the fixture provides one") }

/** The page's side gutter: 48 at the desktop width, 16 on a phone. */
@Composable
internal fun gutter(): Dp = if (LocalHaulCompact.current) 16.dp else 48.dp

/** A loading placeholder block (`surfaceContainerHigh`, «Skeleton» on the canvas). */
@Composable
internal fun Skeleton(
    modifier: Modifier,
    radius: Dp,
) {
    Box(modifier.background(HaulColors.surfaceContainerHigh, RoundedCornerShape(radius)))
}

/**
 * [title] with its [accent] in Bodoni Moda's italic cut at weight 500, as the canvas draws
 * «Shop by *category*».
 */
internal fun accented(
    title: String,
    accent: String?,
    accentColor: Color = Color.Unspecified,
): AnnotatedString =
    buildAnnotatedString {
        val at = accent?.takeIf { it.isNotEmpty() }?.let { title.indexOf(it) } ?: -1
        if (at < 0) {
            append(title)
            return@buildAnnotatedString
        }
        append(title.substring(0, at))
        withStyle(
            SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight(ACCENT_WEIGHT), color = accentColor),
        ) {
            append(accent)
        }
        append(title.substring(at + accent!!.length))
    }

private const val ACCENT_WEIGHT = 500

/**
 * The canvas's `repeating-linear-gradient(135deg, colour 0 1px, transparent 1px period)`: 1 px lines
 * across the 135° axis, one every [period] px along it, starting at the top left corner.
 */
internal fun Modifier.hatching(
    color: Color = HaulColors.tileHatch,
    period: Float = 12f,
): Modifier =
    drawBehind {
        val step = period * sqrt(2f)
        var c = 0f
        while (c < size.width + size.height) {
            drawLine(color, Offset(c, 0f), Offset(0f, c), strokeWidth = 1f)
            c += step
        }
    }

/** A layout that reports [less] fewer pixels of height than its content takes: CSS's negative bottom margin. */
internal fun Modifier.negativeBottomMargin(less: Dp): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(maxHeight = Int.MAX_VALUE))
        val height = (placeable.height - less.roundToPx()).coerceAtLeast(0)
        layout(placeable.width, height) { placeable.place(0, 0) }
    }

/**
 * The canvas's button: a filled or outlined pill-cornered block, its label in Archivo 700 and an
 * optional icon after (or, with [iconFirst], before) it, 10 px apart.
 */
@Composable
internal fun HaulButton(
    label: String,
    modifier: Modifier = Modifier,
    height: Dp,
    radius: Dp,
    horizontal: Dp = 28.dp,
    fill: Color? = null,
    content: Color = HaulColors.onSurface,
    border: Color? = null,
    textSize: Float = 17f,
    icon: ImageVector? = null,
    iconSize: Dp = 20.dp,
    iconFirst: Boolean = false,
    gap: Dp = 10.dp,
) {
    val shape = RoundedCornerShape(radius)
    Row(
        modifier
            .height(height)
            .then(if (fill != null) Modifier.background(fill, shape) else Modifier)
            // A CSS border takes room of its own, outside the padding.
            .then(if (border != null) Modifier.border(2.dp, border, shape).padding(horizontal = 2.dp) else Modifier)
            .padding(horizontal = horizontal),
        horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null && iconFirst) Icon(icon, iconSize, content)
        Text(label, HaulType.text(textSize, 700).copy(color = content), softWrap = false)
        if (icon != null && !iconFirst) Icon(icon, iconSize, content)
    }
}

/**
 * What a deals countdown reads at [now]: the hours, minutes and seconds left until [endsAt] (ISO-8601,
 * as the server sends it), `00:00:00` once it has passed.
 */
public fun countdown(
    endsAt: String,
    now: Instant,
): String {
    val left = (parseInstant(endsAt) - now).coerceAtLeast(Duration.ZERO)
    val seconds = left.inWholeSeconds

    fun two(n: Long) = n.toString().padStart(2, '0')
    return "${two(seconds / 3600)}:${two(seconds / 60 % 60)}:${two(seconds % 60)}"
}

/**
 * An ISO-8601 instant with an offset. Java's `OffsetDateTime` writes no seconds when they are zero
 * («2025-10-08T00:00-04:00»), which `Instant.parse` does not accept, so they are put back first.
 */
internal fun parseInstant(iso: String): Instant {
    val time = iso.substringAfter('T')
    val clock = time.takeWhile { it.isDigit() || it == ':' || it == '.' }
    val full = if (clock.count { it == ':' } == 1) iso.replaceFirst("T$clock", "T$clock:00") else iso
    return Instant.parse(full)
}

/**
 * CSS's flex row of a shrinkable first item and fixed others: the first child takes its natural
 * width while it fits and all the room left once it does not — which is where a wrapped heading ends
 * and the next item starts in a browser. Children are centred vertically, [gap] apart.
 */
@Composable
internal fun ShrinkFirstRow(
    gap: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.ui.layout.Layout(content, modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val rest = measurables.drop(1).map { it.measure(loose) }
        val gapPx = gap.roundToPx()
        val restWidth = rest.sumOf { it.width } + gapPx * rest.size
        val room = (constraints.maxWidth - restWidth).coerceAtLeast(0)
        val first = measurables.first()
        val natural = first.maxIntrinsicWidth(Int.MAX_VALUE)
        val width = if (natural <= room) natural else room
        val head = first.measure(loose.copy(minWidth = width, maxWidth = width))
        val height = maxOf(head.height, rest.maxOfOrNull { it.height } ?: 0)
        layout(head.width + restWidth, height) {
            head.place(0, (height - head.height) / 2)
            var x = head.width + gapPx
            rest.forEach {
                it.place(x, (height - it.height) / 2)
                x += it.width + gapPx
            }
        }
    }
}

/**
 * Text laid out as a browser lays it out. Where a style sets a line height smaller than the font's own
 * (Bodoni's natural line is 1.53 em; the canvas sets 1, .9, .82), Compose grows the paragraph so the
 * glyphs stay inside it, and a 112 px title at `line-height: .9` takes 136 px a line instead of 101.
 * CSS keeps the line box and lets the glyphs overflow it; this reports the line boxes as the size and
 * lets the glyphs draw outside, as the canvas does.
 */
@Composable
internal fun Text(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(AnnotatedString(text), modifier, style, softWrap, maxLines)
}

@Composable
internal fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
) {
    // Unknown until the first layout (an intrinsic measurement comes before it): no crop then.
    val lines = remember { intArrayOf(0, -1) }
    BasicText(
        text,
        modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
            val known = lines[1] >= 0
            val top = if (known) lines[0].coerceIn(0, placeable.height) else 0
            val bottom = if (known) lines[1].coerceIn(top, placeable.height) else placeable.height
            layout(placeable.width, bottom - top) { placeable.place(0, -top) }
        },
        style,
        onTextLayout = { result ->
            lines[0] = result.getLineTop(0).roundToInt()
            lines[1] = result.getLineBottom(result.lineCount - 1).roundToInt()
        },
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        softWrap = softWrap,
        maxLines = maxLines,
    )
}

/**
 * CSS's `flex-wrap: wrap; align-items: baseline` for two items: side by side on one baseline, [gap]
 * apart, while both fit; otherwise the second on a line of its own, [gap] under the first.
 */
@Composable
internal fun BaselineWrapRow(
    gap: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.ui.layout.Layout(content, modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val (first, second) = measurables.map { it.measure(loose) }
        val gapPx = gap.roundToPx()
        val firstBase = first[androidx.compose.ui.layout.FirstBaseline]
        val secondBase = second[androidx.compose.ui.layout.FirstBaseline]
        if (first.width + gapPx + second.width <= constraints.maxWidth) {
            val above = maxOf(firstBase, secondBase)
            val below = maxOf(first.height - firstBase, second.height - secondBase)
            layout(constraints.maxWidth, above + below) {
                first.place(0, above - firstBase)
                second.place(first.width + gapPx, above - secondBase)
            }
        } else {
            // `gap` is the row gap too.
            layout(constraints.maxWidth, first.height + gapPx + second.height) {
                first.place(0, 0)
                second.place(0, first.height + gapPx)
            }
        }
    }
}

/**
 * Two `flex: 1` items [gap] apart. `flex: 1` shares the free space from a basis of zero, and an item's
 * padding and border are not part of that basis: they come on top of its share. So the second item,
 * with [secondFrame] of padding and border, is wider than the first by exactly that — on the phone's
 * catalog, «Filters» 157 px and the sort 191 px, not two halves.
 */
@Composable
internal fun FlexPair(
    gap: Dp,
    secondFrame: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.ui.layout.Layout(content, modifier) { measurables, constraints ->
        val (first, second) = measurables
        val gapPx = gap.roundToPx()
        val frame = secondFrame.roundToPx()
        val share = (constraints.maxWidth - gapPx - frame) / 2
        val firstWidth = share
        val secondWidth = share + frame
        val a = first.measure(constraints.copy(minWidth = firstWidth, maxWidth = firstWidth))
        val b = second.measure(constraints.copy(minWidth = secondWidth, maxWidth = secondWidth))
        layout(constraints.maxWidth, maxOf(a.height, b.height)) {
            a.place(0, 0)
            b.place(firstWidth + gapPx, 0)
        }
    }
}

/**
 * A block holding one inline run of [text] in a font other than the block's: CSS sizes the line by the
 * block's own font as well (its strut), and sits the run on that font's baseline. [strut] is the
 * block's font — Archivo 16 px at the default line height for an eyebrow in a campaign or the Plus block.
 */
@Composable
internal fun InlineLine(
    text: String,
    style: TextStyle,
    strut: TextStyle,
    modifier: Modifier = Modifier,
) {
    Row(modifier) {
        Text(" ", strut, Modifier.width(0.dp).alignByBaseline())
        Text(text, style, Modifier.alignByBaseline(), softWrap = false)
    }
}

/**
 * CSS's `-webkit-line-clamp`: the text broken into lines as usual, the first [maxLines] kept and «…»
 * put after the last kept word — «Wireless Noise…», where Compose's own ellipsis would fill the line
 * with the next word's letters («Wireless Noise Cancelli…»). Only when «…» does not fit after the
 * kept words does it cut letters.
 */
@Composable
internal fun ClampedText(
    text: String,
    style: TextStyle,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth
        val shown =
            remember(text, style, width) {
                val full = measurer.measure(text, style, constraints = Constraints(maxWidth = width))
                if (full.lineCount <= maxLines) {
                    text
                } else {
                    text.substring(0, full.getLineEnd(maxLines - 1, visibleEnd = true)).trimEnd() + "…"
                }
            }
        Text(shown, style, maxLines = maxLines)
    }
}

/**
 * A row of one-line words [gap] apart, laid out as one paragraph. A row of separate texts rounds each
 * one's width up to a whole pixel, and over ten category names that adds up to a visible drift from
 * the browser, which keeps the fractions; here the gaps are placeholders inside a single line.
 */
@Composable
internal fun SpacedWords(
    words: List<String>,
    gap: Dp,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val gapSp = with(LocalDensity.current) { gap.toSp() }
    val text =
        buildAnnotatedString {
            words.forEachIndexed { index, word ->
                if (index > 0) appendInlineContent(GAP, " ")
                append(word)
            }
        }
    BasicText(
        text,
        modifier,
        style,
        softWrap = false,
        maxLines = 1,
        inlineContent =
            mapOf(GAP to InlineTextContent(Placeholder(gapSp, 1.sp, PlaceholderVerticalAlign.AboveBaseline)) {}),
    )
}

private const val GAP = "gap"
