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
)

internal data class Sku(
    val id: String,
    val productId: String,
    val position: Int,
    val options: Map<String, String>,
    val priceCents: Int,
    val oldPriceCents: Int?,
    val stock: Int,
)

internal data class Campaign(
    val slug: String,
    val title: String,
    val subtitle: String,
    val position: Int,
    val tone: String,
    val startsAt: OffsetDateTime,
    val endsAt: OffsetDateTime,
)

internal data class Deal(
    val id: String,
    val skuId: String,
    val priceCents: Int,
    val endsAt: OffsetDateTime,
)

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

/** The catalog's port. Reads only; every method is a whole read the screen needs. */
internal interface CatalogRepository {
    suspend fun categories(): List<Category>

    /** The listed products of these categories, with all their SKUs. */
    suspend fun listedIn(categorySlugs: Set<String>): List<Listed>

    suspend fun listed(productIds: List<String>): List<Listed>

    suspend fun product(id: String): Listed?

    suspend fun seller(id: String): Seller?

    suspend fun campaigns(): List<Campaign>

    suspend fun deals(): List<Deal>
}
