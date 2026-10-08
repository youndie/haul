package io.github.youndie.haul.feature.identity

import io.github.youndie.haul.registry.haulJson
import kotlinx.browser.localStorage
import kotlinx.browser.sessionStorage

/**
 * The session in web storage. The guest id in `localStorage`, so a returning shopper finds the cart
 * they left; the tokens in `sessionStorage`, so a sign-in lasts as long as the tab and a shared
 * computer does not stay signed in. Storage that refuses (a private window, blocked site data) is a
 * session that lasts the page: the shopper is a new guest on the next load, not an error.
 */
public class BrowserSessionStore : SessionStore {
    override fun load(): Session =
        Session(
            guestId = read { localStorage.getItem(GUEST) },
            tokens =
                read {
                    sessionStorage.getItem(TOKENS)
                }?.let { runCatching { haulJson.decodeFromString(Tokens.serializer(), it) }.getOrNull() },
        )

    override fun save(session: Session) {
        write {
            session.guestId?.let { localStorage.setItem(GUEST, it) } ?: localStorage.removeItem(GUEST)
            session.tokens?.let { sessionStorage.setItem(TOKENS, haulJson.encodeToString(Tokens.serializer(), it)) }
                ?: sessionStorage.removeItem(TOKENS)
        }
    }

    @Suppress(
        "ktlint:kapkan:swallowed-failure",
        "Storage that refuses is a session that lasts the page (see the class).",
    )
    private fun read(block: () -> String?): String? =
        try {
            block()
        } catch (_: Throwable) {
            null
        }

    @Suppress(
        "ktlint:kapkan:swallowed-failure",
        "Storage that refuses is a session that lasts the page (see the class).",
    )
    private fun write(block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
            // Not kept: the next load is a new guest.
        }
    }

    @Suppress(
        "ktlint:kapkan:swallowed-failure",
        "Tokens that do not parse (an older shape) are a signed-out session, which is what they are worth.",
    )
    private fun tokens(text: String): Tokens? =
        try {
            haulJson.decodeFromString(Tokens.serializer(), text)
        } catch (_: IllegalArgumentException) {
            null
        }

    private companion object {
        const val GUEST = "haul.guest"
        const val TOKENS = "haul.tokens"
    }
}
