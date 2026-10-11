package io.github.youndie.haul

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.shell.MessageNotice
import io.github.youndie.haul.shell.noAnswer
import io.github.youndie.kompot.standard.MessageLevel
import io.github.youndie.kompot.standard.ShowMessageAction
import io.github.youndie.viddik.annotations.ViddikScreenshot

// B-92: what a press says when its command got no answer or was refused — B-75's message at the error level, the
// alert mark in place of the check. No artboard draws either, so neither has a parity reference.

/** A press with no answer that may be sent again: «Couldn't reach the store» and «Retry». */
@ViddikScreenshot(name = "Message_NoAnswer", group = "Shell", width = 1440, height = 100)
@Composable
internal fun ShellMessageNoAnswer() =
    Fixture(compact = false) {
        Notice(noAnswer {}, button = true)
    }

/** A refused «Place order» on a phone: the server's sentence, long enough to wrap, and no button. */
@ViddikScreenshot(name = "Message_Refused_Phone", group = "Shell", width = 390, height = 160)
@Composable
internal fun ShellMessageRefusedPhone() =
    Fixture(compact = true) {
        Notice(
            ShowMessageAction(
                "Your order changed since the page was drawn — check it and place it again",
                MessageLevel.ERROR,
            ),
            button = false,
        )
    }

@Composable
private fun Notice(
    message: ShowMessageAction,
    button: Boolean,
) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        MessageNotice(message, onAction = if (button) ({}) else null)
    }
}
