package io.github.youndie.haul.db

import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.RealNames
import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.seed.SeedDigest
import io.github.youndie.haul.seed.Seeder
import io.github.youndie.haul.testing.PostgresHarness
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * B-166: the seed renamed its real brands, and V31 renames a catalogue seeded before it the same way, ids included —
 * a stand keeps its database across deploys. The names are written twice, in the migration's SQL and in the seed;
 * this holds them together: a catalogue seeded the old way ([RealNames.old]) and migrated is, row for row, the one a
 * fresh seed writes, and what the stand's orders, sagas and votes name follows it.
 */
class InventedBrandsMigrationTest {
    private val catalog = CatalogSeed.generate(CatalogSeed.CANVAS_DAY)

    @Test
    fun `a catalogue seeded before V31 is the one a fresh seed writes`() {
        val fresh =
            PostgresHarness.freshDatabase().use {
                assertTrue(Seeder.seedIfEmpty(Databases.connect(it), catalog))
                SeedDigest.of(it)
            }
        PostgresHarness.freshDatabase(upTo = "30").use { dataSource ->
            assertTrue(Seeder.seedIfEmpty(Databases.connect(dataSource), RealNames.old(catalog)))
            val before = SeedDigest.of(dataSource)
            assertTrue(dataSource.ids("products").containsAll(RealNames.products.keys), "not seeded the old way")
            // Positive control: the old catalogue is not the new one, so «the same hash» is the migration's doing.
            assertNotEquals(fresh, before, "the old catalogue hashes as the new one")

            Databases.migrate(dataSource)

            assertEquals(fresh, SeedDigest.of(dataSource), "a migrated catalogue is not the one a fresh seed writes")
        }
    }

    @Test
    fun `what a stand's orders name follows the renamed rows`() =
        PostgresHarness.freshDatabase(upTo = "30").use { dataSource ->
            assertTrue(
                Seeder.seedIfEmpty(Databases.connect(dataSource), RealNames.old(catalog), generatedReviews = false),
            )
            val hero = SampleCatalog.SONORA_HEADPHONES
            val sku = RealNames.old("$hero-0")
            val seller = RealNames.old(SampleCatalog.SONORA_STORE)
            val listing = RealNames.old(catalog.products.single { it.id == hero }.listingName!!)
            val title = RealNames.old(catalog.products.single { it.id == hero }.title)
            val review = RealNames.old("r-sonora-aisha")
            dataSource.execute(
                """
                INSERT INTO orders (id, saga_id, customer_id, status, method, payment, items_cents, discount_cents,
                                    delivery_cents, total_cents, points, placed_at)
                VALUES ('HL-1', 'saga-1', 'maya', 'placed', 'courier', 'card-4821', 34900, 0, 0, 34900, 349, now());
                INSERT INTO order_lines (order_id, position, sku_id, seller_id, title, quantity, price_cents, list_cents)
                VALUES ('HL-1', 0, '$sku', '$seller', '$listing', 1, 34900, 44900),
                       ('HL-1', 1, '$sku', '$seller', '$title', 1, 34900, 44900);
                INSERT INTO shipments (id, order_id, seller_id, position, status) VALUES ('HL-1-1', 'HL-1', '$seller', 0, 'placed');
                INSERT INTO stock_reservations (holder, sku_id, quantity, reserved_at) VALUES ('HL-1', '$sku', 1, now());
                INSERT INTO helpful_votes (review_id, customer_id, voted_at) VALUES ('$review', 'maya', now());
                INSERT INTO petiches (id, type, current_phase, current_interceptor_index, status, payload, enriched_payload, version)
                VALUES ('saga-1', 'order', 'p', 0, 'RUNNING',
                        '{"sku":"$sku","seller":"$seller","title":"$listing"}', '{"skus":["$sku"]}', 1);
                INSERT INTO outbox_events (id, type, payload, created_at) VALUES ('e-1', 't', '{"sku":"$sku"}', 0);
                """.trimIndent(),
            )

            Databases.migrate(dataSource)

            val newListing = catalog.products.single { it.id == hero }.listingName!!
            val newTitle = catalog.products.single { it.id == hero }.title
            val store = SampleCatalog.SONORA_STORE
            assertEquals(
                listOf("$hero-0 $store $newListing", "$hero-0 $store $newTitle"),
                dataSource.rows("SELECT sku_id || ' ' || seller_id || ' ' || title FROM order_lines ORDER BY position"),
            )
            assertEquals(listOf(store), dataSource.rows("SELECT seller_id FROM shipments"))
            assertEquals(listOf("$hero-0"), dataSource.rows("SELECT sku_id FROM stock_reservations"))
            assertEquals(listOf("r-sonora-aisha"), dataSource.rows("SELECT review_id FROM helpful_votes"))
            assertEquals(
                listOf("""{"sku":"$hero-0","seller":"$store","title":"$newListing"} {"skus":["$hero-0"]}"""),
                dataSource.rows("SELECT payload::text || ' ' || enriched_payload::text FROM petiches"),
            )
            assertEquals(listOf("""{"sku":"$hero-0"}"""), dataSource.rows("SELECT payload FROM outbox_events"))
            val left =
                dataSource
                    .rows(
                        "SELECT id FROM products UNION ALL SELECT id FROM sellers UNION ALL SELECT id FROM skus " +
                            "UNION ALL SELECT id FROM reviews UNION ALL SELECT id FROM questions",
                    ).filter { RealNames.found.containsMatchIn(it) }
            assertEquals(emptyList(), left, "ids V31 left as they were")
        }

    private fun DataSource.execute(sql: String) =
        connection.use { connection ->
            connection.createStatement().use { it.execute(sql) }
            if (!connection.autoCommit) connection.commit()
        }

    private fun DataSource.rows(sql: String): List<String> =
        connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
            }
        }

    private fun DataSource.ids(table: String): List<String> = rows("SELECT id FROM $table")
}
