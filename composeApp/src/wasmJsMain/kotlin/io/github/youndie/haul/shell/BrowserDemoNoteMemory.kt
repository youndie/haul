package io.github.youndie.haul.shell

import kotlinx.browser.sessionStorage

/**
 * Whether the demo strip was closed, in `sessionStorage` (B-160): a reload or another page of the tab keeps it
 * closed, a new tab shows it again. Storage that refuses — a private window, blocked site data — remembers the close
 * for the page alone: the strip comes back on the next load, which is all it costs.
 */
public class BrowserDemoNoteMemory : DemoNoteMemory {
    private var closedHere = false

    override val closed: Boolean
        get() = closedHere || read() == CLOSED

    override fun close() {
        closedHere = true
        write()
    }

    @Suppress(
        "ktlint:kapkan:swallowed-failure",
        "Storage that refuses is a strip that comes back on the next load (see the class).",
    )
    private fun read(): String? =
        try {
            sessionStorage.getItem(KEY)
        } catch (_: Throwable) {
            null
        }

    @Suppress(
        "ktlint:kapkan:swallowed-failure",
        "Storage that refuses is a strip that comes back on the next load (see the class).",
    )
    private fun write() {
        try {
            sessionStorage.setItem(KEY, CLOSED)
        } catch (_: Throwable) {
            // Not kept: the strip is drawn again on the next load.
        }
    }

    private companion object {
        const val KEY = "haul.demo-note"
        const val CLOSED = "closed"
    }
}
