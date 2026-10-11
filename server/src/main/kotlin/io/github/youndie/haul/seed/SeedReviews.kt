package io.github.youndie.haul.seed

import io.github.youndie.haul.feature.reviews.domain.ReviewCommands
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Connection
import java.time.OffsetDateTime

/**
 * The reviews and questions a product's counts promise, stored one by one (B-99). A product's «2,341 reviews»
 * and «86 questions» are what its page lists, ten at a time: the seed's own rows ([SeedCatalog.reviews], the
 * canvas's two reviews and three questions of the headphones) and, for the rest of each count, generated rows
 * written in SQL — `generate_series` over each product's target, the copy picked from the pools below by the
 * row's number. The seed holds 60,849 reviews and 6,799 questions so; written that way they add about a second
 * and a half to a fresh seed (0.6–1.4 s before, 2.2–2.7 s after, on the build box), which the server's suite,
 * seeding a database per test that writes, pays about ninety times.
 *
 * Every row is deterministic: its id is `rf-<product>-<stars>-<n>` (a review) or `qf-<product>-<n>` (a
 * question), its copy and its date follow from that number and the product's id, and no clock is read. The
 * stars follow the product's histogram where the seed has one ([SeedCatalog.ratingCounts], the canvas's
 * 78 / 14 / 4 / 2 / 2 % of the headphones), and otherwise its average: a 4.7 is seven tenths fives and the
 * rest fours, so the histogram a page draws from the rows agrees with the rating beside it.
 *
 * [fill] runs wherever the seed runs, also on a database seeded before it existed — a stand keeps its
 * database across deploys, and its products said 2,341 over two rows: it inserts the generated rows a
 * database does not have yet (`ON CONFLICT DO NOTHING`, by id), then counts every product's reviews,
 * questions and histogram from the rows, so a review a customer wrote is counted once, as a row. On a database
 * that already holds every generated row it reads one count and writes nothing.
 */
internal object SeedReviews {
    /**
     * Inserts what is missing of [catalog]'s generated reviews and questions over [connection], inside the
     * seeding transaction (which holds the seeding lock), and returns whether it wrote anything. [fresh] is a
     * catalogue seeded in this transaction, which has none of them: its rows go in without the per-row
     * conflict check, which doubled the cost of the inserts (measured: 2.5–2.7 s against 1.3–1.5 s).
     */
    fun fill(
        connection: Connection,
        catalog: SeedCatalog,
        fresh: Boolean,
    ): Boolean {
        val reviews = reviewTargets(catalog)
        val questions = questionTargets(catalog)
        if (!fresh && present(connection) == reviews.sumOf { it.n.toLong() } to questions.sumOf { it.n.toLong() }) {
            return false
        }
        connection.createStatement().use { it.execute(LOCK) }
        connection.prepareStatement(REVIEWS + guard(fresh)).use { statement ->
            statement.setArray(1, connection.createArrayOf("text", AUTHORS.toTypedArray()))
            statement.setArray(2, connection.createArrayOf("text", REVIEW_TITLES.toTypedArray()))
            statement.setArray(3, connection.createArrayOf("text", REVIEW_BODIES.toTypedArray()))
            statement.setArray(4, connection.createArrayOf("text", ReviewCommands.TONES.toTypedArray()))
            // The anchor comes before the targets in the statement's text.
            statement.setObject(5, REVIEWS_BEFORE)
            statement.setArray(6, connection.createArrayOf("text", reviews.map { it.productId }.toTypedArray()))
            statement.setArray(7, connection.createArrayOf("int4", reviews.map { it.stars }.toTypedArray()))
            statement.setArray(8, connection.createArrayOf("int4", reviews.map { it.n }.toTypedArray()))
            statement.setArray(9, connection.createArrayOf("int4", reviews.map { salt(it.productId) }.toTypedArray()))
            statement.executeUpdate()
        }
        connection.prepareStatement(QUESTIONS + guard(fresh)).use { statement ->
            statement.setArray(1, connection.createArrayOf("text", QUESTION_PAIRS.map { it.first }.toTypedArray()))
            statement.setArray(2, connection.createArrayOf("text", QUESTION_PAIRS.map { it.second }.toTypedArray()))
            statement.setArray(3, connection.createArrayOf("text", questions.map { it.productId }.toTypedArray()))
            statement.setArray(4, connection.createArrayOf("int4", questions.map { it.n }.toTypedArray()))
            statement.setArray(5, connection.createArrayOf("int4", questions.map { salt(it.productId) }.toTypedArray()))
            statement.setObject(6, QUESTIONS_BEFORE)
            statement.executeUpdate()
        }
        connection.createStatement().use { statement -> RECOUNT.forEach(statement::execute) }
        return true
    }

