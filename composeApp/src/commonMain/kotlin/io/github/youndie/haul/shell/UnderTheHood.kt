package io.github.youndie.haul.shell

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.feature.cart.CartCommands
import io.github.youndie.haul.feature.cart.method
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.ui.Motion
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotComponentRenderer
import io.github.youndie.kompot.KompotRegistry
import io.github.youndie.kompot.commands.UpdateAction
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.realtime.UpdateComponentMessage
import io.github.youndie.kompot.standard.SequenceAction
import kotlin.time.Duration
import kotlin.time.TimeSource

// «Under the hood» (B-163): every page of the store is a kompot tree the server built, and nothing on the page said so.
// A toggle in the demo strip opens a panel over the page — the tree of the screen on view as an outline, the request
// that brought it, and, while the panel is open, each partial update the page takes, its nodes flashed where they are
// drawn. Only the storefront's root has one (`App`); a view drawn on its own — a golden, the shell's tests — has none.
//
// What it costs while closed, and why: the transport keeps the last [KEPT] requests' path, status, size and times
// (never a body), so the panel opened over a page already drawn can say what brought it; the screen's current tree is
// a reference to the one already drawn, with the nodes updates replaced in it since it arrived, so the outline is the
// page as it is. Nothing else is held: no update is listed, nothing flashes, no tree is encoded, the panel is not
// composed. Each node is drawn inside one box that passes its constraints through, so the flash knows where the node
// is; it draws nothing unless the node is flashing.

/** How many requests the transport remembers, and how many updates the panel lists. */
internal const val KEPT: Int = 20

/**
 * A request the storefront made for a tree — a screen, its parts, the header alone, the strip: its [path], the
 * [status] answered, the body's size in [bytes] as UTF-8, the server's time over it from `Server-Timing`
 * ([serverMillis], `null` without the header) and [total], how long the browser waited for it, sign-in's headers and
 * the body included.
 */
internal class HoodRequest(
    val path: String,
    val status: Int,
    val bytes: Int,
    val serverMillis: Double?,
    val total: Duration,
)

/**
 * A partial update the page took while the panel was open: the request it answered — [method] and [url], `null` when
 * it came from nowhere the panel watches — and the ids of the nodes it [replaced], in the order written. [request] is
 * the transport's record of a `load`'s `GET`, [total] a command's time.
 */
internal class HoodUpdate(
    val method: String?,
    val url: String?,
    val replaced: List<String>,
    val request: HoodRequest?,
    val total: Duration?,
)

/** A batch of nodes flashed together: its own identity, so a new batch restarts the flash. */
internal class FlashBatch(
    val ids: List<String>,
)

/**
 * The screen on view as the panel reads it: the [tree] drawn — `null` for a page the shell draws itself, the sign-in's,
 * a placeholder or an error — fetched at [path], and the nodes updates replaced in it since it arrived. A newer tree is
 * a new screen; one equal to this but delivered again is too, as kompot drops the overrides on every arrival.
 */
@Stable
internal class HoodScreen(
    val tree: KompotComponent?,
    val path: String,
) {
    private val replaced = mutableStateMapOf<String, Replaced>()
    private var written = 0L

    /** Which nodes of the outline are open, by their place in it; the root is open until closed. */
    val expanded: SnapshotStateMap<String, Boolean> = mutableStateMapOf()

    fun replace(updates: List<UpdateComponentMessage>) {
        updates.forEach {
            written += 1
            replaced[it.componentId] = Replaced(it.component, written)
        }
    }

    /** The outline of the tree as it is drawn now, updates included; `null` with no tree. */
    fun outline(): HoodNode? = tree?.let { HoodOutline.of(it, replaced.toMap()) }
}

/** A node an update replaced, and the order it was written in: a later replacement of a node around it wins. */
internal class Replaced(
    val component: KompotComponent,
    val order: Long,
)

/**
 * «Under the hood», as the storefront's root holds it: whether the panel is [open], the requests the transport made
 * ([recording]), the screen on view ([shown]) and, while open, the updates the page took and the nodes flashing for
 * them. [motion] is the page's (B-158): under [Motion.Still] nothing flashes, and the update is listed all the same.
 */
