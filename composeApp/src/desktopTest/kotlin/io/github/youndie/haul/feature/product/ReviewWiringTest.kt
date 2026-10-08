package io.github.youndie.haul.feature.product

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.CANVAS_NOW
import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FieldError
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.decode
import io.github.youndie.haul.feature.reviews.HelpfulVote
import io.github.youndie.haul.feature.reviews.QuestionEntry
import io.github.youndie.haul.feature.reviews.ReviewEntry
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.read
import io.github.youndie.haul.shell.CommandRefused
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.NOT_SENT
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.shell.TreeCommand
import io.github.youndie.haul.shell.TreeCommands
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.ProductReviews
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.encodeKompotComponent
import io.github.youndie.kompot.standard.CloseAction
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.RefreshAction
import io.github.youndie.kompot.standard.SequenceAction
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The two dialogs in the storefront, over the server's own trees (`resources/bodies/product_reviews.json`,
 * `product_questions.json`): «Write a review» and «Ask a question» present the form the tree carries, a
 * form at fault by the server's rules sends nothing, a filled one is the command endpoint-reviews names
 * sent to the URL the form carries, and its answer — close, then refresh — takes the dialog away and
 * fetches the page again. A refusal is drawn in the dialog; Cancel and «×» close it and send nothing.
 * «Helpful» (B-43) sends the vote its review carries and follows the answer, `refresh`.
 */
@OptIn(ExperimentalTestApi::class)
class ReviewWiringTest {
    private val sent = CopyOnWriteArrayList<TreeCommand>()
    private val requests = CopyOnWriteArrayList<String>()

    /** The server's answer to a command: the dialog's `close` and `refresh` unless a test says otherwise. */
    private var answer: (TreeCommand) -> KompotAction = { CLOSED }

    private val commands =
        TreeCommands { command ->
            sent += command
            answer(command)
        }

