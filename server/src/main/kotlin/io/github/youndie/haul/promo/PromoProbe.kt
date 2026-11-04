package io.github.youndie.haul.promo

import io.github.youndie.haul.feature.cart.domain.CartRepository

/** Striff positive control: one feature's code at the root, outside `feature/<name>/` (research D3a). */
internal class PromoProbe(private val carts: CartRepository) {
    override fun toString(): String = "PromoProbe($carts)"
}