@Stable
public class UnderTheHood(
    private val motion: Motion = Motion.Lively,
    private val time: TimeSource = TimeSource.Monotonic,
) {
    /** Whether the panel is drawn over the page. */
    public var open: Boolean by mutableStateOf(false)
        private set

    /** The toggle in the demo strip: opens the panel, or closes it. */
    public fun toggle() {
        if (open) close() else open = true
    }

    /** Closes the panel: its updates are forgotten and nothing flashes any more. */
    public fun close() {
        open = false
        updates.clear()
        sources.clear()
        flashes.clear()
        flashing = null
    }

    private val kept = ArrayDeque<HoodRequest>()

    // Not the list itself as state: a write to a plain deque is cheaper, and this tells the panel when to read again.
    private var heard by mutableIntStateOf(0)

    /** The requests remembered, oldest first. */
    internal val requests: List<HoodRequest>
        get() {
            heard
            return kept.toList()
        }

    /** The last request made for [path], when it is still remembered. */
    internal fun requestFor(path: String): HoodRequest? {
        heard
        return kept.lastOrNull { it.path == path }
    }

    internal fun heard(request: HoodRequest) {
        if (kept.size == KEPT) kept.removeFirst()
        kept.addLast(request)
        heard += 1
    }

    /**
     * [transport], each request remembered as it is answered: its path, status, size and times, never its body. One
     * that got no answer is not remembered — the page says so itself.
     */
    public fun recording(transport: HaulTransport): HaulTransport =
        HaulTransport { path ->
            val asked = time.markNow()
            val response = transport.get(path)
            heard(
                HoodRequest(
                    path,
                    response.status,
                    response.body.utf8Size(),
                    serverMillis(response.serverTiming),
                    asked.elapsedNow(),
                ),
            )
            response
        }

    /** [commands], each answer told to the panel while it is open, so an update a command answered names it. */
    public fun watching(commands: CartCommands): CartCommands =
        CartCommands { command ->
            val asked = time.markNow()
            commands.send(command).also { if (open) answered(command.method, command.url, it, asked.elapsedNow()) }
        }

    /** The screen on view; `null` until a page has drawn. */
    internal var screen: HoodScreen? by mutableStateOf(null)
        private set

    /** The page drawn is [tree], fetched at [path]: a new screen unless it is the one already shown. */
    internal fun shown(
        tree: KompotComponent,
        path: String,
    ) {
        if (screen?.tree !== tree) screen = HoodScreen(tree, path)
    }

    /** The page drawn has no tree — the shell draws it itself at [path]. */
    internal fun noTree(path: String) {
        val now = screen
        if (now == null || now.tree != null || now.path != path) screen = HoodScreen(null, path)
    }

    /** The updates the page took while the panel was open, newest first. */
    internal val updates = mutableStateListOf<HoodUpdate>()

    private val sources = ArrayDeque<Source>()

    /**
     * A `load` or a command was answered with [answer], to [method] [url]: an update in it is listed under that request.
     * Remembered only while the panel is open.
     */
    internal fun answered(
        method: String,
        url: String,
        answer: KompotAction,
        total: Duration? = null,
    ) {
        if (!open) return
        if (sources.size == SOURCES) sources.removeFirst()
        sources.addLast(Source(method, url, answer, total))
    }

    /**
     * [update] is about to replace its nodes on the page: the outline takes them; with the panel open it is listed
     * and, on a lively page, its nodes flash.
     */
    internal fun updated(update: UpdateAction) {
        screen?.replace(update.updates)
        if (!open) return
        val source = sources.lastOrNull { it.answer.holds(update) }
        val ids = update.updates.map { it.componentId }
        updates.add(
            0,
            HoodUpdate(
                source?.method,
                source?.url,
                ids,
                source?.url?.let(::requestFor)?.takeIf { source.method == GET },
                source?.total,
            ),
        )
        while (updates.size > KEPT) updates.removeAt(updates.lastIndex)
        flash(ids)
    }

    /** How strongly each flashing node is drawn, from 1 down to 0; empty while nothing flashes. */
    internal val flashes: SnapshotStateMap<String, Float> = mutableStateMapOf()

    /** The batch flashing now, which [HoodFlashes] fades. */
    internal var flashing: FlashBatch? by mutableStateOf(null)

    private fun flash(ids: List<String>) {
        if (motion != Motion.Lively) return
        flashes.clear()
        ids.forEach { flashes[it] = 1f }
        flashing = FlashBatch(ids)
    }

    /** How strongly the node [id] flashes now: 0 when it does not. */
    internal fun glow(id: String): Float = flashes[id] ?: 0f
}

