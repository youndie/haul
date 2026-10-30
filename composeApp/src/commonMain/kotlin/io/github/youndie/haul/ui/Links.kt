package io.github.youndie.haul.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntSize
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotActionHandler

// A link is a kompot action the server put on a component (`ProductCard.action`, `Crumb.action`, …);
// the client follows it and builds no URL. The views draw deep inside one renderer — a card inside a
// grid inside the results — so the renderer's handler reaches them through [LocalHaulActions] rather
// than through every view's signature.

/**
 * Whoever follows the actions of the components being drawn: the registry provides the handler kompot
 * gave the renderer (`haulRegistry`). `null` — a screenshot, a view drawn on its own — draws the same
 * pixels with nothing to press.
 */
public val LocalHaulActions: ProvidableCompositionLocal<KompotActionHandler?> = staticCompositionLocalOf { null }

/**
 * The scroll of the page being drawn: the storefront's, one per screen (B-62). `null` — a screenshot, a view drawn
 * on its own — has no page to move. A page number moves it to the top (B-108).
 */
public val LocalPageScroll: ProvidableCompositionLocal<ScrollState?> = staticCompositionLocalOf { null }

/** Pressing this follows [action]; no action, or nobody to follow it, leaves the element as it was. */
@Composable
internal fun Modifier.follows(action: KompotAction?): Modifier {
    val handler = LocalHaulActions.current
    return if (action == null || handler == null) this else pressable { handler.handle(action) }.offersLink(action)
}

/**
 * The product page's tab row, asked into view by a press that leads to a tab (B-121). The row is brought into view
 * when another tab arrives (B-71), but «2,341 reviews» pressed on the reviews tab opens the address shown: the same
 * tab arrives, and the row stayed out of sight. Each [ask] brings it into view, whichever tab arrives. One per
 * screen, beside [LocalPageScroll].
 */
@Stable
public class TabsInView {
    /** How many presses have asked; the row answers each new one. */
    public var asked: Int by mutableIntStateOf(0)
        private set

    public fun ask() {
        asked += 1
    }
}

/** The tab row's [TabsInView] on the page being drawn; `null` — a screenshot, a view drawn on its own — moves nothing. */
public val LocalTabsInView: ProvidableCompositionLocal<TabsInView?> = staticCompositionLocalOf { null }

/** [follows], asking for the tab row [action] leads to as it does (B-121). */
@Composable
internal fun Modifier.followsToTabs(action: KompotAction?): Modifier {
    val handler = LocalHaulActions.current
    val tabs = LocalTabsInView.current
    return if (action == null || handler == null) {
        this
    } else {
        pressable {
            tabs?.ask()
            handler.handle(action)
        }.offersLink(action)
    }
}

/** Pressing this runs [onPress], the client's own (Retry, the logo); `null` is not pressable. */
internal fun Modifier.pressable(onPress: (() -> Unit)?): Modifier =
    if (onPress == null) {
        this
    } else {
        // No indication: the canvas draws no pressed or hovered state, and a ripple would be one.
        pointerHoverIcon(PointerIcon.Hand)
            .pressesAgain(onPress)
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onPress)
    }

/**
 * A press of the pointer's alone: the hand over it and [onPress] on a tap, and nothing in semantics — no button for
 * assistive technology, which reaches the same thing another way (a dialog's scrim: its «×» and Escape, B-151). A
 * press on a control inside it is the control's.
 */
internal fun Modifier.pointerPress(onPress: (() -> Unit)?): Modifier =
    if (onPress == null) {
        this
    } else {
        pointerHoverIcon(PointerIcon.Hand).pointerInput(onPress) { detectTapGestures { onPress() } }
    }

/**
 * A control pressed again on the same spot soon after is pressed again (B-154), as it was before the page's words
 * could be selected. The words in a control count presses too, and to them such a press is a double press: it
 * selected the control's word and took the release — «Write a review» pressed again just after its dialog closed
 * opened nothing, a «+» pressed twice added one. Such a press is read here before anything in the control, by the
 * words' own measure (a double press's time, the slop around the last press), taken, and pressed on its release
 * inside the control. Every control under it reads it the same way, and only the innermost presses: a «+» on a card.
 */
