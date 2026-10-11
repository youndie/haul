@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.github.youndie.haul.shell

import io.github.youndie.haul.ui.LinkCopier

/**
 * The browser's clipboard as the share button's (B-71): the page's origin and the address the server wrote,
 * written with `navigator.clipboard`. Where that is refused or missing — no secure context, no permission — the
 * link goes through a selected text area and `document.execCommand('copy')`, which older and insecure pages
 * still allow (B-104). A browser that refuses both says so through `done`, and the button offers the link
 * ([address]) to copy by hand.
 */
internal val clipboardLinks: LinkCopier =
    object : LinkCopier {
        override fun copy(
            path: String,
            done: (Boolean) -> Unit,
        ) = writeLink(path, done)

        override fun address(path: String): String = origin() + path
    }

@JsFun("() => window.location.origin")
private external fun origin(): String

@JsFun(
    """(path, done) => {
        const link = window.location.origin + path;
        const byHand = () => {
            let area = null;
            try {
                area = document.createElement('textarea');
                area.value = link;
                area.setAttribute('readonly', '');
                area.style.position = 'fixed';
                area.style.opacity = '0';
                document.body.appendChild(area);
                area.select();
                return document.execCommand('copy');
            } catch (e) {
                return false;
            } finally {
                if (area) area.remove();
            }
        };
        try {
            navigator.clipboard.writeText(link).then(() => done(true), () => done(byHand()));
        } catch (e) {
            done(byHand());
        }
    }""",
)
private external fun writeLink(
    path: String,
    done: (Boolean) -> Unit,
)
