package io.github.youndie.haul.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// How the storefront moves (B-158). The canvas is still: every artboard is a page at rest, and every golden is one.
// So everything here moves only under [Motion.Lively] and ends where the still page is drawn — a revealed card at
// its place and fully drawn, a pressed button at its size — and under [Motion.Still] adds nothing at all: no layer,
// no pointer reader, the same nodes as before. The screenshots are drawn still; the storefront is lively unless
// the browser asks for reduced motion.

/** Whether the page moves: [Lively] in the storefront, [Still] in a screenshot and under reduced motion. */
public enum class Motion {
    Still,
    Lively,
    ;

    public companion object {
        /** The storefront's motion for a browser that does — or does not — prefer reduced motion. */
        public fun preferring(reducedMotion: Boolean): Motion = if (reducedMotion) Still else Lively
    }
}

/** The motion of the page being drawn; [Motion.Still] unless the storefront's root provides another. */
public val LocalMotion: ProvidableCompositionLocal<Motion> = staticCompositionLocalOf { Motion.Still }

@Composable
@ReadOnlyComposable
internal fun lively(): Boolean = LocalMotion.current == Motion.Lively

/** Fast out, long settle: what arrives decelerates into its place. */
internal val Arrive = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/**
 * Drawn faded and lower until it first comes into the window, then risen into its place: on a page that arrives, top
 * to bottom; further down, as the page is scrolled to it. [order] staggers neighbours that come into view together —
 * the cards of a row, left to right. On a page that arrives at rest ([LocalArrivesAtRest]) it is drawn in its place
 * at once.
 */
@Composable
internal fun Modifier.reveal(
    order: Int = 0,
    rise: Dp = 28.dp,
): Modifier {
    if (!lively()) return this
    // Read once, when the block is first drawn: a block already shown stays shown whatever the page does next.
    val atRest = LocalArrivesAtRest.current
    val shown = remember { Animatable(if (atRest) 1f else 0f) }
    val window = LocalWindowInfo.current.containerSize.height
    // The page's scroll moves the page's layer, not the nodes on it, so a node is not told it moved: where it is
    // comes from where it was first placed and how far the page has scrolled since.
    val scroll = LocalPageScroll.current
    // Its top in the window when first placed, and the page's scroll then.
    val placed = remember { FloatArray(2) }
    var seen by remember { mutableStateOf(atRest) }
    LaunchedEffect(seen) {
        if (!seen || shown.value >= 1f) return@LaunchedEffect
        val (top, from) = placed
        val arriving = window <= 0 || top < window
        if (!arriving && scroll != null) snapshotFlow { scroll.value }.first { top - (it - from) < window }
        // What is on screen as the page arrives cascades down it; what scrolls in comes at once.
        val spread = if (arriving && window > 0) top.coerceIn(0f, window.toFloat()) / window * SPREAD_MS else 0f
        delay(order.coerceAtMost(MAX_ORDER) * STAGGER_MS + spread.toLong())
        shown.animateTo(1f, tween(REVEAL_MS, easing = Arrive))
    }
    val watch =
        if (seen) {
            Modifier
        } else {
            Modifier.onGloballyPositioned { at ->
                placed[0] = at.positionInWindow().y
                placed[1] = scroll?.value?.toFloat() ?: 0f
                seen = true
            }
        }
    return then(watch).graphicsLayer {
        val at = shown.value
        alpha = at
        translationY = (1f - at) * rise.toPx()
    }
}

/**
 * Whether the page being drawn arrives at rest: one come back to — back or forward, or an address already drawn in
 * this session (B-168). Its blocks are drawn in their places at once; `false` for a page drawn for the first time.
 */
internal val LocalArrivesAtRest: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

/**
 * The addresses whose pages were drawn in this session — in memory only, so a reload starts over: an address in it
 * arrives at rest (B-168). Keyed by the whole address, its query included: a search or a filter not seen before is
 * a page not seen before.
 */
internal class SeenPages {
    private val seen = HashSet<String>()

    operator fun contains(address: String): Boolean = address in seen

    fun drawn(address: String) {
        seen += address
    }
}

/**
 * Opened with a little spring: from [from] of its size, [rise] below (above, when negative) its place and clear, to
 * where it stays — a menu, the suggest panel, a dialog's card, a message.
 */
