package io.github.youndie.haul.feature.identity

/**
 * A sign-in in the storefront's own tab (B-66): what is left when the browser blocks the popup. The tab
 * leaves for the provider's page and comes back to the storefront's `/sign-in`, which finishes it — the
 * same authorization code flow with PKCE as the popup's, returning to the same `signed-in.html`.
 * The browser's keeps the request in the tab's session storage across the two page loads
 * (`BrowserSignInHere`); a test's answers what it is told to.
 */
public interface SignInHere {
    /** Whether this page load is the return of a sign-in [leave] started, with the provider's answer. */
    public val returned: Boolean

    /**
     * Whether a sign-in [leave] started is still waiting for the provider's answer on this page load — the
     * shopper went back from the provider's page, which never answered (B-116) — and forgets it, so the
     * page that asked says once that it did not go through. `false` for one that [returned].
     */
    public fun forgetUnanswered(): Boolean

    /**
     * Leaves this page for the provider's; [next] is kept for when it comes back. Returns only by throwing
     * [SignInNotAnswered], when a browser that kept this page shows it again on the shopper's back from
     * the provider's page with no answer from it.
     */
    public suspend fun leave(
        settings: SignInSettings,
        next: String?,
    )

    /**
     * The tokens of the sign-in that [returned], and the next it kept; throws when it did not go through
     * — the provider refused, or the answer is not the request's. Either way the request is used up.
     */
    public suspend fun finish(settings: SignInSettings): SignedInHere
}

/** The provider's page in this tab was left with no answer from it: the shopper went back (B-116). */
public class SignInNotAnswered : Exception("the provider's page was left without an answer")

/** A sign-in in this tab that went through: its [tokens] and the address it was asked to return to. */
public class SignedInHere(
    public val tokens: Tokens,
    public val next: String?,
)

/**
 * What the storefront asks of who the shopper is besides the popup's sign-in (B-66): whether they are
 * signed in, signing out, and the sign-in in this tab for a browser that blocks the popup.
 */
public interface SessionControls {
    public val signedIn: Boolean

    /** Forgets the customer; the shopper is the guest they were. */
    public fun signOut()

    /** Whether there is a sign-in in this tab to offer when the popup is blocked. */
    public val canSignInHere: Boolean

    /** Leaves for the provider's page in this tab; the storefront's `/sign-in` finishes it and opens [next]. */
    public suspend fun signInHere(next: String?)

    /** Whether this page load is a sign-in in this tab coming back, for `/sign-in` to finish. */
    public val returnedFromSignIn: Boolean

    /**
     * Whether a sign-in in this tab left for the provider's page and this page load is the shopper's back
     * from it, unanswered (B-116); forgets it, so `/sign-in` says once that it did not go through.
     */
    public fun forgetUnansweredSignIn(): Boolean

    /** Finishes the sign-in that came back — the tokens kept, the guest cart merged — and answers its next. */
    public suspend fun finishSignInHere(): String?
}
