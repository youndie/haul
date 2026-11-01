package io.github.youndie.haul.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.youndie.haul.ui.HaulHeader
import kotlinx.coroutines.launch

/**
 * The header the shell draws over a page of its own ([LocalShellHeader], B-67, B-105): the last one the server
 * drew — a tree's, or the header alone ([ask]) — for as long as it was drawn for the shopper looking now, a
 * guest or a customer. A sign-in or a sign-out on a page with no tree leaves the one drawn for the other; until
 * the server draws one for them, it is the client's own: [SHELL_HEADER] for a guest, [CUSTOMER_SHELL_HEADER] for
 * a customer. Who is looking is the session's (`SessionControls.signedIn`), read when a header is drawn and when
 * one is asked for, and followed as it changes (`SessionControls.signedInChanges`, B-130): a sign-in the provider
 * would not renew leaves the customer's header as a sign-out does. What the shopper is changes too without a
 * sign-in or a sign-out — a Plus trial started makes a member — and the server says so with a `refresh` of the
 * page, which a page with no tree follows by asking for its header again ([redrawn], B-146).
 *
 * The header kept is the one the page drew last, an `update` included (B-170): «Add to cart» answers the header's
 * count in place, and the next page's placeholder draws that count, not the one the tree arrived with.
 */
internal class ShellHeaders {
    private var last by mutableStateOf<Drawn?>(null)

    /** The server drew [header] for a customer when [signedIn], for a guest otherwise. */
    fun drawn(
        header: HaulHeader,
        signedIn: Boolean,
    ) {
        // Drawn again with nothing changed — a recomposition of the header — is not news: the page under it would
        // only be drawn again for it.
        if (last?.let { it.current && it.header == header && it.signedIn == signedIn } == true) return
        last = Drawn(header, signedIn)
    }

    /**
     * The page is to be drawn again (a `refresh`, B-146): what the server drew may no longer be so — a trial that
     * made the shopper a member — so the header kept is no longer known and a page with no tree asks for it anew.
     * It stays drawn until the answer, rather than giving way to the client's own for a moment; an answer that
     * does not arrive leaves it, and the next visit asks again.
     */
    fun redrawn() {
        last = last?.let { Drawn(it.header, it.signedIn, current = false) }
    }

    /**
     * Whether the header for the shopper ([signedIn] or not) looking now is one the server drew — current or kept
     * through a `refresh` ([redrawn]) — rather than the client's own: a page's placeholder draws it as it is, who is
     * looking and the cart's count included, and only the client's own as a placeholder (B-170).
     */
    fun serverDrew(signedIn: Boolean): Boolean = last?.signedIn == signedIn

    /** Whether the server has drawn a header for the shopper ([signedIn] or not) looking now, and it is current. */
    fun knows(signedIn: Boolean): Boolean = last?.let { it.current && it.signedIn == signedIn } == true

    /** The header for the shopper looking now: the server's last for them, or the client's own. */
    fun of(signedIn: Boolean): HaulHeader =
        last?.takeIf { it.signedIn == signedIn }?.header ?: if (signedIn) CUSTOMER_SHELL_HEADER else SHELL_HEADER

    private class Drawn(
        val header: HaulHeader,
        val signedIn: Boolean,
        val current: Boolean = true,
    )
}

/**
 * Asks the server for the header alone (`GET /ui/header`, B-105) when a page came with no tree — one that is
 * not there, a refusal, an error, the sign-in prompt — and no header has been drawn for who is looking: a
 * customer who reloads a missing order sees her name, her cart's count and category words that lead to their
 * categories, rather than a guest's «Sign in». Once per [visit] and [load]; an answer that does not arrive
 * leaves the client's own header, and the next visit asks again. The sign-in page asks for nothing while it loads
 * (B-66): it is a way through, and a customer who lands on it is sent on at once; a press there that needs the
 * server's categories asks instead ([askForHeaderOnPress], B-138).
 */
@Composable
internal fun AskForHeader(
    headers: ShellHeaders,
    transport: HaulTransport,
    signedIn: Boolean,
    visit: Int,
    load: Int = 0,
) {
    if (headers.knows(signedIn)) return
    LaunchedEffect(visit, load, signedIn) { transport.header()?.let { headers.drawn(it, signedIn) } }
}

/**
 * The header alone asked for by a press rather than by the page (B-138): the sign-in page landed on cold, whose
 * phone menu would list the client's ten category words, each to the catalog's root, and whose search picker has
 * no scopes. The first press asks (`GET /ui/header`, as [AskForHeader] does) and the answer is drawn for who is
 * looking; any press after it asks nothing, answered or not — once per [visit], and an answer that does not arrive
 * leaves the client's own header until the next. `null` once a header has been drawn for who is looking: there
 * is nothing to ask.
 */
@Composable
internal fun askForHeaderOnPress(
    headers: ShellHeaders,
    transport: HaulTransport,
    signedIn: Boolean,
    visit: Int,
): (() -> Unit)? {
    val scope = rememberCoroutineScope()
    var asked by remember(visit, signedIn) { mutableStateOf(false) }
    if (headers.knows(signedIn)) return null
    return {
        if (!asked) {
            asked = true
            scope.launch { transport.header()?.let { headers.drawn(it, signedIn) } }
        }
    }
}

/** The address of the header alone (the server's `Frame.HEADER`). */
internal const val HEADER_TREE: String = "/ui/header"

/** The header alone, or `null` when it did not arrive: the shell keeps the one it has. */
internal suspend fun HaulTransport.header(): HaulHeader? =
    try {
        tree(HEADER_TREE) as? HaulHeader
    } catch (failed: ScreenFailed) {
        null
    }
