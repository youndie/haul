package io.github.youndie.haul.feature.identity

import kotlinx.browser.window
import org.w3c.dom.MessageEvent
import org.w3c.dom.Window

/**
 * The sign-in popup in the browser (B-46). It is opened blank under [SIGN_IN_WINDOW] before the
 * provider's flow starts, and the flow — told the same name ([OidcSignInFlow]) — navigates this window
 * instead of opening one of its own; that is what lets the storefront watch a window the library
 * keeps to itself.
 *
 * [answered] is the return page's message (`signed-in.html` posts the address to this origin and
 * closes), heard by a listener of our own beside the library's.
 */
internal class BrowserSignInPopup private constructor(
    private val popup: Window,
) : SignInPopup {
    private var heard = false

    init {
        // Left registered: it only ever marks this window's answer, and a page holds a handful at most.
        window.addEventListener("message", { event ->
            if (event is MessageEvent && event.origin == window.location.origin && event.source == popup) heard = true
        })
    }

    override val closed: Boolean get() = popup.closed

    override val answered: Boolean get() = heard

    override fun focus() {
        popup.focus()
    }

    override fun close() {
        if (!popup.closed) popup.close()
    }

    companion object {
        /** A blank popup under [SIGN_IN_WINDOW], or `null` when the browser blocked it. */
        fun open(): SignInPopup? = window.open("about:blank", SIGN_IN_WINDOW, FEATURES)?.let(::BrowserSignInPopup)

        /** kotlin-multiplatform-oidc's own window features, so the popup looks as it did before B-46. */
        private const val FEATURES = "width=1000,height=800,resizable=yes,scrollbars=yes"
    }
}

/** The sign-in popup's window name, shared by the storefront and the provider's flow. */
internal const val SIGN_IN_WINDOW: String = "haul-sign-in"