@Composable
internal fun Modifier.popsIn(
    from: Float = 0.94f,
    rise: Dp = 8.dp,
    origin: TransformOrigin = TransformOrigin.Center,
): Modifier {
    if (!lively()) return this
    val at = remember { Animatable(0f) }
    LaunchedEffect(Unit) { at.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 520f)) }
    return graphicsLayer {
        val v = at.value
        alpha = v.coerceIn(0f, 1f)
        scaleX = lerp(from, 1f, v)
        scaleY = lerp(from, 1f, v)
        translationY = (1f - v) * rise.toPx()
        transformOrigin = origin
    }
}

/**
 * Grown out from its middle to its width while [shown], drawn as it is: a tab's underline as the tab is chosen. The
 * one drawn shown when it is first drawn is there already.
 */
@Composable
internal fun Modifier.growsOut(shown: Boolean): Modifier {
    if (!lively()) return this
    val grown = remember { Animatable(if (shown) 1f else 0f) }
    LaunchedEffect(shown) { grown.animateTo(if (shown) 1f else 0f, spring(dampingRatio = 0.75f, stiffness = 420f)) }
    return graphicsLayer { scaleX = grown.value }
}

/** Faded in over [ms] when it opens: a scrim. */
@Composable
internal fun Modifier.fadesIn(ms: Int = 220): Modifier {
    if (!lively()) return this
    val at = remember { Animatable(0f) }
    LaunchedEffect(Unit) { at.animateTo(1f, tween(ms)) }
    return graphicsLayer { alpha = at.value }
}

/**
 * Bounced each time [key] changes after it is first drawn: snapped to [from] of its size and sprung back — the
 * cart's count when a line goes in, a heart pressed.
 */
@Composable
internal fun Modifier.bumpsOn(
    key: Any?,
    from: Float = 1.4f,
): Modifier {
    if (!lively()) return this
    val scale = remember { Animatable(1f) }
    var last by remember { mutableStateOf(key) }
    LaunchedEffect(key) {
        if (key == last) return@LaunchedEffect
        last = key
        scale.snapTo(from)
        scale.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 600f))
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** Shaken side to side each time [key] changes after it is first drawn: the cart's bag as a line goes in. */
@Composable
internal fun Modifier.wigglesOn(key: Any?): Modifier {
    if (!lively()) return this
    val turn = remember { Animatable(0f) }
    var last by remember { mutableStateOf(key) }
    LaunchedEffect(key) {
        if (key == last) return@LaunchedEffect
        last = key
        turn.animateTo(
            0f,
            keyframes {
                durationMillis = WIGGLE_MS
                -16f at 70
                12f at 150
                -7f at 230
                3f at 300
            },
        )
    }
    return graphicsLayer { rotationZ = turn.value }
}

/**
 * Whether the pointer is over what [hovers] marks, eased from 0 to 1 and back. Still, it is always 0 and marks
 * nothing.
 */
@Stable
internal class Hover internal constructor(
    internal val source: MutableInteractionSource?,
    private val at: State<Float>?,
) {
    val progress: Float get() = at?.value ?: 0f
}

private val StillHover = Hover(null, null)

@Composable
internal fun rememberHover(): Hover {
    if (!lively()) return StillHover
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val at =
        animateFloatAsState(if (hovered) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
    return remember(source, at) { Hover(source, at) }
}

/** A [Hover] that never moves, for a call site that is hovered only sometimes: the same calls either way. */
@Composable
internal fun rememberStillHover(): Hover = StillHover

/**
 * The mouse over this is [hover]'s. A mouse only (B-168): a finger has no hover, so nothing a finger does lifts what
 * it touches or keeps it lifted — a touch press takes off a hover the mouse left; the press itself still sinks.
 */
internal fun Modifier.hovers(hover: Hover): Modifier = hover.source?.let { this then MouseHoverElement(it) } ?: this

private data class MouseHoverElement(
    val source: MutableInteractionSource,
) : ModifierNodeElement<MouseHoverNode>() {
    override fun create(): MouseHoverNode = MouseHoverNode(source)

    override fun update(node: MouseHoverNode) = node.follow(source)
}

/** Foundation's `hoverable`, told to [source] for a mouse pointer alone. */
private class MouseHoverNode(
    private var source: MutableInteractionSource,
) : Modifier.Node(),
    PointerInputModifierNode {
    private var entered: HoverInteraction.Enter? = null

    fun follow(next: MutableInteractionSource) {
        if (next == source) return
        leave()
        source = next
    }

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        if (pass != PointerEventPass.Main) return
        val mouse = pointerEvent.changes.any { it.type == PointerType.Mouse }
        when (pointerEvent.type) {
            PointerEventType.Enter -> {
                if (mouse) enter() else leave()
            }

            // A mouse that came in while the node was being placed is told by its first move over it.
            PointerEventType.Move -> {
                if (!mouse) {
                    leave()
                } else if (pointerEvent.over(bounds)) {
                    enter()
                }
            }

            PointerEventType.Press -> {
                if (!mouse) leave()
            }

            PointerEventType.Exit -> {
                leave()
            }
        }
    }

    override fun onCancelPointerInput() = leave()

    override fun onDetach() = leave()

    private fun PointerEvent.over(bounds: IntSize): Boolean =
        changes.any { change ->
            val at = change.position
            at.x >= 0f && at.y >= 0f && at.x < bounds.width && at.y < bounds.height
        }

    private fun enter() {
        if (entered != null) return
        val enter = HoverInteraction.Enter()
        entered = enter
        source.tryEmit(enter)
    }

    private fun leave() {
        val enter = entered ?: return
        entered = null
        source.tryEmit(HoverInteraction.Exit(enter))
    }
}

