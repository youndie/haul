package io.github.youndie.haul.feature.reviews.screen

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.Listed
import io.github.youndie.haul.feature.catalog.domain.Seller
import io.github.youndie.haul.feature.catalog.domain.Sku
import io.github.youndie.haul.feature.catalog.domain.count
import io.github.youndie.haul.feature.catalog.screen.ProductScreen
import io.github.youndie.haul.feature.catalog.screen.ProductTab
import io.github.youndie.haul.feature.reviews.HelpfulCommand
import io.github.youndie.haul.feature.reviews.HelpfulVote
import io.github.youndie.haul.feature.reviews.ReviewPaths
import io.github.youndie.haul.feature.reviews.ReviewRules
import io.github.youndie.haul.feature.reviews.domain.ReviewRepository
import io.github.youndie.haul.feature.reviews.domain.StoredQuestion
import io.github.youndie.haul.feature.reviews.domain.StoredReview
import io.github.youndie.haul.groupedCount
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Parts
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.ui.FormProduct
import io.github.youndie.haul.ui.HistogramBar
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.ProductQuestions
import io.github.youndie.haul.ui.ProductReviews
import io.github.youndie.haul.ui.Question
import io.github.youndie.haul.ui.QuestionForm
import io.github.youndie.haul.ui.Review
import io.github.youndie.haul.ui.ReviewForm
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.CloseAction
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.PresentAction
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The product page's reviews and questions tabs (screen-product: Product_Reviews, Product_Questions), and
 * the two dialogs their buttons present (Product_ReviewDialog, Product_QuestionDialog).
 *
 * «Write a review» and «Ask a question» are a customer's: for one, kompot's `present` of the dialog's form,
 * which posts to `endpoint-reviews`; for a guest, the way to sign in, as the header's account shortcut. A
 * customer who has reviewed the product is not offered a second review the route would refuse: their review
 * leads the list and the button's place says when they wrote it (B-99).
 * «Helpful» on each review is a customer's vote (B-43, `PUT /api/v1/reviews/{id}/helpful`), and the way to
 * sign in for a guest.
 *
 * Each tab lists the first `shown` (ten unless the address says more) and, while the product has more stored
 * than that, «Show more reviews» or «Show more questions»: kompot's `load` of the tab's address listing ten
 * more, answered with the tab's node (B-71, as a page of results is since B-63). The counts beside the tabs
 * are the product's, and since B-99 they are its rows — the seed stores the canvas's 2,341 reviews one by one
 * ([io.github.youndie.haul.seed.SeedReviews]) — so every review counted can be reached; «more» follows the
 * rows: one more is asked for than is drawn.
 */
