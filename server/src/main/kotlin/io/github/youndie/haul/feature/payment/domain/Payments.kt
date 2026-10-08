package io.github.youndie.haul.feature.payment.domain

import io.github.youndie.haul.feature.checkout.domain.PaymentMethod

/** What the order asks the card processor to hold: [amountCents] of [method], for [orderId]. */
internal data class Authorisation(
    val orderId: String,
    val method: PaymentMethod,
    val amountCents: Int,
)

/** What the card processor answered. */
internal enum class AuthorisationOutcome {
    Authorised,
    Declined,
}

/**
 * The card processor (research D4: «authorise at placement, capture per shipment at ship time»), as the
 * order saga calls it. Every call is named by the caller's [key] — the saga member's idempotency key —
 * and the processor keeps it: [authorise] asked again under a key it has seen answers what it answered
 * the first time and holds nothing more, and [void] cancels by that same name, so a rollback needs no
 * evidence that the authorisation landed (petich, «What it asks of a member»).
 *
 * v1 has no real processor: [PaymentSimulator] is the one implementation. Capture is B-17's.
 */
internal interface PaymentProcessor {
    suspend fun authorise(
        key: String,
        authorisation: Authorisation,
    ): AuthorisationOutcome

    /** Voids the authorisation held under [key]; `false` when none is held (never asked, declined, voided). */
    suspend fun void(key: String): Boolean
}

/**
 * The simulator's rule, and the whole of it (research D4, §6): every way to pay is approved except the
 * test card ···· 0002, which is always declined. Haul Pay is authorised like a card — its four payments
 * are B-24's; pay on delivery is never sent here (nothing is authorised for it, feature-orders).
 */
internal object PaymentSimulator {
    fun decide(method: PaymentMethod): AuthorisationOutcome =
        if (method == PaymentMethod.TestCard) AuthorisationOutcome.Declined else AuthorisationOutcome.Authorised
}
