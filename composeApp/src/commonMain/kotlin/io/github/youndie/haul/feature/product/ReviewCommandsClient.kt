package io.github.youndie.haul.feature.product

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.youndie.haul.ErrorBody
import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.FieldError
import io.github.youndie.haul.feature.returns.ReturnEntry
import io.github.youndie.haul.feature.reviews.HelpfulVote
import io.github.youndie.haul.feature.reviews.QuestionEntry
import io.github.youndie.haul.feature.reviews.ReviewEntry
import io.github.youndie.haul.registry.haulJson
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.decodeKompotAction
import io.github.youndie.kompot.standard.RefreshAction
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException

// feature-reviews' commands as the client sends them (endpoint-reviews): each goes where the tree's
// component says — `ReviewForm.url`, `QuestionForm.url`, a review's `HelpfulCommand.url` — with a body from
// the contract. The two dialogs' are answered with kompot's `sequence` of `close` and `refresh`, which the
// dialog hands to the screen's handler; «Helpful» with `refresh`, which the review's button hands there.
// The order's return dialog (B-21) is a dialog like these and goes through the same seam: `ReturnForm.url`,
// a `ReturnEntry`, answered the same way.

/** One command, with the URL the tree gave it and its body. */
public sealed interface ReviewCommand {
    public val url: String

    /** «Post», `POST` a [ReviewEntry] to `ReviewForm.url`. */
    public data class Post(
        override val url: String,
        val entry: ReviewEntry,
    ) : ReviewCommand

    /** «Send», `POST` a [QuestionEntry] to `QuestionForm.url`. */
    public data class Ask(
        override val url: String,
        val entry: QuestionEntry,
    ) : ReviewCommand

    /** «Request return» on an order (B-21), `POST` a [ReturnEntry] to `ReturnForm.url`. */
    public data class Return(
        override val url: String,
        val entry: ReturnEntry,
    ) : ReviewCommand

    /** «Helpful» on a review (B-43), `PUT` the [HelpfulVote] its `HelpfulCommand` carries to its url. */
    public data class Vote(
        override val url: String,
        val vote: HelpfulVote,
    ) : ReviewCommand
}

/**
 * Sends a command and returns the server's answer. A refusal throws [ReviewRefused]; no answer throws what
 * the transport throws.
 */
public fun interface ReviewCommands {
    public suspend fun send(command: ReviewCommand): KompotAction
}

/** The server refused a command: its status and, when the body said, its [code], [reason] and [fields]. */
public class ReviewRefused(
    public val status: Int,
    public val code: ErrorCode?,
    public val reason: String?,
    public val fields: List<FieldError> = emptyList(),
) : Exception("review command answered $status ${code ?: ""}".trim())

/** What became of a command, for the dialog to draw. */
public sealed interface ReviewOutcome {
    /** Answered: the dialog hands [action] — close, then refresh — to the screen. */
    public data class Done(
        val action: KompotAction,
    ) : ReviewOutcome

    /** Refused: the fields at fault under their fields, anything else as [message] over the buttons. */
    public data class Refused(
        val fields: List<FieldError>,
        val message: String?,
    ) : ReviewOutcome

    /** No answer: the dialog stays as it was, and says so. */
    public data object NoAnswer : ReviewOutcome
}

/** [command] sent, its answer or refusal told apart; the dialog never throws at the shopper. */
@Suppress(
    "ktlint:kapkan:swallowed-failure",
    "A command that got no answer changed nothing; the dialog stays open with what was typed and the next press tries again.",
)
public suspend fun ReviewCommands.run(command: ReviewCommand): ReviewOutcome =
    try {
        ReviewOutcome.Done(send(command))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (refused: ReviewRefused) {
        ReviewOutcome.Refused(refused.fields, if (refused.fields.isEmpty()) refused.reason ?: NOT_SENT else null)
    } catch (_: Throwable) {
        // Throwable: in the browser a failed fetch is a JavaScript error, no `Exception` on Wasm.
        ReviewOutcome.NoAnswer
    }

/**
 * A «Helpful» press sent, and what the screen follows next: the server's answer, `refresh`. A refusal —
 * the review gone, or the voter's own — is a `refresh` too, so the page is drawn as the server now has it;
 * no answer at all is `null`, and the page stays as it was.
 */
@Suppress(
    "ktlint:kapkan:swallowed-failure",
    "A vote that got no answer changed nothing the shopper can see; the page stays as the server last drew it and the next press tries again.",
)
public suspend fun ReviewCommands.vote(command: ReviewCommand.Vote): KompotAction? =
    try {
        send(command)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: ReviewRefused) {
        RefreshAction
    } catch (_: Throwable) {
        // Throwable: in the browser a failed fetch is a JavaScript error, no `Exception` on Wasm.
        null
    }

/** What a dialog says when its command got no answer, or a refusal that named nothing. */
internal const val NOT_SENT: String = "That didn’t go through. Try again."

/**
 * Where the dialogs and «Helpful» send their commands; the storefront provides one. `null` — a screenshot, a view drawn
 * on its own — draws the same pixels, and pressing does nothing.
 */
public val LocalReviewCommands: ProvidableCompositionLocal<ReviewCommands?> = staticCompositionLocalOf { null }

/**
 * The browser's commands: [http] against [origin], each request through [send], which adds the headers it
 * is given — sign-in's `Identity.send` (B-12): the customer's bearer token, and one more try after a `401`.
 */
public fun ktorReviewCommands(
    http: HttpClient,
    origin: String,
    send: suspend (
        request: suspend (headers: Map<String, String>) -> HttpResponse,
    ) -> HttpResponse = { it(emptyMap()) },
): ReviewCommands =
    ReviewCommands { command ->
        val (method, body) =
            when (command) {
                is ReviewCommand.Post -> {
                    HttpMethod.Post to
                        haulJson.encodeToString(ReviewEntry.serializer(), command.entry)
                }

                is ReviewCommand.Ask -> {
                    HttpMethod.Post to
                        haulJson.encodeToString(QuestionEntry.serializer(), command.entry)
                }

                is ReviewCommand.Return -> {
                    HttpMethod.Post to
                        haulJson.encodeToString(ReturnEntry.serializer(), command.entry)
                }

                is ReviewCommand.Vote -> {
                    HttpMethod.Put to
                        haulJson.encodeToString(HelpfulVote.serializer(), command.vote)
                }
            }
        val response =
            send { headers ->
                http.request(origin.trimEnd('/') + command.url) {
                    this.method = method
                    headers.forEach { (name, value) -> header(name, value) }
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val error = errorBody(text)
            throw ReviewRefused(response.status.value, error?.code, error?.message, error?.fields.orEmpty())
        }
        haulJson.decodeKompotAction(text)
    }

@Suppress(
    "ktlint:kapkan:swallowed-failure",
    "A body that is not an ErrorBody (a proxy's page) is a refusal all the same; the status says so.",
)
private fun errorBody(text: String): ErrorBody? =
    try {
        haulJson.decodeFromString(ErrorBody.serializer(), text)
    } catch (_: IllegalArgumentException) {
        null
    }
