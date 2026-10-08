package io.github.youndie.haul.feature.reviews.screen

import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.Listed
import io.github.youndie.haul.feature.catalog.domain.Seller
import io.github.youndie.haul.feature.catalog.domain.Sku
import io.github.youndie.haul.feature.catalog.domain.count
import io.github.youndie.haul.feature.reviews.ReviewPaths
import io.github.youndie.haul.feature.reviews.ReviewRules
import io.github.youndie.haul.feature.reviews.domain.ReviewRepository
import io.github.youndie.haul.feature.reviews.domain.StoredQuestion
import io.github.youndie.haul.feature.reviews.domain.StoredReview
import io.github.youndie.haul.feature.reviews.groupedCount
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.ui.FormProduct
import io.github.youndie.haul.ui.HistogramBar
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
 * which posts to `endpoint-reviews`; for a guest, the way to sign in, as the header's account shortcut.
 * The «Helpful» button carries nothing: voting (`PUT /api/v1/reviews/{id}/helpful`) is not built (B-22
 * findings).
 */
internal class ReviewTabs(
    private val reviews: ReviewRepository,
) {
    suspend fun reviews(
        item: Listed,
        sku: Sku,
        viewer: Viewer,
    ): ProductReviews {
        val product = item.product
        val counts = reviews.ratingCounts(product.id)
        val total = counts.values.sum()
        return ProductReviews(
            id = "reviews",
            rating = product.rating.toPlainString(),
            caption = "out of 5 · ${count(product.reviewsCount)} ${plural(product.reviewsCount, "review")}",
            histogram =
                (STARS downTo 1).map { stars ->
                    HistogramBar(stars, if (total == 0) 0 else ((counts[stars] ?: 0) * PERCENT / total).roundToInt())
                },
            actionLabel = "Write a review",
            reviews = reviews.reviews(product.id, SHOWN).map(::review),
            action = forCustomer(viewer) { reviewForm(item, sku) },
        )
    }

    suspend fun questions(
        item: Listed,
        sku: Sku,
        seller: Seller,
        viewer: Viewer,
    ): ProductQuestions {
        val product = item.product
        return ProductQuestions(
            id = "questions",
            count = count(product.questionsCount),
            caption = plural(product.questionsCount, "question"),
            text = "Answers come from ${seller.name}.",
            actionLabel = "Ask a question",
            questions = reviews.questions(product.id, SHOWN).map { question(it, seller) },
            action = forCustomer(viewer) { questionForm(item, sku, seller) },
        )
    }

    private fun review(review: StoredReview): Review =
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
        )

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

    private fun reviewForm(
        item: Listed,
        sku: Sku,
    ): ReviewForm =
        ReviewForm(
            id = "review-form",
            title = "Write a review",
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
        seller: Seller,
    ): QuestionForm =
        QuestionForm(
            id = "question-form",
            title = "Ask a question",
            product = formProduct(item, sku),
            label = "Your question",
            hint = "${groupedCount(ReviewRules.QUESTION_MIN)} – ${groupedCount(ReviewRules.QUESTION_MAX)} characters",
            note = "${seller.name} answers questions. You'll see the answer on this page.",
            submitLabel = "Send",
            cancelLabel = "Cancel",
            url = ReviewPaths.questions(item.product.id),
            close = CloseAction,
        )

    /**
     * The product as a dialog names it: brand and title, the chosen SKU's options — the colour first, as
     * the canvas writes «Midnight Black · Headphones only»; `jsonb` keeps an object's keys in its own
     * order — and its tile.
     */
    private fun formProduct(
        item: Listed,
        sku: Sku,
    ): FormProduct =
        FormProduct(
            name = "${item.product.brand} ${item.product.title}",
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

    private fun day(at: OffsetDateTime): String = DAY.format(at.atZoneSameInstant(DeliveryCalendar.STORE))

    private fun plural(
        n: Int,
        word: String,
    ): String = if (n == 1) word else "${word}s"

    companion object {
        /** How many reviews or questions a tab lists (feature-reviews: 10 per page; no artboard draws a second page). */
        const val SHOWN = 10

        /** What kompot's `present` is asked to show the form as: a dialog over the page. */
        const val DIALOG = "dialog"
        private const val COLOUR = "colour"
        private const val STARS = 5
        private const val PERCENT = 100.0
        private val DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    }
}
