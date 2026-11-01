package io.github.youndie.haul.seed

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.testing.PostgresHarness
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-166: the store sells invented brands from invented sellers. The About page says «No brand sells here»; the seed
 * sold real companies' headphones under their names, from stores named after them, beside invented reviews. This
 * looks for the names B-166 removed ([RealNames.found]) everywhere the store and its goldens take words from: every
 * row a fresh seed writes, the server's code, and the bodies and fixtures the client's goldens are drawn from.
 */
class InventedBrandsTest {
    @Test
    fun `no row a fresh seed writes names a real brand`() =
        PostgresHarness.freshDatabase().use { dataSource ->
            assertTrue(Seeder.seedIfEmpty(Databases.connect(dataSource), CatalogSeed.generate(CatalogSeed.CANVAS_DAY)))
            val found = mutableListOf<String>()
            var cells = 0
            dataSource.connection.use { connection ->
                val tables =
                    connection.createStatement().use { statement ->
                        statement
                            .executeQuery(
                                "SELECT table_name FROM information_schema.tables " +
                                    "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                            ).use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
                    }
                tables.forEach { table ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT * FROM $table").use { rows ->
                            while (rows.next()) {
                                (1..rows.metaData.columnCount).forEach { column ->
                                    val text = rows.getString(column) ?: return@forEach
                                    cells++
                                    RealNames.found.find(text)?.let {
                                        found +=
                                            "$table: «${it.value}» in «${text.take(120)}»"
                                    }
                                }
                            }
                        }
                    }
                }
                connection.rollback()
            }
            // Positive control: the seed's rows were read, the generated reviews among them.
            assertTrue(cells > 500_000, "read $cells cells")
            assertEquals(emptyList(), found.distinct().take(20), "the seed names a real brand")
        }

    @Test
    fun `the server's code and the client's bodies and fixtures name no real brand`() {
        val bodies = File(System.getProperty("haul.clientBodies") ?: error("haul.clientBodies is not set"))
        val roots =
            listOf(
                File("src/main/kotlin"),
                bodies,
                // The client's fixtures beside the bodies: composeApp/src/desktopTest/{resources/bodies,kotlin}.
                bodies.resolve("../../kotlin").normalize(),
            )
        val files = roots.flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension in TEXT }.toList() }
        // Positive control: the walk found the trees it names, not three empty directories.
        roots.forEach { root -> assertTrue(files.count { it.startsWith(root) } > 10, "nothing read under $root") }
        val found =
            files.flatMap { file ->
                file.readLines().mapIndexedNotNull { n, line ->
                    RealNames.found.find(line)?.let { "${file.path}:${n + 1}: «${it.value}»" }
                }
            }
        assertEquals(emptyList(), found, "a real brand is named")
    }

    @Test
    fun `the names looked for are the ones removed`() {
        // Negative control for the two tests above: each old name is found, and its invented one is not.
        RealNames.products.keys.forEach { assertTrue(RealNames.found.containsMatchIn(it), it) }
        RealNames.sellers.keys.forEach { assertTrue(RealNames.found.containsMatchIn(it), it) }
        listOf("JBL", "Apple", "Sennheiser", "Bluetooth 5.4 · LDAC · multipoint", "Sony WH-1000XM6").forEach {
            assertTrue(RealNames.found.containsMatchIn(it), it)
        }
        (RealNames.products.values + RealNames.sellers.values + listOf("apple pie", "Major version")).forEach {
            assertTrue(!RealNames.found.containsMatchIn(it), it)
        }
    }

    /**
     * A renamed product keeps what its id chose: [SeedArt] picks its picture's variant from the id's hash (3 of
     * them), [SeedReviews] the copy its generated reviews start from (997 salts), and lists break ties by the id. So
     * each new id has its old id's `String.hashCode()` modulo 2,991 and sits where the old one sat among every
     * other id — which is why the invented model numbers are what they are — and B-166 changed words, not pictures,
     * reviews or orders.
     */
    @Test
    fun `a renamed product keeps its picture, its reviews' copy and its place`() {
        RealNames.products.forEach { (old, new) ->
            assertEquals(Math.floorMod(old.hashCode(), HASHES), Math.floorMod(new.hashCode(), HASHES), "$old → $new")
        }
        val catalog = CatalogSeed.generate(CatalogSeed.CANVAS_DAY)
        val renamed = RealNames.products.values.toSet()
        val oldSkus = catalog.skus.map { it.copy(productId = RealNames.old(it.productId)) }
        catalog.products.filter { it.id in renamed }.forEach { product ->
            val old = product.copy(id = RealNames.old(product.id))
            assertEquals(SeedPhotos.draw(old, oldSkus), SeedPhotos.draw(product, catalog.skus), product.id)
        }
        val ids = catalog.products.map { it.id } + catalog.sellers.map { it.id }
        val olds = ids.associateWith(RealNames::old)
        assertEquals(ids.sortedBy { olds.getValue(it) }, ids.sorted(), "a renamed id moved among the others")
    }

    private companion object {
        val TEXT = setOf("kt", "json")

        /** [SeedArt]'s 3 picture variants times [SeedReviews]' 997 salts. */
        const val HASHES = 3 * 997
    }
}
