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
 * When a campaign's prices open: at [startsAt] for everybody, and at [plusEarlyAccessAt] for a Plus
 * member when the campaign has one (the trial dialog's «Early access to sales»).
 */
internal data class CampaignOpening(
    val startsAt: OffsetDateTime,
    val plusEarlyAccessAt: OffsetDateTime?,
) {
    fun openFor(
        prices: PriceList,
        at: OffsetDateTime,
    ): Boolean {
        val opens = if (prices == PriceList.Plus) plusEarlyAccessAt ?: startsAt else startsAt
        return !at.isBefore(opens)
    }
}

/**
 * The one rule every price passes through (B-53). A SKU of a campaign is stored at the campaign's price,
 * with its regular price as the old one (`skus.campaign_slug`, V26); until the campaign is open for
 * [prices] the SKU is at that regular price, with nothing struck through — so a card, the product page,
 * the cart and the quote all read the price the viewer may buy at, and placement charges what the
 * quote showed. A SKU in no campaign is as stored.
 *
 * Only the opening is read, not the campaign's end: no item has decided what a campaign's prices do once
 * it is over, and every seeded campaign is over on the stand's clock (B-53's findings).
 */
internal object CampaignPricing {
    fun priced(
        sku: Sku,
        campaign: CampaignOpening?,
        prices: PriceList,
        at: OffsetDateTime,
    ): Sku {
        if (campaign == null || campaign.openFor(prices, at)) return sku
        val regular = sku.oldPriceCents ?: return sku
        return sku.copy(priceCents = regular, oldPriceCents = null)
    }
}
