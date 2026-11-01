package io.github.youndie.haul.shell

import io.github.youndie.haul.ui.CheckoutHeader
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.ColumnComponent

/**
 * Where the page's title goes (B-161): the browser tab's, `document.title`, in the browser; a test's record, or
 * nothing, elsewhere — the screenshots have no tab.
 */
public fun interface BrowserTitle {
    public fun show(title: String)
}

/**
 * The tab's title of a page the shell draws itself with no tree: an error, a page that is not there, a sign-in
 * prompt. It names no page, so it says only whose store it is.
 */
internal const val STORE_TITLE: String = "Haul"

/** The tab's title of the sign-in page, which the client draws itself (B-66): no tree names it. */
internal const val SIGN_IN_TITLE: String = "Sign in · Haul"

/**
 * The tab's title as the page drawn names it (B-161), handed to [title] only when it changes. A page's tree names
 * it on its header ([tabTitle]), the server's copy; a page the shell draws itself with no tree is [STORE_TITLE], the
 * sign-in page [SIGN_IN_TITLE]. A placeholder ([placeholder]) names nothing and leaves the title as it is, unless a
 * page has named it before: a new page's placeholder is not the page the tab still names. The first placeholder —
 * a cold load — keeps `index.html`'s own `<title>`, which already says what the store is, so the tab does not read
 * «Haul» for the moment before the tree names the page.
 */
internal class TabTitle(
    private val title: BrowserTitle?,
) {
    private var shown: String? = null

    /** The page drawn is named [name]. */
    fun named(name: String) {
        if (name == shown) return
        shown = name
        title?.show(name)
    }

    /** A placeholder is drawn while a page is on its way. */
    fun placeholder() {
        if (shown != null) named(STORE_TITLE)
    }
}

/**
 * The tab's title [tree] names: its header's, the storefront's or checkout's, among the root column's children. A
 * tree that names none — a fixture, an older server — is [STORE_TITLE].
 */
internal fun KompotComponent.tabTitle(): String =
    (this as? ColumnComponent)
        ?.children
        ?.firstNotNullOfOrNull {
            when (it) {
                is HaulHeader -> it.tabTitle
                is CheckoutHeader -> it.tabTitle
                else -> null
            }
        } ?: STORE_TITLE
