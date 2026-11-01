package io.github.youndie.haul.feature.product

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.feature.reviews.HelpfulCommand
import io.github.youndie.haul.feature.reviews.HelpfulVote
import io.github.youndie.haul.feature.reviews.QuestionEntry
import io.github.youndie.haul.feature.reviews.ReviewEntry
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.FormProduct
import io.github.youndie.haul.ui.ProductReviews
import io.github.youndie.haul.ui.QuestionForm
import io.github.youndie.haul.ui.Review
import io.github.youndie.haul.ui.ReviewForm
import kotlin.test.Test

/**
 * B-99 on the client: «Helpful» looks voted while the viewer's vote is on the review, a reviewer's tab says
 * so in words where «Write a review» was, and a text box takes a press anywhere in it, not on its first line
 * only. How each looks is in the goldens (`Product_Reviews_Mine`, the dialogs); this holds what a press and a
 * screen reader get.
 */
@OptIn(ExperimentalTestApi::class)
class ReviewStatesTest {
    @Test
    fun `a voted helpful is selected and an unvoted one is not`() =
        runDesktopComposeUiTest(1440, 900) {
            setContent {
                HaulTheme(FixtureFonts.fonts, compact = false) {
                    ProductReviewsView(
                        reviews(
                            review("Daniel R.", voted = true),
                            review("Aisha K.", voted = false),
                        ),
                    )
                }
            }
            onAllNodesWithTag(HELPFUL_TAG)[0].assertIsSelected()
            onAllNodesWithTag(HELPFUL_TAG)[1].assertIsNotSelected()
        }

    @Test
    fun `a reviewer's tab says when they reviewed in place of the button`() =
        runDesktopComposeUiTest(1440, 900) {
            setContent {
                HaulTheme(FixtureFonts.fonts, compact = false) {
                    ProductReviewsView(
                        reviews(review("Maya K.", voted = false)).copy(actionLabel = REVIEWED, action = null),
                    )
                }
            }
            onNodeWithTag(WRITE_REVIEW_TAG).assertTextEquals(REVIEWED)
        }

    @Test
    fun `a press low in the review box focuses it`() =
        runDesktopComposeUiTest(1440, 900) {
            setContent { HaulTheme(FixtureFonts.fonts, compact = false) { ReviewFormView(REVIEW_FORM, ReviewEntry()) } }
            pressLow(BODY_TAG)
        }

    @Test
    fun `a press low in the question box focuses it`() =
        runDesktopComposeUiTest(1440, 900) {
            setContent {
                HaulTheme(
                    FixtureFonts.fonts,
                    compact = false,
                ) { QuestionFormView(QUESTION_FORM, QuestionEntry()) }
            }
            pressLow(QUESTION_TAG)
        }

    /** The field [tag] is the whole box — 120 high at 1440 — and a press near its bottom edge focuses it. */
    private fun androidx.compose.ui.test.ComposeUiTest.pressLow(tag: String) {
        onNodeWithTag(tag).assertHeightIsAtLeast(120.dp)
        onNodeWithTag(tag).performTouchInput { click(Offset(width / 2f, height - 8f)) }
        onNodeWithTag(tag).assertIsFocused()
    }

    private fun reviews(vararg reviews: Review) =
        ProductReviews(
            id = "reviews",
            rating = "4.8",
            caption = "out of 5 · 2,341 reviews",
            histogram = emptyList(),
            actionLabel = "Write a review",
            reviews = reviews.toList(),
        )

    private fun review(
        author: String,
        voted: Boolean,
    ) = Review(
        author = author,
        initial = author.take(1),
        tone = "#E6E4FF",
        meta = "Sep 28",
        score = "5.0",
        title = "The quietest flight I've had",
        text = "Wore them on a 9-hour flight.",
        helpfulCommand = HelpfulCommand("/api/v1/reviews/r-$author/helpful", HelpfulVote(helpful = !voted)),
        helpfulVoted = voted,
    )

    private companion object {
        const val REVIEWED = "You reviewed this on Oct 7"
        val PRODUCT = FormProduct("Sonora SH-718N Wireless Noise Cancelling Headphones", "Midnight Black", "#E6E4FF")
        val REVIEW_FORM =
            ReviewForm(
                id = "review-form",
                title = "Write a review",
                product = PRODUCT,
                ratingLabel = "Your rating",
                titleLabel = "Title",
                bodyLabel = "Review",
                bodyHint = "At least 20 characters",
                submitLabel = "Post",
                cancelLabel = "Cancel",
                url = "/api/v1/products/p/reviews",
            )
        val QUESTION_FORM =
            QuestionForm(
                id = "question-form",
                title = "Ask a question",
                product = PRODUCT,
                label = "Your question",
                hint = "10 – 1,000 characters",
                note = "Your question appears on this page.",
                submitLabel = "Send",
                cancelLabel = "Cancel",
                url = "/api/v1/products/p/questions",
            )
    }
}