    private fun ComposeUiTest.storefront(
        address: String,
        body: String,
        signIn: suspend () -> Unit = {},
    ) {
        val transport =
            HaulTransport { path ->
                requests += path
                HaulResponse(200, body)
            }
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                Storefront(
                    transport,
                    FakeHistory(address),
                    signIn = signIn,
                    clock = FixedClock,
                    treeCommands = commands,
                )
            }
        }
        waitUntil(timeoutMillis = 5_000) { requests.isNotEmpty() }
    }

    private fun ComposeUiTest.reviews() = storefront(REVIEWS_ADDRESS, read("product_reviews.json"))

    private fun ComposeUiTest.writeReview(entry: ReviewEntry) {
        onNodeWithTag(WRITE_REVIEW_TAG).performClick()
        onNodeWithTag(SUBMIT_TAG).assertExists()
        if (entry.rating > 0) onNodeWithTag(starTag(entry.rating)).performClick()
        onNodeWithTag(TITLE_TAG).performTextInput(entry.title)
        onNodeWithTag(BODY_TAG).performTextInput(entry.body)
        onNodeWithTag(SUBMIT_TAG).performClick()
        waitForIdle()
    }

    @Test
    fun `a review is posted where the form says and the answer closes the dialog and redraws the page`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            reviews()
            writeReview(GOOD)
            waitUntil(timeoutMillis = 5_000) { requests.size == 2 }
            assertEquals(listOf<TreeCommand>(TreeCommand.Review(REVIEWS_URL, GOOD)), sent.toList())
            assertEquals(listOf("/ui$REVIEWS_ADDRESS", "/ui$REVIEWS_ADDRESS"), requests.toList())
            onNodeWithTag(SUBMIT_TAG).assertDoesNotExist()
        }

    /** The server's rules, checked before sending (Product_ReviewDialog's note): nothing goes, and each field says why. */
    @Test
    fun `a form at fault by the server's rules sends nothing and says why`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            reviews()
            writeReview(ReviewEntry(rating = 0, title = "Good", body = "Great"))
            onNodeWithText("Choose from 1 to 5 stars").assertExists()
            onNodeWithText("Write at least 20 characters").assertExists()
            assertEquals(emptyList(), sent.toList())
            // Typing in the review takes its error away; the stars' stays until they are chosen.
            onNodeWithTag(BODY_TAG).performTextInput(" — and the battery lasts all week")
            onNodeWithText("Write at least 20 characters").assertDoesNotExist()
            onNodeWithText("Choose from 1 to 5 stars").assertExists()
            assertEquals(1, requests.size)
        }

    @Test
    fun `the server's refusal is drawn in the dialog and the page is not fetched again`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            answer = { throw CommandRefused(409, ErrorCode.ReviewExists, "You have already reviewed this product") }
            reviews()
            writeReview(GOOD)
            waitUntil(timeoutMillis = 5_000) { sent.isNotEmpty() }
            onNodeWithText("You have already reviewed this product").assertExists()
            onNodeWithTag(SUBMIT_TAG).assertExists()
            assertEquals(1, requests.size)
        }

    @Test
    fun `a field the server refused is drawn under it`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            answer = {
                throw CommandRefused(
                    400,
                    ErrorCode.ValidationFailed,
                    "The form has fields to fix",
                    listOf(FieldError("title", ErrorCode.FieldInvalid, "Keep it to 120 characters")),
                )
            }
            reviews()
            writeReview(GOOD)
            waitUntil(timeoutMillis = 5_000) { sent.isNotEmpty() }
            onNodeWithText("Keep it to 120 characters").assertExists()
        }

    @Test
    fun `no answer keeps the dialog and what was typed`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            answer = { throw IOException("connection refused") }
            reviews()
            writeReview(GOOD)
            waitUntil(timeoutMillis = 5_000) { sent.isNotEmpty() }
            onNodeWithText(NOT_SENT).assertExists()
            onNodeWithText(GOOD.title).assertExists()
        }

    @Test
    fun `cancel and the close button take the dialog away and send nothing`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            reviews()
            onNodeWithTag(WRITE_REVIEW_TAG).performClick()
            onNodeWithTag(CANCEL_TAG).performClick()
            onNodeWithTag(SUBMIT_TAG).assertDoesNotExist()
            onNodeWithTag(WRITE_REVIEW_TAG).performClick()
            onNodeWithTag(CLOSE_TAG).performClick()
            onNodeWithTag(SUBMIT_TAG).assertDoesNotExist()
            assertEquals(emptyList(), sent.toList())
            assertEquals(1, requests.size)
        }

    @Test
    fun `a question is sent where its form says`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            storefront(QUESTIONS_ADDRESS, read("product_questions.json"))
            onNodeWithTag(ASK_TAG).performClick()
            onNodeWithTag(SUBMIT_TAG).performClick()
            onNodeWithText("Write at least 10 characters").assertExists()
            onNodeWithTag(QUESTION_TAG).performTextInput("Do the ear cushions come off for cleaning?")
            onNodeWithTag(SUBMIT_TAG).performClick()
            waitUntil(timeoutMillis = 5_000) { requests.size == 2 }
            assertEquals(
                listOf<TreeCommand>(
                    TreeCommand.Ask(QUESTIONS_URL, QuestionEntry("Do the ear cushions come off for cleaning?")),
                ),
                sent.toList(),
            )
            onNodeWithTag(SUBMIT_TAG).assertDoesNotExist()
        }

    /** A guest's «Write a review» is the way to sign in, as the server builds it: sign-in runs, no dialog. */
    @Test
    fun `a guest is sent to sign in`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val signIns = AtomicInteger()
            val tree = decode("product_reviews.json") as ColumnComponent
            val guests =
                tree.copy(
                    children =
                        tree.children.map {
                            if (it is ProductReviews) it.copy(action = NavigateAction("/sign-in")) else it
                        },
                )
            storefront(REVIEWS_ADDRESS, haulWireJson.encodeKompotComponent(guests)) { signIns.incrementAndGet() }
            onNodeWithTag(WRITE_REVIEW_TAG).performClick()
            waitUntil(timeoutMillis = 5_000) { signIns.get() == 1 }
            onNodeWithTag(SUBMIT_TAG).assertDoesNotExist()
        }

    /**
     * B-43: «Helpful» sends the vote its review carries — Aisha's, the second card — to the URL it names,
     * and the answer, `refresh`, fetches the page again, where the server's count is drawn.
     */
    @Test
    fun `helpful sends the review's vote and the answer redraws the page`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            answer = { RefreshAction }
            reviews()
            onAllNodesWithTag(HELPFUL_TAG)[1].performClick()
            waitUntil(timeoutMillis = 5_000) { requests.size == 2 }
            assertEquals(
                listOf<TreeCommand>(TreeCommand.Vote(HELPFUL_URL, HelpfulVote(helpful = true))),
                sent.toList(),
            )
            assertEquals(listOf("/ui$REVIEWS_ADDRESS", "/ui$REVIEWS_ADDRESS"), requests.toList())
        }

    /** A refused vote (the review gone, or the voter's own) draws the page as the server has it; no answer leaves it. */
    @Test
    fun `a refused vote redraws the page and one with no answer does not`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            answer = { throw IOException("connection refused") }
            reviews()
            onAllNodesWithTag(HELPFUL_TAG)[0].performClick()
            waitUntil(timeoutMillis = 5_000) { sent.size == 1 }
            waitForIdle()
            assertEquals(1, requests.size)
            answer = { throw CommandRefused(409, ErrorCode.OwnReview, "You cannot vote on your own review") }
            onAllNodesWithTag(HELPFUL_TAG)[0].performClick()
            waitUntil(timeoutMillis = 5_000) { requests.size == 2 }
            assertEquals(2, sent.size)
        }

    /** A guest's «Helpful» is the way to sign in, as the server builds it: sign-in runs and no vote is sent. */
    @Test
    fun `a guest's helpful signs in and sends no vote`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val signIns = AtomicInteger()
            val tree = decode("product_reviews.json") as ColumnComponent
            val guests =
                tree.copy(
                    children =
                        tree.children.map { child ->
                            if (child is ProductReviews) {
                                child.copy(
                                    reviews =
                                        child.reviews.map {
                                            it.copy(helpfulCommand = null, helpfulAction = NavigateAction("/sign-in"))
                                        },
                                )
                            } else {
                                child
                            }
                        },
                )
            storefront(REVIEWS_ADDRESS, haulWireJson.encodeKompotComponent(guests)) { signIns.incrementAndGet() }
            onAllNodesWithTag(HELPFUL_TAG)[1].performClick()
            waitUntil(timeoutMillis = 5_000) { signIns.get() == 1 }
            assertEquals(emptyList(), sent.toList())
        }

    private object FixedClock : Clock {
        override fun now(): Instant = CANVAS_NOW
    }

    private companion object {
        const val WIDTH = 1440
        const val HEIGHT = 1800
        const val REVIEWS_ADDRESS = "/p/p-sony-wh-1000xm6?tab=reviews"
        const val QUESTIONS_ADDRESS = "/p/p-sony-wh-1000xm6?tab=questions"
        const val REVIEWS_URL = "/api/v1/products/p-sony-wh-1000xm6/reviews"
        const val QUESTIONS_URL = "/api/v1/products/p-sony-wh-1000xm6/questions"
        const val HELPFUL_URL = "/api/v1/reviews/r-sony-aisha/helpful"
        val CLOSED = SequenceAction(listOf(CloseAction, RefreshAction))
        val GOOD =
            ReviewEntry(
                rating = 4,
                title = "Worth it for the commute",
                body = "Noise cancelling handles the subway and the open office.",
            )
    }
}
