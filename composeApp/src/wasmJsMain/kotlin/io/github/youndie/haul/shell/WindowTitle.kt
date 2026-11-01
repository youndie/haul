package io.github.youndie.haul.shell

import kotlinx.browser.document

/** The tab's title is the document's (B-161): what the browser shows on the tab, in its history and its bookmarks. */
internal object WindowTitle : BrowserTitle {
    override fun show(title: String) {
        document.title = title
    }
}
