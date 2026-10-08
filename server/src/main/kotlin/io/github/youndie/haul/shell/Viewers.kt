package io.github.youndie.haul.shell

import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.cart.domain.CartRepository
import io.github.youndie.haul.feature.identity.Caller
import io.github.youndie.haul.feature.identity.Callers
import io.ktor.server.application.ApplicationCall

/**
 * The [Viewer] of a request: a customer's first name and cart, a guest's cart, or nobody's. Every
 * screen's header is drawn from this, so the cart's count is the same on the home page as on the cart
 * (feature-identity: the header's states).
 */
internal class Viewers(
    private val callers: Callers,
    private val carts: CartRepository,
) {
    suspend fun of(call: ApplicationCall): Viewer = of(callers.of(call))

    suspend fun of(caller: Caller): Viewer =
        when (caller) {
            Caller.Nobody -> {
                Viewer()
            }

            is Caller.Guest -> {
                Viewer(cartCount = carts.units(CartOwner.Guest(caller.id)))
            }

            is Caller.Customer -> {
                val customer = caller.customer
                Viewer(
                    firstName = customer.firstName,
                    cartCount = carts.units(CartOwner.Customer(customer.id, customer.plus)),
                    customerId = customer.id,
                )
            }
        }
}
