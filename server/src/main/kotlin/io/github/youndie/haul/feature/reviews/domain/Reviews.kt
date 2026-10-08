package io.github.youndie.haul.feature.reviews.domain

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.FieldError
import java.time.OffsetDateTime

// Reviews and questions as feature-reviews keeps them (research §5: `Review`, `Question`).

/** A review as the page draws it; [customerId] is `null` for the seed's authors, who have no account here. */
internal data class StoredReview(
    val id: String,
    val productId: String,
    val customerId: String?,
    val author: String,
    val rating: Int,
    val title: String,
    val body: String,
    val verified: Boolean,
    val helpful: Int,
    val tone: String,
    val photos: List<String>,
    val createdAt: OffsetDateTime,
)

/** A question, answered when [answer] is there (seed data only: there is no seller side). */
internal data class StoredQuestion(
    val id: String,
    val productId: String,
    val customerId: String?,
    val text: String,
    val askedAt: OffsetDateTime,
    val answer: String? = null,
    val answeredAt: OffsetDateTime? = null,
)

/** Where reviews and questions are kept; the product's count and average move with the review that changes them. */
internal interface ReviewRepository {
    /** The product's reviews, the newest first, at most [limit]. */
    suspend fun reviews(
        productId: String,
        limit: Int,
    ): List<StoredReview>

    /** How many reviews gave each number of stars, 1…5; a number nobody gave is absent. */
    suspend fun ratingCounts(productId: String): Map<Int, Int>

    /** The product's questions: the answered ones first, each group the newest first; at most [limit]. */
    suspend fun questions(
        productId: String,
        limit: Int,
    ): List<StoredQuestion>

    suspend fun hasReviewed(
        customerId: String,
        productId: String,
    ): Boolean

    /**
     * Whether [customerId] has received [productId]: a shipment of one of their orders that holds it was
     * delivered, or picked up (feature-reviews, «Verified purchase»).
     */
    suspend fun received(
        customerId: String,
        productId: String,
    ): Boolean

    /**
     * Stores [review] and, in the same transaction, counts it into its product: one more review, its stars
     * in the histogram, the average moved by it. Returns `false`, storing nothing, when its author already
     * has a review of the product.
     */
    suspend fun write(review: StoredReview): Boolean

    /** Stores [question] and counts it into its product's questions. */
    suspend fun ask(question: StoredQuestion)

    /** The ids among [reviewIds] that [customerId] has voted helpful. */
    suspend fun votedHelpful(
        customerId: String,
        reviewIds: Collection<String>,
    ): Set<String>

    /**
     * Leaves [customerId]'s «Helpful» on [reviewId] as [helpful], at [at]: a vote the customer did not have
     * is stored and the review's count goes up by one, a vote taken back is removed and the count goes down
     * by one — the row and the count in one transaction — and a vote already as asked changes nothing.
     */
    suspend fun vote(
        reviewId: String,
        customerId: String,
        helpful: Boolean,
        at: OffsetDateTime,
    ): HelpfulOutcome
}

/** What became of a «Helpful» vote. */
internal enum class HelpfulOutcome {
    /** The vote is as asked now, whether or not it had to move. */
    Counted,

    /** No review has that id. */
    NoReview,

    /** The review is the voter's own; nothing was stored. */
    OwnReview,
}

/** What a review or question command can refuse with; the application answers each with its status. */
internal sealed class ReviewError(
    val code: ErrorCode,
    override val message: String,
    val field: String? = null,
    val fields: List<FieldError> = emptyList(),
) : Exception(message) {
    class ProductNotFound(
        id: String,
    ) : ReviewError(ErrorCode.ProductNotFound, "No product «$id»")

    /** A form at fault: every field, each with its own code and sentence (`400 validation_failed`). */
    class Refused(
        fields: List<FieldError>,
    ) : ReviewError(ErrorCode.ValidationFailed, "The form has fields to fix", fields.first().field, fields)

    /** A body that is not the JSON the command takes. */
    class Invalid(
        field: String,
        message: String,
    ) : ReviewError(ErrorCode.ValidationFailed, message, field)

    /** One review per customer per product (feature-reviews, «A second review»). */
    class Exists : ReviewError(ErrorCode.ReviewExists, "You have already reviewed this product")

    class ReviewNotFound(
        id: String,
    ) : ReviewError(ErrorCode.ReviewNotFound, "No review «$id»")

    /** The author cannot vote on their own review (feature-reviews). */
    class OwnReview : ReviewError(ErrorCode.OwnReview, "You cannot vote on your own review")
}
