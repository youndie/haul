package io.github.youndie.haul.shell

import io.github.youndie.haul.ErrorCode
import io.ktor.http.decodeURLQueryComponent

// What a page whose tree was refused leads to when Retry cannot help (B-94). The server's answer already
// says what happened — `409 cart_empty`, `400 query_too_short`, `400 validation_failed` with the parameter
// at fault — and the same request sent again gets the same answer, so the shell does not offer it again.

/** Where a refused page goes instead of an error page with a Retry that asks for the same refusal. */
internal sealed interface Recovery {
    /** The page the shopper meant is at [location]: the refused address gives its entry to it. */
    data class Redirect(
        val location: String,
    ) : Recovery

    /** A search for fewer characters than the server matches: the search says so, the query kept in the field. */
    data object QueryTooShort : Recovery

    /** An old or edited link the server refuses and no parameter of it explains: the page says the link is stale. */
    data object StaleLink : Recovery
}

/**
 * What the shell does with [cause], the reason this address's tree did not arrive; `null` when Retry is the
 * way — no answer, or an error of the server's own.
 *
 * - The checkout with nothing selected in the cart (`409 cart_empty`: back after placing an order, a reload,
 *   an empty merged cart) is the cart.
 * - A search shorter than the server takes (`400 query_too_short`) is [Recovery.QueryTooShort].
 * - Any other `400` names a parameter of an old or edited link — a `page`, `sort`, `from` or `shown` that is not
 *   one, a `sku` no longer on the product: the page without it, which drops one parameter per refusal until
 *   the server takes the rest. A `400` that names none the address has is [Recovery.StaleLink].
 */
internal fun Address.recovery(cause: Throwable): Recovery? {
    if (cause !is ScreenFailed.Refused) return null
    return when {
        cause.code == ErrorCode.CartEmpty && kind == PageKind.Checkout -> {
            Recovery.Redirect(CART)
        }

        cause.code == ErrorCode.QueryTooShort -> {
            Recovery.QueryTooShort
        }

        cause.status == BAD_REQUEST -> {
            cause.field
                ?.takeIf { field -> parameters.any { it.named == field } }
                ?.let { Recovery.Redirect(without(it)) }
                ?: Recovery.StaleLink
        }

        else -> {
            null
        }
    }
}

/** Whether pressing Retry on [this] failure can bring a different answer: not for a request the server refused as made. */
internal val Throwable.retryHelps: Boolean
    get() = !(this is ScreenFailed.Refused && (code == ErrorCode.QueryTooShort || status == BAD_REQUEST))

/** This address without the query parameter [name] — every value of it — the rest of the query as it was written. */
internal fun Address.without(name: String): String {
    val kept = parameters.filter { it.named != name }.map { it.written }
    return path + if (kept.isEmpty()) "" else "?" + kept.joinToString("&")
}

/** One `name=value` of the query, as [written] in the address, and the name it decodes to. */
private class Parameter(
    val written: String,
) {
    val named: String = written.substringBefore('=').decodeURLQueryComponent(plusIsSpace = true)
}

private val Address.parameters: List<Parameter>
    get() =
        value
            .substringAfter('?', "")
            .split('&')
            .filter { it.isNotEmpty() }
            .map(::Parameter)

private const val BAD_REQUEST = 400
