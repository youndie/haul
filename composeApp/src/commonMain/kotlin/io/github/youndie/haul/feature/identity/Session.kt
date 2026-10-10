package io.github.youndie.haul.feature.identity

import kotlinx.serialization.Serializable

/**
 * Who the storefront is, as the browser keeps it (feature-identity): the guest id the server issued,
 * and a signed-in customer's [tokens]. A guest id stays after sign-in — its cart was merged and is
 * empty — so signing out is a guest again rather than a new one.
 */
public data class Session(
    val guestId: String? = null,
    val tokens: Tokens? = null,
) {
    val signedIn: Boolean get() = tokens != null
}

/** What the provider's token endpoint answered: the access token sent as a bearer, and the one that renews it. */
@Serializable
public data class Tokens(
    val accessToken: String,
    val refreshToken: String? = null,
)

/** Where a [Session] outlives a reload. The browser's is web storage; a test's is memory. */
public interface SessionStore {
    public fun load(): Session

    public fun save(session: Session)
}

public class MemorySessionStore(
    private var session: Session = Session(),
) : SessionStore {
    override fun load(): Session = session

    override fun save(session: Session) {
        this.session = session
    }
}

/**
 * The provider's half of signing in: the authorization code flow with PKCE against the realm the
 * settings name, and the refresh of a lapsed access token. The browser's is a popup through
 * kotlin-multiplatform-oidc (`OidcSignInFlow`); a test's answers what it is told to.
 */
public interface SignInFlow {
    /**
     * Signs in against the realm [settings] names. They are asked for, not given: a flow that opens a
     * window opens it first, inside the press that asked for it, and only then waits for the server's
     * answer (B-66) — a browser blocks a popup opened after a wait it did not see a click for.
     */
    public suspend fun signIn(settings: suspend () -> SignInSettings): Tokens

    public suspend fun refresh(
        settings: SignInSettings,
        refreshToken: String,
    ): Tokens
}
