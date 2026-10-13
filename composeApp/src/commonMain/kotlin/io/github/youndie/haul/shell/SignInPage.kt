package io.github.youndie.haul.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.youndie.haul.feature.identity.SessionControls
import io.github.youndie.haul.feature.identity.SignInActions
import io.github.youndie.haul.feature.identity.SignInUnreachable
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.kompot.standard.NavigateAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// B-66: `/sign-in` is the client's own page. A press on «Sign in» never needed it — sign-in's actions
// answer the address before it is followed — but a reload of it, a shared link, back or forward onto it
// and a popup the browser blocked all arrive at it, and it had no tree: «This page isn't here».

/** The tag around the sign-in page, for the tests that tell it from the page it returns to. */
public const val SIGN_IN_PAGE_TAG: String = "sign-in-page"

/** The sign-in page's button: the popup's sign-in. */
public const val SIGN_IN_PAGE_LABEL: String = "Sign in"

/** The way that works when the popup is blocked: the sign-in in this tab. */
public const val SIGN_IN_HERE_LABEL: String = "Sign in on this page"

/** The popup once more, for a shopper who has allowed it. */
public const val TRY_POPUP_AGAIN_LABEL: String = "Try the sign-in window again"

/**
 * Sign-in's actions as the shell answers them, wherever the press comes from — a tree, the shell's own
 * header, the prompt of a customer's page, the sign-in page — so each way in ends the same way (B-66).
 *
 * [signIn] is the popup's sign-in; [session], when there is one, says who is signed in, signs out and
 * signs in in this tab. A popup the browser blocked is [onBlocked]'s, told the sign-in page it leads to,
 * which is then opened: the page says why and offers the sign-in in this tab, returning to the press's
 * `next` — or, when the press named none, the page it was on, and on the sign-in page that page's own `next`.
 * A store that did not answer the sign-in's request ([SignInUnreachable], B-147) is said in [messages], over the
 * page the press was on, as any press with no answer is (B-92): «Couldn’t reach the store», and «Retry», the
 * same press again in [scope]; the page stays as it was, asked for nothing.
 */
internal class Signing(
    val signIn: suspend () -> Unit,
    val session: SessionControls?,
    private val navigator: Navigator,
    private val messages: MessageState,
    private val scope: CoroutineScope,
    private val onBlocked: (signInPage: String) -> Unit,
) {
    /**
     * Sign-in's and sign-out's actions on the page at [address]: [redraw] draws it again for who is
     * looking now, [cancelled] answers a sign-in that did not go through, [open] a `next`. A sign-out
     * leaves a customer's page for the home page — the server would refuse it to the guest the shopper
     * is now — and draws any other page again.
     */
    fun actions(
        address: Address,
        redraw: () -> Unit,
        cancelled: () -> Unit = redraw,
        open: (String) -> Unit = navigator::open,
    ): SignInActions =
        SignInActions(
            signIn = signIn,
            redraw = redraw,
            cancelled = cancelled,
            blocked = { deeplink -> blocked(address, deeplink) },
            unreachable = { again -> unreachable { scope.launch { again() } } },
            signOut = { session?.signOut() },
            signedOut = { if (address.kind.isCustomers) navigator.open("/") else redraw() },
            open = open,
        )

    /** «Couldn’t reach the store» over the page, its «Retry» [again] — the press that got no answer (B-147). */
    fun unreachable(again: () -> Unit) {
        messages.show(noAnswer(again)) { (it as? PressAgain)?.press?.invoke() }
    }

    private fun blocked(
        address: Address,
        deeplink: String,
    ) {
        val page =
            when {
                SignInActions.next(deeplink) != null -> deeplink

                // The sign-in page is where it returns already: a press that names no `next` — the header's
                // «Sign in» — keeps the page's own (B-121).
                address.kind == PageKind.SignIn -> address.value

                else -> SignInActions.returningTo(address.value)
            }
        onBlocked(page)
        navigator.open(page)
    }
}

/** Where the sign-in page is: what it draws, and what its presses do. */
private enum class SignInState { Asking, Blocked, Returning, Failed }

/**
 * The page at `/sign-in` (B-66), drawn by the client under the header it last drew. A customer who
 * arrives at it is sent on to its `next` at once; a sign-in in this tab coming back is finished here
 * («Signing you in»), then sent on the same way; one that left for the provider's page and is back with no
 * answer from it — the browser's back (B-116) — did not go through, and the page says so and offers
 * another. Otherwise it asks: «Sign in» opens the popup, and when the browser blocks it ([blocked], or a
 * press here that is blocked) the page says so and offers the sign-in in this tab — [Signing.session]'s,
 * when there is one — and the popup once more. A press here the store did not answer — either way in, offline —
 * leaves the page as it is under «Couldn’t reach the store» and «Retry» ([Signing.unreachable], B-147). Sent on means the page's entry becomes the `next`
 * ([Navigator.redirect]): back does not return to a sign-in that is done. A `next` that is not a
 * storefront address is the home page.
 *
 * A press, never the page's arrival, opens the popup: a browser blocks a popup no click asked for.
 */
