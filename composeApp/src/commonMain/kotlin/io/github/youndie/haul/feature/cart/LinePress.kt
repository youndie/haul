package io.github.youndie.haul.feature.cart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import io.github.youndie.haul.ui.LocalHaulActions
import kotlinx.coroutines.launch

/**
 * The press of a control that carries a line change fixed in the tree — a card's «+» (B-37), «Add to
 * cart» and «Buy now» (B-48): [command] goes to the storefront's cart commands, and the screen's
 * handler follows what comes back — `update` of the header and the control (B-63), `refresh`, or the
 * command's own `next` once the server accepted it. `null`, nothing to press, when the tree gave no command or nobody sends one (a screenshot).
 */
@Composable
internal fun linePress(command: LineCommand?): (() -> Unit)? {
    val cart = LocalCartCommands.current
    val actions = LocalHaulActions.current
    val scope = rememberCoroutineScope()
    if (cart == null || actions == null || command == null) return null
    return {
        scope.launch {
            cart.run(listOf(CartCommand.ChangeLine(command.url, command.change)), command.next)?.let(actions::handle)
        }
    }
}
