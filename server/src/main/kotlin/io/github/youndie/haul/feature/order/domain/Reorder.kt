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
 * gone or out of stock is left out, one with less stock than the order had goes in at what is left, and
 * both are named in the answer ([Reordered.short], B-101); the rest still go in.
 */
internal class Reorder(
    private val orders: OrderRepository,
    private val carts: CartRepository,
    private val catalog: CatalogRepository,
    private val commands: CartCommands,
) {
    /**
     * Puts [orderId]'s SKUs into [customer]'s cart and says what went in and what fell short. Another
     * customer's order is [OrderError.NotFound], as one that does not exist.
     */
    suspend fun reorder(
        customer: CartOwner.Customer,
        orderId: String,
    ): Reordered {
        val order =
            orders.order(orderId)?.takeIf { it.placed.customerId == customer.id } ?: throw OrderError.NotFound(orderId)
        // One SKU bought on two lines is one line of the cart; `groupBy` keeps the order's order.
        val bought = order.placed.lines.groupBy { it.skuId }
        val wanted = bought.mapValues { (_, lines) -> lines.sumOf { it.quantity } }
        val stock =
            catalog
                .listedBySkus(wanted.keys, customer.prices)
                .flatMap { it.skus }
                .associate { it.id to it.stock }
        val cart = carts.cart(customer)
        var added = 0
        val short = mutableListOf<Shortfall>()
        wanted.forEach { (skuId, quantity) ->
            val title = bought.getValue(skuId).first().title
            val left = stock[skuId] ?: 0
            if (left <= 0) {
                short += Shortfall(skuId, title, quantity, 0)
                return@forEach
            }
            val target = minOf(quantity, left, CartCommands.MAX_QUANTITY)
            if (target < quantity) short += Shortfall(skuId, title, quantity, target)
            val held = cart.line(skuId)?.quantity ?: 0
            val change = if (held >= target) LineChange(selected = true) else LineChange(target, selected = true)
            commands.changeLine(customer, skuId, change)
            added += 1
        }
        return Reordered(added, short)
    }
}

/**
 * What a reorder did: how many of the order's SKUs are in the cart now ([added]), and each SKU that went in
 * with fewer units than the order had or not at all ([short]) — what the answer names (B-101).
 */
internal data class Reordered(
    val added: Int,
    val short: List<Shortfall>,
)

/** A SKU of the order the cart could not take whole: [wanted] units bought, [added] put in — 0 when none were left. */
internal data class Shortfall(
    val skuId: String,
    val title: String,
    val wanted: Int,
    val added: Int,
)
