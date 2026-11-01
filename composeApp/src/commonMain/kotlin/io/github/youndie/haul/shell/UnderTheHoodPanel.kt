package io.github.youndie.haul.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.LocalHaulCompact
import io.github.youndie.haul.ui.HaulIcons
import io.github.youndie.haul.ui.Icon
import io.github.youndie.haul.ui.LocalLinkCopier
import io.github.youndie.haul.ui.pressable
import io.github.youndie.haul.ui.toggles
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.DurationUnit

// The panel «Under the hood» opens over the page (B-163): a sheet from the right at 1440, from the bottom across the
// width on a phone. It reads only what the storefront already has — the tree drawn, the requests made — and the page
// under it goes on answering its presses, so a filter pressed beside it flashes its parts and is listed in it. No
// artboard draws it; it is drawn from the theme's tokens, the outline in the store's mono face.

/** The tag on the panel. */
public const val HOOD_PANEL_TAG: String = "under-the-hood"

/** The tag on the panel's close control. */
public const val HOOD_CLOSE_TAG: String = "under-the-hood-close"

/** The tag on the request that brought the tree. */
public const val HOOD_REQUEST_TAG: String = "under-the-hood-request"

/** The tag on each update listed. */
public const val HOOD_UPDATE_TAG: String = "under-the-hood-update"

/** The panel's sections, in the client's words: the server names the panel, the client its parts. */
internal const val HOOD_REQUEST: String = "Request"
internal const val HOOD_UPDATES: String = "Updates"
internal const val HOOD_TREE: String = "Tree"

/** What the panel says first: what the page is. */
internal const val HOOD_LEAD: String =
    "This page is a kompot tree the server built and sent; the browser draws it with Compose. Nothing here is asked " +
        "of the server: it is what the page already has."

/** The updates' place, before the first one. */
internal const val HOOD_NO_UPDATES: String =
    "A filter, the sort or a card’s «+» answers with only the parts it changes: they flash on the page and are listed here."

/** The tree's place on a page the shell draws itself. */
internal const val HOOD_NO_TREE: String = "The browser draws this page itself: no tree came for it."

/** The request's place when the transport no longer remembers it. */
internal const val HOOD_NOT_KEPT: String = "Asked for before the last 20 requests: not remembered."

/**
 * The panel over the page: [label], the server's name for it, and its close control, then the request that brought
 * the screen on view, the updates it took while the panel was open, and its tree as an outline, each node opening to
 * its fields, with a link to where it is drawn and where it is built when [HoodSources] knows.
 */
@Composable
internal fun UnderTheHoodPanel(
    hood: UnderTheHood,
    label: String,
) {
    val compact = LocalHaulCompact.current
    val edge = HaulColors.outlineControl
    Box(Modifier.fillMaxSize(), contentAlignment = if (compact) Alignment.BottomCenter else Alignment.TopEnd) {
        val shape = if (compact) RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp) else RoundedCornerShape(0.dp)
        Column(
            (
                if (compact) {
                    Modifier.fillMaxWidth().fillMaxHeight(
                        COMPACT_HEIGHT,
                    )
                } else {
                    Modifier.width(WIDTH).fillMaxHeight()
                }
            ).testTag(HOOD_PANEL_TAG)
                .drawBehind {
                    // The edge the page meets: its left on a desktop, its top on a phone.
                    val line = 1.dp.toPx()
                    if (compact) {
                        drawRect(edge, size = size.copy(height = line))
                    } else {
                        drawRect(edge, size = size.copy(width = line))
                    }
                }.background(HaulColors.surfaceContainerLowest, shape)
                // Its presses are its own: none reaches the page under it.
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        ) {
            Heading(label, hood::close)
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SIDE, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                BasicText(HOOD_LEAD, style = words(13f))
                val screen = hood.screen
                // The outline follows the updates the screen takes: they replace nodes in what it reads.
                val root by remember(screen) { derivedStateOf { screen?.outline() } }
                Section(HOOD_REQUEST) { RequestOf(hood, screen) }
                Section(HOOD_UPDATES) { Updates(hood.updates) }
                Section(HOOD_TREE, root?.count?.let { "$it ${if (it == 1) "node" else "nodes"}" }) {
                    val outline = root
                    if (screen == null || outline == null) {
                        BasicText(HOOD_NO_TREE, style = words(13f))
                    } else {
                        Column { Outline(outline, 0, screen.expanded) }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun Heading(
    label: String,
    onClose: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = SIDE, end = 12.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, Modifier.weight(1f), style = HaulType.text(18f, 700, lineHeight = 1.3f))
        Box(
            Modifier
                .testTag(HOOD_CLOSE_TAG)
                .size(32.dp)
                .semantics {
                    contentDescription = "Close $label"
                    role = Role.Button
                }.pressable(onClose),
            contentAlignment = Alignment.Center,
        ) { Icon(HaulIcons.close, 14.dp, HaulColors.onSurfaceVariant) }
    }
}

@Composable
private fun Section(
    title: String,
    note: String? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(title.uppercase(), style = HaulType.label(11f, 600, 0.08f).copy(color = HaulColors.outline))
            note?.let {
                BasicText("  ·  $it", style = HaulType.label(11f, 400, 0.04f).copy(color = HaulColors.outlineMuted))
            }
        }
        content()
    }
}