internal fun Modifier.pressesAgain(onPress: () -> Unit): Modifier = this then PressesAgainElement(onPress)

/**
 * [pressesAgain]'s element: the node keeps its reader across presses — the last press is its memory — and takes the
 * newest [onPress] on every update. A reader keyed on the press would have kept an old one: a card's «+» answered
 * with a new line is pressed through a function reference equal to the old, and pressed again it sent the old line.
 */
private class PressesAgainElement(
    val onPress: () -> Unit,
) : ModifierNodeElement<PressesAgainNode>() {
    override fun create(): PressesAgainNode = PressesAgainNode(onPress)

    override fun update(node: PressesAgainNode) {
        node.onPress = onPress
    }

    override fun equals(other: Any?): Boolean = other is PressesAgainElement && other.onPress === onPress

    override fun hashCode(): Int = onPress.hashCode()
}

private class PressesAgainNode(
    var onPress: () -> Unit,
) : DelegatingNode(),
    PointerInputModifierNode {
    private val me = Any()
    private var last: PointerInputChange? = null

    private val reader =
        delegate(
            SuspendingPointerInputModifierNode {
                awaitEachGesture {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val down =
                        event.changes.singleOrNull()?.takeIf {
                            it.changedToDownIgnoreConsumed() &&
                                event.buttons.isPrimaryPressed
                        }
                            ?: return@awaitEachGesture
                    val before = last
                    last = down
                    val again =
                        before != null &&
                            down.uptimeMillis - before.uptimeMillis < viewConfiguration.doubleTapTimeoutMillis &&
                            (down.position - before.position).getDistance() < viewConfiguration.touchSlop
                    if (!again) return@awaitEachGesture
                    // The outer controls read the press first; the innermost, last, is the one pressed.
                    pressedAgain = me
                    down.consume()
                    while (true) {
                        val change =
                            awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                                ?: break
                        change.consume()
                        if (!change.pressed) {
                            val inside =
                                change.position.x in 0f..size.width.toFloat() &&
                                    change.position.y in 0f..size.height.toFloat()
                            if (inside && pressedAgain === me) onPress()
                            break
                        }
                    }
                }
            },
        )

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) = reader.onPointerEvent(pointerEvent, pass, bounds)

    override fun onCancelPointerInput() = reader.onCancelPointerInput()
}

/** The innermost control of the press [pressesAgain] took last. */
private var pressedAgain: Any? = null

/**
 * A control that has nothing to do right now and is drawn greyed for it (B-100): not pressable, and disabled for
 * assistive technology, as the look says.
 */
internal fun Modifier.inert(): Modifier = semantics { disabled() }

/**
 * A control that has nothing to do right now (a card's «+» with nothing to add, B-75) and sits over one that
 * has: the press stops here instead of reaching the one under it.
 */
internal fun Modifier.swallowsPresses(): Modifier = pointerInput(Unit) { detectTapGestures { } }

/**
 * The name a reader of the page hears for a press that shows no words of its own — an icon, a photo, a box to tick
 * (B-126). The browser's accessible mirror of the page names a press by this, else by the text in it; with
 * neither it reads as «button».
 */
internal fun Modifier.named(name: String): Modifier = semantics { contentDescription = name }

/**
 * A box to tick or a switch — its [kind], [Role.Checkbox] or [Role.Switch] — named for what it does and whether it
 * is on: «Select Sony WH-1000XM6, ticked», «Use 2,480 points (−$24.80), off», with [note] after it (B-151). Its
 * [state] is in semantics; its role there is a button's ([MIRRORED_ROLE]). [onPress] turns it; without one it is
 * drawn and does nothing.
 */
internal fun Modifier.toggles(
    what: String,
    state: ToggleableState,
    kind: Role,
    onPress: (() -> Unit)?,
    note: String? = null,
): Modifier {
    val said = listOfNotNull("$what, ${stateWord(state, kind)}", note?.takeIf { it.isNotBlank() }).joinToString(". ")
    val press =
        if (onPress == null) {
            Modifier.semantics {
                role = MIRRORED_ROLE
                toggleableState = state
            }
        } else {
            Modifier.pointerHoverIcon(PointerIcon.Hand).pressesAgain(onPress).triStateToggleable(
                state,
                interactionSource = null,
                indication = null,
                role = MIRRORED_ROLE,
                onClick = onPress,
            )
        }
    return then(press).semantics { contentDescription = said }
}

