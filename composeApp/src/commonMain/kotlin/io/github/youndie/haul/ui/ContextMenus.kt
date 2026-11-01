package io.github.youndie.haul.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.round
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.OpenUrlAction
import kotlin.math.roundToInt

// The browser's own menu never opens over the canvas: Compose takes the right button for itself. On the web its text
// menu is the old one (Compose 1.12 turns the new, customisable one off on skiko): Copy and Select all in a look of its
// own, and pressing either did nothing (B-154). Here the right button opens the storefront's menu at the pointer
// instead, and Compose's never opens: over the page, what text offers; over a link, what a browser offers for one.

/** A menu the right button opened: where, in the overlay layer, and what it offers. */
internal class PointMenuShown(
    val at: Offset,
    val entries: List<MenuEntry>,
)

/**
 * The right button's menu on a [Selectable] page — the one open, if any — and the links on the page that offer one
 * of their own ([offersLink]). The page alone reads the right button and looks the link under it up here: a reader
 * on each link missed presses that the page's reader saw (Compose 1.12, wasm), and the text's menu opened over a link.
 */
@Stable
internal class ContextMenus {
    internal var shown: PointMenuShown? by mutableStateOf(null)

    /** The press the page marked last as one its words count again ([marksRepeatPresses]). */
    internal var repeat: PointerInputChange? = null

    /** The innermost control that took the last repeat press ([pressesAgain]): it alone presses on the release. */
    internal var pressedAgain: Any? = null

    /** Whether [press] is the one the page marked last. */
    internal fun isRepeat(press: PointerInputChange): Boolean =
        repeat?.let { it.id == press.id && it.uptimeMillis == press.uptimeMillis } == true

    private val areas = mutableListOf<MenuArea>()

    internal fun add(area: MenuArea) {
        areas += area
    }

    internal fun remove(area: MenuArea) {
        areas -= area
    }

    /** The innermost link or field drawn at [position] in the window, if any. */
    internal fun areaAt(position: Offset): MenuArea? =
        areas
            .mapNotNull { area ->
                area.coordinates
                    ?.takeIf { it.isAttached }
                    ?.boundsInRoot()
                    ?.let { area to it }
            }.filter { (_, bounds) -> bounds.contains(position) }
            .minByOrNull { (_, bounds) -> bounds.width * bounds.height }
            ?.first
}

/** A link or a field on the page ([offersMenu]): where it is drawn, and its menu's entries as they are when asked. */
internal class MenuArea(
    var entries: () -> List<MenuEntry>,
) {
    var coordinates: LayoutCoordinates? = null
}

/** The host of the right button's menus; `null` — a screenshot, a page that offers none — opens nothing. */
internal val LocalContextMenus: ProvidableCompositionLocal<ContextMenus?> = staticCompositionLocalOf { null }

/**
 * [content] whose words can be selected — dragged over, double-pressed — and copied, the right button opening the
 * storefront's menu over it: Copy (with words selected) and Select all, or, over a link, «Open in new tab» and «Copy
 * link» ([offersLink]). Copy writes the words through the page's clipboard ([LocalLinkCopier]); with none — a
 * screenshot — it is not offered.
 */
@Composable
internal fun Selectable(content: @Composable () -> Unit) {
    val menus = remember { ContextMenus() }
    val selection = rememberSelectionState()
    val copier = LocalLinkCopier.current
    val overlays = LocalOverlays.current
    val placed = remember { Placed() }
    val layer = overlays?.origin ?: Offset.Zero
    // The menu at a point of the window: a link's or a field's there, else the words'.
    val open: (Offset) -> Unit = { inWindow ->
        val entries =
            menus
                .areaAt(inWindow)
                ?.entries
                ?.invoke()
                ?.takeIf { it.isNotEmpty() }
                ?: textEntries(selection, copier)
        menus.shown = PointMenuShown(inWindow - layer, entries)
    }
    CompositionLocalProvider(LocalContextMenus provides menus) {
        Box(
            Modifier
                .onGloballyPositioned { placed.coordinates = it }
                // ⌘C or Ctrl+C copies the words selected. Compose's own copy waits on the web for the browser's
                // `copy` event, which never came: the shortcut copied nothing (Compose 1.12, wasm).
                .onPreviewKeyEvent { key ->
                    val copies =
                        key.type == KeyEventType.KeyDown && key.key == Key.C &&
                            (key.isMetaPressed || key.isCtrlPressed)
                    val words = selectedWords(selection)
                    if (copies && words.isNotEmpty() && copier != null) {
                        copier.copyText(words) { }
                        true
                    } else {
                        false
                    }
                }.marksRepeatPresses(menus)
                .onRightPress { position -> placed.coordinates?.let { open(it.localToRoot(position)) } },
            propagateMinConstraints = true,
        ) {
            SelectionContainer(selection) { content() }
        }
    }
    menus.shown?.let {
        PointMenu(it, onClose = { menus.shown = null }) { at ->
            menus.shown = null
            open(at + layer)
        }
    }
}

/**
 * Marks the press of the left button that the page's selection will count as a second (or third) — within a double
 * press's time of the last one, and within the slop around it — in [menus], before anything on the page reads it, for
 * the controls that are pressed all the same ([pressesAgain]). The selection counts across all the page's texts, so it
 * is counted here, over the whole page, and not by each control. It takes no press.
 */
private fun Modifier.marksRepeatPresses(menus: ContextMenus): Modifier =
    pointerInput(Unit) {
        var last: PointerInputChange? = null
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val down = event.changes.singleOrNull()?.takeIf { it.changedToDownIgnoreConsumed() } ?: continue
                if (!event.buttons.isPrimaryPressed) continue
                val before = last
                last = down
                val again =
                    before != null &&
                        down.uptimeMillis - before.uptimeMillis < viewConfiguration.doubleTapTimeoutMillis &&
                        (down.position - before.position).getDistance() < viewConfiguration.touchSlop
                if (again) menus.repeat = down
            }
        }
    }

