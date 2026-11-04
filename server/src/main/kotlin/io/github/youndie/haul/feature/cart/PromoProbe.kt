package io.github.youndie.haul.feature.cart

import io.github.youndie.haul.feature.cart.domain.CartRepository

/** Striff negative control: the same probe as the positive control, placed where research D3a puts it. */
internal class PromoProbe(private val carts: CartRepository) {
    override fun toString(): String = "PromoProbe($carts)"
}
