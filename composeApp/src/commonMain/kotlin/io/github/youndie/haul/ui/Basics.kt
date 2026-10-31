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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulFonts
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.LocalHaulCompact
import io.github.youndie.haul.theme.LocalHaulFonts
import kotlin.math.floor
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
 * optional icon after (or, with [iconFirst], before) it, 10 px apart. [onClick] makes it a button;
 * without one it is drawn and does nothing. A button that is not [enabled] has nothing to do and looks it
 * (B-100), as the buy box's buttons out of stock do (B-75): Paper's grey under a muted label, filled or
 * outlined alike — an outline is filled to its edge, so the button keeps its size — and it presses nothing.
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
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(radius)
    val drawnFill = if (enabled) fill else HaulColors.outlineVariant
    val drawnBorder = if (enabled || border == null) border else HaulColors.outlineVariant
    val drawnContent = if (enabled) content else HaulColors.outlineMuted
    Row(
        modifier
            .then(if (enabled) Modifier.pressable(onClick) else Modifier.inert())
            .height(height)
            .then(if (drawnFill != null) Modifier.background(drawnFill, shape) else Modifier)
            // A CSS border takes room of its own, outside the padding.
            .then(
                if (drawnBorder != null) {
                    Modifier.border(2.dp, drawnBorder, shape).padding(horizontal = 2.dp)
                } else {
                    Modifier
                },
            ).padding(horizontal = horizontal),
        horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null && iconFirst) Icon(icon, iconSize, drawnContent)
        Text(label, HaulType.text(textSize, 700).copy(color = drawnContent), softWrap = false)
        if (icon != null && !iconFirst) Icon(icon, iconSize, drawnContent)
    }
}

/**
 * What a deals countdown reads at [now]: the hours, minutes and seconds left until [endsAt] (ISO-8601,
 * as the server sends it), `00:00:00` once it has passed. A second begun is counted (B-152): the clock ticks a few
 * milliseconds after each whole second, and a countdown cut down to whole seconds read `00:00:00` a second before
 * the end.
 */
public fun countdown(
    endsAt: String,
    now: Instant,
): String {
    val left = (parseInstant(endsAt) - now).coerceAtLeast(Duration.ZERO)
    val seconds = (left.inWholeMilliseconds + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND

    fun two(n: Long) = n.toString().padStart(2, '0')
    return "${two(seconds / 3600)}:${two(seconds / 60 % 60)}:${two(seconds % 60)}"
}

/** Whether the countdown to [endsAt] has run out at [now]. */
internal fun countdownEnded(
    endsAt: String,
    now: Instant,
): Boolean = now >= parseInstant(endsAt)

private const val MILLIS_PER_SECOND = 1_000L

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
    links: List<TextLink> = emptyList(),
) {
    val legible = HaulType.legibleFigures(text, style)
    // The line boxes are measured here rather than read back from the text's own layout: an intrinsic
    // measurement (a row as tall as its tallest cell) comes before any layout, and must crop too.
    val measurer = rememberTextMeasurer()
    val overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis
    val crops = style.lineHeight.isSpecified
    // How far the face's own line reaches above the first line box, as the last layout found it.
    val reach = remember { IntArray(1) }
    val glyphs = if (crops) rememberGraphicsLayer() else null
    val held = modifier.keepsReachBelowLineTop(glyphs, reach)
    LinkedBasicText(
        legible,
        links,
        held
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                var top = 0
                var bottom = placeable.height
                if (crops) {
                    val width = Constraints(maxWidth = constraints.maxWidth)
                    val lines = measurer.measure(legible, style, overflow, softWrap, maxLines, constraints = width)
                    // Where CSS puts the first line box: the leading (line height minus ascent and descent)
                    // split evenly, so the box's top is (line height + ascent − descent) / 2 above the
                    // baseline. Skia rounds its own line top down, a pixel off at a negative leading.
                    val natural =
                        measurer.measure(
                            AnnotatedString("Hg"),
                            style.copy(lineHeight = TextUnit.Unspecified),
                            constraints = width,
                        )
                    val ascent = natural.firstBaseline
                    val descent = natural.getLineBottom(0) - ascent
                    val lineHeight = style.lineHeight.toPx()
                    val cssTop = lines.firstBaseline - (lineHeight + ascent - descent) / 2f
                    top = cssTop.roundToInt().coerceIn(0, placeable.height)
                    bottom = (top + (lineHeight * lines.lineCount).roundToInt()).coerceIn(top, placeable.height)
                }
                reach[0] = top
                layout(placeable.width, bottom - top) { placeable.place(0, -top) }
            }.drawsInto(glyphs),
        style,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
    )
}

