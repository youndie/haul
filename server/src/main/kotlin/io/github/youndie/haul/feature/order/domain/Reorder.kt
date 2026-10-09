package io.github.youndie.haul.feature.order.domain

import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.cart.domain.CartCommands
import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.cart.domain.CartRepository
import io.github.youndie.haul.feature.catalog.domain.CatalogRepository

/**
 * «Reorder» (feature-orders, endpoint-orders): an order's SKUs back into its customer's cart, through the
 * cart's own command ([CartCommands.changeLine]) so every rule of the cart holds — at most ten, never above
 * the stock — and each line selected, ready to check out.
 *
 * A press twice is one reorder: a line the cart already holds as many of as the order had is only
 * selected, and the order's quantity is a number the line is set to, not one added to it. A SKU that is
 * gone or out of stock is left out and named in the answer; the rest still go in.
 */
internal class Reorder(
    private val orders: OrderRepository,
    private val carts: CartRepository,
    private val catalog: CatalogRepository,
    private val commands: CartCommands,
) {
    /**
     * Puts [orderId]'s SKUs into [customer]'s cart and returns the ones that could not be. Another
     * customer's order is [OrderError.NotFound], as one that does not exist.
     */
    suspend fun reorder(
        customer: CartOwner.Customer,
        orderId: String,
    ): List<String> {
        val order =
            orders.order(orderId)?.takeIf { it.placed.customerId == customer.id } ?: throw OrderError.NotFound(orderId)
        // One SKU bought on two lines is one line of the cart; `groupBy` keeps the order's order.
        val wanted =
            order.placed.lines
                .groupBy { it.skuId }
                .mapValues { (_, lines) -> lines.sumOf { it.quantity } }
        val stock =
            catalog
                .listedBySkus(wanted.keys, customer.prices)
                .flatMap { it.skus }
                .associate { it.id to it.stock }
        val cart = carts.cart(customer)
        val unavailable = mutableListOf<String>()
        wanted.forEach { (skuId, quantity) ->
            val left = stock[skuId] ?: 0
            if (left <= 0) {
                unavailable += skuId
                return@forEach
            }
            val target = minOf(quantity, left, CartCommands.MAX_QUANTITY)
            val held = cart.line(skuId)?.quantity ?: 0
            val change = if (held >= target) LineChange(selected = true) else LineChange(target, selected = true)
            commands.changeLine(customer, skuId, change)
        }
        return unavailable
    }
}
