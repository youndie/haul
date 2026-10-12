package io.github.youndie.haul.feature.reviews

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.reviews.data.ExposedReviews
import io.github.youndie.haul.feature.reviews.domain.Ratings
import io.github.youndie.haul.feature.reviews.domain.StoredQuestion
import io.github.youndie.haul.feature.reviews.domain.StoredReview
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCatalog.SONY_HEADPHONES
import io.github.youndie.haul.shell.Parts
import io.github.youndie.haul.testing.after
import io.github.youndie.haul.testing.answer
import io.github.youndie.haul.testing.assertError
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.json
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.ProductDetails
import io.github.youndie.haul.ui.ProductQuestions
import io.github.youndie.haul.ui.ProductReviews
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.commands.LoadAction
import io.github.youndie.kompot.commands.UpdateAction
import io.github.youndie.kompot.commands.UpdateHistory
import io.github.youndie.kompot.standard.NavigateAction
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import java.time.OffsetDateTime
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B-71: the reviews and questions tabs list ten, then «Show more» — kompot's `load` of the tab's address listing
 * ten more, answered with an `update` of the tab's node and that address, `push`; the page the client draws after
 * it is the page the address opens. Since B-99 the seed stores every review and question a product counts, so
 * the headphones' tabs have more on the seed itself; the pages are counted on a product the seed gives none,
 * with as many written into a database of its own as each test needs.
 */
class ReviewPagesTest {
    private val catalog = CatalogSeed.generate(CatalogSeed.CANVAS_DAY)

    /**
     * A generated product with no questions, and none of its reviews stored in the tests' seed (B-111 leaves out
     * the generated rows; every product has at least one counted since B-86).
     */
    private val product =
        catalog.products
            .first { it.id.matches(Regex("p-\\d{3}-\\d{2}")) && it.questionsCount == 0 }
            .id

    /** The headphones on the seed: 2,341 reviews and 86 questions, each stored, so «Show more» is there (B-99). */
    @Test
    fun `the headphones' tabs offer more on the seed itself`() =
        haulTest {
            val reviews = tree("/ui/p/$SONY_HEADPHONES?tab=reviews").only<ProductReviews>()
            assertEquals(10, reviews.reviews.size)
            assertEquals("Show more reviews", reviews.moreLabel)
            val questions = tree("/ui/p/$SONY_HEADPHONES?tab=questions").only<ProductQuestions>()
            assertEquals(10, questions.questions.size)
            assertEquals("Show more questions", questions.moreLabel)
            // The most a tab lists is 500 of the 2,341: still more.
            assertEquals(
                500,
                tree("/ui/p/$SONY_HEADPHONES?tab=reviews&shown=500").only<ProductReviews>().reviews.size,
            )
        }

    @Test
    fun `the reviews tab lists ten and shows ten more in place until there are no more`() =
        withMore(reviews = 11, questions = 0) {
            val page = tree("/ui/p/$product?tab=reviews")
            val first = page.only<ProductReviews>()
            assertEquals(10, first.reviews.size)
            assertEquals("Show more reviews", first.moreLabel)
            // The page's address names no SKU, and nor does the one it records (B-132).
            assertEquals(LoadAction("${Parts.PREFIX}/p/$product?tab=reviews&shown=20"), first.more)

            val after = loaded(page, first.more, "reviews")
            val all = after.only<ProductReviews>()
            assertEquals(11, all.reviews.size, "the eleven written")
            assertNull(all.moreLabel)
            assertNull(all.more)
        }

    @Test
    fun `the questions tab lists ten and shows ten more in place until there are no more`() =
        withMore(reviews = 0, questions = 11) {
            val page = tree("/ui/p/$product?tab=questions")
            val first = page.only<ProductQuestions>()
            assertEquals(10, first.questions.size)
            assertEquals("Show more questions", first.moreLabel)
            val after = loaded(page, first.more, "questions")
            val all = after.only<ProductQuestions>()
            assertEquals(11, all.questions.size, "the eleven asked")
            assertNull(all.more)
        }

    /** Ten stored are ten listed: «more» follows the rows, one more is asked for than is drawn. */
    @Test
    fun `ten reviews draw no more`() =
        withMore(reviews = 10, questions = 0) {
            val reviews = tree("/ui/p/$product?tab=reviews").only<ProductReviews>()
            assertEquals(10, reviews.reviews.size)
            assertNull(reviews.more)
            assertNull(reviews.moreLabel)
        }

