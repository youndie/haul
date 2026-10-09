package io.github.youndie.haul.feature.recommendations

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.catalog.data.ExposedCatalogRepository
import io.github.youndie.haul.feature.catalog.domain.PriceList
import io.github.youndie.haul.feature.recommendations.data.ExposedPickSources
import io.github.youndie.haul.feature.recommendations.data.ExposedProductViews
import io.github.youndie.haul.feature.recommendations.domain.ProductView
import io.github.youndie.haul.feature.recommendations.domain.ProductViews
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCatalog.DUVET_COVER
import io.github.youndie.haul.seed.SampleCatalog.SONY_HEADPHONES
import io.github.youndie.haul.seed.SampleCatalog.STONEWARE_MUG
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.seededFreshDatabase
import kotlinx.coroutines.runBlocking
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The storage under «Picked for you» (B-25) against PostgreSQL: what a view is (one per product, the newest
 * twenty), whose views a read returns, and what a customer bought.
 */
class ProductViewsTest {
    private val dataSource = seededFreshDatabase()
    private val database = Databases.connect(dataSource)
    private val views = ExposedProductViews(database)
    private val sources = ExposedPickSources(database)
    private val catalog = ExposedCatalogRepository(database, CANVAS_NOW)
    private val now = CatalogSeed.NOW

    @AfterTest
    fun close() = dataSource.close()

    private fun DataSource.sql(statement: String) {
        connection.use { c ->
            c.createStatement().use { it.executeUpdate(statement) }
            c.commit()
        }
    }

    private fun customer(id: String) =
        dataSource.sql("INSERT INTO customers (id, name, plus, created_at) VALUES ('$id', '$id', false, now())")

    /**
     * A view is per product: the product tree is fetched again on every tab, variant and refresh, and each
     * fetch counted as another view would make one product's category outvote three others.
     */
    @Test
    fun `a product viewed again is one view moved to the newest`() =
        runBlocking {
            customer("c-ann")
            views.record("c-ann", SONY_HEADPHONES, now)
            views.record("c-ann", DUVET_COVER, now.plusSeconds(1))
            views.record("c-ann", SONY_HEADPHONES, now.plusSeconds(2))
            assertEquals(
                listOf(ProductView(SONY_HEADPHONES, "headphones"), ProductView(DUVET_COVER, "duvet-covers")),
                views.recent("c-ann"),
            )
        }

    /** Research §5's «last 20 views»: the oldest goes when the twenty-first comes, and the table holds no more. */
    @Test
    fun `the newest twenty are kept`() =
        runBlocking {
            customer("c-bo")
            val products =
                catalog
                    .listedIn(setOf("headphones", "mugs"), PriceList.Public)
                    .map { it.product.id }
                    .sorted()
                    .take(22)
            products.forEachIndexed { i, id -> views.record("c-bo", id, now.plusSeconds(i.toLong())) }
            val kept = views.recent("c-bo").map { it.productId }
            assertEquals(products.drop(2).reversed(), kept, "not the twenty newest, newest first")
            val rows =
                dataSource.connection.use { c ->
                    c.createStatement().use { s ->
                        s.executeQuery("SELECT count(*) FROM product_views WHERE customer_id = 'c-bo'").use {
                            it.next()
                            it.getInt(1)
                        }
                    }
                }
            assertEquals(ProductViews.KEPT, rows, "the views past twenty were read past, not deleted")
        }

    /** The customer is the filter: a read never returns another customer's views. */
    @Test
    fun `one customer's views are not another's`() =
        runBlocking {
            customer("c-cy")
            customer("c-di")
            views.record("c-cy", STONEWARE_MUG, now)
            views.record("c-di", DUVET_COVER, now)
            assertEquals(listOf(DUVET_COVER), views.recent("c-di").map { it.productId })
            // Positive control: the other view was recorded, for the customer who made it.
            assertEquals(listOf(STONEWARE_MUG), views.recent("c-cy").map { it.productId })
        }

    /** The page runs the write beside its own reads, so a link to no product reaches the write: it stores nothing. */
    @Test
    fun `a product the catalog does not have is not recorded`() =
        runBlocking {
            customer("c-ed")
            views.record("c-ed", "p-no-such-product", now)
            assertEquals(emptyList(), views.recent("c-ed"))
            views.record("c-ed", STONEWARE_MUG, now)
            assertEquals(listOf(STONEWARE_MUG), views.recent("c-ed").map { it.productId })
        }

    /** «Neither viewed nor bought»: an order's products are bought unless the order was cancelled, and only its customer's. */
    @Test
    fun `bought is the customer's orders that were not cancelled`() =
        runBlocking {
            customer("c-fi")
            customer("c-gu")
            order("HL-00001", "c-fi", "placed", "$SONY_HEADPHONES-0")
            order("HL-00002", "c-fi", "cancelled", "$DUVET_COVER-0")
            order("HL-00003", "c-gu", "placed", "$STONEWARE_MUG-0")
            assertEquals(setOf(SONY_HEADPHONES), sources.bought("c-fi"))
            assertEquals(setOf(STONEWARE_MUG), sources.bought("c-gu"))
        }

    /** «Popular right now» is the catalog's own «Popular» order, over what can be bought. */
    @Test
    fun `popular is the most reviewed in stock without the excluded`() =
        runBlocking {
            val all = sources.popular(emptySet(), 10)
            assertEquals(10, all.size)
            val listed = catalog.listed(all, PriceList.Public)
            assertTrue(listed.all { it.inStock }, "an out-of-stock product is popular")
            assertEquals(listed.sortedByDescending { it.product.reviewsCount }.map { it.product.id }, all)
            assertEquals(all.drop(1).take(9), sources.popular(setOf(all.first()), 9))
        }

    private fun order(
        id: String,
        customerId: String,
        status: String,
        skuId: String,
    ) {
        dataSource.sql(
            "INSERT INTO orders (id, saga_id, customer_id, status, method, payment, items_cents, discount_cents, " +
                "delivery_cents, total_cents, points, placed_at) " +
                "VALUES ('$id', 's-$id', '$customerId', '$status', 'courier', 'card', 100, 0, 0, 100, 1, now())",
        )
        val sellerId = "(SELECT p.seller_id FROM skus s JOIN products p ON p.id = s.product_id WHERE s.id = '$skuId')"
        dataSource.sql(
            "INSERT INTO order_lines (order_id, position, sku_id, seller_id, title, quantity, price_cents, " +
                "list_cents) " +
                "VALUES ('$id', 0, '$skuId', $sellerId, 't', 1, 100, 100)",
        )
    }
}