@Composable
internal fun SignInPage(
    address: Address,
    header: HaulHeader,
    signing: Signing,
    navigator: Navigator,
    blocked: Boolean,
) {
    val session = signing.session
    val next = remember(address) { SignInActions.next(address.value) ?: "/" }
    var state by remember(address) {
        mutableStateOf(
            when {
                session?.returnedFromSignIn == true -> SignInState.Returning

                // Back from the provider's page, which never answered: said once, then the page asks again.
                session?.forgetUnansweredSignIn() == true -> SignInState.Failed

                else -> SignInState.Asking
            },
        )
    }
    LaunchedEffect(address) {
        when {
            state == SignInState.Returning -> {
                val finished = finished(session) { kept -> navigator.redirect(kept.storefrontOr(next)) }
                if (!finished) state = SignInState.Failed
            }

            session?.signedIn == true -> {
                navigator.redirect(next)
            }
        }
    }
    val scope = rememberCoroutineScope()
    val actions =
        remember(address, signing) {
            // Signed in: on to `next`; a popup closed: the page stays as it is; blocked: told by the shell.
            signing.actions(address, redraw = { navigator.redirect(next) }, cancelled = {}, open = navigator::redirect)
        }
    val popup = { scope.launch { actions.handle(NavigateAction(address.value)) } }

    fun here() {
        scope.launch {
            when (signedInHere(session, SignInActions.next(address.value))) {
                Leaving.Left -> Unit

                Leaving.Failed -> state = SignInState.Failed

                // The store did not say where to sign in (B-147): said over the page, which stays as it was.
                Leaving.Unreachable -> signing.unreachable(::here)
            }
        }
    }
    val canSignInHere = session?.canSignInHere == true
    Box(Modifier.testTag(SIGN_IN_PAGE_TAG)) {
        // The shell's word that a press elsewhere was blocked, or this page's own press.
        when (if (blocked && state == SignInState.Asking) SignInState.Blocked else state) {
            SignInState.Asking -> {
                NotFoundShell(
                    header = header,
                    eyebrow = "Sign in",
                    title = "Sign in to HAUL",
                    accent = "HAUL",
                    text = "Your orders, your Saved list and checkout. Whatever is in your cart comes with you.",
                    actionLabel = SIGN_IN_PAGE_LABEL,
                    onAction = { popup() },
                )
            }

            SignInState.Blocked -> {
                NotFoundShell(
                    header = header,
                    eyebrow = "Sign in",
                    // Worded without a hyphen when the display face drew none (B-66); it draws one since B-106.
                    title = "The browser blocked the window",
                    accent = "blocked",
                    text =
                        if (canSignInHere) {
                            "The sign-in opens in a window of its own, and this browser did not let it open. " +
                                "Sign in on this page instead: you come back here once you have."
                        } else {
                            "The sign-in opens in a window of its own, and this browser did not let it open. " +
                                "Allow pop-up windows for this site, then try again."
                        },
                    actionLabel = if (canSignInHere) SIGN_IN_HERE_LABEL else TRY_POPUP_AGAIN_LABEL,
                    onAction = { if (canSignInHere) here() else popup() },
                    secondaryLabel = if (canSignInHere) TRY_POPUP_AGAIN_LABEL else null,
                    onSecondary = { popup() },
                )
            }

            SignInState.Returning -> {
                NotFoundShell(
                    header = header,
                    eyebrow = "Sign in",
                    title = "Signing you in",
                    accent = "you in",
                    text = "One moment: you’re back where you were once it’s done.",
                    actionLabel = null,
                )
            }

            SignInState.Failed -> {
                NotFoundShell(
                    header = header,
                    eyebrow = "Sign in",
                    title = "The sign-in didn’t go through",
                    accent = "didn’t",
                    text = "You’re still browsing as a guest. Try again.",
                    actionLabel = if (canSignInHere) SIGN_IN_HERE_LABEL else SIGN_IN_PAGE_LABEL,
                    onAction = { if (canSignInHere) here() else popup() },
                    secondaryLabel = if (canSignInHere) TRY_POPUP_AGAIN_LABEL else null,
                    onSecondary = { popup() },
                )
            }
        }
    }
}

/** This `next`, kept across the provider's pages, when it is still a storefront address; [fallback] otherwise. */
private fun String?.storefrontOr(fallback: String): String =
    this?.let { SignInActions.next(SignInActions.returningTo(it)) } ?: fallback

/** The sign-in in this tab that came back, finished: `true` and on to its next, or `false` when it did not go through. */
@Suppress(
    "ktlint:kapkan:swallowed-failure",
    "A sign-in that did not go through — refused, expired, its answer not the request's, the network's — is drawn as the page that says so and offers another; the shopper is the guest they were.",
)
private suspend fun finished(
    session: SessionControls?,
    next: (String?) -> Unit,
): Boolean =
    try {
        next(session?.finishSignInHere())
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        // Throwable: in the browser a failed fetch is a JavaScript error, which is no `Exception` on Wasm.
        false
    }

/** How a sign-in in this tab left, or why it could not. */
private enum class Leaving {
    /** On its way to the provider's page. */
    Left,

    /** It could not leave: no sign-in in this tab, a server without sign-in, storage refused. */
    Failed,

    /** The store did not answer where to sign in (B-147). */
    Unreachable,
}

/** Leaves for the sign-in in this tab. */
@Suppress(
    "ktlint:kapkan:swallowed-failure",
    "A sign-in that could not leave the page is drawn as the page that says so and offers another.",
)
private suspend fun signedInHere(
    session: SessionControls?,
    next: String?,
): Leaving =
    try {
        session?.signInHere(next) ?: return Leaving.Failed
        Leaving.Left
    } catch (e: CancellationException) {
        throw e
    } catch (_: SignInUnreachable) {
        Leaving.Unreachable
    } catch (_: Throwable) {
        Leaving.Failed
    }
