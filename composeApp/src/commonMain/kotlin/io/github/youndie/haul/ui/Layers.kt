package io.github.youndie.haul.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

// What opens over the page — a menu, the phone's menu, the phone's filter sheet, a review's photos — is drawn in the storefront's own
// layer, over the page, rather than in a `Popup` or a `Dialog` (B-126). Each of those is a layer of its own in
// Compose's scene, and Compose Multiplatform 1.12's browser mirror of the page for assistive technology follows
// only the newest layer: once one has opened and closed, the mirror holds the closed layer's nodes until the page
// is reloaded (CMP-9368, fixed upstream in 1.13). In the storefront's layer there is one tree, and the mirror
// follows it.
//
// Above all of it is what the shopper has to read and may have to answer — B-62's «The page didn’t update» with
// «Retry», a message (B-75) — drawn over the page and over whatever is open on it (B-145). On a phone the filter
// sheet covers the page, and the notice a failed tick left was drawn under it: the shopper saw nothing, and the
// press meant for its Retry landed on the sheet's «Show N items» beneath.

/** What is drawn over the storefront now, in the order it opened; the host draws it ([OverlayHost]). */
@Stable
internal class Overlays {
    internal val shown = mutableStateListOf<Overlay>()

    /** The notices over all of it ([NoticeOverPage]), in the order they were raised. */
    internal val notices = mutableStateListOf<Overlay>()

    /** Whether a control of a notice has the key focus: a sheet under it leaves the focus there ([SheetOverPage]). */
    internal var noticeFocused: Boolean by mutableStateOf(false)

    /** Where the host is in the window, so that what opens under a control is placed under it. */
    internal var origin: Offset by mutableStateOf(Offset.Zero)
}

/** One thing drawn over the page: its content, and the composition locals where it was opened from. */
internal class Overlay(
    content: @Composable () -> Unit,
    locals: CompositionLocalContext,
) {
    var content: @Composable () -> Unit by mutableStateOf(content)
    var locals: CompositionLocalContext by mutableStateOf(locals)
}

/** The host of what is drawn over the page; none outside the storefront (a screenshot), where nothing opens. */
internal val LocalOverlays: ProvidableCompositionLocal<Overlays?> = staticCompositionLocalOf { null }

/**
 * [content], over it whatever [OverPage] opened inside it — the last opened on top — and over that the notices
 * [NoticeOverPage] raised. The box is as big as the window: the storefront's own.
 */
@Composable
internal fun OverlayHost(content: @Composable () -> Unit) {
    val overlays = remember { Overlays() }
    Box(Modifier.fillMaxSize().onGloballyPositioned { overlays.origin = it.positionInRoot() }) {
        CompositionLocalProvider(LocalOverlays provides overlays) { content() }
        overlays.shown.forEach { overlay ->
            key(overlay) {
                CompositionLocalProvider(overlay.locals) {
                    CompositionLocalProvider(LocalOnTop provides (overlay === overlays.shown.last())) {
                        overlay.content()
                    }
                }
            }
        }
        // It reads no press: one beside a notice reaches what is drawn under it.
        Box(Modifier.fillMaxSize().onFocusChanged { overlays.noticeFocused = it.hasFocus }) {
            overlays.notices.forEach { notice ->
                key(notice) { CompositionLocalProvider(notice.locals) { notice.content() } }
            }
        }
    }
}

/**
 * [content] drawn over the whole storefront while this is composed, with the composition locals of the place it is
 * called from. Outside an [OverlayHost] it draws nothing, so [isOverPage] tells whether opening it would show
 * anything.
 */
@Composable
internal fun OverPage(content: @Composable () -> Unit) {
    val overlays = LocalOverlays.current ?: return
    Raised(overlays.shown, content)
}

/**
 * A notice the shopper has to read and may have to answer — «The page didn’t update» with «Retry», a message —
 * drawn over the page and over whatever is open on it: the filter sheet, the phone's menu, a dialog (B-145).
 * Outside an [OverlayHost] it is drawn where it is called.
 */
@Composable
internal fun NoticeOverPage(content: @Composable () -> Unit) {
    val overlays = LocalOverlays.current ?: return content()
    Raised(overlays.notices, content)
}

/** [content] in [layer] while this is composed, with the composition locals of the place it is called from. */
@Composable
private fun Raised(
    layer: MutableList<Overlay>,
    content: @Composable () -> Unit,
) {
    val locals = currentCompositionLocalContext
    val overlay = remember { Overlay(content, locals) }
    SideEffect {
        overlay.content = content
        overlay.locals = locals
    }
    DisposableEffect(layer, overlay) {
        layer += overlay
        onDispose { layer -= overlay }
    }
}

/** Whether what is drawn here is the newest thing over the page — the one the keys are for. */
private val LocalOnTop: ProvidableCompositionLocal<Boolean> = compositionLocalOf { true }

/** Whether [OverPage] draws anything here: inside the storefront, yes. */
@Composable
internal fun isOverPage(): Boolean = LocalOverlays.current != null

/**
 * A sheet over the whole storefront — the phone's menu, the phone's filter sheet, a review's photos (B-86) — as a
 * `Dialog` drew it: no press over it reaches the page under it, and Escape is [onDismiss]. The sheet holds the
 * focus, so any other key it reads is [onKey]'s (the photos' arrows); `true` when it took the key.
 *
 * It holds the focus for as long as it is open (B-144). A mouse press focuses the control pressed, and a control
 * that then stops being one — the photos' «›» on the last photo, a facet of the filter sheet while the load it sent
 * runs — takes the focus with it to nowhere: Compose clears it to the window, past the sheet, and the keys stop
 * reaching it. Whenever nothing inside the sheet is focused, the sheet takes the focus back; unless something opened
 * over it since, whose keys they are until it closes, or a notice over it has the focus — Tab to its «Retry» (B-145).
 */
@Composable
internal fun SheetOverPage(
    onDismiss: () -> Unit,
    onKey: (Key) -> Boolean = { false },
    content: @Composable () -> Unit,
) {
    OverPage {
        val focus = remember { FocusRequester() }
        var held by remember { mutableStateOf(false) }
        val onTop = LocalOnTop.current
        val notice = LocalOverlays.current?.noticeFocused == true
        LaunchedEffect(onTop, held, notice) { if (onTop && !held && !notice) focus.requestFocus() }
        Box(
            Modifier
                .fillMaxSize()
                .coversPage()
                .focusRequester(focus)
                .onPreviewKeyEvent {
                    val escape = it.key == Key.Escape
                    if (escape && it.type == KeyEventType.KeyDown) onDismiss()
                    escape || (it.type == KeyEventType.KeyDown && onKey(it.key))
                }.onFocusChanged { held = it.hasFocus }
                .focusable(),
        ) { content() }
    }
}

/**
 * No press here reaches what is drawn under it: Compose hands a press to the topmost node that reads presses at
 * that place, and this reads them all. It consumes none, so the sheet's own readers — a blank press that takes the
 * focus off a field (B-118) — see the press as if nothing were under it.
 */
private fun Modifier.coversPage(): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent()
        }
    }
