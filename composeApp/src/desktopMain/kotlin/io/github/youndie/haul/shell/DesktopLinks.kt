package io.github.youndie.haul.shell

import io.github.youndie.haul.ui.LinkCopier
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.IOException
import java.net.URI

/**
 * The desktop's clipboard and browser as the store's links use them (B-176): a link is [origin] and the server's path,
 * copied to the system clipboard; «Open in new tab» and a link off the store open in the system's browser. A clipboard
 * or a browser the system refuses — a headless session, no browser registered — answers `false` or `null`, and the
 * share button offers the link to copy by hand, as in a browser that refuses (B-104).
 */
internal class DesktopLinks(
    private val origin: String,
) : LinkCopier {
    override fun copy(
        path: String,
        done: (Boolean) -> Unit,
    ) = done(put(address(path)))

    override fun address(path: String): String = origin.trimEnd('/') + path

    override fun openApart(path: String) = browse(address(path))

    override fun openUrl(url: String) = browse(url)

    override fun copyText(
        text: String,
        done: (Boolean) -> Unit,
    ) = done(put(text))

    override fun pasteText(done: (String?) -> Unit) = done(clipboardText())
}

@Suppress(
    "ktlint:kapkan:swallowed-failure",
    "A clipboard the system refuses is answered `false`, which the share button says to the shopper.",
)
private fun put(text: String): Boolean =
    try {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        true
    } catch (_: IllegalStateException) {
        false
    } catch (_: UnsupportedOperationException) {
        // also a headless session's `HeadlessException`
        false
    }

@Suppress(
    "ktlint:kapkan:swallowed-failure",
    "A clipboard with no text, or one the system refuses, has nothing to paste; the field stays as it was.",
)
private fun clipboardText(): String? =
    try {
        Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
    } catch (_: IllegalStateException) {
        null
    } catch (_: IOException) {
        null
    } catch (_: UnsupportedFlavorException) {
        null
    } catch (_: UnsupportedOperationException) {
        // also a headless session's `HeadlessException`
        null
    }

@Suppress(
    "ktlint:kapkan:swallowed-failure",
    "A system with no browser to open the link in leaves the store where it was; the link is still on its menu to copy.",
)
private fun browse(url: String) {
    val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else return
    if (!desktop.isSupported(Desktop.Action.BROWSE)) return
    try {
        desktop.browse(URI(url))
    } catch (_: IOException) {
    } catch (_: IllegalArgumentException) {
    } catch (_: UnsupportedOperationException) {
    }
}
