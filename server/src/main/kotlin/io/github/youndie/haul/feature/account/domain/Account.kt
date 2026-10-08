package io.github.youndie.haul.feature.account.domain

import io.github.youndie.haul.feature.identity.domain.Customer
import java.time.LocalDate

/**
 * A Haul Plus membership as the account draws it: the year it began ([since]), what it saved on delivery
 * this year ([savedCents]), and the day it renews ([renews], unknown until there is a billing period).
 */
internal data class Membership(
    val since: Int,
    val savedCents: Int,
    val renews: LocalDate?,
)

/** A customer's loyalty: their [points] balance and their [membership], `null` when they are not a member. */
internal data class Standing(
    val points: Int,
    val membership: Membership?,
)

/**
 * Where the account's Points and Haul Plus tiles come from. Feature-membership's (B-23) — the points
 * ledger, the trial, the delivery savings — none of which is stored yet; until then the account reads the
 * canvas's numbers for the sample customers (`seed/SampleLoyalty.kt`), and B-23 replaces the source,
 * not the account.
 */
internal fun interface Loyalty {
    suspend fun standing(customer: Customer): Standing
}

/** How many products a customer has saved ([saved]) and how many of them got cheaper since ([priceDrops]). */
internal data class SavedSummary(
    val saved: Int,
    val priceDrops: Int,
)

/**
 * Where the menu's Saved count and the Price drops tile come from: the Saved list's (B-20), which is not
 * stored yet; until then the canvas's numbers for the sample customers (`seed/SampleLoyalty.kt`).
 */
internal fun interface SavedLists {
    suspend fun summary(customerId: String): SavedSummary
}