/** A request whose answer may hold an update. */
private class Source(
    val method: String,
    val url: String,
    val answer: KompotAction,
    val total: Duration?,
)

private const val SOURCES = 8
private const val GET = "GET"

/** Whether [update] is this action, or one of the actions it runs in turn. */
private fun KompotAction.holds(update: UpdateAction): Boolean =
    this === update || (this is SequenceAction && actions.any { it.holds(update) })

/** The «Under the hood» of the storefront being drawn; `null` — a view drawn on its own, a golden — has none. */
public val LocalUnderTheHood: ProvidableCompositionLocal<UnderTheHood?> = staticCompositionLocalOf { null }

/** [this], the screen's action handler, telling [hood] of each update before it is written. */
internal fun KompotActionHandler.watchedBy(hood: UnderTheHood?): KompotActionHandler =
    if (hood == null) {
        this
    } else {
        KompotActionHandler { action ->
            if (action is UpdateAction) hood.updated(action)
            handle(action)
        }
    }

/**
 * [this], every node drawn inside a box that passes its constraints through — as a column's child already is — and
 * draws over the node, while [hood] flashes it, an outline and a wash of the store's blue.
 */
internal fun KompotRegistry.flashedBy(hood: UnderTheHood): KompotRegistry =
    decorated { renderers ->
        renderers.mapValues { (_, renderer) ->
            // The registry looks a renderer up by the component's class, so the component is always its type.
            @Suppress("UNCHECKED_CAST")
            Flashed(renderer as KompotComponentRenderer<KompotComponent>, hood)
        }
    }

private class Flashed(
    private val inner: KompotComponentRenderer<KompotComponent>,
    private val hood: UnderTheHood,
) : KompotComponentRenderer<KompotComponent> {
    @Composable
    override fun Render(
        component: KompotComponent,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        val id = component.id
        val accent = HaulColors.primary
        Box(
            Modifier.drawWithContent {
                drawContent()
                // Read while drawing: a flash redraws the node, it never composes the page again.
                val glow = hood.glow(id)
                if (glow > 0f) {
                    val inset = FLASH_STROKE.toPx() / 2
                    drawRect(accent.copy(alpha = FLASH_WASH * glow))
                    drawRoundRect(
                        accent.copy(alpha = glow),
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - 2 * inset, size.height - 2 * inset),
                        cornerRadius = CornerRadius(FLASH_CORNER.toPx()),
                        style = Stroke(FLASH_STROKE.toPx()),
                    )
                }
            },
            propagateMinConstraints = true,
        ) {
            inner.Render(component, actionHandler, formController)
        }
    }
}

/** Fades the batch [hood] is flashing: held at full for a moment, then gone over [FLASH_FADE_MS]. */
@Composable
internal fun HoodFlashes(hood: UnderTheHood) {
    val batch = hood.flashing ?: return
    LaunchedEffect(batch) {
        animate(1f, 0f, animationSpec = tween(FLASH_FADE_MS, delayMillis = FLASH_HOLD_MS)) { value, _ ->
            batch.ids.forEach { hood.flashes[it] = value }
        }
        batch.ids.forEach(hood.flashes::remove)
        if (hood.flashing === batch) hood.flashing = null
    }
}

internal const val FLASH_HOLD_MS = 250
internal const val FLASH_FADE_MS = 900
private val FLASH_STROKE = 2.dp
private val FLASH_CORNER = 4.dp
private const val FLASH_WASH = 0.10f

/** The time `Server-Timing: app;dur=12.3` names, in milliseconds; `null` without one. */
internal fun serverMillis(header: String?): Double? =
    header
        ?.split(',')
        ?.map { it.trim() }
        ?.firstOrNull { it.substringBefore(';').trim() == "app" }
        ?.split(';')
        ?.firstNotNullOfOrNull { part -> part.trim().takeIf { it.startsWith("dur=") }?.removePrefix("dur=") }
        ?.toDoubleOrNull()

/** How many bytes [this] takes as UTF-8, counted without encoding it. */
internal fun String.utf8Size(): Int {
    var bytes = 0
    var index = 0
    while (index < length) {
        val char = this[index]
        bytes +=
            when {
                char.code < 0x80 -> {
                    1
                }

                char.code < 0x800 -> {
                    2
                }

                char.isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate() -> {
                    index += 1
                    4
                }

                else -> {
                    3
                }
            }
        index += 1
    }
    return bytes
}
