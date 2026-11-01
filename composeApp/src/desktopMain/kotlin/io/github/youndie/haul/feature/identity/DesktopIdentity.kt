package io.github.youndie.haul.feature.identity

import java.util.prefs.Preferences

/**
 * Where the desktop app keeps who it is (B-176), as a browser does (research D5, B-12): the guest id outlives the
 * window — the user's preferences, one per [origin], so a local stand and the public one each keep their own guest
 * and its cart — and a customer's tokens last the window, in memory, as a tab's `sessionStorage` does.
 */
internal class PreferencesSessionStore(
    private val origin: String,
    private val node: Preferences = Preferences.userRoot().node(NODE),
) : SessionStore {
    private var tokens: Tokens? = null

    override fun load(): Session = Session(guestId = node.get(key(), null), tokens = tokens)

    override fun save(session: Session) {
        tokens = session.tokens
        val guest = session.guestId
        if (guest == null) node.remove(key()) else node.put(key(), guest)
    }

    // A preference's key is at most 80 characters; an origin longer than that is cut, which only shares a guest
    // between two such servers, and the second one answers `guest_not_found` and is given its own (B-115).
    private fun key(): String = (GUEST + origin.trimEnd('/')).take(Preferences.MAX_KEY_LENGTH)

    private companion object {
        const val NODE = "io/github/youndie/haul"
        const val GUEST = "guest "
    }
}

/**
 * The desktop app's sign-in through the provider (B-176): none yet. The system browser and a loopback listener —
 * kotlin-multiplatform-oidc's JVM flow — need the stand's realm to accept a loopback return address, which it does
 * not (the item's question). Until then a press on «Sign in» ends as on a server with sign-in off: the shopper stays
 * the guest they were, cart and all. «Try as a demo shopper» signs in without it, where the server offers one.
 */
internal object NoDesktopSignIn : SignInFlow {
    override suspend fun signIn(settings: suspend () -> SignInSettings): Tokens = throw SignInUnavailable()

    override suspend fun refresh(
        settings: SignInSettings,
        refreshToken: String,
    ): Tokens = throw SignInUnavailable()
}
