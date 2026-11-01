package io.github.youndie.haul.db

import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.Seeder
import io.github.youndie.haul.seed.campaignsBeforeV34
import io.github.youndie.haul.testing.PostgresHarness
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-152: the seed wrote a generated product's leaf in lower case inside its description, an initialism with it —
 * «Travel tvs by Northwind.». The seed now keeps «TVs» as it is written, and V30 writes the same into a catalog seeded
 * before; this holds the two together, so a stand migrated from V29 and a fresh seed describe their products alike.
 */
class LeafNameMigrationTest {
    private val catalog = CatalogSeed.generate(CatalogSeed.CANVAS_DAY)

    @Test
    fun `a generated description keeps an initialism and lowers the rest`() {
        val tvs = catalog.products.filter { it.categorySlug == TVS }
        assertTrue(tvs.isNotEmpty(), "the seed generates no TVs")
        tvs.forEach { assertTrue(Regex("^[A-Za-z]+ TVs by [^.]+\\.$").matches(it.description), it.description) }
        val mugs = catalog.products.filter { it.categorySlug == "mugs" && it.description.endsWith(" by ${it.brand}.") }
        assertTrue(mugs.isNotEmpty(), "the seed generates no mugs")
        mugs.forEach { assertTrue(" mugs by " in it.description, it.description) }
    }

    @Test
    fun `a catalog seeded before V30 describes its TVs as the seed does`() =
        PostgresHarness.freshDatabase(upTo = "29").use { dataSource ->
            Seeder.seedIfEmpty(Databases.connect(dataSource), catalog.campaignsBeforeV34())
            // What the seed wrote before B-152: the whole leaf in lower case.
            dataSource.execute("UPDATE products SET description = replace(description, ' TVs by ', ' tvs by ')")
            val before = dataSource.descriptions()
            assertTrue(before.any { (_, text) -> " tvs by " in text }, "no description written the old way")

            Databases.migrate(dataSource)

            assertEquals(catalog.products.associate { it.id to it.description }, dataSource.descriptions())
        }

    private fun DataSource.execute(sql: String) =
        connection.use { connection ->
            connection.createStatement().use { it.executeUpdate(sql) }
            if (!connection.autoCommit) connection.commit()
        }

    private fun DataSource.descriptions(): Map<String, String> =
        connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT id, description FROM products").use { rows ->
                    buildMap { while (rows.next()) put(rows.getString(1), rows.getString(2)) }
                }
            }
        }

    private companion object {
        const val TVS = "tvs"
    }
}