/**
 * A cropped [Text] takes the pointer from its first line box down, and draws where it draws (B-157). The face's own
 * line reaches above the line box the text is laid in, over whatever is laid above it, and a selectable text (B-155)
 * takes the pointer over all of its own: a category's title took the presses meant for the crumbs above it. Only a
 * clipping layer stops the pointer short of a node's own bounds, and a clip cuts the glyphs too — a «$» in the top
 * line lost its stroke, and every word in the layer drew its edges a shade differently. So the text is drawn into
 * [glyphs] under the clip, which keeps nothing of it, and [glyphs] is drawn over the clip as it was recorded, [reach]
 * above the line box where the text's own layout puts it. Below the line box nothing is cut: what is laid under the
 * text is reached before it anyway.
 */
private fun Modifier.keepsReachBelowLineTop(
    glyphs: GraphicsLayer?,
    reach: IntArray,
): Modifier =
    if (glyphs == null) {
        this
    } else {
        drawWithContent {
            drawContent()
            translate(top = -reach[0].toFloat()) { drawLayer(glyphs) }
        }.graphicsLayer(clip = true, shape = BelowLineTop)
    }

/** The text drawn into [glyphs], and nowhere else, for [keepsReachBelowLineTop] to draw. */
private fun Modifier.drawsInto(glyphs: GraphicsLayer?): Modifier =
    if (glyphs == null) this else drawWithContent { glyphs.record { this@drawWithContent.drawContent() } }

/** From the first line box's top down, as wide and as deep as anything draws past it. */
private object BelowLineTop : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Rectangle(Rect(-REACH, 0f, size.width + REACH, size.height + REACH))
}

/** Further than any text draws past its box, sideways or down. */
private const val REACH = 100_000f

/**
 * How tall [Text] draws one line of [style]: the style's line height, rounded as [Text] rounds it, where the style
 * sets one, else the face's own line. A placeholder standing for a line of the page's text is this tall, so what
 * stands under it does not move when the text arrives (B-143).
 */
@Composable
internal fun lineOf(style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    return with(LocalDensity.current) {
        val line =
            if (style.lineHeight.isSpecified) {
                style.lineHeight.toPx().roundToInt()
            } else {
                measurer.measure(" ", style).size.height
            }
        line.toDp()
    }
}

/** [linked] drawn as [Text] draws text, its links pressed on their own. */
@Composable
internal fun Text(
    linked: LinkedText,
    modifier: Modifier = Modifier,
    style: TextStyle,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(linked.text, modifier, style, softWrap, maxLines, linked.links)
}

/**
 * [BasicText] with [links]: each a press of its own over its run of the text, named by its words (B-126). The
 * text is drawn as without them, and the size and the baselines are the text's.
 */