    @Test
    fun `how many a tab lists is from ten to five hundred`() =
        haulTest {
            for (shown in listOf("9", "501", "many")) {
                get(
                    "/ui/p/$product?tab=reviews&shown=$shown",
                ).assertError(HttpStatusCode.BadRequest, ErrorCode.ValidationFailed)
            }
        }

    @Test
    fun `the parts of a product no longer there open its address`() =
        haulTest {
            assertEquals(
                NavigateAction("/p/p-no-such-product?tab=reviews&shown=20"),
                answer("${Parts.PREFIX}/p/p-no-such-product?tab=reviews&shown=20"),
            )
        }

    /**
     * B-86: a bar of the histogram lists the reviews of its stars in place — the tab's node and the address with
     * `stars`, pushed, the page after it the page that address opens — and «more» pages through them; the bar shown
     * pressed, or «All», lists every review again.
     */
    @Test
    fun `a bar lists the reviews of its stars in place and pressed again or all lists every review`() =
        haulTest {
            val page = tree("/ui/p/$SONY_HEADPHONES?tab=reviews")
            val every = page.only<ProductReviews>()
            assertEquals(listOf(5, 4, 3, 2, 1), every.histogram.map { it.stars })
            every.histogram.forEach { bar ->
                assertEquals(LoadAction("$TAB&stars=${bar.stars}"), bar.action, "the ${bar.stars}-star bar")
                assertFalse(bar.selected)
            }
            assertNull(every.filter)
            assertNull(every.all)

            val fours = loaded(page, every.histogram.single { it.stars == 4 }.action, "reviews")
            val four = fours.only<ProductReviews>()
            assertEquals(10, four.reviews.size)
            assertEquals(setOf("4.0"), four.reviews.map { it.score }.toSet(), "only the four-star reviews")
            assertEquals("Aisha K.", four.reviews.first().author, "the canvas's four-star review, the newest of them")
            assertEquals("Showing 4-star reviews", four.filter)
            assertEquals("All", four.allLabel)
            assertEquals(LoadAction(TAB), four.all)
            val bar = four.histogram.single { it.stars == 4 }
            assertTrue(bar.selected)
            assertEquals(LoadAction(TAB), bar.action, "pressed again, the bar takes the filter off")
            assertEquals(LoadAction("$TAB&stars=5"), four.histogram.single { it.stars == 5 }.action)
            // The rating, the caption and the bars are the product's, whatever is listed.
            assertEquals(every.rating, four.rating)
            assertEquals(every.caption, four.caption)
            assertEquals(every.histogram.map { it.percent }, four.histogram.map { it.percent })
            assertEquals(LoadAction("$TAB&stars=4&shown=20"), four.more)

            val more = loaded(fours, four.more, "reviews").only<ProductReviews>()
            assertEquals(20, more.reviews.size)
            assertEquals(setOf("4.0"), more.reviews.map { it.score }.toSet())

            for (clear in listOf(four.all, bar.action)) {
                val all = loaded(fours, clear, "reviews").only<ProductReviews>()
                assertEquals(every, all, "the tab with every review")
            }
        }

    /** A bar nobody gave has nothing to list and presses nothing; one with reviews counted lists them. */
    @Test
    fun `a bar nobody gave presses nothing`() =
        withMore(reviews = 0, questions = 0) {
            val reviews = tree("/ui/p/$product?tab=reviews").only<ProductReviews>()
            val counted = catalog.ratingCounts.getValue(product)
            assertTrue(
                counted.values.any { it == 0 } || (1..5).any { it !in counted },
                "a product with a bar nobody gave",
            )
            reviews.histogram.forEach { bar ->
                val given = counted[bar.stars] ?: 0
                if (given == 0) {
                    assertNull(bar.action, "the ${bar.stars}-star bar, which nobody gave")
                } else {
                    assertEquals(LoadAction("${Parts.PREFIX}/p/$product?tab=reviews&stars=${bar.stars}"), bar.action)
                }
            }
        }