    /** How many generated reviews and questions the database holds. */
    private fun present(connection: Connection): Pair<Long, Long> =
        connection.createStatement().use { statement ->
            statement.executeQuery(PRESENT).use { rows ->
                rows.next()
                rows.getLong(1) to rows.getLong(2)
            }
        }

    /**
     * On a catalogue seeded before: only the rows it does not have, and none for a product it does not have (a
     * catalogue seeded by another generator).
     */
    private fun guard(fresh: Boolean): String = if (fresh) "" else "\n" + ON_EXISTING

    private const val ON_EXISTING =
        "AND EXISTS (SELECT 1 FROM products p WHERE p.id = t.product_id)\nON CONFLICT (id) DO NOTHING"

    /** How many generated reviews of [stars] stars [productId] has: the target less the seed's own rows. */
    internal data class Target(
        val productId: String,
        val stars: Int,
        val n: Int,
    )

    /**
     * Per product and number of stars, the generated reviews: the histogram the seed gives the product — its
     * own, or the one its average and count make — less the reviews the seed holds one by one.
     */
    internal fun reviewTargets(catalog: SeedCatalog): List<Target> {
        val own = catalog.reviews.groupingBy { it.productId to it.rating }.eachCount()
        return catalog.products.flatMap { product ->
            val histogram = catalog.ratingCounts[product.id] ?: histogram(product.rating, product.reviewsCount)
            (STARS downTo 1).mapNotNull { stars ->
                val n = (histogram[stars] ?: 0) - (own[product.id to stars] ?: 0)
                if (n > 0) Target(product.id, stars, n) else null
            }
        }
    }

    /** Per product, the generated questions: its count less the questions the seed holds one by one. */
    internal fun questionTargets(catalog: SeedCatalog): List<Target> {
        val own = catalog.questions.groupingBy { it.productId }.eachCount()
        return catalog.products.mapNotNull { product ->
            val n = product.questionsCount - (own[product.id] ?: 0)
            if (n > 0) Target(product.id, 0, n) else null
        }
    }

    /**
     * [count] reviews averaging [rating]: the two numbers of stars around it, as many of the higher one as
     * the fraction says («4.7» of 420: 294 fives and 126 fours).
     */
    internal fun histogram(
        rating: BigDecimal,
        count: Int,
    ): Map<Int, Int> {
        val low = rating.setScale(0, RoundingMode.FLOOR).toInt().coerceIn(1, STARS)
        if (low == STARS) return mapOf(STARS to count)
        val high =
            (rating - low.toBigDecimal())
                .multiply(
                    count.toBigDecimal(),
                ).setScale(0, RoundingMode.HALF_UP)
                .toInt()
        return mapOf(low + 1 to high, low to count - high)
    }

    /** A number per product that varies which copy its rows start from; `String.hashCode` is the same everywhere. */
    private fun salt(productId: String): Int = Math.floorMod(productId.hashCode(), SALT)

    private const val STARS = 5
    private const val SALT = 997

    /** Generated reviews are older than the canvas's two (Sep 28, Sep 21), spread over the 700 days before. */
    private val REVIEWS_BEFORE: OffsetDateTime = OffsetDateTime.parse("2025-09-20T12:00:00-04:00")

    /** Generated questions are older than the canvas's answered two (Sep 29, Sep 14), over 500 days. */
    private val QUESTIONS_BEFORE: OffsetDateTime = OffsetDateTime.parse("2025-09-10T12:00:00-04:00")

    private const val PRESENT =
        "SELECT (SELECT count(*) FROM reviews WHERE id LIKE 'rf-%'), (SELECT count(*) FROM questions WHERE id LIKE 'qf-%')"

    /** Nothing writes a review, a question or a histogram while the rows are added and counted. */
    private const val LOCK = "LOCK TABLE reviews, questions, rating_counts IN SHARE ROW EXCLUSIVE MODE"