internal class ReviewTabs(
    private val reviews: ReviewRepository,
    private val clock: StoreClock,
) {
    suspend fun reviews(
        item: Listed,
        sku: Sku,
        viewer: Viewer,
        shown: Int = SHOWN,
    ): ProductReviews {
        val product = item.product
        val counts = reviews.ratingCounts(product.id)
        val total = counts.values.sum()
        val found = reviews.reviews(product.id, shown + 1)
        val own = viewer.customerId?.let { reviews.reviewBy(it, product.id) }
        // The viewer's own review leads the list, wherever its date would put it (B-99).
        val listed = (listOfNotNull(own) + found.filter { it.id != own?.id }).take(shown)
        val voted = viewer.customerId?.let { reviews.votedHelpful(it, listed.map(StoredReview::id)) }.orEmpty()
        return ProductReviews(
            id = "reviews",
            rating = product.rating.toPlainString(),
            caption = "out of 5 · ${count(product.reviewsCount)} ${plural(product.reviewsCount, "review")}",
            histogram =
                (STARS downTo 1).map { stars ->
                    HistogramBar(stars, if (total == 0) 0 else ((counts[stars] ?: 0) * PERCENT / total).roundToInt())
                },
            actionLabel = own?.let { "You reviewed this on ${day(it.createdAt)}" } ?: WRITE_REVIEW,
            reviews = listed.map { review(it, viewer, it.id in voted) },
            action = if (own == null) writeReview(item, sku, viewer) else null,
            moreLabel = if (found.size > shown) "Show more reviews" else null,
            more = if (found.size > shown) more(item, sku, ProductTab.Reviews, shown) else null,
        )
    }

    suspend fun questions(
        item: Listed,
        sku: Sku,
        seller: Seller,
        viewer: Viewer,
        shown: Int = SHOWN,
    ): ProductQuestions {
        val product = item.product
        val found = reviews.questions(product.id, shown + 1)
        return ProductQuestions(
            id = "questions",
            count = count(product.questionsCount),
            caption = plural(product.questionsCount, "question"),
            text = "Answers come from ${seller.name}.",
            actionLabel = "Ask a question",
            questions = found.take(shown).map { question(it, seller) },
            action = forCustomer(viewer) { questionForm(item, sku) },
            moreLabel = if (found.size > shown) "Show more questions" else null,
            more = if (found.size > shown) more(item, sku, ProductTab.Questions, shown) else null,
        )
    }

    /** The tab listing ten more than [shown], loaded in place (B-71). */
    private fun more(
        item: Listed,
        sku: Sku,
        tab: ProductTab,
        shown: Int,
    ): KompotAction = Parts.load(ProductScreen.address(item, sku, tab, shown + SHOWN))

    /**
     * [review] as the page draws it for [viewer], who [voted] it helpful or not: «Helpful» is their vote —
     * the opposite of the one they have — for a customer, the way to sign in for a guest, and nothing on
     * the viewer's own review, which the route would refuse (`409 own_review`): there it reads «Your review»,
     * drawn as words (B-71).
     */
    private fun review(
        review: StoredReview,
        viewer: Viewer,
        voted: Boolean,
    ): Review =
        Review(
            author = review.author,
            initial = review.author.take(1).uppercase(),
            tone = review.tone,
            meta = day(review.createdAt) + if (review.verified) " · Verified purchase" else "",
            score = "${review.rating}.0",
            title = review.title,
            text = review.body,
            photos = review.photos,
            helpful =
                when (review.helpful) {
                    0 -> null
                    1 -> "1 person found this helpful"
                    else -> "${count(review.helpful)} people found this helpful"
                },
            helpfulLabel = if (own(review, viewer)) OWN_REVIEW else HELPFUL,
            helpfulAction = if (viewer.customerId == null) NavigateAction(Frame.SIGN_IN) else null,
            helpfulCommand =
                viewer.customerId?.takeUnless { own(review, viewer) }?.let {
                    HelpfulCommand(ReviewPaths.helpful(review.id), HelpfulVote(helpful = !voted))
                },
            helpfulVoted = voted,
        )

    private fun own(
        review: StoredReview,
        viewer: Viewer,
    ): Boolean = viewer.customerId != null && viewer.customerId == review.customerId

    private fun question(
        question: StoredQuestion,
        seller: Seller,
    ): Question =
        Question(
            question = question.text,
            asked = "Asked ${day(question.askedAt)}",
            answer = question.answer,
            answeredBy = question.answeredAt?.let { "${seller.name} · ${day(it)}" },
            pendingLabel = if (question.answer == null) "Not answered yet" else null,
        )

    /**
     * What a delivered line of an order offers for [sku] of [item] (B-18): «Write a review», the review dialog,
     * while [viewer] has not [reviewed] the product, and once they have «Your review», the product's reviews
     * tab, where theirs leads the list — not a dialog whose Post would answer `409 review_exists` (B-99).
     */
    fun orderLine(
        item: Listed,
        sku: Sku,
        viewer: Viewer,
        reviewed: Boolean,
    ): Link =
        if (reviewed) {
            Link(YOUR_REVIEW, NavigateAction(ProductScreen.address(item, sku, ProductTab.Reviews)))
        } else {
            Link(WRITE_REVIEW, writeReview(item, sku, viewer))
        }

    /** The products among [productIds] that [customerId] has reviewed. */
    suspend fun reviewed(
        customerId: String,
        productIds: Collection<String>,
    ): Set<String> = reviews.reviewed(customerId, productIds)

    /**
     * «Write a review» for [sku] of [item] wherever it is drawn — the product's reviews, and a delivered line
     * of an order (B-18): the review dialog for a customer, sign-in for a guest.
     */
    fun writeReview(
        item: Listed,
        sku: Sku,
        viewer: Viewer,
    ): KompotAction = forCustomer(viewer) { reviewForm(item, sku) }

    private fun reviewForm(
        item: Listed,
        sku: Sku,
    ): ReviewForm =
        ReviewForm(
            id = "review-form",
            title = WRITE_REVIEW,
            product = formProduct(item, sku),
            ratingLabel = "Your rating",
            titleLabel = "Title",
            bodyLabel = "Review",
            bodyHint = "At least ${ReviewRules.BODY_MIN} characters",
            submitLabel = "Post",
            cancelLabel = "Cancel",
            url = ReviewPaths.reviews(item.product.id),
            close = CloseAction,
        )

    private fun questionForm(
        item: Listed,
        sku: Sku,
    ): QuestionForm =
        QuestionForm(
            id = "question-form",
            title = "Ask a question",
            product = formProduct(item, sku),
            label = "Your question",
            hint = "${groupedCount(ReviewRules.QUESTION_MIN)} – ${groupedCount(ReviewRules.QUESTION_MAX)} characters",
            note = QUESTION_NOTE,
            submitLabel = "Send",
            cancelLabel = "Cancel",
            url = ReviewPaths.questions(item.product.id),
            close = CloseAction,
        )

    /**
     * The product as a dialog names it: its listing name, as its card, cart line and order line write it
     * (B-45, B-60) — not brand and title, which named the duvet «Brooklyn Home Co. Linen Duvet …» after its
     * seller — then the chosen SKU's options, the colour first, as the canvas writes «Midnight Black ·
     * Headphones only» (`jsonb` keeps an object's keys in its own order), and its tile.
     */
    private fun formProduct(
        item: Listed,
        sku: Sku,
    ): FormProduct =
        FormProduct(
            name = item.product.listingName,
            detail =
                sku.options.entries
                    .sortedBy { if (it.key == COLOUR) 0 else 1 }
                    .joinToString(" · ") { it.value },
            tone = item.product.tone,
        )

    private fun forCustomer(
        viewer: Viewer,
        dialog: () -> KompotComponent,
    ): KompotAction = if (viewer.customerId == null) NavigateAction(Frame.SIGN_IN) else PresentAction(dialog(), DIALOG)

    /**
     * «Sep 28» within the store's current year, as the canvas writes it, and «Oct 21, 2023» outside it (B-129): a
     * seeded review two years old read «Oct 21» on Oct 11, a date that reads as the future.
     */
    private fun day(at: OffsetDateTime): String {
        val local = at.atZoneSameInstant(DeliveryCalendar.STORE)
        val thisYear = clock.now().withZoneSameInstant(DeliveryCalendar.STORE).year
        return (if (local.year == thisYear) DAY else DAY_YEAR).format(local)
    }

    private fun plural(
        n: Int,
        word: String,
    ): String = if (n == 1) word else "${word}s"

    companion object {
        /**
         * How many reviews or questions a tab lists at first, and how many more each «Show more» adds
         * (feature-reviews: 10 per page; no artboard draws a second page).
         */
        const val SHOWN = 10

        /** The most a tab lists, however many presses of «Show more» the address says (B-71). */
        const val MAX_SHOWN = 500

        private const val HELPFUL = "Helpful"
        private const val OWN_REVIEW = "Your review"
        private const val WRITE_REVIEW = "Write a review"
        private const val YOUR_REVIEW = "Your review"

        /**
         * What the question dialog says happens (B-99): the question is shown on the page, unanswered, and
         * nothing answers it — the seed's answers are the only ones there are (feature-reviews: no seller
         * side). It promised «Sony answers questions. You'll see the answer on this page.» before.
         */
        const val QUESTION_NOTE =
            "Your question appears on this page for other shoppers, marked «Not answered yet». " +
                "Sellers can't answer new questions on Haul yet."

        /** What kompot's `present` is asked to show the form as: a dialog over the page. */
        const val DIALOG = "dialog"
        private const val COLOUR = "colour"
        private const val STARS = 5
        private const val PERCENT = 100.0
        private val DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US)
        private val DAY_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    }
}
