package io.github.youndie.haul.feature.identity

import io.github.youndie.haul.StorefrontPage
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.standard.NavigateAction
import io.ktor.http.URLDecodeException
import io.ktor.http.encodeURLParameter
import io.ktor.http.parseQueryString
import kotlinx.coroutines.CancellationException

/**
 * The seam between a tree's actions and sign-in, for whatever follows the actions: the server sends a
 * guest to `/sign-in` — the header's «Sign in», the cart's «Sign in to check out» — and the client
 * answers it by running [signIn] (the provider's popup, then the cart merge). A sign-in that went
 * through [open]s the address the action asked to come back to ([next], B-41) when it carries one the
 * storefront has a page for; otherwise — no `next` or a refused one — the screen is drawn again with
 * [redraw]. A sign-in that did not go through — a popup closed, a server without sign-in, a request
 * that failed in the browser (B-47) — is [cancelled]'s, which draws the screen again too unless the
 * caller has somewhere else to be: a page that asked for the sign-in itself (B-44) has nothing to draw
 * for a guest, and goes home. A press while a sign-in is already under way ([SignInPending], B-46) does
 * none of these: that sign-in's own press answers when it ends. [handle] says whether the action was
 * sign-in's, so the navigation that owns every other `navigate` can hand it the rest.
 */
public class SignInActions(
    private val signIn: suspend () -> Unit,
    private val redraw: () -> Unit,
    private val cancelled: () -> Unit = redraw,
    private val open: (address: String) -> Unit,
) {
    public suspend fun handle(action: KompotAction): Boolean {
        if (action !is NavigateAction || action.deeplink.substringBefore('?') != SIGN_IN) return false
        when (attempt()) {
            Outcome.SignedIn -> next(action.deeplink).let { next -> if (next == null) redraw() else open(next) }
            Outcome.NotSignedIn -> cancelled()
            Outcome.AlreadyUnderWay -> Unit // the press that started it answers when it ends
        }
        return true
    }

    private enum class Outcome { SignedIn, NotSignedIn, AlreadyUnderWay }

    /** How the shopper came back. */
    @Suppress(
        "ktlint:kapkan:swallowed-failure",
        "A closed or blocked popup is the shopper's or the browser's choice, a server without sign-in is the deployment's and a failed request is the network's; either way the shopper stays the guest they were, which the redrawn header shows.",
    )
    private suspend fun attempt(): Outcome =
        try {
            signIn()
            Outcome.SignedIn
        } catch (e: CancellationException) {
            throw e
        } catch (_: SignInPending) {
            Outcome.AlreadyUnderWay
        } catch (_: Throwable) {
            // Throwable, not Exception: in the browser a fetch that fails during the token exchange is a
            // JavaScript error, which is no `Exception` on Wasm — caught narrower, it escaped the press
            // and the shopper was left on the prompt with nothing happening (B-47). The shopper is the
            // guest they were (see the suppression above).
            Outcome.NotSignedIn
        }

    public companion object {
        /** The server's word for «sign in here» (`Frame.SIGN_IN` on the server). */
        public const val SIGN_IN: String = "/sign-in"

        /** The sign-in that returns to [address] once it has gone through — what the server writes as `next`. */
        public fun returningTo(address: String): String = "$SIGN_IN?next=" + address.encodeURLParameter()

        /**
         * Where a sign-in asked by [deeplink] returns: its `next`, decoded, when that is a storefront
         * address — a path [StorefrontPage] has a page for, its query kept — and `null` otherwise.
         *
         * The parameter travels in an address anybody can write, so it is an allow-list, not a
         * blacklist: an absolute URL, a scheme (`javascript:`), a protocol-relative `//host` or `/\host`,
         * a path the storefront has no page for and `/sign-in` itself are all refused, and the shopper
         * stays where they were. «Anything starting with `/`» is not the rule: `//evil.example` starts
         * with one and names another site, and a path with no page behind it would greet a customer who
         * has just signed in with a 404.
         */
        internal fun next(deeplink: String): String? {
            val query = deeplink.substringAfter('?', "")
            if (query.isEmpty()) return null
            val next = decoded(query)?.get("next") ?: return null
            val page = StorefrontPage.of(next.substringBefore('?'))
            return next.takeIf { page != null && page != StorefrontPage.SignIn }
        }

        @Suppress(
            "ktlint:kapkan:swallowed-failure",
            "A query that does not decode names no address to return to; the shopper stays where they were.",
        )
        private fun decoded(query: String) =
            try {
                parseQueryString(query)
            } catch (_: URLDecodeException) {
                null
            }
    }
}
