package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.feature.catalog.domain.CampaignOpening
import io.github.youndie.haul.feature.catalog.domain.CampaignPricing
import io.github.youndie.haul.feature.catalog.domain.PriceList
import io.github.youndie.haul.feature.catalog.domain.Sku
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The rule every price passes through (B-53), at its edges: a campaign's SKU is at its regular price
 * with nothing struck through until the campaign opens for the list — at the early-access start for
 * Plus, at the start for everybody — and at the campaign's price from that instant on.
 */
class CampaignPricingTest {
    private val sale = Sku("p-1-0", "p-1", 0, emptyMap(), priceCents = 34_900, oldPriceCents = 44_900, stock = 3)
    private val regular = sale.copy(priceCents = 44_900, oldPriceCents = null)
    private val early = OffsetDateTime.parse("2025-10-06T00:00:00-04:00")
    private val start = OffsetDateTime.parse("2025-10-07T00:00:00-04:00")
    private val opening = CampaignOpening(start, early)

    private fun at(
        prices: PriceList,
        instant: OffsetDateTime,
        campaign: CampaignOpening? = opening,
    ): Sku = CampaignPricing.priced(sale, campaign, prices, instant)

    /** Before the early-access start nobody has the campaign's price, a member included. */
    @Test
    fun `before early access everybody pays the regular price`() {
        val before = early.minusSeconds(1)
        assertEquals(regular, at(PriceList.Plus, before))
        assertEquals(regular, at(PriceList.Public, before))
    }

    /** From the early-access instant to the start a member has the campaign's price and nobody else does. */
    @Test
    fun `in the early window only a member has the campaign price`() {
        assertEquals(sale, at(PriceList.Plus, early))
        assertEquals(sale, at(PriceList.Plus, start.minusSeconds(1)))
        assertEquals(regular, at(PriceList.Public, early))
        assertEquals(regular, at(PriceList.Public, start.minusSeconds(1)))
    }

    /** From the start everybody has the campaign's price. */
    @Test
    fun `from the start everybody has the campaign price`() {
        assertEquals(sale, at(PriceList.Public, start))
        assertEquals(sale, at(PriceList.Plus, start))
    }

    /** A campaign with no early access opens to a member at its start, like to everybody. */
    @Test
    fun `a campaign without early access opens to a member at its start`() {
        val plain = CampaignOpening(start, plusEarlyAccessAt = null)
        assertEquals(regular, at(PriceList.Plus, early, plain))
        assertEquals(sale, at(PriceList.Plus, start, plain))
    }

    /** A SKU in no campaign is as stored, its old price included, whoever looks and whenever. */
    @Test
    fun `a sku in no campaign is as stored`() {
        assertEquals(sale, at(PriceList.Public, early.minusDays(1), campaign = null))
    }
}
