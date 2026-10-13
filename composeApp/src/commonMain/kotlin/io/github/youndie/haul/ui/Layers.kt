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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics

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
//
// A sheet or a dialog leaves only itself to assistive technology while it is open (B-151): what is under it — the
// page, and whatever opened before it — has its semantics cleared, so the browser's mirror holds the sheet alone,
// as a modal dialog would. A menu is not modal: the page under it stays. The notices stay over all of it.

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

/**
 * One thing drawn over the page: its content, the composition locals where it was opened from, and whether it is
 * [modal] — a sheet or a dialog, which hides what is under it from assistive technology.
 */
internal class Overlay(
    content: @Composable () -> Unit,
    locals: CompositionLocalContext,
    val modal: Boolean = false,
) {
    var content: @Composable () -> Unit by mutableStateOf(content)
    var locals: CompositionLocalContext by mutableStateOf(locals)
}

/** The host of what is drawn over the page; none outside the storefront (a screenshot), where nothing opens. */
internal val LocalOverlays: ProvidableCompositionLocal<Overlays?> = staticCompositionLocalOf { null }

/**
 * [content], over it whatever [OverPage] opened inside it — the last opened on top — and over that the notices
 * [NoticeOverPage] raised. The box is as big as the window: the storefront's own. Under the newest modal overlay
 * ([SheetOverPage]) the page and the overlays before it are hidden from assistive technology.
 */
@Composable
internal fun OverlayHost(content: @Composable () -> Unit) {
    val overlays = remember { Overlays() }
    // The newest sheet or dialog: everything drawn before it is under it.
    val modal = overlays.shown.indexOfLast { it.modal }
    Box(Modifier.fillMaxSize().onGloballyPositioned { overlays.origin = it.positionInRoot() }) {
        Box(Modifier.fillMaxSize().hiddenUnder(modal >= 0)) {
            CompositionLocalProvider(LocalOverlays provides overlays) { content() }
        }
        overlays.shown.forEachIndexed { index, overlay ->
            key(overlay) {
                CompositionLocalProvider(overlay.locals) {
                    CompositionLocalProvider(LocalOnTop provides (overlay === overlays.shown.last())) {
                        Box(Modifier.hiddenUnder(index < modal)) { overlay.content() }
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
    Raised(overlays.shown, content = content)
}

/**
 * What is drawn here, kept from assistive technology while [hidden]: its semantics cleared, so the browser's mirror
 * leaves it out. Compose 1.12's mirror walks the tree a clearing node empties (`replacedChildren`); a property such
 * as `hideFromAccessibility` it does not read. Nothing drawn or pressed changes.
 */
private fun Modifier.hiddenUnder(hidden: Boolean): Modifier = if (hidden) clearAndSetSemantics { } else this

/**
 * A notice the shopper has to read and may have to answer — «The page didn’t update» with «Retry», a message —
 * drawn over the page and over whatever is open on it: the filter sheet, the phone's menu, a dialog (B-145).
 * Outside an [OverlayHost] it is drawn where it is called.
 */
@Composable
internal fun NoticeOverPage(content: @Composable () -> Unit) {
    val overlays = LocalOverlays.current ?: return content()
    Raised(overlays.notices, content = content)
}

/** [content] in [layer] while this is composed, with the composition locals of the place it is called from. */
@Composable
private fun Raised(
    layer: MutableList<Overlay>,
    modal: Boolean = false,
    content: @Composable () -> Unit,
) {
    val locals = currentCompositionLocalContext
    val overlay = remember { Overlay(content, locals, modal) }
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
 * A sheet over the whole storefront — the phone's menu, the phone's filter sheet, a review's photos (B-86), a dialog
 * a `present` draws (B-151) — as a `Dialog` drew it: no press over it reaches the page under it, the page is hidden
 * from assistive technology while it is open, and Escape is [onDismiss] — once what has the focus inside it has
 * declined the key: a list open in the return dialog closes first. The sheet holds the focus, so any other key it
 * reads is [onKey]'s (the photos' arrows); `true` when it took the key.
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
    val overlays = LocalOverlays.current ?: return
    Raised(overlays.shown, modal = true) {
        val focus = remember { FocusRequester() }
        var held by remember { mutableStateOf(false) }
        val onTop = LocalOnTop.current
        val notice = LocalOverlays.current?.noticeFocused == true
        LaunchedEffect(onTop, held, notice) { if (onTop && !held && !notice) focus.requestFocus() }
        Box(
            Modifier
                .fillMaxSize()
                .testTag(OVER_PAGE_TAG)
                .coversPage()
                .focusRequester(focus)
                .onPreviewKeyEvent { it.type == KeyEventType.KeyDown && it.key != Key.Escape && onKey(it.key) }
                .onKeyEvent {
                    val escape = it.key == Key.Escape
                    if (escape && it.type == KeyEventType.KeyDown) onDismiss()
                    escape
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

/** The sheet's box, and what the browser's mirror holds while one is open (B-151): its id there. */
internal const val OVER_PAGE_TAG: String = "over-page"