    /**
     * Row `i` of `n` of a product's reviews of `stars`: an author, a title and a body for its stars (eight
     * each, in the pools' order: five stars first), a tone, three in four verified, one in six with people who
     * found it helpful, dated back from [REVIEWS_BEFORE] by `i / n` of 700 days; [guard] ends it.
     */
    private val REVIEWS =
        """
        WITH pool AS (SELECT ?::text[] AS authors, ?::text[] AS titles, ?::text[] AS bodies, ?::text[] AS tones)
        INSERT INTO reviews (id, product_id, customer_id, author, rating, title, body, verified, helpful, tone, created_at)
        SELECT 'rf-' || t.product_id || '-' || t.stars || '-' || g.i,
               t.product_id,
               NULL,
               pool.authors[1 + (g.i * 7 + t.salt) % cardinality(pool.authors)],
               t.stars,
               pool.titles[1 + (5 - t.stars) * $PER_STAR + (g.i + t.salt) % $PER_STAR],
               pool.bodies[1 + (5 - t.stars) * $PER_STAR + (g.i * 3 + t.salt) % $PER_STAR],
               (g.i + t.stars) % 4 <> 0,
               CASE WHEN (g.i + t.salt) % 6 = 0 THEN 1 + (g.i * 13 + t.salt) % 40 ELSE 0 END,
               pool.tones[1 + (g.i + t.salt) % cardinality(pool.tones)],
               ?::timestamptz
                   - make_interval(secs => (g.i::bigint * 60480000 / t.n)::double precision)
                   - make_interval(mins => t.stars * 7)
        FROM unnest(?::text[], ?::int4[], ?::int4[], ?::int4[]) AS t(product_id, stars, n, salt)
        CROSS JOIN LATERAL generate_series(1, t.n) AS g(i)
        CROSS JOIN pool
        WHERE t.n > 0
        """.trimIndent()

    /**
     * Row `i` of `n` of a product's questions: one of the pairs below, asked back from [QUESTIONS_BEFORE] by
     * `i / n` of 500 days and answered by the seller the next day — the seed's answers are the only ones
     * there are (feature-reviews: no seller side).
     */
    private val QUESTIONS =
        """
        WITH pool AS (SELECT ?::text[] AS asked, ?::text[] AS answers)
        INSERT INTO questions (id, product_id, customer_id, text, asked_at, answer, answered_at)
        SELECT 'qf-' || t.product_id || '-' || g.i,
               t.product_id,
               NULL,
               pool.asked[1 + (g.i + t.salt) % cardinality(pool.asked)],
               d.at,
               pool.answers[1 + (g.i + t.salt) % cardinality(pool.answers)],
               d.at + make_interval(hours => 20 + (g.i % 9))
        FROM unnest(?::text[], ?::int4[], ?::int4[]) AS t(product_id, n, salt)
        CROSS JOIN LATERAL generate_series(1, t.n) AS g(i)
        CROSS JOIN pool
        CROSS JOIN LATERAL (
            SELECT ?::timestamptz - make_interval(secs => (g.i::bigint * 43200000 / t.n)::double precision) AS at
        ) d
        WHERE t.n > 0
        """.trimIndent()

    /** Every product's counts and histogram, from its rows. */
    private val RECOUNT =
        listOf(
            """
            UPDATE products p SET reviews_count = c.n
            FROM (SELECT p2.id, count(r.id)::int AS n FROM products p2 LEFT JOIN reviews r ON r.product_id = p2.id GROUP BY p2.id) c
            WHERE c.id = p.id AND p.reviews_count <> c.n
            """.trimIndent(),
            """
            UPDATE products p SET questions_count = c.n
            FROM (SELECT p2.id, count(q.id)::int AS n FROM products p2 LEFT JOIN questions q ON q.product_id = p2.id GROUP BY p2.id) c
            WHERE c.id = p.id AND p.questions_count <> c.n
            """.trimIndent(),
            "DELETE FROM rating_counts",
            "INSERT INTO rating_counts (product_id, stars, count) SELECT product_id, rating, count(*) FROM reviews GROUP BY product_id, rating",
        )

    private const val PER_STAR = 8

    private val AUTHORS =
        listOf(
            "Chris P.",
            "Lena M.",
            "Omar S.",
            "Priya N.",
            "Tom W.",
            "Grace L.",
            "Mateo R.",
            "Hannah B.",
            "Kenji T.",
            "Sofia G.",
            "Ben A.",
            "Nadia F.",
            "Lucas D.",
            "Emma J.",
            "Ravi K.",
            "Zoe C.",
            "Marcus H.",
            "Ines V.",
            "Jonah E.",
            "Mia O.",
            "Felix Z.",
            "Ama Y.",
            "Diego Q.",
            "Ruth I.",
        )

    /** Eight titles per number of stars, five stars first. */
    private val REVIEW_TITLES =
        listOf(
            "Exactly what I hoped for",
            "Better than expected",
            "Would buy again",
            "Five stars, no notes",
            "Worth every cent",
            "Use it every day",
            "Great quality",
            "Love it",
            "Very good, one small gripe",
            "Solid choice",
            "Good value",
            "Nearly perfect",
            "Happy with it",
            "Does the job well",
            "Recommended",
            "Good, not great",
            "Fine, nothing special",
            "Okay for the price",
            "Mixed feelings",
            "Does the basics",
            "Average",
            "It's alright",
            "Middle of the road",
            "Could be better",
            "Not for me",
            "Expected better",
            "Disappointing",
            "Below expectations",
            "Wouldn't buy again",
            "Feels cheap",
            "Not as pictured",
            "Meh",
            "Sent it back",
            "Did not work for me",
            "Would not recommend",
            "Not as described",
            "Broke quickly",
            "Arrived damaged",
            "Waste of money",
            "Returned it",
        )

