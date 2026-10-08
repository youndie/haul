package io.github.youndie.haul.feature.order

import androidx.compose.runtime.Composable
import io.github.youndie.haul.shell.NotFoundShell
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.following

/**
 * What the client draws for `404 order_not_found` (Order_NotFound) — an order that is not there, or not
 * the shopper's, which the server answers alike: the answer carries no tree, so the copy is the client's
 * and the header is the one it last drew. «Go to your orders» follows that header's «Orders»
 * (`HaulHeader.orders`), the server's address for them.
 */
@Composable
public fun OrderNotFound(header: HaulHeader) {
    NotFoundShell(
        header = header,
        eyebrow = "Order",
        title = "Order not found",
        accent = "found",
        text = "Check the order number, or find it in your orders.",
        actionLabel = "Go to your orders",
        onAction = following(header.orders) ?: {},
    )
}
