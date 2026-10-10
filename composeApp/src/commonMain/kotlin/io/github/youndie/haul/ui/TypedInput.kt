package io.github.youndie.haul.ui

import androidx.compose.runtime.withFrameNanos

/**
 * Waits until what was typed before now has reached the text fields, for a press that reads one.
 *
 * In the browser Compose collects the page's input events and applies them to the focused field at the
 * next animation frame (`NativeInputEventsProcessor`, through `requestAnimationFrame`), while a press is
 * handled the moment it arrives: a press within that frame of the last keystroke read the field without
 * it — on the stand «Apply» sent nothing for a code typed just before (B-76). The first frame may be one
 * requested before the keystrokes, which runs ahead of the input; the second is requested after it, so by
 * then the input has been applied. Elsewhere the input is applied at once and this costs two frames.
 */
internal suspend fun awaitTypedInput() {
    repeat(2) { withFrameNanos { } }
}
