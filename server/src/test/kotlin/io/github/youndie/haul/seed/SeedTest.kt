package io.github.youndie.haul.seed

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.testing.PostgresHarness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SeedTest {
    /**
     * B-03's acceptance: two fresh databases seeded by the same build hold the same rows. A seed that
     * drifts — a clock read, an unordered map, a second `Random` — makes every screenshot and every
     * scenario that reads the catalog differ between machines, and nothing else would say why.
     */
    @Test
    fun `a fresh database is seeded to the same hash twice`() {
        val first = PostgresHarness.freshDatabase()
        val second = PostgresHarness.freshDatabase()

        assertTrue(Seeder.seedIfEmpty(Databases.connect(first), CatalogSeed.generate()))
        assertTrue(Seeder.seedIfEmpty(Databases.connect(second), CatalogSeed.generate()))

        val digest = SeedDigest.of(first)
        assertEquals(digest, SeedDigest.of(second), "two seeds of a fresh database differ")
        // Positive control: the hash is over rows, not over two empty catalogs.
        assertNotEquals(
            SeedDigest.of(PostgresHarness.freshDatabase()),
            digest,
            "the digest does not see the seeded rows",
        )
    }

    /** Every replica seeds on start; the second one must find the catalog and leave it alone. */
    @Test
    fun `a second seed of the same database inserts nothing`() {
        val dataSource = PostgresHarness.freshDatabase()
        val database = Databases.connect(dataSource)
        assertTrue(Seeder.seedIfEmpty(database, CatalogSeed.generate()))
        val digest = SeedDigest.of(dataSource)

        assertFalse(Seeder.seedIfEmpty(database, CatalogSeed.generate()), "the second seed reported inserting")
        assertEquals(digest, SeedDigest.of(dataSource), "the second seed changed the catalog")
    }

    /** The sample data of research §6 is what every artboard shows; the generator must not lose it. */
    @Test
    fun `the generated catalog carries the sample products at their canvas prices`() {
        val catalog = CatalogSeed.generate()
        val headphones = catalog.skus.first { it.id == "${SampleCatalog.SONY_HEADPHONES}-0" }
        assertEquals(34_900, headphones.priceCents)
        assertEquals(44_900, headphones.oldPriceCents)
        assertTrue(
            catalog.skus
                .filter {
                    it.productId == SampleCatalog.SONY_HEADPHONES &&
                        it.options.toString().contains("Silver")
                }.all { it.stock == 0 },
        )
        assertTrue(catalog.products.size in 1_900..2_100, "about two thousand products, got ${catalog.products.size}")
        assertEquals(
            catalog.products.size,
            catalog.products
                .map { it.id }
                .toSet()
                .size,
            "product ids collide",
        )
        assertEquals(
            catalog.categories.size,
            catalog.categories
                .map { it.slug }
                .toSet()
                .size,
            "category slugs collide",
        )
    }
}
