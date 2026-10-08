package io.github.youndie.haul.seed

import io.github.youndie.haul.feature.account.domain.SavedLists
import io.github.youndie.haul.feature.account.domain.SavedSummary

/**
 * The menu's Saved count and the Price drops tile until the Saved list exists (B-20): Maya's 48 saved
 * products and 6 price drops (research §6), nothing for anybody else.
 */
internal object SampleSavedLists : SavedLists {
    override suspend fun summary(customerId: String): SavedSummary =
        if (customerId == SampleCustomers.MAYA) SavedSummary(saved = 48, priceDrops = 6) else SavedSummary(0, 0)
}
