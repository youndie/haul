package io.github.youndie.haul.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
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
    return if (action == null || handler == null) this else pressable { handler.handle(action) }
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
        }
    }
}

/** Pressing this runs [onPress], the client's own (Retry, the logo); `null` is not pressable. */
internal fun Modifier.pressable(onPress: (() -> Unit)?): Modifier =
    if (onPress == null) {
        this
    } else {
        // No indication: the canvas draws no pressed or hovered state, and a ripple would be one.
        pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onPress)
    }

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
}

/** Whoever copies links: the browser's clipboard in the app; `null` — a screenshot — copies nothing. */
public val LocalLinkCopier: ProvidableCompositionLocal<LinkCopier?> = staticCompositionLocalOf { null }
