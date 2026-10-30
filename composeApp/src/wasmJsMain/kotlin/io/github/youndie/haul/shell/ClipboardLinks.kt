@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.github.youndie.haul.shell

import io.github.youndie.haul.ui.LinkCopier

/**
 * The browser's clipboard as the share button's (B-71): the page's origin and the address the server wrote,
 * written with `navigator.clipboard`. Where that is refused or missing — no secure context, no permission — the
 * link goes through a selected text area and `document.execCommand('copy')`, which older and insecure pages
 * still allow (B-104). A browser that refuses both says so through `done`, and the button offers the link
 * ([address]) to copy by hand. The same two ways copy the words selected on a page (B-154); a field's «Paste» reads
 * with `navigator.clipboard.readText`, which the browser may ask the shopper to allow (B-155).
 */
internal val clipboardLinks: LinkCopier =
    object : LinkCopier {
        override fun copy(
            path: String,
            done: (Boolean) -> Unit,
        ) = writeText(origin() + path, done)

        override fun address(path: String): String = origin() + path

        override fun openApart(path: String) = openTab(origin() + path)

        override fun copyText(
            text: String,
            done: (Boolean) -> Unit,
        ) = writeText(text, done)

        override fun pasteText(done: (String?) -> Unit) = readText(done)
    }

@JsFun(
    """(done) => {
        try {
            navigator.clipboard.readText().then((text) => done(text), () => done(null));
        } catch (e) {
            done(null);
        }
    }""",
)
private external fun readText(done: (String?) -> Unit)

@JsFun("(link) => { window.open(link, '_blank', 'noopener') }")
private external fun openTab(link: String)

@JsFun("() => window.location.origin")
private external fun origin(): String

@JsFun(
    """(text, done) => {
        const byHand = () => {
            let area = null;
            try {
                area = document.createElement('textarea');
                area.value = text;
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
            navigator.clipboard.writeText(text).then(() => done(true), () => done(byHand()));
        } catch (e) {
            done(byHand());
        }
    }""",
)
private external fun writeText(
    text: String,
    done: (Boolean) -> Unit,
)
