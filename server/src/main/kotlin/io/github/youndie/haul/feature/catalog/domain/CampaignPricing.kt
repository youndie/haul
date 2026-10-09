package io.github.youndie.haul.feature.catalog.domain

import java.time.OffsetDateTime

/**
 * Whose prices a read draws (B-53): everybody's, or a Haul Plus member's — a trial included — which
 * open a campaign at its early-access start rather than at its start. Nothing else differs between the
 * two; delivery and points read the membership on their own.
 */
internal enum class PriceList {
    Public,
    Plus,
    ;

    companion object {
        fun of(plus: Boolean): PriceList = if (plus) Plus else Public
    }
}

/**
 * When a campaign's prices hold: from [startsAt] for everybody, and from [plusEarlyAccessAt] for a Plus
 * member when the campaign has one (the trial dialog's «Early access to sales»); up to, not including,
 * [endsAt] for everybody (B-58) — early access opens a sale sooner, it does not keep it longer.
 */
internal data class CampaignWindow(
    val startsAt: OffsetDateTime,
    val endsAt: OffsetDateTime,
    val plusEarlyAccessAt: OffsetDateTime?,
) {
    fun liveFor(
        prices: PriceList,
        at: OffsetDateTime,
    ): Boolean {
        val opens = if (prices == PriceList.Plus) plusEarlyAccessAt ?: startsAt else startsAt
        return !at.isBefore(opens) && at.isBefore(endsAt)
    }
}

/**
 * The one rule every price passes through (B-53, B-57, B-58). A SKU of a campaign is stored at the campaign's
 * price, with its regular price as the old one (`skus.campaign_slug`, V26); outside the campaign's window for
 * [prices] — before it opens, and from its end on — the SKU is at that regular price, with nothing struck
 * through. A deal live at [at] is a price of
 * its SKU too: when it is below the price the SKU would otherwise sell at — the campaign's or the regular
 * one — it is the SKU's price, and the price it beats is struck through; a deal at or above that price
 * changes nothing, so a deal and a campaign on one SKU sell at the lower of the two. A card, the product
 * page, the cart and the quote therefore all read the price the viewer may buy at, and placement charges
 * what the quote showed. A SKU in no campaign and with no live deal is as stored.
 */
internal object CampaignPricing {
    fun priced(
        sku: Sku,
        campaign: CampaignWindow?,
        deals: List<Deal>,
        prices: PriceList,
        at: OffsetDateTime,
    ): Sku {
        val sold = inCampaign(sku, campaign, prices, at)
        val deal =
            deals.filter { it.skuId == sku.id && it.liveAt(at) }.minOfOrNull { it.priceCents } ?: return sold
        if (deal >= sold.priceCents) return sold
        return sold.copy(priceCents = deal, oldPriceCents = sold.priceCents)
    }

    private fun inCampaign(
        sku: Sku,
        campaign: CampaignWindow?,
        prices: PriceList,
        at: OffsetDateTime,
    ): Sku {
        if (campaign == null || campaign.liveFor(prices, at)) return sku
        val regular = sku.oldPriceCents ?: return sku
        return sku.copy(priceCents = regular, oldPriceCents = null)
    }
}
