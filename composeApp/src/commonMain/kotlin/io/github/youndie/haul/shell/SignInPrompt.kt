package io.github.youndie.haul.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.youndie.haul.feature.identity.SignInActions
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.kompot.standard.NavigateAction
import kotlinx.coroutines.launch

// B-44: a customer's page — the checkout, the account, an order — answers a guest, or a customer whose
// sign-in has lapsed past renewing, with `401`. That is not an error the shopper can Retry away; the
// page is asked of them again once they have signed in.

/** The tag around the page that asks the shopper to sign in, for the tests that tell it from an error. */
public const val SIGN_IN_PROMPT_TAG: String = "sign-in-prompt"

/** The prompt's button, the one press that starts the sign-in. */
public const val SIGN_IN_PROMPT_LABEL: String = "Sign in to continue"

/**
 * Whether a screen's tree was refused because nobody is signed in: the server's `401`, which reaches
 * the shell only after `Identity.send` has tried to renew the customer's token and could not.
 */
internal val Throwable.asksForSignIn: Boolean
    get() = this is ScreenFailed.Refused && status == UNAUTHENTICATED

/**
 * The page at [address] for a shopper it was refused to (`401`): why, and «Sign in to continue» —
 * the sign-in a tree's `/sign-in?next=` starts (B-41), through [SignInActions] like every other, with
 * `next` set to this page. Once it has gone through, [onSignedIn] loads the page again; a sign-in that
 * did not go through — the popup closed, a server without sign-in — leaves for the home page, where a
 * guest has something to see.
 *
 * A press, not the page's arrival, starts it: the sign-in is a popup (B-12), and a browser blocks a
 * popup no click asked for — the sign-in would end before the shopper saw it, and send them home.
 */
@Composable
internal fun SignInPrompt(
    address: Address,
    header: HaulHeader,
    signIn: suspend () -> Unit,
    navigator: Navigator,
    onSignedIn: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val actions =
        remember(address) {
            SignInActions(signIn = signIn, redraw = onSignedIn, cancelled = { navigator.open("/") }) { next ->
                if (next == address.value) onSignedIn() else navigator.open(next)
            }
        }
    Box(Modifier.testTag(SIGN_IN_PROMPT_TAG)) {
        NotFoundShell(
            header = header,
            eyebrow = "Sign in",
            title = address.kind.signInTitle,
            accent = "sign-in",
            text = "Sign in and you’re back here. Whatever is in your cart comes with you.",
            actionLabel = SIGN_IN_PROMPT_LABEL,
            onAction = {
                scope.launch { actions.handle(NavigateAction(SignInActions.returningTo(address.value))) }
            },
        )
    }
}

/** What the prompt says needs the sign-in: the page, by what it is. */
private val PageKind.signInTitle: String
    get() =
        when (this) {
            PageKind.Checkout -> "Checkout needs a sign-in"
            PageKind.Order -> "This order needs a sign-in"
            else -> "This page needs a sign-in"
        }

private const val UNAUTHENTICATED = 401
