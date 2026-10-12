package io.github.youndie.haul.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.youndie.haul.ui.HaulHeader

/**
 * The header the shell draws over a page of its own ([LocalShellHeader], B-67, B-105): the last one the server
 * drew — a tree's, or the header alone ([ask]) — for as long as it was drawn for the shopper looking now, a
 * guest or a customer. A sign-in or a sign-out on a page with no tree leaves the one drawn for the other; until
 * the server draws one for them, it is the client's own: [SHELL_HEADER] for a guest, [CUSTOMER_SHELL_HEADER] for
 * a customer. Who is looking is the session's (`SessionControls.signedIn`), read when a header is drawn and when
 * one is asked for, and followed as it changes (`SessionControls.signedInChanges`, B-130): a sign-in the provider
 * would not renew leaves the customer's header as a sign-out does.
 */
internal class ShellHeaders {
    private var last by mutableStateOf<Drawn?>(null)

    /** The server drew [header] for a customer when [signedIn], for a guest otherwise. */
    fun drawn(
        header: HaulHeader,
        signedIn: Boolean,
    ) {
        last = Drawn(header, signedIn)
    }

    /** Whether the server has drawn a header for the shopper ([signedIn] or not) looking now. */
    fun knows(signedIn: Boolean): Boolean = last?.signedIn == signedIn

    /** The header for the shopper looking now: the server's last for them, or the client's own. */
    fun of(signedIn: Boolean): HaulHeader =
        last?.takeIf { it.signedIn == signedIn }?.header ?: if (signedIn) CUSTOMER_SHELL_HEADER else SHELL_HEADER

    private class Drawn(
        val header: HaulHeader,
        val signedIn: Boolean,
    )
}

/**
 * Asks the server for the header alone (`GET /ui/header`, B-105) when a page came with no tree — one that is
 * not there, a refusal, an error, the sign-in prompt — and no header has been drawn for who is looking: a
 * customer who reloads a missing order sees her name, her cart's count and category words that lead to their
 * categories, rather than a guest's «Sign in». Once per [visit] and [load]; an answer that does not arrive
 * leaves the client's own header, and the next visit asks again. The sign-in page asks for nothing (B-66): it
 * is a way through, and a customer who lands on it is sent on at once.
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

/** The address of the header alone (the server's `Frame.HEADER`). */
internal const val HEADER_TREE: String = "/ui/header"

/** The header alone, or `null` when it did not arrive: the shell keeps the one it has. */
internal suspend fun HaulTransport.header(): HaulHeader? =
    try {
        tree(HEADER_TREE) as? HaulHeader
    } catch (failed: ScreenFailed) {
        null
    }