@Composable
internal fun LinkedBasicText(
    text: AnnotatedString,
    links: List<TextLink>,
    modifier: Modifier,
    style: TextStyle,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    inlineContent: Map<String, InlineTextContent> = emptyMap(),
) {
    if (links.isEmpty()) {
        BasicText(
            text,
            modifier,
            style,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            inlineContent = inlineContent,
        )
        return
    }
    // The text's layout of the measurement under way: the text is measured first, then the runs are laid on it.
    val drawn = remember { arrayOfNulls<TextLayoutResult>(1) }
    androidx.compose.ui.layout.Layout(
        content = {
            BasicText(
                text,
                Modifier,
                style,
                onTextLayout = { drawn[0] = it },
                overflow = overflow,
                softWrap = softWrap,
                maxLines = maxLines,
                inlineContent = inlineContent,
            )
            links.forEach { link -> Box(Modifier.named(link.label).pressable(link.onPress).offersLink(link.action)) }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val placed = measurables.first().measure(constraints)
        val layout = drawn[0]
        val runs =
            measurables.drop(1).zip(links) { measurable, link ->
                val bounds =
                    layout
                        ?.takeIf { link.end <= it.layoutInput.text.length }
                        ?.getPathForRange(link.start, link.end)
                        ?.getBounds()
                val left = (bounds?.left ?: 0f).roundToInt().coerceIn(0, placed.width)
                val top = (bounds?.top ?: 0f).roundToInt().coerceIn(0, placed.height)
                val right = (bounds?.right ?: 0f).roundToInt().coerceIn(left, placed.width)
                val bottom = (bounds?.bottom ?: 0f).roundToInt().coerceIn(top, placed.height)
                Triple(measurable.measure(Constraints.fixed(right - left, bottom - top)), left, top)
            }
        layout(placed.width, placed.height) {
            placed.place(0, 0)
            runs.forEach { (run, x, y) -> run.place(x, y) }
        }
    }
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
 * the browser, which keeps the fractions; here the gaps are placeholders inside a single line. A word
 * follows the action of the entry of [links] with its name (the header's categories, B-37).
 */
@Composable
internal fun SpacedWords(
    words: List<String>,
    gap: Dp,
    style: TextStyle,
    modifier: Modifier = Modifier,
    links: List<Link> = emptyList(),
) {
    val gapSp = with(LocalDensity.current) { gap.toSp() }
    val actions = LocalHaulActions.current
    val linked =
        linkedText {
            words.forEachIndexed { index, word ->
                if (index > 0) text.appendInlineContent(GAP, " ")
                link(word, links.firstOrNull { it.label == word }?.action, actions)
            }
        }
    LinkedBasicText(
        linked.text,
        linked.links,
        modifier,
        style,
        softWrap = false,
        maxLines = 1,
        inlineContent =
            mapOf(GAP to InlineTextContent(Placeholder(gapSp, 1.sp, PlaceholderVerticalAlign.AboveBaseline)) {}),
    )
}

private const val GAP = "gap"

/**
 * A display text at [size], or at the largest size below it that the width it is given holds (B-82): one line
 * no wider than the width when [softWrap] is off — the campaign's «70%», cut at the card's edge at 1024 — or
 * lines none of whose words is wider than it — Compose breaks a word that does not fit its line between two
 * letters («WH-1000XM / 6» at 1280), where CSS would let it overflow, and neither reads. A word runs from space
 * to space: the line breaker keeps «WH-1000XM6» whole (no break between a hyphen and a figure), so the room
 * after a hyphen ([widestWord]'s) is not counted on. [style] is
 * the style at a size, built in the fonts given, so that a display face's optical size follows the size; never
 * below [min]. [textModifier] is the text's own, at the size drawn.
 *
 * It measures in a [BoxWithConstraints], which has no intrinsic size: where a row asks its cells for one, a
 * fact tile's title shrinks after its first measurement instead (B-104's `FactTitle`).
 */
@Composable
internal fun FittedText(
    text: AnnotatedString,
    size: Float,
    style: (fonts: HaulFonts, size: Float) -> TextStyle,
    modifier: Modifier = Modifier,
    softWrap: Boolean = true,
    min: Float = size / 2,
    textModifier: (size: Float) -> Modifier = { Modifier },
) {
    val fonts = LocalHaulFonts.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val room = constraints.maxWidth
        val fitted =
            remember(text, size, room, fonts, softWrap, min) {
                if (room == Constraints.Infinity) return@remember size

                val words =
                    if (softWrap) {
                        SPACED.findAll(text.text).map { text.subSequence(it.range.first, it.range.last + 1) }.toList()
                    } else {
                        listOf(text)
                    }

                fun width(at: Float): Int {
                    val atSize = style(fonts, at)
                    return words.maxOfOrNull { word: AnnotatedString ->
                        measurer.measure(word, atSize, softWrap = false, maxLines = 1).size.width
                    } ?: 0
                }
                var at = size
                var wide = width(at)
                while (wide > room && at > min) {
                    // Widths grow with the size, nearly in proportion: the estimate, then half a pixel at a time.
                    at = maxOf(min, minOf(at - 0.5f, floor(at * room / wide * 2f) / 2f))
                    wide = width(at)
                }
                at
            }
        Text(text, textModifier(fitted), style(fonts, fitted), softWrap = softWrap)
    }
}

/** A run of [FittedText]'s between two spaces. */
private val SPACED = Regex("""\S+""")

/**
 * [SpacedWords] in the width it is given, as many of [words] as fit whole and the rest left out (B-82): at
 * 1440 the header's category row draws every word, at 1024 the first eight; a word cut at the edge, or the
 * row pushing what follows it into a column of letters, was the alternative. Whatever is left out has
 * another way in — the header's «Catalog» lists every category.
 */
@Composable
internal fun FittingWords(
    words: List<String>,
    gap: Dp,
    style: TextStyle,
    modifier: Modifier = Modifier,
    links: List<Link> = emptyList(),
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val room = constraints.maxWidth
        val gapPx = with(LocalDensity.current) { gap.roundToPx() }
        val shown =
            remember(words, style, room, gapPx) {
                var used = 0
                var count = 0
                for (word in words) {
                    val width = measurer.measure(word, style, softWrap = false, maxLines = 1).size.width
                    val next = used + (if (count > 0) gapPx else 0) + width
                    if (next > room) break
                    used = next
                    count++
                }
                words.take(count)
            }
        SpacedWords(shown, gap, style, links = links)
    }
}

/**
 * CSS's `text-wrap: balance`: the text keeps the number of lines it breaks into at the full width, at
 * the narrowest width that still holds it in that many — «Search / didn’t respond» rather than
 * «Search didn’t / respond». Chrome finds that width by bisection too.
 *
 * Never narrower than its widest word, though: below that the line breaker splits a word to keep the
 * count — «tomorrow / ,» — which Chrome never does (an order's «Arriving tomorrow, 15:00 – 18:00»).
 */
@Composable
internal fun BalancedText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val legible = HaulType.legibleFigures(text, style)
    val measurer = rememberTextMeasurer()
    Text(
        legible,
        modifier.layout { measurable, constraints ->
            fun lines(width: Int) =
                measurer.measure(legible, style, constraints = Constraints(maxWidth = width)).lineCount
            val full = constraints.maxWidth
            val count = lines(full)
            var width = full
            if (count > 1) {
                var low = minOf(widestWord(measurer, legible, style), full)
                var high = full
                while (low < high) {
                    val mid = (low + high) / 2
                    if (lines(mid) <= count) high = mid else low = mid + 1
                }
                width = low
            }
            val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = width))
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        },
        style = style,
    )
}

