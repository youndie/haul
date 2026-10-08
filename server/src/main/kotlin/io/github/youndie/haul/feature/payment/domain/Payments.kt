package io.github.youndie.haul.feature.payment.domain

import io.github.youndie.haul.feature.checkout.domain.PaymentMethod
import java.time.Instant

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

/** What a shipment asks to be charged when it ships: [amountCents] of [orderId]'s authorisation, at [at]. */
internal data class Capture(
    val orderId: String,
    val amountCents: Int,
    val at: Instant,
) {
    init {
        require(amountCents > 0) { "a capture takes a positive amount, not $amountCents cents" }
    }
}

/** What the card processor answered a capture. */
internal sealed interface CaptureOutcome {
    /** [amountCents] were taken — now, or by the first call under the same key. */
    data class Captured(
        val amountCents: Int,
    ) : CaptureOutcome

    /** The order holds no authorisation to take from: never authorised, declined or voided. */
    data object NotAuthorised : CaptureOutcome

    /** The capture would take more than the authorisation still holds, [remainingCents]. */
    data class Exceeds(
        val remainingCents: Int,
    ) : CaptureOutcome
}

/**
 * The card processor (research D4: «authorise at placement, capture per shipment at ship time»), as the
 * order saga and the fulfilment simulator call it. Every call is named by the caller's [key] — the saga member's idempotency key —
 * and the processor keeps it: [authorise] asked again under a key it has seen answers what it answered
 * the first time and holds nothing more, and [void] cancels by that same name, so a rollback needs no
 * evidence that the authorisation landed (petich, «What it asks of a member»).
 *
 * A capture takes part of the order's authorisation (B-17): one per shipment, when it ships, named by the
 * caller's key like everything else, so a shipment whose capture is asked twice — a pass re-run after a
 * restart — is charged once, and the captures of an authorisation never add up to more than it holds.
 *
 * v1 has no real processor: [PaymentSimulator] is the one implementation.
 */
internal interface PaymentProcessor {
    suspend fun authorise(
        key: String,
        authorisation: Authorisation,
    ): AuthorisationOutcome

    /**
     * Voids the authorisation held under [key]; `false` when none is held (never asked, declined, voided)
     * or when part of it was captured already — money taken is returned by a refund, not by a void.
     */
    suspend fun void(key: String): Boolean

    /**
     * Takes [capture] out of its order's authorisation under [key]. A key captured before answers what it
     * took then and takes nothing more, whatever this call asks for.
     */
    suspend fun capture(
        key: String,
        capture: Capture,
    ): CaptureOutcome

    /** What has been captured of [orderId], by the key each capture was taken under. */
    suspend fun captured(orderId: String): Map<String, Int>
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
