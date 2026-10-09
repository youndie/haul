package io.github.youndie.haul.feature.reviews

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.SampleCatalog.DUVET_COVER
import io.github.youndie.haul.seed.SampleCatalog.SONY_HEADPHONES
import io.github.youndie.haul.seed.SampleCatalog.SONY_STORE
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.testing.SeededDatabase
import io.github.youndie.haul.testing.ShildikHarness
import io.github.youndie.haul.testing.assertError
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.HistogramBar
import io.github.youndie.haul.ui.ProductQuestions
import io.github.youndie.haul.ui.ProductReviews
import io.github.youndie.haul.ui.ProductTabs
import io.github.youndie.haul.ui.QuestionForm
import io.github.youndie.haul.ui.Review
import io.github.youndie.haul.ui.ReviewForm
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotAction
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.standard.CloseAction
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.PresentAction
import io.github.youndie.kompot.standard.RefreshAction
import io.github.youndie.kompot.standard.SequenceAction
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * feature-reviews over HTTP, against a running shildik ([ShildikHarness]) and the seeded catalog in
 * PostgreSQL, at the canvas's «now». The tabs are the product page's (`/ui/p/{id}?tab=…`); the commands
 * are endpoint-reviews'. A test that writes works over a database of its own: the counts it moves are
 * the product's, which every other test reads.
 */
class ReviewRoutesTest {
    private val sony = SONY_HEADPHONES
    private val reviewsTab = "/ui/p/$sony?tab=reviews"
    private val questionsTab = "/ui/p/$sony?tab=questions"
    private val good =
        ReviewEntry(
            rating = 5,
            title = "Worth it for the commute",
            body =
                "Noise cancelling handles the subway and the open office. Comfortable for a full workday, " +
                    "and the battery lasts all week.",
        )

    @Test
    fun `the reviews tab is the server's - the canvas's rating, histogram and two reviews`() =
        haulTest {
            val tree = tree(reviewsTab)
            val reviews = tree.only<ProductReviews>()
            assertEquals("4.8", reviews.rating)
            assertEquals("out of 5 · 2,341 reviews", reviews.caption)
            assertEquals(
                listOf(
                    HistogramBar(5, 78),
                    HistogramBar(4, 14),
                    HistogramBar(3, 4),
                    HistogramBar(2, 2),
                    HistogramBar(1, 2),
                ),
                reviews.histogram,
            )
            assertEquals(listOf("Daniel R.", "Aisha K."), reviews.reviews.map { it.author })
            assertEquals(
                listOf("Sep 28 · Verified purchase", "Sep 21 · Verified purchase"),
                reviews.reviews.map { it.meta },
            )
            assertEquals(listOf("5.0", "4.0"), reviews.reviews.map { it.score })
            assertEquals(listOf(null, "48 people found this helpful"), reviews.reviews.map { it.helpful })
            // A guest is sent to sign in, as the header's account shortcut sends one.
            assertEquals(NavigateAction(Frame.SIGN_IN), reviews.action)
            val tabs = tree.only<ProductTabs>().tabs
            assertEquals(listOf("description", "specifications", "reviews", "questions"), tabs.map { it.key })
            assertEquals(listOf(null, null, "2,341", "86"), tabs.map { it.count })
            assertEquals(listOf(false, false, true, false), tabs.map { it.selected })
        }

    @Test
    fun `the questions tab lists the answered ones first and the open one as not answered yet`() =
        haulTest {
            val questions = tree(questionsTab).only<ProductQuestions>()
            assertEquals("86", questions.count)
            assertEquals("questions", questions.caption)
            assertEquals("Answers come from Sony Official Store.", questions.text)
            assertEquals(listOf("Asked Sep 29", "Asked Sep 14", "Asked Oct 6"), questions.questions.map { it.asked })
            assertEquals(
                listOf("Sony Official Store · Sep 30", "Sony Official Store · Sep 15", null),
                questions.questions.map { it.answeredBy },
            )
            assertEquals(listOf(null, null, "Not answered yet"), questions.questions.map { it.pendingLabel })
            assertEquals(NavigateAction(Frame.SIGN_IN), questions.action)
        }

