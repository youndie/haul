package io.github.youndie.haul.db

import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.testing.PostgresHarness
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * V27 gives a deal written before it a window (B-57): a deal of the day, opening a day before it ends
 * (research D7). The seed writes the same window for its deals of the canvas's day (`CatalogSeed.DEALS_START`),
 * and this is what holds the two together — a stand migrated from V26 and a fresh seed must price the same
 * deals at the same instants, or one of them sells a deal the other does not.
 */
class DealWindowMigrationTest {
    @Test
    fun `a deal written before V27 opens a day before it ends`() =
        PostgresHarness.freshDatabase(upTo = "26").use { dataSource ->
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(
                        "INSERT INTO categories (slug, parent_slug, name, position, tone, label) " +
                            "VALUES ('c', NULL, 'C', 0, '#FFFFFF', 'c')",
                    )
                    statement.execute(
                        "INSERT INTO sellers (id, name, rating, positive_percent, years_on_haul) VALUES ('s', 'S', 4.5, 90, 1)",
                    )
                    statement.execute(
                        "INSERT INTO products ($PRODUCT_COLUMNS) " +
                            "VALUES ('p', 's', 'c', 'p', 'b', 'd', '[]', 4.5, 0, 0, '#FFFFFF', 'l', now(), 'p')",
                    )
                    statement.execute(
                        "INSERT INTO skus (id, product_id, position, options, price_cents, stock) " +
                            "VALUES ('p-0', 'p', 0, '{}', 44900, 3)",
                    )
                    statement.execute(
                        "INSERT INTO deals (id, sku_id, price_cents, ends_at) " +
                            "VALUES ('${SampleCatalog.SONY_DEAL.id}', 'p-0', 34900, '${CatalogSeed.DEALS_END}')",
                    )
                }
                connection.commit()
            }

            Databases.migrate(dataSource)

            val window =
                dataSource.connection.use { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT starts_at, ends_at FROM deals").use { rows ->
                            rows.next()
                            rows.getObject(1, OffsetDateTime::class.java) to
                                rows.getObject(2, OffsetDateTime::class.java)
                        }
                    }
                }
            assertEquals(CatalogSeed.DEALS_END.toInstant(), window.second.toInstant(), "V27 moved the deal's end")
            assertEquals(
                SampleCatalog.SONY_DEAL.startsAt.toInstant(),
                window.first.toInstant(),
                "V27 and the seed open the canvas's deals at different instants",
            )
        }

    private companion object {
        /** A product row as V26 has it, without the columns that have defaults. */
        const val PRODUCT_COLUMNS =
            "id, seller_id, category_slug, title, brand, description, specifications, rating, reviews_count, " +
                "questions_count, tone, label, created_at, headline"
    }
}