/** The request that brought the tree on view: `GET` and its path, then its status, size and times. */
@Composable
private fun RequestOf(
    hood: UnderTheHood,
    screen: HoodScreen?,
) {
    if (screen == null) {
        BasicText(HOOD_NO_TREE, style = words(13f))
        return
    }
    val request = hood.requestFor(screen.path)
    if (screen.tree == null && request == null) {
        BasicText(HOOD_NO_TREE, style = words(13f))
        return
    }
    Column(Modifier.testTag(HOOD_REQUEST_TAG), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BasicText("GET ${screen.path}", style = mono(13f, 600))
        BasicText(request?.said() ?: HOOD_NOT_KEPT, style = words(13f))
    }
}

/** The updates the page took while the panel was open, newest first. */
@Composable
private fun Updates(updates: List<HoodUpdate>) {
    if (updates.isEmpty()) {
        BasicText(HOOD_NO_UPDATES, style = words(13f))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        updates.forEach { update ->
            Column(Modifier.testTag(HOOD_UPDATE_TAG), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(
                    listOfNotNull(update.method, update.url).joinToString(" ").ifEmpty { "update" },
                    style = mono(13f, 600),
                )
                BasicText(update.said(), style = words(13f))
            }
        }
    }
}

@Composable
private fun Outline(
    node: HoodNode,
    depth: Int,
    expanded: SnapshotStateMap<String, Boolean>,
) {
    val open = expanded[node.key] ?: (depth == 0)
    NodeRow(node, depth, open) { expanded[node.key] = !open }
    if (open) {
        node.fields.forEach { FieldRow(it, depth + 1) }
        node.children.forEach { child -> key(child.key) { Outline(child, depth + 1, expanded) } }
    }
}

/** A node: its type and id, pressed to open or close; where it is drawn and built, when known, at its end. */
@Composable
private fun NodeRow(
    node: HoodNode,
    depth: Int,
    open: Boolean,
    onToggle: () -> Unit,
) {
    val openable = node.fields.isNotEmpty() || node.children.isNotEmpty()
    val state = if (open) ToggleableState.On else ToggleableState.Off
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (openable) Modifier.toggles("${node.type} ${node.id}", state, Role.Switch, onToggle) else Modifier)
            .padding(start = indent(depth), top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            if (openable) {
                Icon(if (open) HaulIcons.chevronDown else HaulIcons.chevronRight, 11.dp, HaulColors.outline)
            }
        }
        val name = listOfNotNull(node.via?.let { "$it:" }, node.type).joinToString(" ")
        BasicText(name, Modifier.padding(start = 2.dp), style = mono(12f, 600))
        BasicText(
            if (node.id.isEmpty()) "" else " #${node.id}",
            Modifier.weight(1f),
            style = mono(12f).copy(color = HaulColors.outlineMuted),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HoodSources.of(node.type)?.let { SourceLinks(it) }
    }
}

/** «view» and «server»: the files the node's type is drawn and built in, each opened in a tab of its own. */
@Composable
private fun SourceLinks(source: HoodSource) {
    val links = LocalLinkCopier.current
    Row(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOfNotNull(source.view?.let { VIEW to it }, source.builder?.let { BUILDER to it }).forEach { (word, path) ->
            BasicText(
                word,
                Modifier
                    .semantics { contentDescription = "$word: ${path.substringAfterLast('/')} on GitHub" }
                    .pressable { links?.openUrl(HoodSources.url(path)) },
                style = mono(11f, 600).copy(color = HaulColors.primary, textDecoration = TextDecoration.Underline),
            )
        }
    }
}

/** A field of an open node: its name, then its value said short. */
@Composable
private fun FieldRow(
    field: HoodField,
    depth: Int,
) {
    Row(Modifier.fillMaxWidth().padding(start = indent(depth) + 18.dp, top = 1.dp, bottom = 1.dp)) {
        BasicText("${field.name}: ", style = mono(12f).copy(color = HaulColors.outline))
        BasicText(field.value, Modifier.weight(1f), style = mono(12f).copy(color = HaulColors.onSurfaceVariant))
    }
}

/** The words for the node links: where the type is drawn, where it is built. */
internal const val VIEW: String = "view"
internal const val BUILDER: String = "server"

private fun indent(depth: Int): Dp = (depth * INDENT).dp

/** «200 · 48.2 kB · server 12.4 ms · total 37 ms». */
internal fun HoodRequest.said(): String =
    listOfNotNull(
        status.toString(),
        bytes.said(),
        serverMillis?.let { "server ${tenths(it)} ms" },
        "total ${total.millis()} ms",
    ).joinToString(" · ")

/** «replaced header, card-mug-3 · server 8.1 ms · total 22 ms». */
internal fun HoodUpdate.said(): String =
    listOfNotNull(
        "replaced " + replaced.joinToString(", "),
        request?.serverMillis?.let { "server ${tenths(it)} ms" },
        (request?.total ?: total)?.let { "total ${it.millis()} ms" },
    ).joinToString(" · ")

/** A size as a browser's network panel writes it: «812 B», «48.2 kB». */
internal fun Int.said(): String = if (this < KILO) "$this B" else "${tenths(this / KILO.toDouble())} kB"

private fun tenths(value: Double): String {
    val tenths = (value * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}

private fun Duration.millis(): Int = toDouble(DurationUnit.MILLISECONDS).roundToInt()

@Composable
private fun words(size: Float): TextStyle =
    HaulType.text(size, lineHeight = 1.45f).copy(color = HaulColors.onSurfaceVariant)

@Composable
private fun mono(
    size: Float,
    weight: Int = 400,
): TextStyle = HaulType.label(size, weight, 0f).copy(lineHeight = (size * MONO_LINE).sp, color = HaulColors.onSurface)

private val WIDTH = 440.dp
private val SIDE = 20.dp
private const val COMPACT_HEIGHT = 0.62f
private const val INDENT = 14
private const val KILO = 1000
private const val MONO_LINE = 1.45f
