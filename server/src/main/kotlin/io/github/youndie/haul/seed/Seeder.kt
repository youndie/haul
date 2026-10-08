package io.github.youndie.haul.seed

import io.github.youndie.haul.feature.cart.data.CartLinesTable
import io.github.youndie.haul.feature.cart.data.CartsTable
import io.github.youndie.haul.feature.cart.data.PromoCodesTable
import io.github.youndie.haul.feature.catalog.data.CampaignsTable
import io.github.youndie.haul.feature.catalog.data.CategoriesTable
import io.github.youndie.haul.feature.catalog.data.DealsTable
import io.github.youndie.haul.feature.catalog.data.ProductsTable
import io.github.youndie.haul.feature.catalog.data.SellersTable
import io.github.youndie.haul.feature.catalog.data.SkusTable
import io.github.youndie.haul.feature.checkout.data.AddressesTable
import io.github.youndie.haul.feature.checkout.data.PickupPointsTable
import io.github.youndie.haul.feature.identity.data.CustomersTable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Inserts a [SeedCatalog] into an empty catalog, once.
 *
 * Run at start-up, explicitly — never from a repository's initialiser — and safe to run from every
 * replica at once: the transaction takes an advisory lock first, so the second replica waits, then
 * finds the catalog no longer empty and inserts nothing.
 */
internal object Seeder {
    /** An arbitrary key for `pg_advisory_xact_lock`, held for the seeding transaction only. */
    private const val LOCK_KEY = 0x4841554CL // "HAUL"

    /** Returns whether it seeded; `false` means the catalog already had rows. */
    fun seedIfEmpty(
        database: Database,
        catalog: SeedCatalog,
    ): Boolean =
        transaction(database) {
            exec("SELECT pg_advisory_xact_lock($LOCK_KEY)")
            if (!CategoriesTable.selectAll().empty()) return@transaction false

            CategoriesTable.batchInsert(catalog.categories) {
                this[CategoriesTable.slug] = it.slug
                this[CategoriesTable.parentSlug] = it.parentSlug
                this[CategoriesTable.name] = it.name
                this[CategoriesTable.position] = it.position
                this[CategoriesTable.tone] = it.tone
                this[CategoriesTable.label] = it.label
            }
            SellersTable.batchInsert(catalog.sellers) {
                this[SellersTable.id] = it.id
                this[SellersTable.name] = it.name
                this[SellersTable.rating] = it.rating
                this[SellersTable.positivePercent] = it.positivePercent
                this[SellersTable.yearsOnHaul] = it.yearsOnHaul
            }
            ProductsTable.batchInsert(catalog.products) {
                this[ProductsTable.id] = it.id
                this[ProductsTable.sellerId] = it.sellerId
                this[ProductsTable.categorySlug] = it.categorySlug
                this[ProductsTable.title] = it.title
                this[ProductsTable.brand] = it.brand
                this[ProductsTable.description] = it.description
                this[ProductsTable.specifications] = it.specifications
                this[ProductsTable.rating] = it.rating
                this[ProductsTable.reviewsCount] = it.reviewsCount
                this[ProductsTable.questionsCount] = it.questionsCount
                this[ProductsTable.tone] = it.tone
                this[ProductsTable.label] = it.label
                this[ProductsTable.createdAt] = it.createdAt
                this[ProductsTable.features] = JsonArray(it.features.map(::JsonPrimitive))
                this[ProductsTable.kind] = it.kind
                this[ProductsTable.dispatchDays] = it.dispatchDays
                this[ProductsTable.headline] = it.headline
                this[ProductsTable.headlineAccent] = it.headlineAccent
            }
            SkusTable.batchInsert(catalog.skus) {
                this[SkusTable.id] = it.id
                this[SkusTable.productId] = it.productId
                this[SkusTable.position] = it.position
                this[SkusTable.optionValues] = it.options
                this[SkusTable.priceCents] = it.priceCents
                this[SkusTable.oldPriceCents] = it.oldPriceCents
                this[SkusTable.stock] = it.stock
            }
            CampaignsTable.batchInsert(catalog.campaigns) {
                this[CampaignsTable.slug] = it.slug
                this[CampaignsTable.title] = it.title
                this[CampaignsTable.subtitle] = it.subtitle
                this[CampaignsTable.position] = it.position
                this[CampaignsTable.tone] = it.tone
                this[CampaignsTable.startsAt] = it.startsAt
                this[CampaignsTable.endsAt] = it.endsAt
                this[CampaignsTable.plusEarlyAccessAt] = it.plusEarlyAccessAt
            }
            DealsTable.batchInsert(catalog.deals) {
                this[DealsTable.id] = it.id
                this[DealsTable.skuId] = it.skuId
                this[DealsTable.priceCents] = it.priceCents
                this[DealsTable.endsAt] = it.endsAt
            }
            PromoCodesTable.batchInsert(catalog.promoCodes) {
                this[PromoCodesTable.code] = it.code
                this[PromoCodesTable.percentOff] = it.percentOff
                this[PromoCodesTable.capCents] = it.capCents
                this[PromoCodesTable.startsAt] = it.startsAt
                this[PromoCodesTable.endsAt] = it.endsAt
            }
            CustomersTable.batchInsert(catalog.customers) {
                this[CustomersTable.id] = it.id
                this[CustomersTable.name] = it.name
                this[CustomersTable.plus] = it.plus
                this[CustomersTable.createdAt] = CatalogSeed.NOW
            }
            val prices = catalog.skus.associate { it.id to it.priceCents }
            catalog.carts.forEach { cart ->
                CartsTable.insert {
                    it[CartsTable.id] = cart.id
                    it[CartsTable.customerId] = cart.customerId
                }
                CartLinesTable.batchInsert(cart.skuIds.withIndex()) { (index, skuId) ->
                    this[CartLinesTable.cartId] = cart.id
                    this[CartLinesTable.skuId] = skuId
                    this[CartLinesTable.quantity] = 1
                    this[CartLinesTable.selected] = true
                    this[CartLinesTable.seenPriceCents] = checkNotNull(prices[skuId]) { "no SKU $skuId in the seed" }
                    this[CartLinesTable.seenInStock] = true
                    // A minute apart, the first a day before «now»: the order is the cart's.
                    this[CartLinesTable.addedAt] = CatalogSeed.NOW.minusDays(1).plusMinutes(index.toLong())
                    this[CartLinesTable.position] = index + 1
                }
            }
            PickupPointsTable.batchInsert(catalog.pickupPoints) {
                this[PickupPointsTable.id] = it.id
                this[PickupPointsTable.kind] = it.kind
                this[PickupPointsTable.name] = it.name
                this[PickupPointsTable.distanceMeters] = it.distanceMeters
                this[PickupPointsTable.hours] = it.hours
                this[PickupPointsTable.position] = it.position
            }
            AddressesTable.batchInsert(catalog.addresses) {
                this[AddressesTable.id] = it.id
                this[AddressesTable.customerId] = it.customerId
                this[AddressesTable.street] = it.street
                this[AddressesTable.apt] = it.apt
                this[AddressesTable.city] = it.city
                this[AddressesTable.zip] = it.zip
                this[AddressesTable.createdAt] = CatalogSeed.NOW.minusDays(ADDRESS_AGE_DAYS)
            }
            true
        }

    /** Maya's address was saved long before the canvas's «now». */
    private const val ADDRESS_AGE_DAYS = 365L
}