/** Drawn as [block] says at [hover]'s progress — moved, scaled, turned; still, as it is. */
internal fun Modifier.onHover(
    hover: Hover,
    block: GraphicsLayerScope.(Float) -> Unit,
): Modifier = if (hover.source == null) this else graphicsLayer { block(hover.progress) }

/**
 * Drawn grown by [by] — and turned by [degrees] — about [pivot] at [hover]'s progress; still, as it is. Unlike
 * [onHover]'s layer, which scales the pixels it recorded, this draws what is inside again at its grown size, so a
 * picture under it is rasterised at the pixels it then covers and stays sharp while the pointer rests on it (B-181).
 * Nothing inside may be a layer of its own for that, a [clip] included: clip with [clipDrawn].
 */
internal fun Modifier.growsOnHover(
    hover: Hover,
    by: Float,
    pivot: TransformOrigin = TransformOrigin.Center,
    degrees: Float = 0f,
): Modifier =
    if (hover.source == null) {
        this
    } else {
        drawWithContent {
            val at = hover.progress
            if (at == 0f) {
                drawContent()
            } else {
                val origin = Offset(size.width * pivot.pivotFractionX, size.height * pivot.pivotFractionY)
                withTransform({
                    if (degrees != 0f) rotate(degrees * at, origin)
                    scale(1f + by * at, 1f + by * at, origin)
                }) { this@drawWithContent.drawContent() }
            }
        }
    }

/**
 * Clipped to [shape] as it is drawn rather than as a layer (B-181): what is inside is drawn with the transform it is
 * drawn under — [growsOnHover]'s — instead of being recorded at rest and scaled.
 */
internal fun Modifier.clipDrawn(shape: Shape): Modifier =
    drawWithCache {
        val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        onDrawWithContent { clipPath(outline) { this@onDrawWithContent.drawContent() } }
    }

/** Over its background and under its content, [tint] lightly over [shape] as [hover] comes in. */
internal fun Modifier.glowsOnHover(
    hover: Hover,
    tint: Color,
    shape: Shape,
): Modifier =
    if (hover.source == null) {
        this
    } else {
        drawWithContent {
            val at = hover.progress
            if (at > 0f) drawOutline(shape.createOutline(size, layoutDirection, this), tint.copy(alpha = GLOW * at))
            drawContent()
        }
    }

/**
 * A control pressed sinks a few pixels into the page and springs back on release: what a finger or the mouse
 * pressed answers at once, before the server does. By the same few pixels whatever its size, so a card sinks less
 * than a «+». The primary button and a touch only; the right button opens a menu.
 */
internal fun Modifier.sinksWhenPressed(): Modifier = this then SinkElement

private data object SinkElement : ModifierNodeElement<SinkNode>() {
    override fun create(): SinkNode = SinkNode()

    override fun update(node: SinkNode) = Unit
}

