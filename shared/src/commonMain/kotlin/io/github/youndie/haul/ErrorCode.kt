package io.github.youndie.haul

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Every error the server answers with; the client tells cases apart by this, never by the message. */
@Serializable
public enum class ErrorCode {
    @SerialName("validation_failed")
    ValidationFailed,

    @SerialName("category_not_found")
    CategoryNotFound,

    @SerialName("product_not_found")
    ProductNotFound,

    /** A dependency the answer needs is down — the database could not be reached; try again later (`503`). */
    @SerialName("unavailable")
    Unavailable,

    /** A failure nobody expected: a bug, reported to katcher. Not worth retrying as it is (`500`). */
    @SerialName("internal")
    Internal,

    /** A search or a suggestion asked for with fewer than two characters (feature-search). */
    @SerialName("query_too_short")
    QueryTooShort,

    /** A cart route called with neither a guest id the server issued nor a customer token. */
    @SerialName("unauthenticated")
    Unauthenticated,

    /** A cart line asked for a SKU that does not exist. */
    @SerialName("sku_not_found")
    SkuNotFound,

    /** A SKU added with stock 0, or a quantity above its stock (feature-cart, feature-product). */
    @SerialName("out_of_stock")
    OutOfStock,

    /** A command named a line that is not in the caller's cart. */
    @SerialName("line_not_found")
    LineNotFound,

    @SerialName("promo_not_found")
    PromoNotFound,

    /** One promo code per cart: another one is already applied (feature-cart). */
    @SerialName("promo_already_applied")
    PromoAlreadyApplied,

    @SerialName("promo_expired")
    PromoExpired,

    /** The code is valid but not for this cart: nothing selected, or its window has not opened. */
    @SerialName("promo_not_applicable")
    PromoNotApplicable,
}

/** The body of every error answer: the code, a sentence for a person, and the field when one is at fault. */
@Serializable
public data class ErrorBody(
    val code: ErrorCode,
    val message: String,
    val field: String? = null,
)
