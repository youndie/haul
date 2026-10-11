package io.github.youndie.haul.feature.reviews

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.reviews.data.ExposedReviews
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * B-71: the reviews and questions tabs list ten, then «Show more» — kompot's `load` of the tab's address listing
 * ten more, answered with an `update` of the tab's node and that address, `push`; the page the client draws after
 * it is the page the address opens. Since B-99 the seed stores every review and question a product counts, so
 * the headphones' tabs have more on the seed itself; the pages are counted on a product the seed gives none,
 * with as many written into a database of its own as each test needs.
 */
class ReviewPagesTest {
    /** A generated product with no reviews and no questions in the seed. */
    private val product =
        CatalogSeed
            .generate(CatalogSeed.CANVAS_DAY)
            .products
            .first { it.reviewsCount == 0 && it.questionsCount == 0 }
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
            val sku = page.only<ProductDetails>().skuId
            val first = page.only<ProductReviews>()
            assertEquals(10, first.reviews.size)
            assertEquals("Show more reviews", first.moreLabel)
            assertEquals(LoadAction("${Parts.PREFIX}/p/$product?sku=$sku&tab=reviews&shown=20"), first.more)

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
            repeat(reviews) {
                repository.write(
                    StoredReview(
                        id = "r-more-$it",
                        productId = product,
                        customerId = null,
                        author = "Reader $it",
                        rating = 4,
                        title = "Review $it",
                        body = "A review written for the second page of the tab.",
                        verified = false,
                        helpful = 0,
                        tone = "#E6E4FF",
                        photos = emptyList(),
                        createdAt = before.minusDays(it.toLong()),
                    ),
                )
            }
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
}
