package io.github.youndie.haul.feature.checkout.domain

import java.security.MessageDigest

/**
 * Who may choose the test card ···· 0002, the one the payment simulator always declines (B-89): a request that
 * carries [key] in `TEST_CARD_KEY_HEADER` — the synthetic shoppers', so a share of the stand's traffic runs the
 * order saga's compensation. Nobody else: the storefront never lists the card ([PaymentMethod.listed]), and a
 * visitor who names it by its id is refused as for any way to pay that is not offered. With no key configured
 * (`HAUL_TEST_CARD_KEY` unset) nobody may.
 */
internal class TestCardAccess(
    private val key: String?,
) {
    /** Whether [presented], the header's value or `null` without one, opens the test card. */
    fun admits(presented: String?): Boolean =
        key != null && presented != null && MessageDigest.isEqual(key.toByteArray(), presented.toByteArray())

    companion object {
        /** No key: the test card is nobody's. */
        val NONE: TestCardAccess = TestCardAccess(null)
    }
}