    @Test
    fun `a customer's buttons present the two dialogs, each posting to its product`() =
        signedIn { token ->
            val review = assertIs<PresentAction>(customerTree(token, reviewsTab).only<ProductReviews>().action)
            val form = assertIs<ReviewForm>(review.content)
            assertEquals(ReviewPaths.reviews(sony), form.url)
            assertEquals("Sony WH-1000XM6 Wireless Noise Cancelling Headphones", form.product.name)
            assertEquals("Midnight Black · Headphones only", form.product.detail)
            assertEquals(CloseAction, form.close)
            val question = assertIs<PresentAction>(customerTree(token, questionsTab).only<ProductQuestions>().action)
            val ask = assertIs<QuestionForm>(question.content)
            assertEquals(ReviewPaths.questions(sony), ask.url)
            assertEquals("10 – 1,000 characters", ask.hint)
            // The chosen SKU is the dialog's: Silver with a travel case.
            val silver =
                assertIs<PresentAction>(customerTree(token, "$reviewsTab&sku=$sony-4").only<ProductReviews>().action)
            assertEquals("Silver · + Travel case", assertIs<ReviewForm>(silver.content).product.detail)
        }

    /**
     * The dialogs name a product by its listing name, as its card, cart line and order line do (B-60). They
     * wrote brand and title before, which for the duvet put its seller's name in front — «Brooklyn Home Co.
     * Linen Duvet Cover Set, Queen» — where everything else writes the title it is listed under. The
     * headphones cannot tell the two apart: their brand and title are their listing name.
     */
    @Test
    fun `both dialogs name the product by its listing name and not by brand and title`() =
        signedIn { token ->
            val reviews = customerTree(token, "/ui/p/$DUVET_COVER?tab=reviews").only<ProductReviews>()
            val review = assertIs<ReviewForm>(assertIs<PresentAction>(reviews.action).content)
            assertEquals("Linen Duvet Cover Set, Queen", review.product.name)
            val questions = customerTree(token, "/ui/p/$DUVET_COVER?tab=questions").only<ProductQuestions>()
            val ask = assertIs<QuestionForm>(assertIs<PresentAction>(questions.action).content)
            assertEquals("Linen Duvet Cover Set, Queen", ask.product.name)
        }

    /** feature-reviews, «A verified review»: Maya with delivered order #HL-46102 holding the product. */
    @Test
    fun `a verified review - marked verified, and the product's count grows by one`() =
        ownDatabase { dataSource ->
            dataSource.order("HL-46102", SampleCustomers.MAYA, "delivered")
            signedIn(dataSource, "Maya Kowalski", SampleCustomers.MAYA) { token ->
                postReview(token, good).assertClosed()
                val tree = customerTree(token, reviewsTab)
                val reviews = tree.only<ProductReviews>()
                val mine = reviews.reviews.first()
                assertEquals("Maya K.", mine.author)
                assertEquals("Oct 7 · Verified purchase", mine.meta)
                assertEquals("5.0", mine.score)
                assertEquals(good.title, mine.title)
                assertEquals("out of 5 · 2,342 reviews", reviews.caption)
                assertEquals("4.8", reviews.rating)
                assertEquals(
                    "2,342",
                    tree
                        .only<ProductTabs>()
                        .tabs
                        .single { it.key == "reviews" }
                        .count,
                )
            }
        }

    @Test
    fun `a review without a delivered shipment of the product is posted unmarked`() =
        ownDatabase { dataSource ->
            // On its way, not received: not a verified purchase yet.
            dataSource.order("HL-48211", SampleCustomers.MAYA, "in_transit")
            signedIn(dataSource, "Maya Kowalski", SampleCustomers.MAYA) { token ->
                postReview(token, good).assertClosed()
                assertEquals(
                    "Oct 7",
                    customerTree(token, reviewsTab)
                        .only<ProductReviews>()
                        .reviews
                        .first()
                        .meta,
                )
            }
        }

    /** feature-reviews, «A second review». */
    @Test
    fun `a second review is refused with review_exists`() =
        ownDatabase { dataSource ->
            signedIn(dataSource, "Maya Kowalski", SampleCustomers.MAYA) { token ->
                postReview(token, good).assertClosed()
                postReview(
                    token,
                    good.copy(title = "Still worth it"),
                ).assertError(HttpStatusCode.Conflict, ErrorCode.ReviewExists)
                assertEquals("out of 5 · 2,342 reviews", customerTree(token, reviewsTab).only<ProductReviews>().caption)
            }
        }

    /** feature-reviews, «Too short». */
    @Test
    fun `a body of five characters is refused naming body`() =
        signedIn { token ->
            val error =
                postReview(
                    token,
                    good.copy(body = "Great"),
                ).assertError(HttpStatusCode.BadRequest, ErrorCode.ValidationFailed)
            assertEquals("body", error.field)
            assertEquals(listOf("body"), error.fields.map { it.field })
            assertEquals(ErrorCode.FieldInvalid, error.fields.single().code)
        }

