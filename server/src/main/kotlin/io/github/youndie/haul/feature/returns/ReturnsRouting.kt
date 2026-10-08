package io.github.youndie.haul.feature.returns

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.FieldError
import io.github.youndie.haul.feature.identity.Caller
import io.github.youndie.haul.feature.identity.Callers
import io.github.youndie.haul.feature.identity.domain.IdentityError
import io.github.youndie.haul.feature.returns.domain.RequestReturn
import io.github.youndie.haul.feature.returns.domain.ReturnError
import io.github.youndie.haul.feature.reviews.CLOSE_AND_REFRESH
import io.github.youndie.haul.haulWireJson
import io.github.youndie.kompot.encodeKompotAction
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.koin.ktor.ext.inject

/**
 * «Request return» (endpoint-orders, B-21), in the customer tier: a request without a verified token never
 * reaches here (`401 unauthenticated`). It answers `201` with kompot's `sequence` of `close` and `refresh` —
 * the dialog goes and the order is drawn again, its return requested — as the review dialog's command does.
 * Every refusal is a [ReturnError], or the order's `404` ([RequestReturn]).
 */
internal fun Route.returnsRouting() {
    val requestReturn by inject<RequestReturn>()
    val callers by inject<Callers>()

    post(ReturnPaths.RETURNS) {
        val customer = (callers.of(call) as? Caller.Customer)?.customer ?: throw IdentityError.Unauthenticated()
        val entry =
            try {
                haulWireJson.decodeFromString(ReturnEntry.serializer(), call.receiveText())
            } catch (_: IllegalArgumentException) {
                throw ReturnError.Invalid(
                    listOf(
                        FieldError(
                            "request",
                            ErrorCode.FieldInvalid,
                            "The request body is not the JSON this command takes",
                        ),
                    ),
                )
            }
        requestReturn.request(customer.id, call.parameters["id"]!!, entry)
        call.respondText(
            haulWireJson.encodeKompotAction(CLOSE_AND_REFRESH),
            ContentType.Application.Json,
            HttpStatusCode.Created,
        )
    }
}

/** The return's path: the server's string (CLAUDE.md), handed to the client inside `ReturnForm.url`. */
internal object ReturnPaths {
    const val RETURNS = "/api/v1/me/orders/{id}/returns"

    fun returns(orderId: String): String = RETURNS.replace("{id}", orderId)
}
