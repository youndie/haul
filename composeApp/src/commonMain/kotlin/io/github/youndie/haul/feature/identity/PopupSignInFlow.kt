package io.github.youndie.haul.feature.identity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The window a browser sign-in runs in, as the storefront watches it (B-46).
 *
 * [closed] alone does not say the shopper gave up: the return page (`signed-in.html`) hands the
 * provider's answer back and closes itself while the code is still being exchanged for tokens.
 * [answered] is what tells the two closes apart.
 */
public interface SignInPopup {
    /** The window is gone — the shopper closed it, or the return page did once it had answered. */
    public val closed: Boolean

    /** The return page has handed the provider's answer back to the storefront. */
    public val answered: Boolean

    /** Brings the window to the front: what a second press does while this sign-in is under way. */
    public fun focus()

    /** Closes the window, if it is still open, once its sign-in has ended one way or another. */
    public fun close()
}

/**
 * A [SignInFlow] whose sign-in runs in a popup the storefront opens and watches itself, around the
 * provider's flow in [delegate], which is told to use that window (B-46).
 *
 * kotlin-multiplatform-oidc 0.18.4's popup flow waits for the return page's message and nothing else:
 * a popup the shopper closes leaves it waiting for good, and it does not give in to cancellation
 * either. So the provider's flow runs detached from the press, and the press waits for whichever
 * comes first — the tokens, or the popup closed without an answer, which throws
 * [SignInPopupClosed] within a second. A popup [open] could not get ([open] answers `null`: the
 * browser blocked it) throws [SignInPopupBlocked] before the provider is asked at all. Either is a
 * sign-in that did not go through, which [SignInActions] answers as `cancelled`.
 *
 * One sign-in at a time: a [signIn] while one is pending brings its popup to the front and throws
 * [SignInPending], so a second press opens no second window — two would race for one stored
 * request and one return page.
 */
public class PopupSignInFlow(
    private val open: () -> SignInPopup?,
    private val delegate: SignInFlow,
    private val pollEvery: Duration = POLL,
) : SignInFlow {
    private var pending: SignInPopup? = null

    override suspend fun signIn(settings: SignInSettings): Tokens {
        pending?.let {
            it.focus()
            throw SignInPending()
        }
        val popup = open() ?: throw SignInPopupBlocked()
        pending = popup
        // Detached on purpose: the provider's wait ignores cancellation, and a child would hold the
        // press open for as long as it waits — for good, after a close.
        val attempt = CoroutineScope(currentCoroutineContext() + Job()).async { delegate.signIn(settings) }
        try {
            return settled(popup, attempt)
        } finally {
            // A window left open after its press is gone — the page left mid-sign-in — would hand its
            // answer to a flow nobody waits for, and the next press would reuse it.
            popup.close()
            attempt.cancel()
            pending = null
        }
    }

    override suspend fun refresh(
        settings: SignInSettings,
        refreshToken: String,
    ): Tokens = delegate.refresh(settings, refreshToken)

    /**
     * The tokens, or [SignInPopupClosed] once the popup has been seen closed without an answer twice
     * in a row: the return page posts its answer and closes in one breath, and a poll that falls
     * between the two must not read that close as the shopper's.
     */
    private suspend fun settled(
        popup: SignInPopup,
        attempt: Deferred<Tokens>,
    ): Tokens {
        var closedBefore = false
        while (true) {
            withTimeoutOrNull(pollEvery) { attempt.await() }?.let { return it }
            val abandoned = popup.closed && !popup.answered
            if (abandoned && closedBefore) throw SignInPopupClosed()
            closedBefore = abandoned
        }
    }

    public companion object {
        /** How often the popup is looked at: a close is settled within two of these. */
        public val POLL: Duration = 250.milliseconds
    }
}

/** The shopper closed the sign-in popup before the provider answered. */
public class SignInPopupClosed : Exception("the sign-in popup was closed before the provider answered")

/** The browser refused to open the sign-in popup. */
public class SignInPopupBlocked : Exception("the browser blocked the sign-in popup")

/** A sign-in was asked for while another is under way; its popup was brought to the front instead. */
public class SignInPending : Exception("a sign-in is already under way")