/** What the right button offers over the page's words: Copy, when some are selected, and Select all. */
private fun textEntries(
    selection: SelectionState,
    copier: LinkCopier?,
): List<MenuEntry> {
    val words = selectedWords(selection)
    return listOfNotNull(
        if (words.isNotEmpty() && copier != null) MenuEntry(COPY) { copier.copyText(words) { } } else null,
        MenuEntry(SELECT_ALL) { selection.selectAll() },
    )
}

/** The words [selection] holds, a line apart where it spans several texts; empty with nothing selected. */
private fun selectedWords(selection: SelectionState): String = selection.selectedTexts.joinToString("\n") { it.text }

/**
 * The right button over this opens «Open in new tab» and «Copy link» for [action]'s address — an address the server
 * wrote, a [NavigateAction]'s, or a whole one off the storefront, an [OpenUrlAction]'s (B-160); any other action is a
 * command, with no address to offer, and the right button over it opens the page's. Outside a [Selectable] page, or
 * with nobody to open or copy links, nothing opens.
 */
@Composable
internal fun Modifier.offersLink(action: KompotAction?): Modifier {
    val links = LocalLinkCopier.current ?: return this
    val path = (action as? NavigateAction)?.deeplink
    val url = (action as? OpenUrlAction)?.url
    return when {
        path != null -> {
            offersMenu {
                listOf(
                    MenuEntry(OPEN_IN_NEW_TAB) { links.openApart(path) },
                    MenuEntry(COPY_LINK) { links.copy(path) { } },
                )
            }
        }

        url != null -> {
            offersMenu {
                listOf(
                    MenuEntry(OPEN_IN_NEW_TAB) { links.openUrl(url) },
                    MenuEntry(COPY_LINK) { links.copyText(url) { } },
                )
            }
        }

        else -> {
            this
        }
    }
}

/**
 * The right button over this opens [entries] — asked for when it is pressed, so a field offers what its selection is
 * then — instead of the page's. Empty entries leave the page's menu. Outside a [Selectable] page nothing opens.
 */
@Composable
internal fun Modifier.offersMenu(entries: () -> List<MenuEntry>): Modifier {
    val menus = LocalContextMenus.current ?: return this
    val area = remember(menus) { MenuArea(entries) }
    area.entries = entries
    DisposableEffect(menus, area) {
        menus.add(area)
        onDispose { menus.remove(area) }
    }
    return onGloballyPositioned { area.coordinates = it }
}

/**
 * [onPress] at the press of the right button over this, read before anything under it and taken: Compose's own text
 * menu waits for a press nobody took, and never opens. Every event is read on its own, not as a gesture waiting for
 * its release: a menu opened by the press covers the page before the release comes, and a gesture waiting for it
 * would wait for good, the right button opening nothing after. The reader is never restarted — a press that came
 * while it restarted would be lost — so [onPress] is read as it is now.
 */
@Composable
private fun Modifier.onRightPress(onPress: (Offset) -> Unit): Modifier {
    val current by rememberUpdatedState(onPress)
    return pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                    event.changes.forEach { it.consume() }
                    current(event.changes.first().position)
                }
            }
        }
    }
}

/** Where something was last placed, read when the right button goes down over it. */
private class Placed {
    var coordinates: LayoutCoordinates? = null
}

internal const val COPY: String = "Copy"
internal const val SELECT_ALL: String = "Select all"
internal const val OPEN_IN_NEW_TAB: String = "Open in new tab"
internal const val COPY_LINK: String = "Copy link"

/**
 * [shown]'s entries at its point, over the page: below and to the right of it, turned back to the left or up where it
 * would not fit. A press anywhere off it, or Escape, is [onClose] — of the right button, [onRightPressOff], at the
 * point in the overlay layer; an entry pressed closes it and runs.
 */
@Composable
private fun PointMenu(
    shown: PointMenuShown,
    onClose: () -> Unit,
    onRightPressOff: (Offset) -> Unit,
) {
    OverPage {
        val drawn = remember { arrayOf(IntRect.Zero) }
        Layout(
            content = { MenuColumn(shown.entries, current = null, onClose = onClose) },
            modifier =
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            // A browser's menu closes on the press, not on the release; the right button opens the
                            // page's menu where it was pressed instead. `awaitFirstDown` reads a mouse's left button
                            // alone.
                            val event = awaitPointerEvent()
                            val press = event.changes.first()
                            if (event.type == PointerEventType.Press && !drawn[0].contains(press.position.round())) {
                                press.consume()
                                if (event.buttons.isSecondaryPressed) onRightPressOff(press.position) else onClose()
                            }
                        }
                    }
                },
        ) { measurables, constraints ->
            val menu = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
            val window = IntSize(constraints.maxWidth, constraints.maxHeight)
            val x =
                shown.at.x
                    .roundToInt()
                    .let { if (it + menu.width > window.width) it - menu.width else it }
            val y =
                shown.at.y
                    .roundToInt()
                    .let { if (it + menu.height > window.height) it - menu.height else it }
            val at =
                IntOffset(
                    x.coerceIn(0, (window.width - menu.width).coerceAtLeast(0)),
                    y.coerceIn(0, (window.height - menu.height).coerceAtLeast(0)),
                )
            drawn[0] = IntRect(at, IntSize(menu.width, menu.height))
            layout(window.width, window.height) { menu.place(at) }
        }
    }
}
