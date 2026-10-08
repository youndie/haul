package io.github.youndie.haul.feature.cart

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.youndie.haul.ErrorBody
import io.github.youndie.haul.ErrorCode
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

// The cart's commands as the client sends them (endpoint-cart) — and the Saved list's (endpoint-saved,
// B-20), which go through the same seam: each goes where the tree's component says — `CartLine.url`,
// `CartSelection.linesUrl`, `PromoField.url`, a card's heart — with a body from the contract, and the
// server answers kompot's `refresh`, which the renderer hands to the screen's action handler.

/** One command, with the URL the tree gave it and its body. */
public sealed interface CartCommand {
    public val url: String

    /** A quantity or a selection, `PUT` to `CartLine.url`. */
    public data class ChangeLine(
        override val url: String,
        val change: LineChange,
    ) : CartCommand

    /** «Remove» or «Delete selected», `DELETE` to `CartSelection.linesUrl`. */
    public data class RemoveLines(
        override val url: String,
        val removal: LinesRemoval,
    ) : CartCommand

    /** «OK» on a changed line, `POST` to `CartLine.acknowledgeUrl`. */
    public data class Acknowledge(
        override val url: String,
    ) : CartCommand

    /** «Apply», `PUT` to `PromoField.url`. */
    public data class ApplyPromo(
        override val url: String,
        val entry: PromoEntry,
    ) : CartCommand

    /** «Remove» on an applied code, `DELETE` to `PromoField.url`. */
    public data class RemovePromo(
        override val url: String,
    ) : CartCommand

    /**
     * «Reorder» on an order's page (B-18), `POST` to `OrderTotals.reorderUrl`: the order's lines back into
     * the cart, answered with `navigate` to it rather than `refresh`.
     */
    public data class Reorder(
        override val url: String,
    ) : CartCommand

    /**
     * The heart (B-20), as the tree fixed it (`SaveCommand`): `PUT` to the product's address in the Saved
     * list when [save], `DELETE` otherwise; answered `refresh`, which draws the heart as it now is.
     */
    public data class Heart(
        override val url: String,
        val save: Boolean,
    ) : CartCommand

    /** «Save for later» on a line (B-20), `POST` to `CartLine.saveUrl`: the line moves to the Saved list. */
    public data class SaveForLater(
        override val url: String,
    ) : CartCommand
}

/**
 * Sends a cart command and returns the server's answer, an action — `refresh`, or a reorder's `navigate`
 * to the cart — for the screen to follow. A refusal throws [CartRefused]; no answer throws what the transport throws.
 */
public fun interface CartCommands {
    public suspend fun send(command: CartCommand): KompotAction
}

/**
 * Sends [batch] in order — «Select all» is one command per line — and returns what the screen follows
 * next: [next] when the tree gave one («Buy now»'s way to checkout, B-48), else the server's last
 * answer (`refresh`). A refusal stops the batch and is a `refresh` too, [next] or not: the server
 * remembers a refused promo code and the next tree draws it with the reason, and any other refusal is
 * drawn as the cart now is. No answer at all is `null`: the page stays as it was.
 */
@Suppress(
    "ktlint:kapkan:swallowed-failure",
    "A command that got no answer changed nothing the shopper can see; the page stays as the server last drew it and the next press tries again.",
)
public suspend fun CartCommands.run(
    batch: List<CartCommand>,
    next: KompotAction? = null,
): KompotAction? {
    var answer: KompotAction? = null
    for (command in batch) {
        answer =
            try {
                send(command)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: CartRefused) {
                return RefreshAction
            } catch (_: Throwable) {
                // Throwable: in the browser a failed fetch is a JavaScript error, no `Exception` on Wasm.
                return null
            }
    }
    return next ?: answer
}

/** The server refused a command: its status and, when the body said, its [code] and [reason]. */
public class CartRefused(
    public val status: Int,
    public val code: ErrorCode?,
    public val reason: String?,
) : Exception("cart command answered $status ${code ?: ""}".trim())

/**
 * Where the cart's renderers send their commands; the storefront provides one. `null` — a screenshot,
 * a view drawn on its own — draws the same pixels, and pressing does nothing.
 */
public val LocalCartCommands: ProvidableCompositionLocal<CartCommands?> = staticCompositionLocalOf { null }

/**
 * The browser's commands: [http] against [origin], each request through [send], which adds the
 * headers it is given — sign-in's `Identity.send` (B-12): the customer's bearer token or the guest id,
 * and one more try after a `401`.
 */
public fun ktorCartCommands(
    http: HttpClient,
    origin: String,
    send: suspend (
        request: suspend (headers: Map<String, String>) -> HttpResponse,
    ) -> HttpResponse = { it(emptyMap()) },
): CartCommands =
    CartCommands { command ->
        val (method, body) = command.request()
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
            throw CartRefused(response.status.value, error?.code, error?.message)
        }
        haulJson.decodeKompotAction(text)
    }

/** The method and the JSON body each command is sent with (endpoint-cart). */
private fun CartCommand.request(): Pair<HttpMethod, String?> =
    when (this) {
        is CartCommand.ChangeLine -> HttpMethod.Put to haulJson.encodeToString(LineChange.serializer(), change)
        is CartCommand.RemoveLines -> HttpMethod.Delete to haulJson.encodeToString(LinesRemoval.serializer(), removal)
        is CartCommand.Acknowledge -> HttpMethod.Post to null
        is CartCommand.ApplyPromo -> HttpMethod.Put to haulJson.encodeToString(PromoEntry.serializer(), entry)
        is CartCommand.RemovePromo -> HttpMethod.Delete to null
        is CartCommand.Reorder -> HttpMethod.Post to null
        is CartCommand.Heart -> (if (save) HttpMethod.Put else HttpMethod.Delete) to null
        is CartCommand.SaveForLater -> HttpMethod.Post to null
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
