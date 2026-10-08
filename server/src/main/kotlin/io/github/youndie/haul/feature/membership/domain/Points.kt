package io.github.youndie.haul.feature.membership.domain

import java.time.OffsetDateTime

/**
 * The ways points move (research §5, `PointsEntry`): [Earned] and [Returned] add, [Redeemed] and
 * [Reversed] take away. [Returned] gives back points an order was paid with — the order cancelled, or a
 * return refunded; [Reversed] takes back points an order earned — a return.
 */
internal enum class PointsKind(
    val id: String,
) {
    Earned("earned"),
    Redeemed("redeemed"),
    Returned("returned"),
    Reversed("reversed"),
    ;

    companion object {
        fun of(id: String): PointsKind = entries.first { it.id == id }
    }
}

/**
 * One row of a customer's points ledger: [points] signed (a redemption is negative), [orderId] the order it
 * is about, if any. [key] names the movement and makes it happen once: the same movement recorded twice —
 * a saga member re-run, two simulator passes at once — finds its row and writes nothing. Built through the
 * functions of the companion, which give each kind its key and its sign.
 */
internal data class PointsMovement(
    val key: String,
    val customerId: String,
    val kind: PointsKind,
    val points: Int,
    val orderId: String?,
    val at: OffsetDateTime,
) {
    companion object {
        /** [shipmentId]'s share of its order's points, credited as it is delivered or collected. */
        fun earned(
            customerId: String,
            orderId: String,
            shipmentId: String,
            points: Int,
            at: OffsetDateTime,
        ): PointsMovement = PointsMovement("earned:$shipmentId", customerId, PointsKind.Earned, points, orderId, at)

        /** The points [orderId] was paid with, «Use N points» at checkout. */
        fun redeemed(
            customerId: String,
            orderId: String,
            points: Int,
            at: OffsetDateTime,
        ): PointsMovement = PointsMovement("redeemed:$orderId", customerId, PointsKind.Redeemed, -points, orderId, at)

        /**
         * Points [orderId] was paid with, given back: all of them when the order is cancelled ([returnId]
         * `null`, one per order), or a return's share of them when it is refunded (one per return).
         */
        fun returned(
            customerId: String,
            orderId: String,
            points: Int,
            at: OffsetDateTime,
            returnId: String? = null,
        ): PointsMovement =
            PointsMovement("returned:${returnId ?: orderId}", customerId, PointsKind.Returned, points, orderId, at)

        /** Points [orderId] earned, taken back by the return [returnId] (B-21's refund). */
        fun reversed(
            customerId: String,
            orderId: String,
            returnId: String,
            points: Int,
            at: OffsetDateTime,
        ): PointsMovement = PointsMovement("reversed:$returnId", customerId, PointsKind.Reversed, -points, orderId, at)

        /** A balance earned before the store kept orders (the sample data's 2,480 for Maya), once per customer. */
        fun opening(
            customerId: String,
            points: Int,
            at: OffsetDateTime,
        ): PointsMovement = PointsMovement("opening:$customerId", customerId, PointsKind.Earned, points, null, at)
    }
}

/**
 * The points ledger (feature-membership): append-only, a customer's balance the sum of their rows. Every
 * write is idempotent by the movement's key.
 */
internal interface PointsLedger {
    suspend fun balance(customerId: String): Int

    /** Writes [movement] unless its key was written before; whether it was written now. */
    suspend fun record(movement: PointsMovement): Boolean

    /**
     * Writes [movement], a redemption, only while the customer's balance covers it — read and written
     * under a lock on the customer, so two orders placed at once cannot spend one balance twice. `true`
     * when it is written, now or before; `false` when the balance is short and nothing is written.
     */
    suspend fun redeem(movement: PointsMovement): Boolean

    /**
     * Gives back what [orderId] was paid with, once: a [PointsKind.Returned] row for its redemption, if it
     * has one. `false` when it has none, or it was given back before.
     */
    suspend fun giveBack(
        orderId: String,
        at: OffsetDateTime,
    ): Boolean

    /** [customerId]'s rows, oldest first. */
    suspend fun movements(customerId: String): List<PointsMovement>
}

/** The numbers of points (research D7): 1 point a whole dollar, twice that for Plus; 100 points = $1. */
internal object PointsRules {
    /** A point is worth a cent: 100 points = $1, so 2,480 points take $24.80 off. */
    const val CENTS_PER_POINT = 1

    private const val CENTS_PER_DOLLAR = 100

    /** What an order of [totalCents] earns: a point per whole dollar, ×2 for Plus. */
    fun earned(
        totalCents: Int,
        plus: Boolean,
    ): Int = totalCents / CENTS_PER_DOLLAR * if (plus) 2 else 1

    /**
     * How many of [balance]'s points an order takes when the toggle is on: all of them — «all or nothing» —
     * but never more than the order's items after discounts ([capCents]), so points never pay for delivery
     * and never make a total negative (feature-checkout).
     */
    fun redeemable(
        balance: Int,
        capCents: Int,
    ): Int = minOf(balance, capCents / CENTS_PER_POINT).coerceAtLeast(0)
}
