package io.github.youndie.haul.feature.reviews.domain

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.identity.domain.Customer
import io.github.youndie.haul.feature.reviews.QuestionEntry
import io.github.youndie.haul.feature.reviews.ReviewEntry
import io.github.youndie.haul.feature.reviews.questionProblems
import io.github.youndie.haul.feature.reviews.reviewProblems
import java.util.UUID

/**
 * feature-reviews' two commands, both a customer's: posting a review and asking a question. The rules are
 * the contract's (`reviewProblems`, `questionProblems`), the ones the client checks before sending.
 *
 * Who may review: any customer, once per product. A review is «Verified purchase» when its author has
 * received the product — a delivered shipment holding it — at the moment it is written; without one it is
 * posted all the same, unmarked. Text is stored without its leading and trailing blanks.
 */
internal class ReviewCommands(
    private val reviews: ReviewRepository,
    private val catalog: CatalogRepository,
    private val clock: StoreClock,
) {
    suspend fun post(
        customer: Customer,
        productId: String,
        entry: ReviewEntry,
    ): StoredReview {
        catalog.product(productId) ?: throw ReviewError.ProductNotFound(productId)
        reviewProblems(entry).takeIf { it.isNotEmpty() }?.let { throw ReviewError.Refused(it) }
        if (reviews.hasReviewed(customer.id, productId)) throw ReviewError.Exists()
        val review =
            StoredReview(
                id = "r-${UUID.randomUUID()}",
                productId = productId,
                customerId = customer.id,
                author = authorName(customer.name),
                rating = entry.rating,
                title = entry.title.trim(),
                body = entry.body.trim(),
                verified = reviews.received(customer.id, productId),
                helpful = 0,
                tone = avatarTone(customer.id),
                photos = emptyList(),
                createdAt = clock.now().toOffsetDateTime(),
            )
        // Two posts at once both pass `hasReviewed`; the unique index lets one in.
        if (!reviews.write(review)) throw ReviewError.Exists()
        return review
    }

    suspend fun ask(
        customer: Customer,
        productId: String,
        entry: QuestionEntry,
    ): StoredQuestion {
        catalog.product(productId) ?: throw ReviewError.ProductNotFound(productId)
        questionProblems(entry).takeIf { it.isNotEmpty() }?.let { throw ReviewError.Refused(it) }
        val question =
            StoredQuestion(
                id = "q-${UUID.randomUUID()}",
                productId = productId,
                customerId = customer.id,
                text = entry.text.trim(),
                askedAt = clock.now().toOffsetDateTime(),
            )
        reviews.ask(question)
        return question
    }

    companion object {
        /** The canvas's tile tones (`canvas.json`, `tileTones`), one per author, so an avatar keeps its colour. */
        private val TONES =
            listOf("#E6E4FF", "#F1EBDD", "#FFE5DD", "#F1E4F5", "#FFF1C9", "#E3F5D8", "#E9F0D2", "#E0EEF7", "#ECE9E2")

        /** «Maya K.» for «Maya Kowalski», as the canvas signs a review; a single name stays whole. */
        fun authorName(name: String): String {
            val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            return when {
                words.isEmpty() -> "A customer"
                words.size == 1 -> words.single()
                else -> "${words.first()} ${words.last().first().uppercaseChar()}."
            }
        }

        fun avatarTone(customerId: String): String = TONES[Math.floorMod(customerId.hashCode(), TONES.size)]
    }
}
