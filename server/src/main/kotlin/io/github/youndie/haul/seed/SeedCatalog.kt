package io.github.youndie.haul.seed

import io.github.youndie.haul.feature.membership.domain.PlusMembership
import io.github.youndie.haul.feature.reviews.domain.StoredQuestion
import io.github.youndie.haul.feature.reviews.domain.StoredReview
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.math.BigDecimal
import java.time.OffsetDateTime

/** What a seed inserts, table by table, in the order it inserts it. */
internal data class SeedCatalog(
    val categories: List<SeedCategory>,
    val sellers: List<SeedSeller>,
    val products: List<SeedProduct>,
    val skus: List<SeedSku>,
    val campaigns: List<SeedCampaign>,
    val deals: List<SeedDeal>,
    val promoCodes: List<SeedPromoCode>,
    val customers: List<SeedCustomer> = emptyList(),
    val carts: List<SeedCart> = emptyList(),
    val pickupPoints: List<SeedPickupPoint> = emptyList(),
    val addresses: List<SeedAddress> = emptyList(),
    val reviews: List<StoredReview> = emptyList(),
    /** Per product, stars to how many reviews gave them: the histogram (feature-reviews). */
    val ratingCounts: Map<String, Map<Int, Int>> = emptyMap(),
    val questions: List<StoredQuestion> = emptyList(),
    val saved: List<SeedSaved> = emptyList(),
    /** The sample customers' Haul Plus memberships (B-23). */
    val memberships: List<PlusMembership> = emptyList(),
    /** The points the sample customers had before the store kept orders, by customer (B-23). */
    val openingPoints: Map<String, Int> = emptyMap(),
    /** The sample customers' product views (B-25). */
    val views: List<SeedView> = emptyList(),
)

internal data class SeedCategory(
    val slug: String,
    val parentSlug: String?,
    val name: String,
    val position: Int,
    val tone: String,
    val label: String,
)

internal data class SeedSeller(
    val id: String,
    val name: String,
    val rating: BigDecimal,
    val positivePercent: Int,
    val yearsOnHaul: Int,
)

internal data class SeedProduct(
    val id: String,
    val sellerId: String,
    val categorySlug: String,
    val title: String,
    val brand: String,
    val description: String,
    val specifications: JsonArray,
    val rating: BigDecimal,
    val reviewsCount: Int,
    val questionsCount: Int,
    val tone: String,
    val label: String,
    val createdAt: OffsetDateTime,
    val features: List<String> = emptyList(),
    val kind: String? = null,
    val dispatchDays: Int = 0,
    val headline: String,
    val headlineAccent: String? = null,
    /** The month of sales no seeded order holds (B-52, V23): the sample products' «bought this month». */
    val boughtBase: Int = 0,
    /** What cards, cart lines and order lines write (B-45, V25), as the canvas writes it; null is the title. */
    val listingName: String? = null,
) {
    init {
        require(listingName == null || listingName.isNotEmpty()) { "$id has an empty listing name" }
        // The same rule V6 checks, failing at generation instead of at the insert.
        require(headline.isNotBlank()) { "$id has a blank headline" }
        require(headlineAccent == null || headlineAccent in headline) {
            "$id: the accent «$headlineAccent» is not in the headline «$headline»"
        }
    }
}

internal data class SeedSku(
    val id: String,
    val productId: String,
    val position: Int,
    val options: JsonObject,
    val priceCents: Int,
    val oldPriceCents: Int?,
    val stock: Int,
    /** The campaign whose price [priceCents] is, [oldPriceCents] the regular one (B-53, V26). */
    val campaignSlug: String? = null,
)

internal data class SeedCampaign(
    val slug: String,
    val title: String,
    val subtitle: String,
    val position: Int,
    val tone: String,
    val startsAt: OffsetDateTime,
    val endsAt: OffsetDateTime,
    val plusEarlyAccessAt: OffsetDateTime?,
)

internal data class SeedDeal(
    val id: String,
    val skuId: String,
    val priceCents: Int,
    val endsAt: OffsetDateTime,
)

internal data class SeedPromoCode(
    val code: String,
    val percentOff: Int,
    val capCents: Int?,
    val startsAt: OffsetDateTime,
    val endsAt: OffsetDateTime,
)

internal data class SeedCustomer(
    val id: String,
    val name: String,
    val plus: Boolean,
)

/** A product in a customer's Saved list (B-20): the price it was saved at, and when. */
internal data class SeedSaved(
    val customerId: String,
    val productId: String,
    val savedPriceCents: Int,
    val savedAt: OffsetDateTime,
)

/** A product a customer opened (B-25), and when. */
internal data class SeedView(
    val customerId: String,
    val productId: String,
    val viewedAt: OffsetDateTime,
)

/** A customer's cart: one of each SKU, selected, at the price the catalog has, in the order given. */
internal data class SeedCart(
    val id: String,
    val customerId: String,
    val skuIds: List<String>,
)

/** A pickup point or a locker: [kind] `pickup_point` or `parcel_locker`; [distanceMeters] `null` where research §6 gives none. */
internal data class SeedPickupPoint(
    val id: String,
    val kind: String,
    val name: String,
    val distanceMeters: Int?,
    val hours: String,
    val position: Int,
)

/** A customer's saved address. */
internal data class SeedAddress(
    val id: String,
    val customerId: String,
    val street: String,
    val apt: String?,
    val city: String,
    val zip: String,
)
