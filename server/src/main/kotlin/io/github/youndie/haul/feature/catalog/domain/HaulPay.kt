package io.github.youndie.haul.feature.catalog.domain

import kotlin.math.roundToInt

/**
 * Haul Pay, as far as a price or a quote shows it (research D6): four equal payments two weeks apart,
 * offered for amounts from $50 to $2,000; one payment is the amount ÷ 4, rounded to cents.
 */
internal object HaulPay {
    const val MIN_CENTS = 5_000
    const val MAX_CENTS = 200_000
    const val PAYMENTS = 4

    fun offered(cents: Int): Boolean = cents in MIN_CENTS..MAX_CENTS

    fun paymentCents(cents: Int): Int = (cents / PAYMENTS.toDouble()).roundToInt()
}
