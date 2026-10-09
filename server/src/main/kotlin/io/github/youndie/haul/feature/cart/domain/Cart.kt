package io.github.youndie.haul.feature.cart.domain

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.feature.catalog.domain.PriceList
import java.time.OffsetDateTime

/**
 * Whose cart (research §5: a cart is owned by a customer or a guest). [Customer.plus] doubles the
 * points, frees delivery and opens a campaign's prices early ([prices], B-53).
 */
internal sealed interface CartOwner {
    /** The prices the cart's lines are at: a member's, or everybody's for a guest and a non-member. */
    val prices: PriceList

    data class Guest(
        val id: String,
    ) : CartOwner {
        override val prices: PriceList get() = PriceList.Public
    }

    data class Customer(
        val id: String,
        val plus: Boolean,
    ) : CartOwner {
        override val prices: PriceList get() = PriceList.of(plus)
    }
}

/** What a cart command can refuse with; the application answers each with its status and body. */
internal sealed class CartError(
    val code: ErrorCode,
    override val message: String,
    val field: String? = null,
) : Exception(message) {
    class Invalid(
        field: String,
        message: String,
    ) : CartError(ErrorCode.ValidationFailed, message, field)

    class SkuNotFound(
        skuId: String,
    ) : CartError(ErrorCode.SkuNotFound, "No SKU «$skuId»")

    class OutOfStock(
        message: String,
    ) : CartError(ErrorCode.OutOfStock, message, "quantity")

    class LineNotFound(
        skuId: String,
    ) : CartError(ErrorCode.LineNotFound, "No line for «$skuId» in this cart")

    /** A promo refusal; [code] is one of the four promo codes, [message] what the field shows. */
    class Promo(
        code: ErrorCode,
        message: String,
    ) : CartError(code, message, "code")
}

/**
 * A line as stored: the quantity, the selection, and the price and stock the shopper last saw —
 * when the line was added, or when they acknowledged a change.
 */
internal data class StoredLine(
    val skuId: String,
    val quantity: Int,
    val selected: Boolean,
    val seenPriceCents: Int,
    val seenInStock: Boolean,
    val addedAt: OffsetDateTime,
)

/** A cart as stored; [promoAttempt] and [promoError] are the last refused code and why. */
internal data class StoredCart(
    val lines: List<StoredLine>,
    val promoCode: String? = null,
    val promoAttempt: String? = null,
    val promoError: ErrorCode? = null,
) {
    fun line(skuId: String): StoredLine? = lines.firstOrNull { it.skuId == skuId }

    companion object {
        val EMPTY = StoredCart(emptyList())
    }
}

/** A promo code (research §6): [percentOff] of the selected items at their price, at most [capCents]. */
internal data class PromoCode(
    val code: String,
    val percentOff: Int,
    val capCents: Int?,
    val startsAt: OffsetDateTime,
    val endsAt: OffsetDateTime,
)

/**
 * The cart's storage. Every method takes the owner, and a cart that does not exist yet is created by
 * the first write; a read of one that does not exist is [StoredCart.EMPTY].
 */
internal interface CartRepository {
    /** The owner's cart, its lines in the order they were first added. */
    suspend fun cart(owner: CartOwner): StoredCart

    /**
     * Writes [line] as the owner's line for its SKU, and forgets a refused promo attempt. A new line
     * goes last; one already there keeps its place and its [StoredLine.addedAt].
     */
    suspend fun putLine(
        owner: CartOwner,
        line: StoredLine,
    )

    /** Deletes the owner's lines for these SKUs (those it has), and forgets a refused promo attempt. */
    suspend fun removeLines(
        owner: CartOwner,
        skuIds: Set<String>,
    )

    /** Sets the applied code (`null` removes it) and the refused attempt with its reason. */
    suspend fun setPromo(
        owner: CartOwner,
        code: String?,
        attempt: String? = null,
        error: ErrorCode? = null,
    )

    suspend fun promo(code: String): PromoCode?

    /**
     * Sign-in's merge, in one transaction: writes [lines] into the customer's cart (a new SKU goes
     * last, one already there keeps its place), sets its code to [promoCode], and deletes the guest's
     * cart.
     */
    suspend fun merge(
        from: CartOwner.Guest,
        into: CartOwner.Customer,
        lines: List<StoredLine>,
        promoCode: String?,
    )
}
