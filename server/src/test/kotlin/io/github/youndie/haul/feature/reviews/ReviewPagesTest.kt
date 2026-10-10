package io.github.youndie.haul.feature.reviews

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.reviews.data.ExposedReviews
import io.github.youndie.haul.feature.reviews.domain.StoredQuestion
import io.github.youndie.haul.feature.reviews.domain.StoredReview
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
 * it is the page the address opens. The seed stores two reviews and three questions of the headphones (the
 * canvas's 2,341 and 86 are the product's counts), so this writes more into a database of its own.
 */
class ReviewPagesTest {
    private val sony = SONY_HEADPHONES

    @Test
    fun `the reviews tab lists ten and shows ten more in place until there are no more`() =
        withMore(reviews = 11, questions = 0) {
            val page = tree("/ui/p/$sony?tab=reviews")
            val sku = page.only<ProductDetails>().skuId
            val first = page.only<ProductReviews>()
            assertEquals(10, first.reviews.size)
            assertEquals("Show more reviews", first.moreLabel)
            assertEquals(LoadAction("${Parts.PREFIX}/p/$sony?sku=$sku&tab=reviews&shown=20"), first.more)

            val after = loaded(page, first.more, "reviews")
            val all = after.only<ProductReviews>()
            assertEquals(13, all.reviews.size, "the seed's two and the eleven written")
            assertNull(all.moreLabel)
            assertNull(all.more)
        }

    @Test
    fun `the questions tab lists ten and shows ten more in place until there are no more`() =
        withMore(reviews = 0, questions = 9) {
            val page = tree("/ui/p/$sony?tab=questions")
            val first = page.only<ProductQuestions>()
            assertEquals(10, first.questions.size)
            assertEquals("Show more questions", first.moreLabel)
            val after = loaded(page, first.more, "questions")
            val all = after.only<ProductQuestions>()
            assertEquals(12, all.questions.size, "the seed's three and the nine asked")
            assertNull(all.more)
        }

    /** Ten stored are ten listed: «more» follows the rows, one more is asked for than is drawn. */
    @Test
    fun `ten reviews draw no more`() =
        withMore(reviews = 8, questions = 0) {
            val reviews = tree("/ui/p/$sony?tab=reviews").only<ProductReviews>()
            assertEquals(10, reviews.reviews.size)
            assertNull(reviews.more)
            assertNull(reviews.moreLabel)
        }

    @Test
    fun `how many a tab lists is from ten to five hundred`() =
        haulTest {
            for (shown in listOf("9", "501", "many")) {
                get(
                    "/ui/p/$sony?tab=reviews&shown=$shown",
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

    /** The seeded catalog with [reviews] more reviews and [questions] more questions of the headphones, older than the seed's. */
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
                        productId = sony,
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
                        productId = sony,
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