    @Test
    fun `every field at fault is named at once`() =
        signedIn { token ->
            val error =
                postReview(token, ReviewEntry(rating = 0, title = " ", body = ""))
                    .assertError(HttpStatusCode.BadRequest, ErrorCode.ValidationFailed)
            assertEquals(listOf("rating", "title", "body"), error.fields.map { it.field })
            assertEquals(List(3) { ErrorCode.FieldRequired }, error.fields.map { it.code })
            val long =
                postReview(
                    token,
                    good.copy(rating = 6, title = "x".repeat(121)),
                ).assertError(HttpStatusCode.BadRequest, ErrorCode.ValidationFailed)
            assertEquals(listOf("rating", "title"), long.fields.map { it.field })
            post(ReviewPaths.reviews(sony)) {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody("{\"rating\": \"five\"}")
            }.assertError(
                HttpStatusCode.BadRequest,
                ErrorCode.ValidationFailed,
            ).also { assertEquals("request", it.field) }
        }

    @Test
    fun `the commands are a customer's`() =
        haulTest {
            postReview(null, good).assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
            ask(
                null,
                QuestionEntry("Does it fold flat?"),
            ).assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
        }

    @Test
    fun `the commands are for a product that exists`() =
        signedIn { token ->
            post(ReviewPaths.reviews("p-nope")) {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(haulWireJson.encodeToString(ReviewEntry.serializer(), good))
            }.assertError(HttpStatusCode.NotFound, ErrorCode.ProductNotFound)
            post(ReviewPaths.questions("p-nope")) {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(haulWireJson.encodeToString(QuestionEntry.serializer(), QuestionEntry("Does it fold flat?")))
            }.assertError(HttpStatusCode.NotFound, ErrorCode.ProductNotFound)
        }

    @Test
    fun `a question asked shows as not answered yet and is counted`() =
        ownDatabase { dataSource ->
            signedIn(dataSource, "Sam Ortiz", SampleCustomers.SAM) { token ->
                ask(token, QuestionEntry("  Do the ear cushions come off for cleaning?  ")).assertClosed()
                val questions = customerTree(token, questionsTab).only<ProductQuestions>()
                assertEquals("87", questions.count)
                val asked = questions.questions.single { it.question == "Do the ear cushions come off for cleaning?" }
                assertEquals("Asked Oct 7", asked.asked)
                assertEquals("Not answered yet", asked.pendingLabel)
                assertNull(asked.answer)
            }
        }

    @Test
    fun `a question shorter than ten characters is refused naming text`() =
        signedIn { token ->
            val error =
                ask(
                    token,
                    QuestionEntry("Fold?"),
                ).assertError(HttpStatusCode.BadRequest, ErrorCode.ValidationFailed)
            assertEquals(listOf("text"), error.fields.map { it.field })
            val long =
                ask(
                    token,
                    QuestionEntry("x".repeat(1_001)),
                ).assertError(HttpStatusCode.BadRequest, ErrorCode.ValidationFailed)
            assertEquals("Keep it to 1,000 characters", long.fields.single().message)
        }

    /** B-43: «Helpful» carries a customer's vote — the one they do not have — and a guest's way to sign in. */
    @Test
    fun `helpful is a customer's vote and a guest's way to sign in`() =
        signedIn { token ->
            val guest = tree(reviewsTab).only<ProductReviews>().reviews
            assertEquals(List(2) { NavigateAction(Frame.SIGN_IN) }, guest.map { it.helpfulAction })
            assertEquals(listOf(null, null), guest.map { it.helpfulCommand })
            val customer = customerTree(token, reviewsTab).only<ProductReviews>().reviews
            assertEquals(
                listOf(
                    HelpfulCommand(ReviewPaths.helpful(DANIEL), HelpfulVote(helpful = true)),
                    HelpfulCommand(ReviewPaths.helpful(AISHA), HelpfulVote(helpful = true)),
                ),
                customer.map { it.helpfulCommand },
            )
            assertEquals(listOf(null, null), customer.map { it.helpfulAction })
        }

