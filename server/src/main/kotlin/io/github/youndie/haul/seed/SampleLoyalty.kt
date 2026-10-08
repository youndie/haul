package io.github.youndie.haul.seed

import io.github.youndie.haul.feature.account.domain.Loyalty
import io.github.youndie.haul.feature.account.domain.Membership
import io.github.youndie.haul.feature.account.domain.Standing
import io.github.youndie.haul.feature.identity.domain.Customer
import java.time.LocalDate

/**
 * The account's points and membership until feature-membership keeps them (B-23): research §6's numbers
 * for Maya — Plus since 2023, renewing on Nov 2, 2,480 points, $186 saved on delivery — and, for anybody
 * else, no points and the membership the customer row says, with nothing saved yet. A placeholder with
 * the canvas's data, the way the checkout's points toggle was drawn from it (B-15); the ledger replaces
 * this class, and the account does not change.
 */
internal object SampleLoyalty : Loyalty {
    override suspend fun standing(customer: Customer): Standing {
        val membership =
            when {
                !customer.plus -> null
                customer.id == SampleCustomers.MAYA -> MAYAS_MEMBERSHIP
                else -> Membership(since = customer.joined.year, savedCents = 0, renews = null)
            }
        val points = if (customer.id == SampleCustomers.MAYA) MAYAS_POINTS else 0
        return Standing(points, membership)
    }

    private const val MAYAS_POINTS = 2_480
    private val MAYAS_MEMBERSHIP = Membership(since = 2023, savedCents = 18_600, renews = LocalDate.of(2025, 11, 2))
}
