package io.github.youndie.haul.feature.cart

import kotlinx.serialization.Serializable

/**
 * The cart's routes (endpoint-cart). Every one is in the public tier: the caller is a guest, named by
 * the `X-Haul-Guest` header (`GuestRoutes.HEADER`), until sign-in (B-12). Every command answers a
 * kompot action — `refresh`, which redraws the cart and its header count — or an `ErrorBody`.
 */
public object CartRoutes {
    /** `GET`: the Cart tree. */
    public const val SCREEN: String = "/ui/cart"

    /** `DELETE` with [LinesRemoval]: removes the lines named. */
    public const val LINES: String = "/api/v1/cart/lines"

    /** `PUT` with [LineChange]: adds the SKU, or changes its line's quantity or selection. */
    public const val LINE: String = "$LINES/{skuId}"

    /** `POST`, no body: accepts what changed on the line since it was added. */
    public const val ACKNOWLEDGE: String = "$LINES/{skuId}/acknowledge"

    /** `PUT` with [PromoEntry] applies a code; `DELETE` removes it. */
    public const val PROMO: String = "/api/v1/cart/promo"

    public fun line(skuId: String): String = "$LINES/$skuId"

    public fun acknowledge(skuId: String): String = "$LINES/$skuId/acknowledge"
}

/**
 * A change to one line. A SKU not yet in the cart is added with [quantity] (1 when absent), selected
 * unless [selected] says otherwise; a line already there changes only the fields given. At least one
 * field is required.
 */
@Serializable
public data class LineChange(
    val quantity: Int? = null,
    val selected: Boolean? = null,
)

/** «Delete selected» and «Remove»: the SKUs whose lines go. */
@Serializable
public data class LinesRemoval(
    val skuIds: List<String>,
)

/** A promo code as typed; the server trims it and ignores its case. */
@Serializable
public data class PromoEntry(
    val code: String,
)
