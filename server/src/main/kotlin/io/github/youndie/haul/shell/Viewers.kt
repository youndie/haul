package io.github.youndie.haul.shell

import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.cart.domain.CartRepository
import io.github.youndie.haul.feature.checkout.domain.CheckoutRepository
import io.github.youndie.haul.feature.identity.Caller
import io.github.youndie.haul.feature.identity.Callers
import io.github.youndie.haul.feature.saved.domain.SavedRepository
import io.ktor.server.application.ApplicationCall

/**
 * The [Viewer] of a request: a customer's first name, cart, Saved list and place, a guest's cart, or nobody's; a guest
 * and nobody are offered a demo shopper when [demo] is on (B-162). Every
 * screen's header is drawn from this, so the cart's count is the same on the home page as on the cart
 * (feature-identity: the header's states).
 */
internal class Viewers(
    private val callers: Callers,
    private val carts: CartRepository,
    private val saved: SavedRepository,
    private val checkouts: CheckoutRepository,
    private val demo: Boolean = false,
) {
    suspend fun of(call: ApplicationCall): Viewer = of(callers.of(call))

    suspend fun of(caller: Caller): Viewer =
        when (caller) {
            Caller.Nobody -> {
                Viewer(demo = demo)
            }

            is Caller.Guest -> {
                withCart(Viewer(demo = demo), CartOwner.Guest(caller.id))
            }

            is Caller.Customer -> {
                val customer = caller.customer
                withCart(
                    Viewer(
                        firstName = customer.firstName,
                        customerId = customer.id,
                        saved = saved.productIds(customer.id),
                        customer = customer,
                        place = place(customer.id),
                    ),
                    CartOwner.Customer(customer.id, customer.plus),
                )
            }
        }

    /** The city and ZIP of the address a courier delivers to (B-98); `null` before the customer has one. */
    private suspend fun place(customerId: String): String? =
        checkouts.checkout(customerId).deliveredTo(checkouts.addresses(customerId))?.place

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
