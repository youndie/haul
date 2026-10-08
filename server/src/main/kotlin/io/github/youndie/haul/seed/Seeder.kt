package io.github.youndie.haul.seed

import io.github.youndie.haul.feature.catalog.data.CampaignsTable
import io.github.youndie.haul.feature.catalog.data.CategoriesTable
import io.github.youndie.haul.feature.catalog.data.DealsTable
import io.github.youndie.haul.feature.catalog.data.ProductsTable
import io.github.youndie.haul.feature.catalog.data.SellersTable
import io.github.youndie.haul.feature.catalog.data.SkusTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
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
            true
        }
}
