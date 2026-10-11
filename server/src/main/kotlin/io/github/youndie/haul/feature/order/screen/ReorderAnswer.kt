package io.github.youndie.haul.feature.order.screen

import io.github.youndie.haul.feature.order.domain.Reordered
import io.github.youndie.haul.feature.order.domain.Shortfall
import io.github.youndie.haul.shell.Frame
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.standard.MessageLevel
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.SequenceAction
import io.github.youndie.kompot.standard.ShowMessageAction

/**
 * What «Reorder» answers (B-101): `navigate` to the cart when every SKU went in whole, as before; when some fell
 * short, the cart and, over it, a message naming them — «Added what was in stock — Stoneware Mug isn't available
 * now.»; when none went in, only the message — «Nothing was added: …» — and the shopper stays on the order rather
 * than landing on a cart that has none of it. B-75's message, at the `error` level for the alert mark: something
 * pressed for did not happen.
 */
internal object ReorderAnswer {
    fun of(reordered: Reordered): KompotAction {
        val toCart = NavigateAction(Frame.CART)
        if (reordered.short.isEmpty()) return toCart
        val said = said(reordered.short)
        if (reordered.added == 0) return ShowMessageAction("Nothing was added: $said.", MessageLevel.ERROR)
        return SequenceAction(listOf(toCart, ShowMessageAction("Added what was in stock — $said.", MessageLevel.ERROR)))
    }

    /** «A and B aren't available now; C: only 1 of 2 went in». */
    private fun said(short: List<Shortfall>): String {
        val gone = short.filter { it.added == 0 }.map { it.title }
        val fewer = short.filter { it.added > 0 }
        return listOfNotNull(
            gone.takeIf { it.isNotEmpty() }?.let { names ->
                names.joined() + if (names.size == 1) " isn't available now" else " aren't available now"
            },
        ).plus(fewer.map { "${it.title}: only ${it.added} of ${it.wanted} went in" })
            .joinToString("; ")
    }

    /** «A», «A and B», «A, B and C». */
    private fun List<String>.joined(): String =
        if (size == 1) single() else dropLast(1).joinToString(", ") + " and " + last()
}
