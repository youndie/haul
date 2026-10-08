package io.github.youndie.haul.shell

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

// The commands a tree fixes for the client (B-51): the tree names the URL — `ReviewForm.url`,
// `QuestionForm.url`, `ReturnForm.url`, `PlusTrialDialog.url`, a review's `HelpfulCommand.url` — the
// contract the body, and the server answers with a kompot action the screen follows: a dialog's `close`
// and `refresh`, «Helpful»'s `refresh`. One seam for all of them, whatever feature drew the component:
// the review, question, return and Plus trial dialogs (B-22, B-21, B-23) and «Helpful» (B-43), which is
// a button on the page and not a dialog, but the same kind of command. A request the tree names only by
// method and path, «Clear» on recent searches (B-37), is [HaulCommands]'.

/** One command, with the URL the tree gave it and its body. */
public sealed interface TreeCommand {
    public val url: String

    /** «Post» in the review dialog (B-22), `POST` a [ReviewEntry] to `ReviewForm.url`. */
    public data class Review(
        override val url: String,
        val entry: ReviewEntry,
    ) : TreeCommand

    /** «Send» in the question dialog (B-22), `POST` a [QuestionEntry] to `QuestionForm.url`. */
    public data class Ask(
        override val url: String,
        val entry: QuestionEntry,
    ) : TreeCommand

    /** «Request return» on an order (B-21), `POST` a [ReturnEntry] to `ReturnForm.url`. */
    public data class Return(
        override val url: String,
        val entry: ReturnEntry,
    ) : TreeCommand

    /** «Start trial» in the Haul Plus dialog (B-23), `POST` to `PlusTrialDialog.url` with no body. */
    public data class StartTrial(
        override val url: String,
    ) : TreeCommand

    /** «Helpful» on a review (B-43), `PUT` the [HelpfulVote] its `HelpfulCommand` carries to its url. */
    public data class Vote(
        override val url: String,
        val vote: HelpfulVote,
    ) : TreeCommand
}

/**
 * Sends a command and returns the server's answer. A refusal throws [CommandRefused]; no answer throws what
 * the transport throws.
 */
public fun interface TreeCommands {
    public suspend fun send(command: TreeCommand): KompotAction
}

/** The server refused a command: its status and, when the body said, its [code], [reason] and [fields]. */
public class CommandRefused(
    public val status: Int,
    public val code: ErrorCode?,
    public val reason: String?,
    public val fields: List<FieldError> = emptyList(),
) : Exception("command answered $status ${code ?: ""}".trim())

/** What became of a dialog's command, for the dialog to draw. */
public sealed interface CommandOutcome {
    /** Answered: the dialog hands [action] — close, then refresh — to the screen. */
    public data class Done(
        val action: KompotAction,
    ) : CommandOutcome

    /** Refused: the fields at fault under their fields, anything else as [message] over the buttons. */
    public data class Refused(
        val fields: List<FieldError>,
        val message: String?,
    ) : CommandOutcome

    /** No answer: the dialog stays as it was, and says so. */
    public data object NoAnswer : CommandOutcome
}

/** [command] sent, its answer or refusal told apart; the dialog never throws at the shopper. */
@Suppress(
    "ktlint:kapkan:swallowed-failure",
    "A command that got no answer changed nothing; the dialog stays open with what was typed and the next press tries again.",
)
public suspend fun TreeCommands.run(command: TreeCommand): CommandOutcome =
    try {
        CommandOutcome.Done(send(command))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (refused: CommandRefused) {
        CommandOutcome.Refused(refused.fields, if (refused.fields.isEmpty()) refused.reason ?: NOT_SENT else null)
    } catch (_: Throwable) {
        // Throwable: in the browser a failed fetch is a JavaScript error, no `Exception` on Wasm.
        CommandOutcome.NoAnswer
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
public suspend fun TreeCommands.vote(command: TreeCommand.Vote): KompotAction? =
    try {
        send(command)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: CommandRefused) {
        RefreshAction
    } catch (_: Throwable) {
        // Throwable: in the browser a failed fetch is a JavaScript error, no `Exception` on Wasm.
        null
    }

/** What a dialog says when its command got no answer, or a refusal that named nothing. */
internal const val NOT_SENT: String = "That didn’t go through. Try again."

/**
 * Where the dialogs and «Helpful» send their commands; the storefront provides one. `null` — a screenshot, a
 * view drawn on its own — draws the same pixels, and pressing does nothing.
 */
public val LocalTreeCommands: ProvidableCompositionLocal<TreeCommands?> = staticCompositionLocalOf { null }

/**
 * The browser's commands: [http] against [origin], each request through [send], which adds the headers it
 * is given — sign-in's `Identity.send` (B-12): the customer's bearer token, and one more try after a `401`.
 * A command with a body sends it as JSON; one without (the trial's) sends no body and no content type.
 */
public fun ktorTreeCommands(
    http: HttpClient,
    origin: String,
    send: suspend (
        request: suspend (headers: Map<String, String>) -> HttpResponse,
    ) -> HttpResponse = { it(emptyMap()) },
): TreeCommands =
    TreeCommands { command ->
        val (method, body) =
            when (command) {
                is TreeCommand.Review -> {
                    HttpMethod.Post to
                        haulJson.encodeToString(ReviewEntry.serializer(), command.entry)
                }

                is TreeCommand.Ask -> {
                    HttpMethod.Post to
                        haulJson.encodeToString(QuestionEntry.serializer(), command.entry)
                }

                is TreeCommand.Return -> {
                    HttpMethod.Post to
                        haulJson.encodeToString(ReturnEntry.serializer(), command.entry)
                }

                is TreeCommand.StartTrial -> {
                    HttpMethod.Post to null
                }

                is TreeCommand.Vote -> {
                    HttpMethod.Put to
                        haulJson.encodeToString(HelpfulVote.serializer(), command.vote)
                }
            }
        val response =
            send { headers ->
                http.request(origin.trimEnd('/') + command.url) {
                    this.method = method
                    headers.forEach { (name, value) -> header(name, value) }
                    if (body != null) {
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }
                }
            }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val error = errorBody(text)
            throw CommandRefused(response.status.value, error?.code, error?.message, error?.fields.orEmpty())
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