/**
 * The role a box or a switch has in semantics: a button's, while the browser's mirror writes a role and no state.
 * Compose 1.12's mirror makes [Role.Checkbox] a `role="checkbox"` with no `aria-checked`, which Chromium reads as not
 * checked — a ticked box would say «ticked» in its name and «not checked» in its state. 1.13's mirror writes
 * `aria-checked` (read in 1.13.0-alpha02's library); once the stack is on it, the role is the box's [Role.Checkbox] or
 * the switch's [Role.Switch], and this goes.
 */
private val MIRRORED_ROLE = Role.Button

/** How a box or a switch says its state in its name: «ticked», «partly ticked», «not ticked»; «on», «off». */
internal fun stateWord(
    state: ToggleableState,
    kind: Role,
): String =
    when {
        kind == Role.Switch -> if (state == ToggleableState.On) "on" else "off"
        state == ToggleableState.On -> "ticked"
        state == ToggleableState.Indeterminate -> "partly ticked"
        else -> "not ticked"
    }

/** Text with runs that are pressed on their own — a crumb, a word of the category row ([linkedText]). */
internal class LinkedText(
    val text: AnnotatedString,
    val links: List<TextLink>,
)

/** The run of [LinkedText.text] from [start] to [end], named [label], that runs [onPress]. */
internal class TextLink(
    val start: Int,
    val end: Int,
    val label: String,
    val onPress: () -> Unit,
)

/**
 * Builds text with links in it. Not `LinkAnnotation`: Compose draws each of those as a press of its own with no
 * name, which the browser's accessible mirror reads as an unnamed button (B-126); a [TextLink] is pressed the
 * same way and named by its words ([LinkedBasicText]).
 */
internal fun linkedText(build: LinkedTextBuilder.() -> Unit): LinkedText = LinkedTextBuilder().apply(build).build()

/** What [linkedText] builds with: [text] for what is drawn, [link] for a run that follows an action. */
internal class LinkedTextBuilder {
    val text: AnnotatedString.Builder = AnnotatedString.Builder()
    private val links = mutableListOf<TextLink>()

    /** [label] as a link that follows [action]; plain text when there is no action or nobody to follow it. */
    fun link(
        label: String,
        action: KompotAction?,
        actions: KompotActionHandler?,
    ) {
        val start = text.length
        text.append(label)
        if (action != null && actions != null) links += TextLink(start, text.length, label) { actions.handle(action) }
    }

    fun build(): LinkedText = LinkedText(text.toAnnotatedString(), links.toList())
}

/** What pressing a button that carries [action] does: follow it, or nothing when there is nobody to. */
@Composable
internal fun following(action: KompotAction?): (() -> Unit)? {
    val handler = LocalHaulActions.current
    return if (action == null || handler == null) null else ({ handler.handle(action) })
}

/**
 * Puts a link to an address of this storefront on the clipboard (B-71): the page's own origin and [copy]'s
 * `path`, as the server wrote the address (`ProductDetails.share`); `done` says whether it went. The origin
 * is the browser's, which the server does not know.
 */
public fun interface LinkCopier {
    public fun copy(
        path: String,
        done: (Boolean) -> Unit,
    )

    /**
     * The whole link to [path], which the share button shows to copy by hand when [copy] says it could not
     * (B-104): the browser's origin and the path; the path alone where there is no origin to know.
     */
    public fun address(path: String): String = path

    /** Opens the link to [path] in a tab of its own, as a browser's «Open in new tab» does; nowhere to open, nothing. */
    public fun openApart(path: String) {}

    /** Puts [text] — words selected on the page (B-154) — on the clipboard as it is; `done` says whether it went. */
    public fun copyText(
        text: String,
        done: (Boolean) -> Unit,
    ) {
        done(false)
    }
}

/** Whoever copies links: the browser's clipboard in the app; `null` — a screenshot — copies nothing. */
public val LocalLinkCopier: ProvidableCompositionLocal<LinkCopier?> = staticCompositionLocalOf { null }
