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
import io.github.youndie.haul.feature.membership.data.MembershipsTable
import io.github.youndie.haul.feature.membership.data.PointsEntriesTable
import io.github.youndie.haul.feature.membership.domain.PointsMovement
import io.github.youndie.haul.feature.recommendations.data.ProductViewsTable
import io.github.youndie.haul.feature.reviews.data.QuestionsTable
import io.github.youndie.haul.feature.reviews.data.RatingCountsTable
import io.github.youndie.haul.feature.reviews.data.ReviewsTable
import io.github.youndie.haul.feature.saved.data.SavedItemsTable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

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

    /**
     * Returns whether it seeded; `false` means the catalog already had rows. The sample customers' Plus
     * memberships and opening points ([seedLoyalty], B-23) are written either way, each once: a database
     * seeded before they existed gets them on its next start.
     */
    fun seedIfEmpty(
        database: Database,
        catalog: SeedCatalog,
    ): Boolean =
        transaction(database) {
            exec("SELECT pg_advisory_xact_lock($LOCK_KEY)")
            if (!CategoriesTable.selectAll().empty()) {
                seedLoyalty(catalog)
                return@transaction false
            }

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
                this[ProductsTable.boughtBase] = it.boughtBase
                this[ProductsTable.listingName] = it.listingName
            }
            // Campaigns before the SKUs, which name them (V26).
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
            SkusTable.batchInsert(catalog.skus) {
                this[SkusTable.id] = it.id
                this[SkusTable.productId] = it.productId
                this[SkusTable.position] = it.position
                this[SkusTable.optionValues] = it.options
                this[SkusTable.priceCents] = it.priceCents
                this[SkusTable.oldPriceCents] = it.oldPriceCents
                this[SkusTable.stock] = it.stock
                this[SkusTable.campaignSlug] = it.campaignSlug
            }
            DealsTable.batchInsert(catalog.deals) {
                this[DealsTable.id] = it.id
                this[DealsTable.skuId] = it.skuId
                this[DealsTable.priceCents] = it.priceCents
                this[DealsTable.startsAt] = it.startsAt
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
            ReviewsTable.batchInsert(catalog.reviews) {
                this[ReviewsTable.id] = it.id
                this[ReviewsTable.productId] = it.productId
                this[ReviewsTable.customerId] = it.customerId
                this[ReviewsTable.author] = it.author
                this[ReviewsTable.rating] = it.rating
                this[ReviewsTable.title] = it.title
                this[ReviewsTable.body] = it.body
                this[ReviewsTable.verified] = it.verified
                this[ReviewsTable.helpful] = it.helpful
                this[ReviewsTable.tone] = it.tone
                this[ReviewsTable.photos] = JsonArray(it.photos.map(::JsonPrimitive))
                this[ReviewsTable.createdAt] = it.createdAt
            }
            RatingCountsTable.batchInsert(
                catalog.ratingCounts.flatMap { (product, counts) ->
                    counts.map { (stars, n) -> Triple(product, stars, n) }
                },
            ) { (product, stars, n) ->
                this[RatingCountsTable.productId] = product
                this[RatingCountsTable.stars] = stars
                this[RatingCountsTable.count] = n
            }
            SavedItemsTable.batchInsert(catalog.saved) {
                this[SavedItemsTable.customerId] = it.customerId
                this[SavedItemsTable.productId] = it.productId
                this[SavedItemsTable.savedPriceCents] = it.savedPriceCents
                this[SavedItemsTable.savedAt] = it.savedAt
            }
            ProductViewsTable.batchInsert(catalog.views) {
                this[ProductViewsTable.customerId] = it.customerId
                this[ProductViewsTable.productId] = it.productId
                this[ProductViewsTable.viewedAt] = it.viewedAt
            }
            QuestionsTable.batchInsert(catalog.questions) {
                this[QuestionsTable.id] = it.id
                this[QuestionsTable.productId] = it.productId
                this[QuestionsTable.customerId] = it.customerId
                this[QuestionsTable.text] = it.text
                this[QuestionsTable.askedAt] = it.askedAt
                this[QuestionsTable.answer] = it.answer
                this[QuestionsTable.answeredAt] = it.answeredAt
            }
            seedLoyalty(catalog)
            true
        }

    /**
     * Moves the sample sale to [catalog]'s day on a catalogue seeded on an earlier one (B-58), and returns
     * whether it did. [seedIfEmpty] writes a fresh catalogue's sale for the day it seeds, but a stand keeps
     * its database across deploys, and its sale — a day of deals inside an eight-day campaign — would be over
     * for good a week after the first start.
     *
     * The sample sale is the seed's own rows and nothing else: the campaigns by their slug, the deals by
     * their id **and** their SKU; any other campaign or deal is not read. They move only when every sample
     * deal has ended by the start of [catalog]'s deals — never in the middle of a live deal, so a price on
     * screen does not change under a restart, and at most once a day — and then all of them at once, to
     * exactly the windows a fresh seed on that day writes. Prices, carts and orders are not touched: a cart
     * line put in at an ended price is a changed price, as any price is (B-11).
     *
     * Called by `main` only when it seeds (`HAUL_SEED`): a database that was never seeded is never re-dated.
     * Takes the seeding lock, so replicas starting together re-date once.
     */
    fun redateSale(
        database: Database,
        catalog: SeedCatalog,
    ): Boolean =
        transaction(database) {
            exec("SELECT pg_advisory_xact_lock($LOCK_KEY)")
            val opens = catalog.deals.minOf { it.startsAt }
            val seeded = catalog.deals.map { it.id to it.skuId }.toSet()
            val sample =
                DealsTable
                    .selectAll()
                    .where { DealsTable.id inList catalog.deals.map { it.id } }
                    .filter { (it[DealsTable.id] to it[DealsTable.skuId]) in seeded }
            if (sample.isEmpty() || sample.any { it[DealsTable.endsAt].isAfter(opens) }) return@transaction false
            catalog.campaigns.forEach { campaign ->
                CampaignsTable.update({ CampaignsTable.slug eq campaign.slug }) {
                    it[startsAt] = campaign.startsAt
                    it[endsAt] = campaign.endsAt
                    it[plusEarlyAccessAt] = campaign.plusEarlyAccessAt
                }
            }
            catalog.deals.forEach { deal ->
                DealsTable.update({ (DealsTable.id eq deal.id) and (DealsTable.skuId eq deal.skuId) }) {
                    it[startsAt] = deal.startsAt
                    it[endsAt] = deal.endsAt
                }
            }
            true
        }

    /**
     * The memberships and the opening balances of [catalog], for the customers that exist: a membership
     * the customer has already, or an opening balance written before — spent since, perhaps — is left as it
     * is (the membership's key is the customer, the balance's its ledger key).
     */
    private fun seedLoyalty(catalog: SeedCatalog) {
        val known =
            CustomersTable
                .select(CustomersTable.id)
                .where { CustomersTable.id inList catalog.customers.map { it.id } }
                .map { it[CustomersTable.id] }
                .toSet()
        catalog.memberships.filter { it.customerId in known }.forEach { membership ->
            MembershipsTable.insertIgnore {
                it[customerId] = membership.customerId
                it[startedAt] = membership.startedAt
                it[trial] = membership.trial
                it[paidFrom] = membership.paidFrom
                it[carriedSavingsCents] = membership.carriedSavingsCents
                it[carriedSavingsYear] = membership.carriedSavingsYear
            }
        }
        catalog.openingPoints.filterKeys { it in known }.forEach { (customer, points) ->
            val opening = PointsMovement.opening(customer, points, CatalogSeed.NOW)
            PointsEntriesTable.insertIgnore {
                it[key] = opening.key
                it[customerId] = opening.customerId
                it[kind] = opening.kind.id
                it[PointsEntriesTable.points] = opening.points
                it[at] = opening.at
            }
        }
    }

    /** Maya's address was saved long before the canvas's «now». */
    private const val ADDRESS_AGE_DAYS = 365L
}
