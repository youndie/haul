package io.github.youndie.haul.db

import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.testing.PostgresHarness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * V23 gives a catalogue seeded before it the bases the seed gives the sample products (B-52), so a stand that
 * was already running reads «12K bought this month» as a fresh one does. The numbers are written twice — in
 * the migration's SQL and in `SampleCatalog` — and this is what holds them together: a base changed in one
 * place only fails here instead of making the running stand and a fresh seed disagree.
 */
class BoughtBaseMigrationTest {
    @Test
    fun `a catalogue seeded before V23 gets the sample products' bases`() =
        PostgresHarness.freshDatabase(upTo = "21").use { dataSource ->
            val samples = SampleCatalog.products.filter { it.boughtBase > 0 }.map { it.id }
            assertTrue(samples.isNotEmpty(), "no sample product carries a base")
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(
                        "INSERT INTO categories (slug, parent_slug, name, position, tone, label) " +
                            "VALUES ('c', NULL, 'C', 0, '#FFFFFF', 'c')",
                    )
                    statement.execute(
                        "INSERT INTO sellers (id, name, rating, positive_percent, years_on_haul) VALUES ('s', 'S', 4.5, 90, 1)",
                    )
                    (samples + OTHER).forEach { id ->
                        statement.execute(
                            "INSERT INTO products ($PRODUCT_COLUMNS) " +
                                "VALUES ('$id', 's', 'c', '$id', 'b', 'd', '[]', 4.5, 0, 0, '#FFFFFF', 'l', now(), '$id')",
                        )
                    }
                }
                connection.commit()
            }

            Databases.migrate(dataSource)

            val bases =
                dataSource.connection.use { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT id, bought_base FROM products").use { rows ->
                            buildMap { while (rows.next()) put(rows.getString(1), rows.getInt(2)) }
                        }
                    }
                }
            assertEquals(
                SampleCatalog.products.filter { it.id in samples }.associate { it.id to it.boughtBase } + (OTHER to 0),
                bases,
                "V23 and the seed give the sample products different bases",
            )
        }

    private companion object {
        /** A product the seed gives no base: the migration must leave it at 0. */
        const val OTHER = "p-000-00"

        /** A product row as V21 has it, without the columns that have defaults. */
        const val PRODUCT_COLUMNS =
            "id, seller_id, category_slug, title, brand, description, specifications, rating, reviews_count, " +
                "questions_count, tone, label, created_at, headline"
    }
}
