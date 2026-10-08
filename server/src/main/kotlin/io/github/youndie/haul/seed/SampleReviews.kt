package io.github.youndie.haul.seed

import io.github.youndie.haul.feature.reviews.domain.StoredQuestion
import io.github.youndie.haul.feature.reviews.domain.StoredReview
import io.github.youndie.haul.seed.SampleCatalog.SONY_HEADPHONES
import java.time.OffsetDateTime

/**
 * The headphones' reviews and questions as Product_Reviews and Product_Questions draw them (the canvas's
 * copy), and the histogram behind «4.8 out of 5 · 2,341 reviews». Answers are seed data only: there is no
 * seller side (feature-reviews).
 *
 * The two reviews' authors have no account here (`customerId` `null`): they stand for the 2,341 reviews
 * the canvas counts, which the seed does not hold one by one; so do the histogram's counts, which give the
 * canvas's 78 / 14 / 4 / 2 / 2 % of 2,341.
 */
internal object SampleReviews {
    val reviews: List<StoredReview> =
        listOf(
            StoredReview(
                id = "r-sony-daniel",
                productId = SONY_HEADPHONES,
                customerId = null,
                author = "Daniel R.",
                rating = 5,
                title = "The quietest flight I've had",
                body =
                    "Wore them on a 9-hour flight. Engine noise basically disappears and they stay comfortable " +
                        "the whole way. Battery was at 60% when we landed.",
                verified = true,
                helpful = 0,
                tone = "#E6E4FF",
                photos = listOf("#E9E6E0", "#E6E4FF"),
                createdAt = at("2025-09-28T14:10:00-04:00"),
            ),
            StoredReview(
                id = "r-sony-aisha",
                productId = SONY_HEADPHONES,
                customerId = null,
                author = "Aisha K.",
                rating = 4,
                title = "Great sound, tight at first",
                body =
                    "Clear, balanced sound and the app EQ is easy to use. The headband felt tight for the first " +
                        "few days, then loosened up. Calls sound good even outside.",
                verified = true,
                helpful = 48,
                tone = "#FFE5DD",
                photos = emptyList(),
                createdAt = at("2025-09-21T09:30:00-04:00"),
            ),
        )

    /** Stars to how many reviews gave them: 1,826 + 328 + 94 + 47 + 46 = 2,341, the product's count. */
    val ratingCounts: Map<String, Map<Int, Int>> =
        mapOf(SONY_HEADPHONES to mapOf(5 to 1_826, 4 to 328, 3 to 94, 2 to 47, 1 to 46))

    val questions: List<StoredQuestion> =
        listOf(
            StoredQuestion(
                id = "q-sony-cable",
                productId = SONY_HEADPHONES,
                customerId = null,
                text = "Can I use them with a cable on a plane?",
                askedAt = at("2025-09-29T11:00:00-04:00"),
                answer = "Yes. The 3.5 mm audio cable is in the box, and noise cancelling keeps working when wired.",
                answeredAt = at("2025-09-30T10:15:00-04:00"),
            ),
            StoredQuestion(
                id = "q-sony-multipoint",
                productId = SONY_HEADPHONES,
                customerId = null,
                text = "Do they connect to a laptop and a phone at the same time?",
                askedAt = at("2025-09-14T16:40:00-04:00"),
                answer = "Yes. Multipoint keeps two devices paired and switches to the phone when a call comes in.",
                answeredAt = at("2025-09-15T09:05:00-04:00"),
            ),
            StoredQuestion(
                id = "q-sony-headband",
                productId = SONY_HEADPHONES,
                customerId = null,
                text = "Is the headband adjustable enough for a small head?",
                askedAt = at("2025-10-06T20:25:00-04:00"),
            ),
        )

    private fun at(instant: String): OffsetDateTime = OffsetDateTime.parse(instant)
}
