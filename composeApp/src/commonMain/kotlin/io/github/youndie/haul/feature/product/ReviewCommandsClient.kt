package io.github.youndie.haul.feature.product

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.youndie.haul.ErrorBody
import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.FieldError
import io.github.youndie.haul.feature.reviews.QuestionEntry
import io.github.youndie.haul.feature.reviews.ReviewEntry
import io.github.youndie.haul.registry.haulJson
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.decodeKompotAction
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException

// The two dialogs' commands as the client sends them (endpoint-reviews): each goes where the dialog's
// component says — `ReviewForm.url`, `QuestionForm.url` — with a body from the contract, and is answered
// with kompot's `sequence` of `close` and `refresh`, which the dialog hands to the screen's handler.

/** One command, with the URL the dialog gave it and its body. */
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
}

/**
 * Sends a dialog's command and returns the server's answer. A refusal throws [ReviewRefused]; no answer
 * throws what the transport throws.
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

/** What a dialog says when its command got no answer, or a refusal that named nothing. */
internal const val NOT_SENT: String = "That didn’t go through. Try again."

/**
 * Where the dialogs send their commands; the storefront provides one. `null` — a screenshot, a view drawn
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
        val body =
            when (command) {
                is ReviewCommand.Post -> haulJson.encodeToString(ReviewEntry.serializer(), command.entry)
                is ReviewCommand.Ask -> haulJson.encodeToString(QuestionEntry.serializer(), command.entry)
            }
        val response =
            send { headers ->
                http.post(origin.trimEnd('/') + command.url) {
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
