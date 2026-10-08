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

    /** A guest cart merged on sign-in names a guest the server never issued (feature-identity). */
    @SerialName("guest_not_found")
    GuestNotFound,

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

    /** Checkout with no line selected in the cart: nothing to quote (feature-checkout). */
    @SerialName("cart_empty")
    CartEmpty,

    /** A delivery window at capacity: it is drawn and not selectable, and choosing it is refused. */
    @SerialName("slot_unavailable")
    SlotUnavailable,

    /** A delivery window checkout does not offer: not one of the next five days' four windows. */
    @SerialName("slot_not_found")
    SlotNotFound,

    @SerialName("pickup_point_not_found")
    PickupPointNotFound,

    /** An address that is not one of the caller's own. */
    @SerialName("address_not_found")
    AddressNotFound,

    /** A way to pay this checkout does not offer: pay on delivery to a locker, Haul Pay outside $50–$2,000. */
    @SerialName("payment_method_not_allowed")
    PaymentMethodNotAllowed,

    /** Placement without an `Idempotency-Key` header, or with an empty one (feature-checkout). */
    @SerialName("idempotency_key_missing")
    IdempotencyKeyMissing,

    /** Placement under an `Idempotency-Key` already used for a different request (feature-checkout). */
    @SerialName("idempotency_key_reused")
    IdempotencyKeyReused,

    /**
     * Placement of a quote that is no longer the checkout's: a line, a price, the code, the method, the
     * address, the point, the window or the way to pay changed since the page was drawn.
     */
    @SerialName("cart_changed")
    CartChanged,

    /**
     * Placement of a checkout that holds «Place order» (`CheckoutSummary.placeEnabled = false`): an address
     * form the server refused is on record, so the address the quote still names is not the one the shopper
     * is entering. A `409`, not `validation_failed`: the request is well-formed, the conflict is with what
     * the checkout holds, and the same request under the same key places once a saved address is chosen.
     */
    @SerialName("checkout_held")
    CheckoutHeld,

    /** One review per customer per product: this customer has already reviewed it (feature-reviews). */
    @SerialName("review_exists")
    ReviewExists,

    /** In [ErrorBody.fields] only: a form field that must be filled was left empty. */
    @SerialName("field_required")
    FieldRequired,

    /** In [ErrorBody.fields] only: a form field filled with something it cannot hold (a ZIP of letters). */
    @SerialName("field_invalid")
    FieldInvalid,
}

/**
 * The body of every error answer: the code, a sentence for a person, and the field when one is at
 * fault. A form refused as a whole (`validation_failed`) also lists every field at fault in [fields],
 * each with its own code and sentence; [field] is then the first of them.
 */
@Serializable
public data class ErrorBody(
    val code: ErrorCode,
    val message: String,
    val field: String? = null,
    val fields: List<FieldError> = emptyList(),
)

/** One field of a refused form: which, why ([code], `field_required` or `field_invalid`), and what to fix. */
@Serializable
public data class FieldError(
    val field: String,
    val code: ErrorCode,
    val message: String,
)