    @Test
    fun `the stars listed are one to five`() =
        haulTest {
            for (stars in listOf("0", "6", "four")) {
                get("/ui/p/$SONY_HEADPHONES?tab=reviews&stars=$stars")
                    .assertError(HttpStatusCode.BadRequest, ErrorCode.ValidationFailed)
                get("${Parts.PREFIX}/p/$SONY_HEADPHONES?tab=reviews&stars=$stars")
                    .assertError(HttpStatusCode.BadRequest, ErrorCode.ValidationFailed)
            }
        }

    /**
     * B-86: a review written moves the rating to its histogram's average — the bars and the number over them from
     * one count — on a product of few reviews, where one moves it.
     */
    @Test
    fun `a review written moves the rating to the histogram's average`() =
        seededFreshDatabase().use { dataSource ->
            val counted = catalog.ratingCounts.getValue(product)
            val after = counted + (1 to (counted[1] ?: 0) + 1)
            val expected = assertNotNull(Ratings.average(after)).toPlainString()
            haulTest(dataSource) {
                val before = tree("/ui/p/$product?tab=reviews")
                assertEquals(Ratings.average(counted)?.toPlainString(), before.only<ProductReviews>().rating)
                assertEquals(before.only<ProductReviews>().rating, before.only<ProductDetails>().rating)
                assertNotEquals(
                    before.only<ProductReviews>().rating,
                    expected,
                    "one review moves this product's rating",
                )
            }
            runBlocking { ExposedReviews(Databases.connect(dataSource)).write(review("r-one-star", rating = 1)) }
            haulTest(dataSource) {
                val page = tree("/ui/p/$product?tab=reviews")
                val reviews = page.only<ProductReviews>()
                assertEquals(expected, reviews.rating)
                assertEquals(expected, page.only<ProductDetails>().rating, "the buy box's rating, the same number")
                val total = after.values.sum()
                assertEquals(
                    (5 downTo 1).map { ((after[it] ?: 0) * 100.0 / total).roundToInt() },
                    reviews.histogram.map { it.percent },
                )
            }
        }

    private fun review(
        id: String,
        rating: Int = 4,
        createdAt: OffsetDateTime = OffsetDateTime.parse("2025-06-01T12:00:00Z"),
    ) = StoredReview(
        id = id,
        productId = product,
        customerId = null,
        author = "Reader $id",
        rating = rating,
        title = "Review $id",
        body = "A review written for the second page of the tab.",
        verified = false,
        helpful = 0,
        tone = "#E6E4FF",
        photos = emptyList(),
        createdAt = createdAt,
    )

    /**
     * Follows [more] from [page]: one `update` of the node [id] alone, the address it makes pushed — and the page
     * after it is the page that address opens. Returns that page.
     */
    private suspend fun HttpClient.loaded(
        page: KompotComponent,
        more: Any?,
        id: String,
    ): KompotComponent {
        val load = assertIs<LoadAction>(more)
        val update = assertIs<UpdateAction>(answer(load.url))
        assertEquals(listOf(id), update.updates.map { it.componentId })
        assertEquals(UpdateHistory.PUSH, update.history)
        val deeplink = assertNotNull(update.deeplink)
        assertEquals(load.url.removePrefix(Parts.PREFIX), deeplink)
        val whole = tree("/ui$deeplink")
        assertEquals(whole.json(), page.after(update), "the page after the update is not $deeplink")
        return whole
    }

    /** The seeded catalog with [reviews] reviews and [questions] questions of the product, which the seed gives none. */
    private fun withMore(
        reviews: Int,
        questions: Int,
        block: suspend HttpClient.() -> Unit,
    ) = seededFreshDatabase().use { dataSource ->
        val repository = ExposedReviews(Databases.connect(dataSource))
        val before = OffsetDateTime.parse("2025-06-01T12:00:00Z")
        runBlocking {
            repeat(reviews) { repository.write(review("r-more-$it", createdAt = before.minusDays(it.toLong()))) }
            repeat(questions) {
                repository.ask(
                    StoredQuestion(
                        id = "q-more-$it",
                        productId = product,
                        customerId = null,
                        text = "Question $it for the second page?",
                        askedAt = before.minusDays(it.toLong()),
                    ),
                )
            }
        }
        haulTest(dataSource, block = block)
    }

    private companion object {
        const val TAB = "${Parts.PREFIX}/p/$SONY_HEADPHONES?tab=reviews"
    }
}
