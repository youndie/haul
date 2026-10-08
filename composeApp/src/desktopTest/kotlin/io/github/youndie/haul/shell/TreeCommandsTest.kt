package io.github.youndie.haul.shell

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.FieldError
import io.github.youndie.haul.feature.returns.ReturnEntry
import io.github.youndie.haul.feature.reviews.HelpfulVote
import io.github.youndie.haul.feature.reviews.QuestionEntry
import io.github.youndie.haul.feature.reviews.ReviewEntry
import io.github.youndie.kompot.standard.CloseAction
import io.github.youndie.kompot.standard.RefreshAction
import io.github.youndie.kompot.standard.SequenceAction
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The commands the trees fix as the browser sends them (endpoint-reviews, B-21, B-23, B-43), against a
 * server played by a mock engine: each to the URL its component carries with the method and the contract's
 * body its kind says and the bearer token, the server's `close` + `refresh` handed back, and a refusal with
 * its fields.
 */
class TreeCommandsTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var answer: Pair<HttpStatusCode, String> = HttpStatusCode.Created to CLOSED
    private var unreachable = false

    private val http =
        HttpClient(
            MockEngine { request ->
                requests += request
                if (unreachable) throw IOException("connection refused")
                respond(answer.second, answer.first, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )

    private val commands =
        ktorTreeCommands(http, "http://haul.test/") { request ->
            request(
                mapOf(HttpHeaders.Authorization to "Bearer t-1"),
            )
        }

    private fun HttpRequestData.text(): String? = (body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString()

    @Test
    fun `a review and a question are posted where the dialog says, with their bodies`() =
        runBlocking {
            val closed = SequenceAction(listOf(CloseAction, RefreshAction))
            assertEquals(
                closed,
                commands.send(TreeCommand.Review(REVIEWS, ReviewEntry(5, "Worth it", "Twenty characters and more"))),
            )
            assertEquals(closed, commands.send(TreeCommand.Ask(QUESTIONS, QuestionEntry("Does it fold flat?"))))
            assertEquals(
                listOf("http://haul.test$REVIEWS", "http://haul.test$QUESTIONS"),
                requests.map { it.url.toString() },
            )
            assertEquals(listOf(HttpMethod.Post, HttpMethod.Post), requests.map { it.method })
            assertEquals(
                listOf(
                    """{"rating":5,"title":"Worth it","body":"Twenty characters and more"}""",
                    """{"text":"Does it fold flat?"}""",
                ),
                requests.map { it.text() },
            )
            requests.forEach { assertEquals("Bearer t-1", it.headers[HttpHeaders.Authorization]) }
        }

    @Test
    fun `a refusal carries its code and fields, and the outcome tells them apart`() =
        runBlocking {
            answer =
                HttpStatusCode.BadRequest to
                """{"code":"validation_failed","message":"The form has fields to fix","field":"body",""" +
                """"fields":[{"field":"body","code":"field_invalid","message":"Write at least 20 characters"}]}"""
            val refused =
                assertFailsWith<CommandRefused> {
                    commands.send(
                        TreeCommand.Review(REVIEWS, ReviewEntry(5, "x", "short")),
                    )
                }
            assertEquals(ErrorCode.ValidationFailed, refused.code)
            val field = FieldError("body", ErrorCode.FieldInvalid, "Write at least 20 characters")
            assertEquals(listOf(field), refused.fields)
            assertEquals(
                CommandOutcome.Refused(listOf(field), null),
                commands.run(TreeCommand.Review(REVIEWS, ReviewEntry())),
            )

            answer =
                HttpStatusCode.Conflict to
                """{"code":"review_exists","message":"You have already reviewed this product"}"""
            assertEquals(
                CommandOutcome.Refused(emptyList(), "You have already reviewed this product"),
                commands.run(TreeCommand.Review(REVIEWS, ReviewEntry())),
            )

            unreachable = true
            assertEquals(
                CommandOutcome.NoAnswer,
                commands.run(TreeCommand.Ask(QUESTIONS, QuestionEntry("Does it fold flat?"))),
            )
        }

    /**
     * The return (B-21) is a `POST` of its entry as JSON; the Plus trial (B-23) a `POST` with no body and no
     * content type, as `ktorCommands` sent it before the trial moved onto this seam (B-51).
     */
    @Test
    fun `a return and a trial are posted where their dialogs say`() =
        runBlocking {
            val closed = SequenceAction(listOf(CloseAction, RefreshAction))
            assertEquals(closed, commands.send(TreeCommand.Return(RETURNS, ReturnEntry(listOf(0), "doesnt_fit"))))
            assertEquals(closed, commands.send(TreeCommand.StartTrial(TRIAL)))
            assertEquals(
                listOf("http://haul.test$RETURNS", "http://haul.test$TRIAL"),
                requests.map { it.url.toString() },
            )
            assertEquals(listOf(HttpMethod.Post, HttpMethod.Post), requests.map { it.method })
            val (returned, trial) = requests
            assertEquals("""{"lines":[0],"reason":"doesnt_fit"}""", returned.text())
            assertEquals(true, returned.body.contentType?.match(ContentType.Application.Json))
            assertIs<OutgoingContent.NoContent>(trial.body, "the trial sends no body")
            assertNull(trial.body.contentType, "the trial sends no content type")
            assertNull(trial.headers[HttpHeaders.ContentType], "the trial sends no content type")
            requests.forEach { assertEquals("Bearer t-1", it.headers[HttpHeaders.Authorization]) }
        }

    /**
     * B-43: «Helpful» is a `PUT` of the vote the tree carries — the state, not a toggle — and its answer,
     * `refresh`, is what the screen follows. A refusal is a `refresh` too, so the page shows what the
     * server has; no answer is nothing to follow.
     */
    @Test
    fun `a vote is put where the review says and its answer or refusal redraws the page`() =
        runBlocking {
            answer = HttpStatusCode.OK to """{"type":"refresh"}"""
            val command = TreeCommand.Vote(HELPFUL, HelpfulVote(helpful = false))
            assertEquals(RefreshAction, commands.vote(command))
            val request = requests.single()
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("http://haul.test$HELPFUL", request.url.toString())
            assertEquals("""{"helpful":false}""", request.text())
            assertEquals("Bearer t-1", request.headers[HttpHeaders.Authorization])

            answer =
                HttpStatusCode.Conflict to """{"code":"own_review","message":"You cannot vote on your own review"}"""
            val refused = assertFailsWith<CommandRefused> { commands.send(command) }
            assertEquals(ErrorCode.OwnReview, refused.code)
            assertEquals(RefreshAction, commands.vote(command))

            unreachable = true
            assertNull(commands.vote(command))
        }

    private companion object {
        const val HELPFUL = "/api/v1/reviews/r-sony-aisha/helpful"
        const val REVIEWS = "/api/v1/products/p-sony-wh-1000xm6/reviews"
        const val QUESTIONS = "/api/v1/products/p-sony-wh-1000xm6/questions"
        const val RETURNS = "/api/v1/me/orders/HL-46102/returns"
        const val TRIAL = "/api/v1/me/plus/trial"
        const val CLOSED = """{"type":"sequence","actions":[{"type":"close"},{"type":"refresh"}]}"""
    }
}