    /** Eight bodies per number of stars, five stars first; each at least `ReviewRules.BODY_MIN` long. */
    private val REVIEW_BODIES =
        listOf(
            "Arrived well packed and works exactly as described. Feels solid and looks better in person.",
            "Used it every day for a few weeks now and it has not let me down once. Easy recommendation.",
            "Great quality for the price. Setup took a couple of minutes and everything just worked.",
            "Bought one as a gift, then a second for myself. No complaints at all.",
            "The details are what sold me: nothing rattles, nothing feels like an afterthought.",
            "Replaced an older one I had for years and this is better in every way I care about.",
            "Exactly as pictured, arrived a day early, and still feels new after a month.",
            "My partner borrowed it and now wants one. That says enough.",
            "Does the job well and feels well made. Took a little getting used to, but I'm happy with it.",
            "Good quality overall. The colour is slightly different from the photos, still nice.",
            "Works as promised. The instructions could be clearer, otherwise a great buy.",
            "Not the cheapest option, but you can tell where the money went.",
            "One small thing I'd change, everything else is spot on.",
            "Comfortable and well finished. Lost a star only because the box was battered.",
            "A clear step up from what I had before. A bit pricey, though.",
            "Solid and dependable. I'd buy it again, maybe in another colour.",
            "It works, but it doesn't feel as premium as the photos suggest. Fine for everyday use.",
            "Decent, though I expected a bit more for the price. Keeping it for now.",
            "Some things I like, some I don't. Middle of the road overall.",
            "Does what it should. Nothing more, nothing less.",
            "Fine for occasional use. I wouldn't rely on it every day.",
            "The finish is okay up close, a little rough at the edges.",
            "Took a while to get used to. Now it's fine, just not exciting.",
            "Good idea, average execution. It gets the job done.",
            "Quality is not what I expected. It works, but I'd probably choose something else next time.",
            "Had a problem in the first week. It was sorted out, but it shouldn't have happened.",
            "Smaller than I thought from the photos, and the finish is a bit rough.",
            "Works, but feels cheap in the hand for what it costs.",
            "Started fine, then got worse after a couple of weeks of use.",
            "The colour was well off from the listing. Kept it, but only just.",
            "Too fiddly to use every day. I reach for my old one instead.",
            "Not terrible, just not worth the price in my opinion.",
            "Stopped working after a few days. The return was easy, at least.",
            "Did not match the description at all. Sent it back the same week.",
            "Poor fit and finish. Returned it and bought a different one.",
            "Arrived damaged, and the second one was no better, so I asked for a refund.",
            "Broke the first time I used it properly. Very disappointed.",
            "Nothing like the photos. Back in the box the same evening.",
            "Could not get it to work at all, even after trying everything.",
            "Uncomfortable from the first minute. Returned without a second thought.",
        )

    /** Questions any product is asked, and the answers its seller gave. */
    private val QUESTION_PAIRS =
        listOf(
            "Does it come with a warranty?" to
                "Yes. It carries the manufacturer's one-year warranty from the day it is delivered.",
            "Can I return it if it doesn't suit me?" to
                "Yes. Returns are free for 30 days, and the courier picks it up.",
            "Is this the current model?" to "Yes, this listing is the current model.",
            "Do the colours match the photos?" to
                "Closely. Screens vary a little, so the shade can look slightly different in person.",
            "Is it new or refurbished?" to "New, in its original packaging.",
            "Is it suitable as a gift?" to "Yes. It arrives in its retail box, so it is ready to wrap.",
            "Does it ship in its own box?" to "It ships in a plain outer carton with the retail box inside.",
            "Is the price the same in store?" to "We sell online only; the price on this page is the price.",
            "How long does it usually last?" to "With normal use, years. The warranty covers the first one.",
            "Is there a manual in the box?" to "Yes, a printed quick-start guide is included.",
            "Can I buy more than one?" to "Yes, as many as the page shows in stock.",
            "Is it the same as the one in the photos?" to "Yes, the photos are of this exact product.",
            "Does it need assembly?" to "No, it is ready to use out of the box.",
            "Can I pick it up instead of delivery?" to "Yes. Choose a pickup point at checkout.",
            "Is it covered if it arrives damaged?" to "Yes. Tell us within 30 days and we replace it or refund you.",
            "Are there other colours coming?" to "Every colour we sell is listed on this page.",
        )
}
