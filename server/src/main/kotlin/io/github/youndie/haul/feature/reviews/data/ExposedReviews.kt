package io.github.youndie.haul.feature.reviews.data

import io.github.youndie.haul.feature.catalog.data.ProductsTable
import io.github.youndie.haul.feature.catalog.data.SkusTable
import io.github.youndie.haul.feature.order.data.OrderLinesTable
import io.github.youndie.haul.feature.order.data.OrdersTable
import io.github.youndie.haul.feature.order.data.ShipmentsTable
import io.github.youndie.haul.feature.order.domain.ShipmentStatus
import io.github.youndie.haul.feature.reviews.domain.HelpfulOutcome
import io.github.youndie.haul.feature.reviews.domain.Ratings
import io.github.youndie.haul.feature.reviews.domain.ReviewRepository
import io.github.youndie.haul.feature.reviews.domain.StoredQuestion
import io.github.youndie.haul.feature.reviews.domain.StoredReview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime

/**
 * Reviews, questions and «Helpful» votes over Exposed. Writing a review takes its product's row first
 * (`FOR UPDATE`), so two reviews of one product count into it one after the other and neither average is
 * lost. A vote needs no lock: the count moves by an increment in SQL, and only when the vote's row went in
 * or out.
 */
internal class ExposedReviews(
    private val database: Database,
) : ReviewRepository {
    private suspend fun <T> tx(block: () -> T): T = withContext(Dispatchers.IO) { transaction(database) { block() } }

    override suspend fun reviews(
        productId: String,
        limit: Int,
        stars: Int?,
    ): List<StoredReview> =
        tx {
            ReviewsTable
                .selectAll()
                .where {
                    val product = ReviewsTable.productId eq productId
                    if (stars == null) product else product and (ReviewsTable.rating eq stars)
                }.orderBy(ReviewsTable.createdAt to SortOrder.DESC, ReviewsTable.id to SortOrder.ASC)
                .limit(limit)
                .map(::review)
        }

    override suspend fun ratingCounts(productId: String): Map<Int, Int> =
        tx {
            RatingCountsTable
                .selectAll()
                .where { RatingCountsTable.productId eq productId }
                .associate { it[RatingCountsTable.stars] to it[RatingCountsTable.count] }
        }

    override suspend fun questions(
        productId: String,
        limit: Int,
    ): List<StoredQuestion> =
        tx {
            QuestionsTable
                .selectAll()
                .where { QuestionsTable.productId eq productId }
                .orderBy(
                    QuestionsTable.answer.isNotNull() to SortOrder.DESC,
                    QuestionsTable.askedAt to SortOrder.DESC,
                    QuestionsTable.id to SortOrder.ASC,
                ).limit(limit)
                .map(::question)
        }

    override suspend fun hasReviewed(
        customerId: String,
        productId: String,
    ): Boolean =
        tx {
            !ReviewsTable
                .select(ReviewsTable.id)
                .where { (ReviewsTable.productId eq productId) and (ReviewsTable.customerId eq customerId) }
                .empty()
        }

    override suspend fun reviewBy(
        customerId: String,
        productId: String,
    ): StoredReview? =
        tx {
            ReviewsTable
                .selectAll()
                .where { (ReviewsTable.productId eq productId) and (ReviewsTable.customerId eq customerId) }
                .singleOrNull()
                ?.let(::review)
        }

    override suspend fun reviewed(
        customerId: String,
        productIds: Collection<String>,
    ): Set<String> =
        if (productIds.isEmpty()) {
            emptySet()
        } else {
            tx {
                ReviewsTable
                    .select(ReviewsTable.productId)
                    .where { (ReviewsTable.customerId eq customerId) and (ReviewsTable.productId inList productIds) }
                    .mapTo(mutableSetOf()) { it[ReviewsTable.productId] }
            }
        }

    override suspend fun received(
        customerId: String,
        productId: String,
    ): Boolean =
        tx {
            // A shipment is one seller's part of an order, so the line is the order's with the shipment's seller.
            !ShipmentsTable
                .join(OrdersTable, JoinType.INNER, ShipmentsTable.orderId, OrdersTable.id)
                .join(
                    OrderLinesTable,
                    JoinType.INNER,
                    additionalConstraint = {
                        (OrderLinesTable.orderId eq ShipmentsTable.orderId) and
                            (OrderLinesTable.sellerId eq ShipmentsTable.sellerId)
                    },
                ).join(SkusTable, JoinType.INNER, OrderLinesTable.skuId, SkusTable.id)
                .select(ShipmentsTable.id)
                .where {
                    (OrdersTable.customerId eq customerId) and (SkusTable.productId eq productId) and
                        (ShipmentsTable.status inList RECEIVED)
                }.limit(1)
                .empty()
        }

    override suspend fun write(review: StoredReview): Boolean =
        tx {
            val product =
                ProductsTable
                    .select(ProductsTable.reviewsCount)
                    .where { ProductsTable.id eq review.productId }
                    .forUpdate()
                    .single()
            val inserted =
                ReviewsTable
                    .insertIgnore {
                        it[id] = review.id
                        it[productId] = review.productId
                        it[customerId] = review.customerId
                        it[author] = review.author
                        it[rating] = review.rating
                        it[title] = review.title
                        it[body] = review.body
                        it[verified] = review.verified
                        it[helpful] = review.helpful
                        it[tone] = review.tone
                        it[photos] = JsonArray(review.photos.map(::JsonPrimitive))
                        it[createdAt] = review.createdAt
                    }.insertedCount
            if (inserted == 0) return@tx false
            val counted =
                RatingCountsTable.update({
                    (RatingCountsTable.productId eq review.productId) and (RatingCountsTable.stars eq review.rating)
                }) { it[RatingCountsTable.count] = RatingCountsTable.count + 1 }
            if (counted == 0) {
                RatingCountsTable.insert {
                    it[productId] = review.productId
                    it[stars] = review.rating
                    it[RatingCountsTable.count] = 1
                }
            }
            // The rating is the histogram's, as it is now (B-86): one source for both.
            val histogram =
                RatingCountsTable
                    .select(RatingCountsTable.stars, RatingCountsTable.count)
                    .where { RatingCountsTable.productId eq review.productId }
                    .associate { it[RatingCountsTable.stars] to it[RatingCountsTable.count] }
            ProductsTable.update({ ProductsTable.id eq review.productId }) {
                it[reviewsCount] = product[ProductsTable.reviewsCount] + 1
                Ratings.average(histogram)?.let { average -> it[rating] = average }
            }
            true
        }

    override suspend fun ask(question: StoredQuestion) {
        tx {
            QuestionsTable.insert {
                it[id] = question.id
                it[productId] = question.productId
                it[customerId] = question.customerId
                it[text] = question.text
                it[askedAt] = question.askedAt
                it[answer] = question.answer
                it[answeredAt] = question.answeredAt
            }
            ProductsTable.update({ ProductsTable.id eq question.productId }) {
                it[questionsCount] = ProductsTable.questionsCount + 1
            }
        }
    }

    override suspend fun votedHelpful(
        customerId: String,
        reviewIds: Collection<String>,
    ): Set<String> =
        if (reviewIds.isEmpty()) {
            emptySet()
        } else {
            tx {
                HelpfulVotesTable
                    .select(HelpfulVotesTable.reviewId)
                    .where {
                        (HelpfulVotesTable.customerId eq customerId) and
                            (HelpfulVotesTable.reviewId inList reviewIds)
                    }.mapTo(mutableSetOf()) { it[HelpfulVotesTable.reviewId] }
            }
        }

    override suspend fun vote(
        reviewId: String,
        customerId: String,
        helpful: Boolean,
        at: OffsetDateTime,
    ): HelpfulOutcome =
        tx {
            val review =
                ReviewsTable
                    .select(ReviewsTable.customerId)
                    .where { ReviewsTable.id eq reviewId }
                    .singleOrNull() ?: return@tx HelpfulOutcome.NoReview
            if (review[ReviewsTable.customerId] == customerId) return@tx HelpfulOutcome.OwnReview
            // The row decides whether the count moves: a vote the customer already has inserts nothing
            // (the primary key, `ON CONFLICT DO NOTHING` — two at once included, the second waits for the
            // first), and taking back one they do not have deletes nothing.
            val moved =
                if (helpful) {
                    HelpfulVotesTable
                        .insertIgnore {
                            it[HelpfulVotesTable.reviewId] = reviewId
                            it[HelpfulVotesTable.customerId] = customerId
                            it[votedAt] = at
                        }.insertedCount
                } else {
                    HelpfulVotesTable.deleteWhere {
                        (HelpfulVotesTable.reviewId eq reviewId) and (HelpfulVotesTable.customerId eq customerId)
                    }
                }
            if (moved > 0) {
                ReviewsTable.update({ ReviewsTable.id eq reviewId }) {
                    it[ReviewsTable.helpful] =
                        if (helpful) ReviewsTable.helpful + moved else ReviewsTable.helpful - moved
                }
            }
            HelpfulOutcome.Counted
        }

    private fun review(row: ResultRow): StoredReview =
        StoredReview(
            id = row[ReviewsTable.id],
            productId = row[ReviewsTable.productId],
            customerId = row[ReviewsTable.customerId],
            author = row[ReviewsTable.author],
            rating = row[ReviewsTable.rating],
            title = row[ReviewsTable.title],
            body = row[ReviewsTable.body],
            verified = row[ReviewsTable.verified],
            helpful = row[ReviewsTable.helpful],
            tone = row[ReviewsTable.tone],
            photos = row[ReviewsTable.photos].map { it.jsonPrimitive.content },
            createdAt = row[ReviewsTable.createdAt],
        )

    private fun question(row: ResultRow): StoredQuestion =
        StoredQuestion(
            id = row[QuestionsTable.id],
            productId = row[QuestionsTable.productId],
            customerId = row[QuestionsTable.customerId],
            text = row[QuestionsTable.text],
            askedAt = row[QuestionsTable.askedAt],
            answer = row[QuestionsTable.answer],
            answeredAt = row[QuestionsTable.answeredAt],
        )

    private companion object {
        val RECEIVED = listOf(ShipmentStatus.DELIVERED, ShipmentStatus.PICKED_UP)
    }
}
