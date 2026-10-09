package io.github.youndie.haul.feature.returns

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.FieldError
import io.github.youndie.haul.groupedCount
import kotlinx.serialization.Serializable

// The body of feature-orders' return (endpoint-orders, B-21) and the rules both sides hold it to. Where it is
// sent is the server's string, carried by the dialog (`ReturnForm.url`); the server answers kompot's
// `sequence` of `close` and `refresh` — the dialog goes and the order is drawn again as returned — or an
// `ErrorBody`. The rules live here once: the server refuses a form by them (`400 validation_failed`, every
// field at fault in `ErrorBody.fields`), and the client checks the same rules before it sends.

/**
 * «Request return», `POST` to `ReturnForm.url`: the [lines] to return, by their place in the order
 * (`ReturnLine.position`) — each line whole, as the dialog's checkboxes take it — and the [reason], one of
 * `ReturnForm.reasons` by its id.
 */
@Serializable
public data class ReturnEntry(
    val lines: List<Int> = emptyList(),
    val reason: String = "",
)

/** Every field of [entry] at fault, in the form's order; empty when it can be sent. */
public fun returnProblems(entry: ReturnEntry): List<FieldError> =
    buildList {
        if (entry.lines.isEmpty()) add(FieldError("lines", ErrorCode.FieldRequired, "Choose what to return"))
        if (entry.reason.isBlank()) add(FieldError("reason", ErrorCode.FieldRequired, "Choose a reason for the return"))
    }

/**
 * «$80.00», «$1,204.50»: an amount the way the order's page writes it to the cent. The return dialog adds up
 * the lines the shopper ticks and writes the sum itself, so both sides need the one format.
 */
public fun exactDollars(cents: Int): String =
    "$" + groupedCount(cents / CENTS) + "." + (cents % CENTS).toString().padStart(2, '0')

private const val CENTS = 100
