@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.github.youndie.haul.shell

import io.github.youndie.haul.ui.LinkCopier

/**
 * The browser's clipboard as the share button's (B-71): the page's origin and the address the server wrote,
 * written with `navigator.clipboard`. A browser that refuses — no secure context, no permission — says so
 * through `done`, and the button stays as it was.
 */
internal val clipboardLinks: LinkCopier = LinkCopier { path, done -> writeLink(path, done) }

@JsFun(
    """(path, done) => {
        try {
            navigator.clipboard.writeText(window.location.origin + path).then(() => done(true), () => done(false));
        } catch (e) {
            done(false);
        }
    }""",
)
private external fun writeLink(
    path: String,
    done: (Boolean) -> Unit,
)