    /**
     * B-43: a vote moves the review's count by one and the second press — the command the redrawn tree now
     * carries — moves it back. The count is the stored one: Aisha's seeded 48 have no rows behind them.
     */
    @Test
    fun `a vote moves the count by one and the second press moves it back`() =
        ownDatabase { dataSource ->
            signedIn(dataSource, "Sam Ortiz", SampleCustomers.SAM) { token ->
                val press = review(token, "Aisha K.").helpfulCommand!!
                vote(token, press.url, press.vote).assertRefreshed()
                val voted = review(token, "Aisha K.")
                assertEquals("49 people found this helpful", voted.helpful)
                assertEquals(HelpfulVote(helpful = false), voted.helpfulCommand?.vote)
                vote(token, voted.helpfulCommand!!.url, voted.helpfulCommand!!.vote).assertRefreshed()
                val back = review(token, "Aisha K.")
                assertEquals("48 people found this helpful", back.helpful)
                assertEquals(HelpfulVote(helpful = true), back.helpfulCommand?.vote)
                // Nobody had found Daniel's helpful: one vote is one person.
                vote(token, ReviewPaths.helpful(DANIEL), HelpfulVote(helpful = true)).assertRefreshed()
                assertEquals("1 person found this helpful", review(token, "Daniel R.").helpful)
            }
        }

    /**
     * B-43: one vote per customer per review. The same vote sent again — a retried press, a double click —
     * or several at once counts once, and taking it back twice takes back one: the vote's row is the
     * primary key of `helpful_votes`, and the count moves only when the row went in or out.
     */
    @Test
    fun `one customer's vote sent twice or at once counts once`() =
        ownDatabase { dataSource ->
            signedIn(dataSource, "Sam Ortiz", SampleCustomers.SAM) { token ->
                val url = ReviewPaths.helpful(AISHA)
                vote(token, url, HelpfulVote(helpful = true)).assertRefreshed()
                vote(token, url, HelpfulVote(helpful = true)).assertRefreshed()
                coroutineScope {
                    List(4) { async { vote(token, url, HelpfulVote(helpful = true)) } }
                        .awaitAll()
                        .forEach { it.assertRefreshed() }
                }
                assertEquals("49 people found this helpful", review(token, "Aisha K.").helpful)
                assertEquals(1, dataSource.votes(AISHA), "one row per customer per review")
                vote(token, url, HelpfulVote(helpful = false)).assertRefreshed()
                vote(token, url, HelpfulVote(helpful = false)).assertRefreshed()
                assertEquals("48 people found this helpful", review(token, "Aisha K.").helpful)
                assertEquals(0, dataSource.votes(AISHA))
            }
        }

    /** B-43: the author cannot vote on their own review — no vote in the tree, and `409 own_review`. */
    @Test
    fun `a vote on one's own review is refused with own_review`() =
        ownDatabase { dataSource ->
            signedIn(dataSource, "Maya Kowalski", SampleCustomers.MAYA) { token ->
                postReview(token, good).assertClosed()
                val mine = review(token, "Maya K.")
                assertNull(mine.helpfulCommand, "the author's own review carries no vote")
                assertNull(mine.helpfulAction)
                val id = dataSource.reviewOf(SampleCustomers.MAYA)
                vote(token, ReviewPaths.helpful(id), HelpfulVote(helpful = true))
                    .assertError(HttpStatusCode.Conflict, ErrorCode.OwnReview)
                assertNull(review(token, "Maya K.").helpful, "the refused vote was counted")
                assertEquals(0, dataSource.votes(id))
                // Her vote on somebody else's review still counts.
                vote(token, ReviewPaths.helpful(AISHA), HelpfulVote(helpful = true)).assertRefreshed()
                assertEquals("49 people found this helpful", review(token, "Aisha K.").helpful)
            }
        }

    @Test
    fun `a vote is a customer's for a review that exists`() =
        signedIn { token ->
            vote(null, ReviewPaths.helpful(AISHA), HelpfulVote(helpful = true))
                .assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
            vote(token, ReviewPaths.helpful("r-nope"), HelpfulVote(helpful = true))
                .assertError(HttpStatusCode.NotFound, ErrorCode.ReviewNotFound)
            put(ReviewPaths.helpful(AISHA)) {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody("{}")
            }.assertError(HttpStatusCode.BadRequest, ErrorCode.ValidationFailed)
                .also { assertEquals("request", it.field) }
            assertEquals("48 people found this helpful", review(token, "Aisha K.").helpful)
        }

    private fun signedIn(
        dataSource: DataSource = SeededDatabase.dataSource,
        name: String = "Reviewer",
        id: String? = null,
        block: suspend HttpClient.(token: String) -> Unit,
    ) {
        val sub = if (id == null) ShildikHarness.person(name) else ShildikHarness.person(name, id)
        val token = ShildikHarness.accessToken(sub)
        haulTest(dataSource, signIn = ShildikHarness.signIn) { block(token) }
    }

