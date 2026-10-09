package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.feature.catalog.domain.CampaignOpening
import io.github.youndie.haul.feature.catalog.domain.CampaignPricing
import io.github.youndie.haul.feature.catalog.domain.Deal
import io.github.youndie.haul.feature.catalog.domain.PriceList
import io.github.youndie.haul.feature.catalog.domain.Sku
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The rule every price passes through (B-53, B-57), at its edges: a campaign's SKU is at its regular price
 * with nothing struck through until the campaign opens for the list — at the early-access start for
 * Plus, at the start for everybody — and at the campaign's price from that instant on; a deal is the SKU's
 * price inside its window when it is below the price it would otherwise sell at, over that price.
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
        deals: List<Deal> = emptyList(),
    ): Sku = CampaignPricing.priced(sale, campaign, deals, prices, instant)

    private val dealStart = OffsetDateTime.parse("2025-10-07T00:00:00-04:00")
    private val dealEnd = OffsetDateTime.parse("2025-10-08T00:00:00-04:00")

    private fun deal(
        cents: Int,
        skuId: String = sale.id,
    ) = Deal("deal-$cents", skuId, cents, dealStart, dealEnd)

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

    /**
     * A deal is live from its start up to, not including, its end; outside that the SKU is as the campaign
     * has it. Drawn for Plus, for whom the campaign is open on either side of the window.
     */
    @Test
    fun `a deal is the price inside its window only`() {
        val deal = listOf(deal(24_900))
        val dealt = sale.copy(priceCents = 24_900, oldPriceCents = 34_900)
        assertEquals(sale, at(PriceList.Plus, dealStart.minusSeconds(1), deals = deal))
        assertEquals(dealt, at(PriceList.Plus, dealStart, deals = deal))
        assertEquals(dealt, at(PriceList.Plus, dealEnd.minusSeconds(1), deals = deal))
        assertEquals(sale, at(PriceList.Plus, dealEnd, deals = deal))
    }

    /**
     * A deal and a campaign on one SKU: the lower price wins. A deal below the campaign's price strikes the
     * campaign's through; one at or above it leaves the SKU at the campaign's price under the regular one.
     */
    @Test
    fun `a deal and a campaign sell at the lower price`() {
        assertEquals(
            sale.copy(priceCents = 29_900, oldPriceCents = 34_900),
            at(PriceList.Public, start, deals = listOf(deal(29_900))),
        )
        assertEquals(sale, at(PriceList.Public, start, deals = listOf(deal(34_900))))
        assertEquals(sale, at(PriceList.Public, start, deals = listOf(deal(39_900))))
    }

    /** Before the campaign opens for the list, a deal beats the regular price and strikes that through. */
    @Test
    fun `a deal before the campaign opens beats the regular price`() {
        val live = OffsetDateTime.parse("2025-10-07T12:00:00-04:00")
        val late = CampaignOpening(OffsetDateTime.parse("2025-10-09T00:00:00-04:00"), plusEarlyAccessAt = null)
        assertEquals(
            sale.copy(priceCents = 39_900, oldPriceCents = 44_900),
            at(PriceList.Public, live, late, listOf(deal(39_900))),
        )
        assertEquals(regular, at(PriceList.Public, live, late, listOf(deal(44_900))))
    }

    /** Two live deals on one SKU: the lower; a deal of another SKU is nothing to this one. */
    @Test
    fun `the lowest live deal of the sku is the one that counts`() {
        val deals = listOf(deal(29_900), deal(19_900), deal(9_900, skuId = "p-2-0"))
        assertEquals(sale.copy(priceCents = 19_900, oldPriceCents = 34_900), at(PriceList.Public, start, deals = deals))
    }
}
