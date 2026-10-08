package io.github.youndie.haul.seed

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
) {
    init {
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

/** A customer's cart: one of each SKU, selected, at the price the catalog has, in the order given. */
internal data class SeedCart(
    val id: String,
    val customerId: String,
    val skuIds: List<String>,
)