    private suspend fun HttpClient.customerTree(
        token: String,
        path: String,
    ): KompotComponent {
        val response = get(path) { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return haulWireJson.decodeKompotComponent(response.bodyAsText())
    }

    private suspend fun HttpClient.postReview(
        token: String?,
        entry: ReviewEntry,
    ): HttpResponse =
        post(ReviewPaths.reviews(sony)) {
            token?.let { bearerAuth(it) }
            contentType(ContentType.Application.Json)
            setBody(haulWireJson.encodeToString(ReviewEntry.serializer(), entry))
        }

    private suspend fun HttpClient.ask(
        token: String?,
        entry: QuestionEntry,
    ): HttpResponse =
        post(ReviewPaths.questions(sony)) {
            token?.let { bearerAuth(it) }
            contentType(ContentType.Application.Json)
            setBody(haulWireJson.encodeToString(QuestionEntry.serializer(), entry))
        }

    private suspend fun HttpClient.vote(
        token: String?,
        url: String,
        vote: HelpfulVote,
    ): HttpResponse =
        put(url) {
            token?.let { bearerAuth(it) }
            contentType(ContentType.Application.Json)
            setBody(haulWireJson.encodeToString(HelpfulVote.serializer(), vote))
        }

    /** The review by [author] on the reviews tab, as [token]'s customer sees it. */
    private suspend fun HttpClient.review(
        token: String,
        author: String,
    ): Review = customerTree(token, reviewsTab).only<ProductReviews>().reviews.single { it.author == author }

    /** A vote done: `200` and the page drawn again. */
    private suspend fun HttpResponse.assertRefreshed() {
        assertEquals(HttpStatusCode.OK, status, bodyAsText())
        assertEquals(RefreshAction, haulWireJson.decodeKompotAction(bodyAsText()))
    }

    /** How many `helpful_votes` rows [reviewId] has, read past the repository. */
    private fun DataSource.votes(reviewId: String): Int =
        connection.use { c ->
            c.prepareStatement("SELECT count(*) FROM helpful_votes WHERE review_id = ?").use {
                it.setString(1, reviewId)
                it.executeQuery().use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
        }

    /** The id of [customer]'s review of the headphones. */
    private fun DataSource.reviewOf(customer: String): String =
        connection.use { c ->
            c.prepareStatement("SELECT id FROM reviews WHERE customer_id = ? AND product_id = ?").use {
                it.setString(1, customer)
                it.setString(2, sony)
                it.executeQuery().use { rows ->
                    check(rows.next()) { "$customer has no review of $sony" }
                    rows.getString(1)
                }
            }
        }

    /** A dialog's command done: `201`, the dialog closed and the page drawn again. */
    private suspend fun HttpResponse.assertClosed() {
        assertEquals(HttpStatusCode.Created, status, bodyAsText())
        assertEquals(SequenceAction(listOf(CloseAction, RefreshAction)), haulWireJson.decodeKompotAction(bodyAsText()))
    }

    /** One order of [customer]'s holding the headphones from the Sony store, its shipment [status]. */
    private fun DataSource.order(
        id: String,
        customer: String,
        status: String,
    ) {
        connection.use { c ->
            c.createStatement().use {
                it.executeUpdate(
                    """
                    INSERT INTO orders (id, saga_id, customer_id, status, method, payment, items_cents, discount_cents,
                        delivery_cents, total_cents, points, placed_at)
                    VALUES ('$id', 'saga-$id', '$customer', 'placed', 'courier', 'card', 34900, 0, 0, 34900, 349,
                        '2025-09-20T10:00:00-04:00');
                    INSERT INTO order_lines (order_id, position, sku_id, seller_id, title, quantity, price_cents, list_cents)
                    VALUES ('$id', 1, '$sony-0', '$SONY_STORE', 'WH-1000XM6 Wireless Noise Cancelling Headphones', 1,
                        34900, 44900);
                    INSERT INTO shipments (id, order_id, seller_id, position, status)
                    VALUES ('$id-1', '$id', '$SONY_STORE', 1, '$status');
                    """.trimIndent(),
                )
            }
            c.commit()
        }
    }

    /** A seeded database of the test's own, its pool closed afterwards. */
    private fun ownDatabase(block: (DataSource) -> Unit) = seededFreshDatabase().use(block)

    private companion object {
        /** The seed's two reviews of the headphones (`SampleReviews`): nobody's, so anyone may vote. */
        const val DANIEL = "r-sony-daniel"
        const val AISHA = "r-sony-aisha"
    }
}
