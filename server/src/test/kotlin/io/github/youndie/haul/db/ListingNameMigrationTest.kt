package io.github.youndie.haul.db

import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.testing.PostgresHarness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * V25 gives a catalogue seeded before it the listing names the seed gives the sample products (B-45), so a
 * stand that was already running writes «Sony WH-1000XM6 …» on its cards as a fresh one does. The names are
 * written twice — in the migration's SQL and in `SampleCatalog` — and this is what holds them together: a
 * name changed in one place only fails here instead of making the running stand and a fresh seed disagree.
 */
class ListingNameMigrationTest {
    @Test
    fun `a catalogue seeded before V25 gets the sample products' listing names`() =
        PostgresHarness.freshDatabase(upTo = "23").use { dataSource ->
            val named = SampleCatalog.products.filter { it.listingName != null }.associate { it.id to it.listingName }
            assertTrue(named.isNotEmpty(), "no sample product carries a listing name")
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(
                        "INSERT INTO categories (slug, parent_slug, name, position, tone, label) " +
                            "VALUES ('c', NULL, 'C', 0, '#FFFFFF', 'c')",
                    )
                    statement.execute(
                        "INSERT INTO sellers (id, name, rating, positive_percent, years_on_haul) VALUES ('s', 'S', 4.5, 90, 1)",
                    )
                    (named.keys + OTHER).forEach { id ->
                        statement.execute(
                            "INSERT INTO products ($PRODUCT_COLUMNS) " +
                                "VALUES ('$id', 's', 'c', '$id', 'b', 'd', '[]', 4.5, 0, 0, '#FFFFFF', 'l', now(), '$id')",
                        )
                    }
                }
                connection.commit()
            }

            Databases.migrate(dataSource)

            val names =
                dataSource.connection.use { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT id, listing_name FROM products").use { rows ->
                            buildMap { while (rows.next()) put(rows.getString(1), rows.getString(2)) }
                        }
                    }
                }
            assertEquals(
                named + (OTHER to null),
                names,
                "V25 and the seed give the sample products different listing names",
            )
        }

    private companion object {
        /** A product the seed names no listing name: the migration must leave it to its title. */
        const val OTHER = "p-000-00"

        /** A product row as V23 has it, without the columns that have defaults. */
        const val PRODUCT_COLUMNS =
            "id, seller_id, category_slug, title, brand, description, specifications, rating, reviews_count, " +
                "questions_count, tone, label, created_at, headline"
    }
}
