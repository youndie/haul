package io.github.youndie.haul.shell

import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.cart.domain.CartRepository
import io.github.youndie.haul.feature.identity.Caller
import io.github.youndie.haul.feature.identity.Callers
import io.github.youndie.haul.feature.saved.domain.SavedRepository
import io.ktor.server.application.ApplicationCall

/**
 * The [Viewer] of a request: a customer's first name, cart and Saved list, a guest's cart, or nobody's. Every
 * screen's header is drawn from this, so the cart's count is the same on the home page as on the cart
 * (feature-identity: the header's states).
 */
internal class Viewers(
    private val callers: Callers,
    private val carts: CartRepository,
    private val saved: SavedRepository,
) {
    suspend fun of(call: ApplicationCall): Viewer = of(callers.of(call))

    suspend fun of(caller: Caller): Viewer =
        when (caller) {
            Caller.Nobody -> {
                Viewer()
            }

            is Caller.Guest -> {
                withCart(Viewer(), CartOwner.Guest(caller.id))
            }

            is Caller.Customer -> {
                val customer = caller.customer
                withCart(
                    Viewer(
                        firstName = customer.firstName,
                        customerId = customer.id,
                        saved = saved.productIds(customer.id),
                    ),
                    CartOwner.Customer(customer.id, customer.plus),
                )
            }
        }

    /** The header's count, and how many of each SKU the cart holds — what a card's «+» sends one more of. */
    private suspend fun withCart(
        viewer: Viewer,
        owner: CartOwner,
    ): Viewer {
        val lines = carts.cart(owner).lines
        return viewer.copy(
            cartCount = lines.sumOf { it.quantity },
            inCart = lines.associate { it.skuId to it.quantity },
        )
    }
}
