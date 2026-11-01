@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.github.youndie.haul.shell

import io.github.youndie.haul.ui.KeyboardFocus

/**
 * A [KeyboardFocus] the document tells (B-167), as a browser decides `:focus-visible`: every key that goes down and
 * every pointer press, heard in the capture phase before Compose reads them. Compose's own nodes hear a key only while
 * something has the focus; a Tab pressed after a press that followed a link — whose page took the focused control
 * away — reached none of them, and the control it focused could not tell it came from the keyboard.
 */
internal fun browserKeyboardFocus(): KeyboardFocus {
    val focus = KeyboardFocus()
    // Left registered: one per page, for as long as the page is open.
    listenToInput(
        key = { shortcut, alone -> if (!alone) focus.keyPressed(shortcut) },
        pointer = { focus.pointerPressed() },
    )
    return focus
}

@JsFun(
    "(key, pointer) => {" +
        " window.addEventListener('keydown', (e) => key(e.metaKey || e.ctrlKey || e.altKey," +
        " ['Shift', 'Control', 'Alt', 'Meta'].includes(e.key)), true);" +
        " window.addEventListener('pointerdown', () => pointer(), true);" +
        " }",
)
private external fun listenToInput(
    key: (Boolean, Boolean) -> Unit,
    pointer: () -> Unit,
)
