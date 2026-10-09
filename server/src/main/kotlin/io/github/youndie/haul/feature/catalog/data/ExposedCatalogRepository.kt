package io.github.youndie.haul.feature.catalog.data

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.feature.catalog.domain.Campaign
import io.github.youndie.haul.feature.catalog.domain.CampaignPricing
import io.github.youndie.haul.feature.catalog.domain.CampaignWindow
import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.domain.Category
import io.github.youndie.haul.feature.catalog.domain.Deal
import io.github.youndie.haul.feature.catalog.domain.Listed
import io.github.youndie.haul.feature.catalog.domain.PriceList
import io.github.youndie.haul.feature.catalog.domain.Product
import io.github.youndie.haul.feature.catalog.domain.Seller
import io.github.youndie.haul.feature.catalog.domain.Sku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * The catalog over Exposed. JDBC blocks, so every read runs on the IO dispatcher. SKUs are read with
 * their campaign's window and their deals, and priced for the caller's [PriceList] at the store's
 * [clock] (B-53, B-57, B-58), here and nowhere else, so every screen and command that reads a SKU reads the
 * same price — a deal card's included.
 */
internal class ExposedCatalogRepository(
    private val database: Database,
    private val clock: StoreClock,
) : CatalogRepository {
    private suspend fun <T> read(block: () -> T): T = withContext(Dispatchers.IO) { transaction(database) { block() } }

    override suspend fun categories(): List<Category> =
        read {
            CategoriesTable.selectAll().map {
                Category(
                    it[CategoriesTable.slug],
                    it[CategoriesTable.parentSlug],
                    it[CategoriesTable.name],
                    it[CategoriesTable.position],
                    it[CategoriesTable.tone],
                    it[CategoriesTable.label],
                )
            }
        }

    override suspend fun listedIn(
        categorySlugs: Set<String>,
        prices: PriceList,
    ): List<Listed> =
        read {
            withSkus(
                ProductsTable.selectAll().where { ProductsTable.categorySlug inList categorySlugs }.map(::product),
                prices,
            )
        }

    override suspend fun listed(
        productIds: List<String>,
        prices: PriceList,
    ): List<Listed> =
        read {
            val byId =
                withSkus(
                    ProductsTable
                        .selectAll()
                        .where {
                            ProductsTable.id inList productIds
                        }.map(::product),
                    prices,
                ).associateBy { it.product.id }
            productIds.mapNotNull(byId::get)
        }

    override suspend fun listedBySkus(
        skuIds: Set<String>,
        prices: PriceList,
    ): List<Listed> =
        read {
            if (skuIds.isEmpty()) return@read emptyList()
            val productIds =
                SkusTable
                    .select(SkusTable.productId)
                    .where { SkusTable.id inList skuIds }
                    .map { it[SkusTable.productId] }
                    .distinct()
            withSkus(ProductsTable.selectAll().where { ProductsTable.id inList productIds }.map(::product), prices)
        }

    override suspend fun product(
        id: String,
        prices: PriceList,
    ): Listed? =
        read {
            withSkus(ProductsTable.selectAll().where { ProductsTable.id eq id }.map(::product), prices).singleOrNull()
        }

    override suspend fun seller(id: String): Seller? =
        read {
            SellersTable.selectAll().where { SellersTable.id eq id }.singleOrNull()?.let {
                Seller(
                    it[SellersTable.id],
                    it[SellersTable.name],
                    it[SellersTable.rating],
                    it[SellersTable.positivePercent],
                    it[SellersTable.yearsOnHaul],
                )
            }
        }

    override suspend fun campaigns(): List<Campaign> =
        read {
            CampaignsTable.selectAll().orderBy(CampaignsTable.position).map {
                Campaign(
                    it[CampaignsTable.slug],
                    it[CampaignsTable.title],
                    it[CampaignsTable.subtitle],
                    it[CampaignsTable.position],
                    it[CampaignsTable.tone],
                    it[CampaignsTable.startsAt],
                    it[CampaignsTable.endsAt],
                    it[CampaignsTable.plusEarlyAccessAt],
                )
            }
        }

    override suspend fun deals(): List<Deal> =
        read {
            val at = clock.now().toOffsetDateTime()
            DealsTable
                .selectAll()
                .orderBy(DealsTable.id)
                .map(::deal)
                .filter { it.liveAt(at) }
        }

    private fun withSkus(
        products: List<Product>,
        prices: PriceList,
    ): List<Listed> {
        if (products.isEmpty()) return emptyList()
        val at = clock.now().toOffsetDateTime()
        val rows =
            SkusTable
                .join(CampaignsTable, JoinType.LEFT, SkusTable.campaignSlug, CampaignsTable.slug)
                .selectAll()
                .where { SkusTable.productId inList products.map { it.id } }
                .toList()
        val deals =
            DealsTable
                .selectAll()
                .where { DealsTable.skuId inList rows.map { it[SkusTable.id] } }
                .map(::deal)
                .groupBy { it.skuId }
        val skus =
            rows
                .map { row ->
                    val sku = sku(row)
                    CampaignPricing.priced(sku, window(row), deals[sku.id].orEmpty(), prices, at)
                }.groupBy { it.productId }
        return products.mapNotNull { product ->
            skus[product.id]?.sortedBy { it.position }?.let { Listed(product, it) }
        }
    }

    private fun product(row: ResultRow): Product =
        Product(
            id = row[ProductsTable.id],
            sellerId = row[ProductsTable.sellerId],
            categorySlug = row[ProductsTable.categorySlug],
            title = row[ProductsTable.title],
            brand = row[ProductsTable.brand],
            description = row[ProductsTable.description],
            specifications =
                row[ProductsTable.specifications].map {
                    val pair = it.jsonObject
                    pair.getValue("key").jsonPrimitive.content to pair.getValue("value").jsonPrimitive.content
                },
            rating = row[ProductsTable.rating],
            reviewsCount = row[ProductsTable.reviewsCount],
            questionsCount = row[ProductsTable.questionsCount],
            tone = row[ProductsTable.tone],
            label = row[ProductsTable.label],
            features = row[ProductsTable.features].map { it.jsonPrimitive.content },
            kind = row[ProductsTable.kind],
            dispatchDays = row[ProductsTable.dispatchDays],
            createdAt = row[ProductsTable.createdAt],
            headline = row[ProductsTable.headline],
            headlineAccent = row[ProductsTable.headlineAccent],
            imageKey = row[ProductsTable.imageKey],
            boughtBase = row[ProductsTable.boughtBase],
            listingName = row[ProductsTable.listingName] ?: row[ProductsTable.title],
        )

    private fun deal(row: ResultRow): Deal =
        Deal(
            id = row[DealsTable.id],
            skuId = row[DealsTable.skuId],
            priceCents = row[DealsTable.priceCents],
            startsAt = row[DealsTable.startsAt],
            endsAt = row[DealsTable.endsAt],
        )

    /** The window of the SKU's campaign, from the joined row; none for a SKU in no campaign. */
    private fun window(row: ResultRow): CampaignWindow? =
        row[SkusTable.campaignSlug]?.let {
            CampaignWindow(
                startsAt = row[CampaignsTable.startsAt],
                endsAt = row[CampaignsTable.endsAt],
                plusEarlyAccessAt = row[CampaignsTable.plusEarlyAccessAt],
            )
        }

    private fun sku(row: ResultRow): Sku =
        Sku(
            id = row[SkusTable.id],
            productId = row[SkusTable.productId],
            position = row[SkusTable.position],
            options = row[SkusTable.optionValues].mapValues { it.value.jsonPrimitive.content },
            priceCents = row[SkusTable.priceCents],
            oldPriceCents = row[SkusTable.oldPriceCents],
            stock = row[SkusTable.stock],
        )
}
