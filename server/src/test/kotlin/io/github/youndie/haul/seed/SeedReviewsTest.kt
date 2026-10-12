package io.github.youndie.haul.seed

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.reviews.domain.Ratings
import io.github.youndie.haul.seed.SampleCatalog.SONY_HEADPHONES
import io.github.youndie.haul.testing.PostgresHarness
import java.math.BigDecimal
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * B-99: a product's counts are what its page can list. The seed stores every review and question a product
 * counts — the canvas's 2,341 and 86 of the headphones included — and its histogram is its rows' stars; a
 * catalogue seeded before that (a stand's, which said 2,341 over two rows) gets the rows on its next start,
 * and the counts from them, a customer's own review counted once.
 */
class SeedReviewsTest {
    @Test
    fun `every product's counts and histogram are its stored rows`() =
        PostgresHarness.freshDatabase().use { dataSource ->
            assertTrue(Seeder.seedIfEmpty(Databases.connect(dataSource), CatalogSeed.generate(CatalogSeed.CANVAS_DAY)))

            assertEquals(emptyList(), dataSource.miscounted(), "products whose counts are not their rows")
            assertEquals(2_341 to 86, dataSource.counts(SONY_HEADPHONES))
            assertEquals(mapOf(5 to 1_966, 4 to 281, 3 to 47, 2 to 24, 1 to 23), dataSource.histogram(SONY_HEADPHONES))
            // The seed's counts are the ones stored: what the cards and the sorts read.
            val seeded =
                CatalogSeed.generate(CatalogSeed.CANVAS_DAY).products.associate {
                    it.id to
                        (it.reviewsCount to it.questionsCount)
                }
            assertEquals(seeded, dataSource.allCounts())
        }

    @Test
    fun `a catalogue seeded before the rows gets them and its counts from them`() =
        PostgresHarness.freshDatabase().use { dataSource ->
            val database = Databases.connect(dataSource)
            Seeder.seedIfEmpty(database, CatalogSeed.generate(CatalogSeed.CANVAS_DAY))
            val generated =
                CatalogSeed
                    .generate(CatalogSeed.CANVAS_DAY)
                    .products
                    .first { it.id.matches(Regex("p-\\d{3}-\\d{2}")) && it.reviewsCount > 0 }
                    .id
            // As a stand was: the canvas's rows only, the counts at marketplace scale, and a review Maya wrote.
            dataSource.run(
                "DELETE FROM reviews WHERE id LIKE 'rf-%'",
                "DELETE FROM questions WHERE id LIKE 'qf-%'",
                "DELETE FROM rating_counts",
                "INSERT INTO rating_counts VALUES ('$SONY_HEADPHONES', 5, 1827), ('$SONY_HEADPHONES', 4, 328)",
                "UPDATE products SET reviews_count = 9000, questions_count = 150 WHERE id = '$generated'",
                "UPDATE products SET reviews_count = 2342 WHERE id = '$SONY_HEADPHONES'",
                """
                INSERT INTO reviews (id, product_id, customer_id, author, rating, title, body, verified, helpful, tone, created_at)
                VALUES ('r-mayas', '$SONY_HEADPHONES', '${SampleCustomers.MAYA}', 'Maya K.', 5, 'Quiet', 'Quiet on every flight I take.',
                    false, 0, '#E6E4FF', '2025-10-07T12:00:00-04:00')
                """.trimIndent(),
            )

            assertFalse(
                Seeder.seedIfEmpty(database, CatalogSeed.generate(CatalogSeed.CANVAS_DAY)),
                "the catalog was there",
            )
            assertEquals(emptyList(), dataSource.miscounted())
            assertEquals(2_342 to 86, dataSource.counts(SONY_HEADPHONES), "the seed's 2,341 and Maya's")
            assertEquals(mapOf(5 to 1_967, 4 to 281, 3 to 47, 2 to 24, 1 to 23), dataSource.histogram(SONY_HEADPHONES))
            val seeded = CatalogSeed.generate(CatalogSeed.CANVAS_DAY).products.single { it.id == generated }
            assertEquals(seeded.reviewsCount to seeded.questionsCount, dataSource.counts(generated))

            // The next start finds every row there and writes nothing.
            val digest = SeedDigest.of(dataSource)
            Seeder.seedIfEmpty(database, CatalogSeed.generate(CatalogSeed.CANVAS_DAY))
            assertEquals(digest, SeedDigest.of(dataSource), "a second start changed the reviews")
        }

    /**
     * B-111: the tests' seed leaves the generated rows out, and a start that fills — `main`'s, which never
     * passes the switch — puts every one of them in, so no database stays without them past such a start.
     */
    @Test
    fun `a database seeded without the generated rows gets them on the next start that fills`() =
        PostgresHarness.freshDatabase().use { dataSource ->
            val database = Databases.connect(dataSource)
            assertTrue(
                Seeder.seedIfEmpty(database, CatalogSeed.generate(CatalogSeed.CANVAS_DAY), generatedReviews = false),
            )
            assertEquals(0 to 0, dataSource.generated(), "the tests' seed wrote the generated rows")
            val headphones = CatalogSeed.generate(CatalogSeed.CANVAS_DAY).products.single { it.id == SONY_HEADPHONES }
            assertEquals(headphones.reviewsCount to headphones.questionsCount, dataSource.counts(SONY_HEADPHONES))

            assertFalse(
                Seeder.seedIfEmpty(database, CatalogSeed.generate(CatalogSeed.CANVAS_DAY)),
                "the catalog was there",
            )
            val filled =
                PostgresHarness.freshDatabase().use {
                    assertTrue(Seeder.seedIfEmpty(Databases.connect(it), CatalogSeed.generate(CatalogSeed.CANVAS_DAY)))
                    SeedDigest.of(it)
                }
            assertEquals(filled, SeedDigest.of(dataSource), "not the database a seed that fills writes")
        }

    /**
     * B-86: the rating is the histogram's average, so the seed's headphones moved from the canvas's bars (78 / 14 /
     * 4 / 2 / 2 %, a 4.6) to bars that give its 4.8. A stand seeded before keeps its database: its next start turns it
     * into the database a fresh seed writes — the rows past each target gone with their votes, the missing ones
     * written, every row dated as a fresh seed dates it, and every rating its histogram's.
     */
    @Test
    fun `a stand seeded with the canvas's bars gets the seed's and every rating its histogram's`() =
        PostgresHarness.freshDatabase().use { dataSource ->
            val database = Databases.connect(dataSource)
            Seeder.seedIfEmpty(database, CatalogSeed.generate(CatalogSeed.CANVAS_DAY))
            val generated =
                CatalogSeed
                    .generate(CatalogSeed.CANVAS_DAY)
                    .products
                    .first { it.id.matches(Regex("p-\\d{3}-\\d{2}")) }
                    .id
            val rf = "rf-$SONY_HEADPHONES"
            // As the stand was: the canvas's bars (1,825 generated fives, 327 fours, 94 threes, 47 twos, 46 ones),
            // its dates, a vote on a review the new bars do not have, the drawn rating of a generated product.
            dataSource.run(
                "DELETE FROM reviews WHERE id LIKE '$rf-5-%' AND substring(id FROM '[0-9]+$')::int > 1825",
                *listOf(4 to 281..327, 3 to 48..94, 2 to 25..47, 1 to 24..46)
                    .map { (stars, numbers) ->
                        """
                        INSERT INTO reviews (id, product_id, customer_id, author, rating, title, body, verified, helpful, tone, photos, created_at)
                        SELECT '$rf-$stars-' || g, product_id, NULL, author, rating, title, body, verified, helpful, tone, photos, created_at
                        FROM reviews, generate_series(${numbers.first}, ${numbers.last}) g WHERE id = '$rf-$stars-1'
                        """.trimIndent()
                    }.toTypedArray(),
                "UPDATE reviews SET created_at = created_at - interval '1 day' WHERE id LIKE '$rf-%'",
                "INSERT INTO helpful_votes VALUES ('$rf-3-90', '${SampleCustomers.MAYA}', '2025-10-06T12:00:00Z')",
                "DELETE FROM rating_counts",
                "INSERT INTO rating_counts SELECT product_id, rating, count(*) FROM reviews GROUP BY 1, 2",
                "UPDATE products SET rating = 4.8 WHERE id = '$SONY_HEADPHONES'",
                "UPDATE products SET rating = 1.0 WHERE id = '$generated'",
            )
            assertEquals(mapOf(5 to 1_826, 4 to 328, 3 to 94, 2 to 47, 1 to 46), dataSource.histogram(SONY_HEADPHONES))
            assertEquals(
                listOf(SONY_HEADPHONES, generated).sorted(),
                dataSource.miscounted(),
                "the stand's ratings, before",
            )

            assertFalse(Seeder.seedIfEmpty(database, CatalogSeed.generate(CatalogSeed.CANVAS_DAY)))
            assertEquals(emptyList(), dataSource.miscounted())
            val fresh =
                PostgresHarness.freshDatabase().use {
                    assertTrue(Seeder.seedIfEmpty(Databases.connect(it), CatalogSeed.generate(CatalogSeed.CANVAS_DAY)))
                    SeedDigest.of(it)
                }
            assertEquals(fresh, SeedDigest.of(dataSource), "not the database a fresh seed writes")
        }

    /** B-86: every product the seed has is reviewed, and its rating is its reviews' average — none is a number alone. */
    @Test
    fun `every product of the seed has reviews and the rating they give`() {
        val catalog = CatalogSeed.generate(CatalogSeed.CANVAS_DAY)
        assertEquals(
            emptyList(),
            catalog.products.filter { it.reviewsCount == 0 }.map { it.id },
            "products nobody reviewed",
        )
        val disagreeing =
            catalog.products.filter { product ->
                val histogram = catalog.ratingCounts.getValue(product.id)
                histogram.values.sum() != product.reviewsCount || Ratings.average(histogram) != product.rating
            }
        assertEquals(emptyList(), disagreeing.map { it.id }, "products whose rating or count is not their histogram's")
        assertEquals(BigDecimal("4.8"), catalog.products.single { it.id == SONY_HEADPHONES }.rating)
    }

    @Test
    fun `a histogram made from an average gives that average`() {
        assertEquals(mapOf(5 to 294, 4 to 126), SeedReviews.histogram(BigDecimal("4.7"), 420))
        assertEquals(mapOf(4 to 8, 3 to 2), SeedReviews.histogram(BigDecimal("3.8"), 10))
        assertEquals(mapOf(5 to 0, 4 to 0), SeedReviews.histogram(BigDecimal("4.2"), 0))
        assertEquals(mapOf(5 to 7), SeedReviews.histogram(BigDecimal("5.0"), 7))
    }

    /**
     * Products whose stored counts, histogram or rating differ from their rows — the rating from their stars'
     * average to a tenth (B-86) — read past every repository.
     */
    private fun DataSource.miscounted(): List<String> =
        query(
            """
            SELECT p.id FROM products p
            WHERE p.reviews_count <> (SELECT count(*) FROM reviews r WHERE r.product_id = p.id)
               OR p.questions_count <> (SELECT count(*) FROM questions q WHERE q.product_id = p.id)
               OR p.rating IS DISTINCT FROM (SELECT round(avg(r.rating), 1) FROM reviews r WHERE r.product_id = p.id)
            UNION
            SELECT product_id FROM (
                (SELECT product_id, rating AS stars, count(*)::int AS n FROM reviews GROUP BY 1, 2
                 EXCEPT SELECT product_id, stars, count FROM rating_counts)
                UNION
                (SELECT product_id, stars, count FROM rating_counts
                 EXCEPT SELECT product_id, rating, count(*)::int FROM reviews GROUP BY 1, 2)
            ) d
            ORDER BY 1
            """.trimIndent(),
        ) { it.getString(1) }

    private fun DataSource.counts(product: String): Pair<Int, Int> =
        query("SELECT reviews_count, questions_count FROM products WHERE id = '$product'") {
            it.getInt(1) to
                it.getInt(2)
        }.single()

    /** How many generated reviews and questions the database holds. */
    private fun DataSource.generated(): Pair<Int, Int> =
        query(
            "SELECT (SELECT count(*) FROM reviews WHERE id LIKE 'rf-%'), (SELECT count(*) FROM questions WHERE id LIKE 'qf-%')",
        ) { it.getInt(1) to it.getInt(2) }.single()

    private fun DataSource.allCounts(): Map<String, Pair<Int, Int>> =
        query("SELECT id, reviews_count, questions_count FROM products") {
            it.getString(1) to
                (it.getInt(2) to it.getInt(3))
        }.toMap()

    private fun DataSource.histogram(product: String): Map<Int, Int> =
        query("SELECT stars, count FROM rating_counts WHERE product_id = '$product' ORDER BY stars DESC") {
            it.getInt(1) to it.getInt(2)
        }.toMap()

    private fun <T> DataSource.query(
        sql: String,
        row: (java.sql.ResultSet) -> T,
    ): List<T> =
        connection.use { c ->
            c.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows -> buildList { while (rows.next()) add(row(rows)) } }
            }
        }

    private fun DataSource.run(vararg sql: String) =
        connection.use { c ->
            c.createStatement().use { statement -> sql.forEach(statement::execute) }
            c.commit()
        }
}
