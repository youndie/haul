package io.github.youndie.haul.feature.catalog.domain

import java.math.BigDecimal
import java.time.OffsetDateTime

// The catalog as the browse half reads it. Rows, not entities: nothing here is written by a request.

internal data class Category(
    val slug: String,
    val parentSlug: String?,
    val name: String,
    val position: Int,
    val tone: String,
    val label: String,
)

internal data class Seller(
    val id: String,
    val name: String,
    val rating: BigDecimal,
    val positivePercent: Int,
    val yearsOnHaul: Int,
)

internal data class Product(
    val id: String,
    val sellerId: String,
    val categorySlug: String,
    /** What the product page writes under [brand] («WH-1000XM6 Wireless Noise Cancelling Headphones»). */
    val title: String,
    val brand: String,
    val description: String,
    val specifications: List<Pair<String, String>>,
    val rating: BigDecimal,
    val reviewsCount: Int,
    val questionsCount: Int,
    val tone: String,
    val label: String,
    val features: List<String>,
    val kind: String?,
    val dispatchDays: Int,
    val createdAt: OffsetDateTime,
    /** Over the description tab («Silence, tuned to you»); [headlineAccent] is a piece of it, or none. */
    val headline: String,
    val headlineAccent: String?,
    /** The key of the product's photo in the object storage (B-30); `null` is the placeholder tile. */
    val imageKey: String? = null,
    /**
     * The month of sales the store holds no orders for, added to the live count of «bought this month»
     * (B-52, V23): the seed's stand-in for the sample products, 0 for every other product.
     */
    val boughtBase: Int = 0,
    /**
     * What a card, a cart line, the checkout's summary and an order line write (B-45, V25): «Sony
     * WH-1000XM6 Wireless Noise Cancelling Headphones», where the product page writes [brand] above
     * [title]. The title when the catalogue names none; never «brand + title», which the duvet, whose
     * brand is its seller's, shows to be no rule.
     */
    val listingName: String = title,
)

/**
 * A SKU at the prices one [PriceList] draws: [priceCents] is what a card shows and the cart charges,
 * [oldPriceCents] what is struck through. A SKU of a campaign the list does not see open yet is at its
 * regular price with nothing struck through (B-53), and a SKU with a live deal below that price is at the
 * deal's, over the price it beats (B-57, [CampaignPricing]).
 */
internal data class Sku(
    val id: String,
    val productId: String,
    val position: Int,
    val options: Map<String, String>,
    val priceCents: Int,
    val oldPriceCents: Int?,
    val stock: Int,
)

/**
 * A campaign as home draws it (B-59): the first by [position] is the sale its hero announces, drawn while
 * the sale is live for the viewer ([window]); the others are its banners, drawn until they end.
 */
internal data class Campaign(
    val slug: String,
    val title: String,
    val subtitle: String,
    val position: Int,
    val tone: String,
    val startsAt: OffsetDateTime,
    val endsAt: OffsetDateTime,
    val plusEarlyAccessAt: OffsetDateTime?,
) {
    /** When its prices hold — the rule a SKU of it is priced by ([CampaignPricing]). */
    val window: CampaignWindow get() = CampaignWindow(startsAt, endsAt, plusEarlyAccessAt)

    /** From its [endsAt] on nothing of it is drawn — not even for a Plus member, whose early access ends there too. */
    fun endedAt(at: OffsetDateTime): Boolean = !at.isBefore(endsAt)
}

/**
 * The sale home's hero announces and the empty cart's line names («up to −70 % in the Autumn mega sale»):
 * the first of [campaigns] by position, while it is live for [prices] at [at] — a Plus member's from its
 * early access — else `null`, and then neither is drawn (B-59). Before, both were drawn for good: past the
 * sale's end the hero announced a sale whose prices were gone (B-58's findings).
 */
internal fun liveSale(
    campaigns: List<Campaign>,
    prices: PriceList,
    at: OffsetDateTime,
): Campaign? = campaigns.minByOrNull { it.position }?.takeIf { it.window.liveFor(prices, at) }

/**
 * A price of one SKU for a window (B-57): live from [startsAt] up to, not including, [endsAt] on the
 * store's clock. While it is live, [CampaignPricing] makes it the SKU's price wherever the SKU is read,
 * unless a campaign already sells the SKU at or below it; outside the window it is nothing.
 */
internal data class Deal(
    val id: String,
    val skuId: String,
    val priceCents: Int,
    val startsAt: OffsetDateTime,
    val endsAt: OffsetDateTime,
) {
    fun liveAt(at: OffsetDateTime): Boolean = !at.isBefore(startsAt) && at.isBefore(endsAt)
}

/** A product with its SKUs: what a card and a facet count need. */
internal data class Listed(
    val product: Product,
    val skus: List<Sku>,
) {
    /** The SKU a card shows: the cheapest in stock, or the cheapest at all when none is. */
    val shown: Sku = skus.filter { it.stock > 0 }.minByOrNull { it.priceCents } ?: skus.minBy { it.priceCents }
    val inStock: Boolean get() = shown.stock > 0
    val colours: Set<String> get() = skus.mapNotNull { it.options["colour"] }.toSet()
}

/**
 * The catalog's port. Reads only; every method is a whole read the screen needs.
 *
 * Every read that returns SKUs takes the [PriceList] their prices are drawn for, with no default: a
 * caller that forgot whose prices it draws would otherwise get somebody's, and a member's early campaign
 * price drawn for a non-member is the one mistake B-53 must not make.
 */
internal interface CatalogRepository {
    suspend fun categories(): List<Category>

    /** The listed products of these categories, with all their SKUs. */
    suspend fun listedIn(
        categorySlugs: Set<String>,
        prices: PriceList,
    ): List<Listed>

    suspend fun listed(
        productIds: List<String>,
        prices: PriceList,
    ): List<Listed>

    /** The listed products that own these SKUs, each with all its SKUs; an unknown id matches nothing. */
    suspend fun listedBySkus(
        skuIds: Set<String>,
        prices: PriceList,
    ): List<Listed>

    suspend fun product(
        id: String,
        prices: PriceList,
    ): Listed?

    suspend fun seller(id: String): Seller?

    suspend fun campaigns(): List<Campaign>

    /** The deals live on the store's clock, by id; an ended or a future deal is not among them. */
    suspend fun deals(): List<Deal>
}
