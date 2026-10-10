package io.github.youndie.haul.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.withLink
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

/** Pressing this follows [action]; no action, or nobody to follow it, leaves the element as it was. */
@Composable
internal fun Modifier.follows(action: KompotAction?): Modifier {
    val handler = LocalHaulActions.current
    return if (action == null || handler == null) this else pressable { handler.handle(action) }
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
 * A control that has nothing to do right now (a card's «+» with nothing to add, B-75) and sits over one that
 * has: the press stops here instead of reaching the one under it.
 */
internal fun Modifier.swallowsPresses(): Modifier = pointerInput(Unit) { detectTapGestures { } }

/**
 * [text] as a link that follows [action] inside a run of text (a crumb); plain text when there is no
 * action or nobody to follow it. The link carries no style of its own, so it reads as the canvas draws it.
 */
internal fun AnnotatedString.Builder.appendLink(
    text: String,
    action: KompotAction?,
    actions: KompotActionHandler?,
) {
    if (action == null || actions == null) {
        append(text)
    } else {
        withLink(LinkAnnotation.Clickable(text) { actions.handle(action) }) { append(text) }
    }
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
}

/** Whoever copies links: the browser's clipboard in the app; `null` — a screenshot — copies nothing. */
public val LocalLinkCopier: ProvidableCompositionLocal<LinkCopier?> = staticCompositionLocalOf { null }
