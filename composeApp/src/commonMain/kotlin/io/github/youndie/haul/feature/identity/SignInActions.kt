package io.github.youndie.haul.feature.identity

import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.standard.NavigateAction
import kotlinx.coroutines.CancellationException

/**
 * The seam between a tree's actions and sign-in, for whatever follows the actions: the server sends a
 * guest to `/sign-in` — the header's «Sign in», the cart's «Sign in to check out» — and the client
 * answers it by running [signIn] (the provider's popup, then the cart merge) and drawing the screen
 * again with [redraw], signed in or not. [handle] says whether the action was sign-in's, so the
 * navigation that owns every other `navigate` can hand it the rest.
 */
public class SignInActions(
    private val signIn: suspend () -> Unit,
    private val redraw: () -> Unit,
) {
    public suspend fun handle(action: KompotAction): Boolean {
        if (action !is NavigateAction || action.deeplink.substringBefore('?') != SIGN_IN) return false
        attempt()
        redraw()
        return true
    }

    @Suppress(
        "ktlint:kapkan:swallowed-failure",
        "A closed popup is the shopper's choice and a server without sign-in is the deployment's; either way the shopper stays the guest they were, which the redrawn header shows.",
    )
    private suspend fun attempt() {
        try {
            signIn()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The shopper is the guest they were (see the suppression above).
        }
    }

    public companion object {
        /** The server's word for «sign in here» (`Frame.SIGN_IN` on the server). */
        public const val SIGN_IN: String = "/sign-in"
    }
}
