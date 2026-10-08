package io.github.youndie.haul.feature.cart.domain

import io.github.youndie.haul.feature.catalog.domain.Listed
import io.github.youndie.haul.feature.catalog.domain.Sku
import java.time.OffsetDateTime

/** A stored line with the SKU as the catalog has it now. */
internal data class PricedLine(
    val stored: StoredLine,
    val item: Listed,
    val sku: Sku,
) {
    val outOfStock: Boolean get() = sku.stock <= 0

    val priceChanged: Boolean get() = sku.priceCents != stored.seenPriceCents

    /**
     * Changed since the shopper last saw it: a different price, or out of stock when it was in stock
     * (feature-cart). A changed line is marked and left out of the selection until acknowledged.
     */
    val changed: Boolean get() = priceChanged || (stored.seenInStock && outOfStock)

    /** In the selection that totals and checkout count: ticked, in stock, unchanged. */
    val counted: Boolean get() = stored.selected && !changed && !outOfStock

    /** What the line costs before discounts: the old price where there is one. */
    val listCents: Int get() = (sku.oldPriceCents?.takeIf { it > sku.priceCents } ?: sku.priceCents) * stored.quantity

    val priceCents: Int get() = sku.priceCents * stored.quantity
}

/**
 * The summary of feature-cart's rules, over the counted lines: Items = Σ old price (or price) × qty;
 * Discount = Σ (old − price) × qty + promo; Delivery per research D7; Total = Items − Discount + Delivery.
 * [deliveryWaivedCents] is the fee Plus took off — what a non-member would have paid — which an order
 * keeps for «saved on delivery this year» (B-23).
 */
internal data class Totals(
    val itemsCents: Int,
    val productDiscountCents: Int,
    val promoCents: Int,
    val deliveryCents: Int,
    val counted: Int,
    val deliveryWaivedCents: Int = 0,
) {
    val discountCents: Int get() = productDiscountCents + promoCents

    val totalCents: Int get() = itemsCents - discountCents + deliveryCents

    /** «You'll earn N points»: whole dollars of the total, twice that for Plus (research D6–D7). */
    fun points(plus: Boolean): Int = totalCents / CENTS_PER_DOLLAR * if (plus) 2 else 1

    companion object {
        /** Delivery is free from $35 of items at their price, or for Plus; otherwise $5.99 (research D7). */
        const val FREE_DELIVERY_FROM_CENTS = 3_500
        const val DELIVERY_CENTS = 599
        private const val CENTS_PER_DOLLAR = 100

        fun of(
            lines: List<PricedLine>,
            promo: PromoCode?,
            plus: Boolean,
            now: OffsetDateTime,
        ): Totals {
            val counted = lines.filter { it.counted }
            val items = counted.sumOf { it.listCents }
            val atPrice = counted.sumOf { it.priceCents }
            val promoCents = promo?.takeIf { it.activeAt(now) }?.discountOn(atPrice) ?: 0
            val fee = if (counted.isEmpty() || atPrice >= FREE_DELIVERY_FROM_CENTS) 0 else DELIVERY_CENTS
            val delivery = if (plus) 0 else fee
            return Totals(
                itemsCents = items,
                productDiscountCents = items - atPrice,
                promoCents = promoCents,
                deliveryCents = delivery,
                counted = counted.size,
                deliveryWaivedCents = fee - delivery,
            )
        }
    }
}

internal fun PromoCode.activeAt(now: OffsetDateTime): Boolean = !now.isBefore(startsAt) && now.isBefore(endsAt)

/** [PromoCode.percentOff] of [cents], rounded down to a cent, and no more than the cap. */
internal fun PromoCode.discountOn(cents: Int): Int {
    val off = cents.toLong() * percentOff / PERCENT
    return capCents?.let { minOf(off, it.toLong()) }?.toInt() ?: off.toInt()
}

private const val PERCENT = 100