private class SinkNode :
    Modifier.Node(),
    LayoutModifierNode,
    PointerInputModifierNode,
    CompositionLocalConsumerModifierNode {
    private val scale = Animatable(1f)
    private var lively = false
    private var pressed = false
    private var running: Job? = null

    override fun onAttach() {
        lively = currentValueOf(LocalMotion) == Motion.Lively
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            if (lively) {
                placeable.placeWithLayer(0, 0) {
                    scaleX = scale.value
                    scaleY = scale.value
                }
            } else {
                placeable.place(0, 0)
            }
        }
    }

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        if (!lively || pass != PointerEventPass.Main) return
        when (pointerEvent.type) {
            PointerEventType.Press -> {
                val touch = pointerEvent.changes.any { it.type == PointerType.Touch }
                if (!pressed && (touch || pointerEvent.buttons.isPrimaryPressed)) {
                    pressed = true
                    val side = (bounds.width + bounds.height) / 2f
                    val to = 1f - (SINK_PX / side.coerceAtLeast(1f)).coerceAtMost(MAX_SINK)
                    go(to, spring(dampingRatio = 1f, stiffness = Spring.StiffnessHigh))
                }
            }

            PointerEventType.Release -> {
                if (pointerEvent.changes.none { it.pressed }) release()
            }
        }
    }

    override fun onCancelPointerInput() = release()

    private fun release() {
        if (!pressed) return
        pressed = false
        go(1f, spring(dampingRatio = 0.4f, stiffness = 700f))
    }

    private fun go(
        to: Float,
        spec: androidx.compose.animation.core.AnimationSpec<Float>,
    ) {
        running?.cancel()
        running = coroutineScope.launch { scale.animateTo(to, spec) }
    }
}

/**
 * A light passing over a loading placeholder in [shape], one sweep across the window every [SHIMMER_MS]: every
 * placeholder on the page reads the same frame clock and its own place in the window, so the light crosses them
 * together, as one band across the page.
 */
@Composable
internal fun Modifier.shimmer(shape: Shape): Modifier {
    if (!lively()) return this
    val phase =
        produceState(0f) {
            while (true) withInfiniteAnimationFrameMillis { value = (it % SHIMMER_MS) / SHIMMER_MS.toFloat() }
        }
    val window =
        LocalWindowInfo.current.containerSize.width
            .toFloat()
    val left = remember { FloatArray(1) }
    val shine = HaulColors.shimmer
    return onGloballyPositioned { left[0] = it.positionInWindow().x }.drawWithContent {
        drawContent()
        val wide = if (window > 0f) window else size.width
        val band = wide * SHIMMER_BAND
        val centre = -band + phase.value * (wide + 2 * band) - left[0]
        val light =
            Brush.linearGradient(
                listOf(Color.Transparent, shine, Color.Transparent),
                start = Offset(centre - band / 2, 0f),
                end = Offset(centre + band / 2, band * SHIMMER_SLANT),
            )
        drawOutline(shape.createOutline(size, layoutDirection, this), light)
    }
}

/**
 * [text] whose characters roll when they change, as an odometer's: each one that differs from the one drawn before
 * slides out of its place and its successor slides in — up when the figure grew, down when it shrank. At rest it is
 * [Text], drawn as [Text] draws it, to the pixel: a roll draws the text recorded before the change and the text now,
 * each clipped to the places of the characters that changed, and nothing else.
 */
@Composable
internal fun RollingText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    if (!lively()) {
        Text(text, style, modifier, softWrap = false)
        return
    }
    val measurer = rememberTextMeasurer()
    val now = rememberGraphicsLayer()
    val before = rememberGraphicsLayer()
    val roll = remember(now, before) { Roll(text, now, before) }
    // Seen in composition, so the draw that follows already rolls: the text before is in the layer drawn last.
    roll.changeTo(text)
    // Laid out with the figures [Text] draws, so the places are where its characters are.
    val legible = HaulType.legibleFigures(AnnotatedString(text), style)
    val progress = remember { Animatable(1f) }
    LaunchedEffect(text) {
        if (!roll.pending) return@LaunchedEffect
        roll.pending = false
        progress.snapTo(0f)
        progress.animateTo(1f, tween(ROLL_MS, easing = Arrive))
    }
    Text(
        text,
        style,
        modifier.drawWithContent {
            // Read whether or not it is pending: the draw then follows the roll from its first frame.
            val rolled = progress.value
            val at = if (roll.pending) 0f else rolled
            roll.now.record { this@drawWithContent.drawContent() }
            if (at >= 1f) {
                drawContent()
                return@drawWithContent
            }
            val places = roll.places(measurer, legible, style, size.width)
            val height = size.height
            val way = if (roll.grew) -1f else 1f
            outside(places, 0, height) { this@drawWithContent.drawContent() }
            places.forEach { (left, right) ->
                clipRect(left, 0f, right, height) {
                    translate(top = way * at * height) { drawLayer(roll.before) }
                    translate(top = -way * (1f - at) * height) { drawLayer(roll.now) }
                }
            }
        },
        softWrap = false,
    )
}

