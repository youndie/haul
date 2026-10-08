package io.github.youndie.haul.feature.checkout

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.youndie.haul.ErrorBody
import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.FieldError
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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// The checkout's commands as the client sends them (endpoint-checkout, endpoint-identity): each goes
// where the tree's section says — `DeliveryMethods.url`, `CheckoutAddress.url`, `CheckoutSummary.placeUrl`
// — with a body from the contract. A choice and the address form are answered `refresh`, which the
// renderer hands to the screen's action handler; placing the order is answered with where to go next.

/** One command, with the URL the tree gave it and its body. */
public sealed interface CheckoutCommand {
    public val url: String

    /** A method, a point, a window or a way to pay, `PUT` to the section's `url`. */
    public data class Choose(
        override val url: String,
        val choice: CheckoutChoice,
    ) : CheckoutCommand

    /** The address form, `POST` to `CheckoutAddress.url`; a form at fault is refused with its fields. */
    public data class SaveAddress(
        override val url: String,
        val entry: AddressEntry,
    ) : CheckoutCommand

    /**
     * «Place order», `POST` to `CheckoutSummary.placeUrl` with the [quote] the shopper saw and an
     * [idempotencyKey] that is the same for every press on the same quote, so a retry places one order
     * (feature-checkout, «Same key twice»): a [PlaceOrderRequest] under [IDEMPOTENCY_KEY_HEADER] (B-16).
     */
    public data class Place(
        override val url: String,
        val quote: String,
        val idempotencyKey: String,
    ) : CheckoutCommand
}

/**
 * Sends a checkout command and returns the server's answer: `refresh` for a choice or an address, where
 * to go for an order placed. A refusal throws [CheckoutRefused]; no answer throws what the transport throws.
 */
public fun interface CheckoutCommands {
    public suspend fun send(command: CheckoutCommand): KompotAction
}

/**
 * What the screen follows after [command]: the server's answer. A refusal is a `refresh`: the server
 * keeps a refused address form and draws it again with its errors (`Checkout_Validation`), and a window
 * that filled up is cleared in the next tree (`Checkout_PlaceError`). No answer at all is `null`: the
 * page stays as it was.
 */
@Suppress(
    "ktlint:kapkan:swallowed-failure",
    "A command that got no answer changed nothing the shopper can see; the page stays as the server last drew it and the next press tries again.",
)
public suspend fun CheckoutCommands.run(command: CheckoutCommand): KompotAction? =
    try {
        send(command)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: CheckoutRefused) {
        RefreshAction
    } catch (_: Throwable) {
        // Throwable: in the browser a failed fetch is a JavaScript error, no `Exception` on Wasm.
        null
    }

/** The server refused a command: its status and, when the body said, its [code], [reason] and [fields]. */
public class CheckoutRefused(
    public val status: Int,
    public val code: ErrorCode?,
    public val reason: String?,
    public val fields: List<FieldError> = emptyList(),
) : Exception("checkout command answered $status ${code ?: ""}".trim())

/**
 * Where the checkout's renderer sends its commands; the storefront provides one. `null` — a screenshot,
 * a view drawn on its own — draws the same pixels, and pressing does nothing.
 */
public val LocalCheckoutCommands: ProvidableCompositionLocal<CheckoutCommands?> = staticCompositionLocalOf { null }

/** A key for one quote's placement: random, so two shoppers' keys never meet. */
@OptIn(ExperimentalUuidApi::class)
public fun newIdempotencyKey(): String = Uuid.random().toString()

/**
 * The browser's commands: [http] against [origin], each request through [send], which adds the headers
 * it is given — sign-in's `Identity.send` (B-12): the customer's bearer token, and one more try after a
 * `401`.
 */
public fun ktorCheckoutCommands(
    http: HttpClient,
    origin: String,
    send: suspend (
        request: suspend (headers: Map<String, String>) -> HttpResponse,
    ) -> HttpResponse = { it(emptyMap()) },
): CheckoutCommands =
    CheckoutCommands { command ->
        val (method, body) = command.request()
        val response =
            send { headers ->
                http.request(origin.trimEnd('/') + command.url) {
                    this.method = method
                    headers.forEach { (name, value) -> header(name, value) }
                    if (command is CheckoutCommand.Place) header(IDEMPOTENCY_KEY_HEADER, command.idempotencyKey)
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val error = errorBody(text)
            throw CheckoutRefused(response.status.value, error?.code, error?.message, error?.fields.orEmpty())
        }
        haulJson.decodeKompotAction(text)
    }

/** The method and the JSON body each command is sent with (endpoint-checkout). */
private fun CheckoutCommand.request(): Pair<HttpMethod, String> =
    when (this) {
        is CheckoutCommand.Choose -> {
            HttpMethod.Put to haulJson.encodeToString(CheckoutChoice.serializer(), choice)
        }

        is CheckoutCommand.SaveAddress -> {
            HttpMethod.Post to haulJson.encodeToString(AddressEntry.serializer(), entry)
        }

        is CheckoutCommand.Place -> {
            HttpMethod.Post to haulJson.encodeToString(PlaceOrderRequest.serializer(), PlaceOrderRequest(quote))
        }
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