/**
 * Archivo at [size] on a line as tall as Chrome's `line-height: normal` draws it: the font's ascent and
 * descent (878 and 210 per 1,000) each rounded to a whole pixel — 19 px at 17, 15 at 14. Compose's own
 * line is a pixel shorter at most sizes, and a form that stacks enough lines on a phone (the checkout,
 * the review dialog) moves by tens of pixels for it.
 */
@Composable
internal fun normal(
    size: Float,
    weight: Int = 400,
): TextStyle {
    val line = (size * ARCHIVO_ASCENT).roundToInt() + (size * ARCHIVO_DESCENT).roundToInt()
    return HaulType.text(size, weight, lineHeight = line / size)
}

private const val ARCHIVO_ASCENT = 0.878f
private const val ARCHIVO_DESCENT = 0.21f

/** The widest run of [text] no line can break inside: words split at spaces and after hyphens and dashes. */
internal fun widestWord(
    measurer: TextMeasurer,
    text: AnnotatedString,
    style: TextStyle,
): Int {
    var widest = 0
    var start = 0

    fun word(end: Int) {
        if (end > start) {
            widest = maxOf(widest, measurer.measure(text.subSequence(start, end), style, softWrap = false).size.width)
        }
    }
    text.text.forEachIndexed { index, char ->
        if (char == ' ') {
            word(index)
            start = index + 1
        } else if (char in BREAKS_AFTER) {
            word(index + 1)
            start = index + 1
        }
    }
    word(text.length)
    return widest
}

/** The characters a line may break after inside a word: the hyphen and the dashes. */
private const val BREAKS_AFTER = "-–—"