/** What a [RollingText] rolls from: the text drawn before, recorded, and which of its places changed. */
private class Roll(
    var shown: String,
    var now: GraphicsLayer,
    var before: GraphicsLayer,
) {
    private var from = shown

    /** A change not yet rolling: drawn as the start of its roll. */
    var pending = false

    /** Whether the figure grew: the new characters come up from below. */
    var grew = false
        private set

    fun changeTo(text: String) {
        if (text == shown) return
        from = shown
        shown = text
        grew = (text.filter(Char::isDigit).toLongOrNull() ?: 0) >= (from.filter(Char::isDigit).toLongOrNull() ?: 0)
        // The layer holding the text before keeps it; the other records the text now.
        val kept = now
        now = before
        before = kept
        pending = true
    }

    /** The places, left to right and as wide as [width] allows, of the characters that differ from before. */
    fun places(
        measurer: TextMeasurer,
        legible: AnnotatedString,
        style: TextStyle,
        width: Float,
    ): List<Pair<Float, Float>> {
        if (from.length != shown.length) return listOf(0f to width)
        val laid = measurer.measure(legible, style, softWrap = false)
        return shown.indices.filter { shown[it] != from[it] }.map { index ->
            val left = laid.getBoundingBox(index).left
            val right = if (index + 1 < shown.length) laid.getBoundingBox(index + 1).left else width
            left to right
        }
    }
}

/** [draw] clipped to everything outside [places] from the [first] on, each as tall as [height]. */
private fun DrawScope.outside(
    places: List<Pair<Float, Float>>,
    first: Int,
    height: Float,
    draw: DrawScope.() -> Unit,
) {
    if (first == places.size) return draw()
    val (left, right) = places[first]
    clipRect(left, 0f, right, height, ClipOp.Difference) { outside(places, first + 1, height, draw) }
}

/**
 * A ring and sparks bursting out of it each time [on] turns on after it is first drawn: a heart saved. They are
 * drawn around it, past its bounds, and are gone once the burst is over.
 */
@Composable
internal fun Modifier.burstsOn(
    on: Boolean,
    color: Color = HaulColors.tertiaryContainer,
): Modifier {
    if (!lively()) return this
    val burst = remember { Animatable(1f) }
    var last by remember { mutableStateOf(on) }
    LaunchedEffect(on) {
        if (on == last) return@LaunchedEffect
        last = on
        if (on) {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(BURST_MS, easing = Arrive))
        }
    }
    return drawBehind {
        val t = burst.value
        if (t >= 1f) return@drawBehind
        val radius = size.minDimension / 2
        val fade = 1f - t
        drawCircle(
            color.copy(alpha = fade),
            radius = radius * (0.6f + t),
            style = Stroke(width = radius * 0.35f * fade),
        )
        repeat(SPARKS) { spark ->
            val angle = spark * 2 * PI / SPARKS - PI / 2
            val out = radius * (0.9f + 0.9f * t)
            drawCircle(
                color.copy(alpha = fade),
                radius = radius * 0.16f * fade,
                center = center + Offset(cos(angle).toFloat() * out, sin(angle).toFloat() * out),
            )
        }
    }
}

// The first window settles within 600 ms of the page arriving (B-168): the last block starts by
// MAX_ORDER × STAGGER_MS + SPREAD_MS and takes REVEAL_MS.
private const val REVEAL_MS = 280
private const val STAGGER_MS = 35L
private const val MAX_ORDER = 5
private const val SPREAD_MS = 100f
private const val WIGGLE_MS = 380
private const val ROLL_MS = 320
private const val BURST_MS = 560
private const val SPARKS = 7

/** How far a pressed control sinks at its edges, and the least it may keep of its size. */
private const val SINK_PX = 5f
private const val MAX_SINK = 0.06f

private const val GLOW = 0.1f

private const val SHIMMER_MS = 1600
private const val SHIMMER_BAND = 0.32f
private const val SHIMMER_SLANT = 0.35f
