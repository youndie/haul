package io.github.youndie.haul.feature.reviews

import io.github.youndie.haul.feature.identity.Caller
import io.github.youndie.haul.feature.identity.Callers
import io.github.youndie.haul.feature.identity.domain.Customer
import io.github.youndie.haul.feature.identity.domain.IdentityError
import io.github.youndie.haul.feature.reviews.domain.ReviewCommands
import io.github.youndie.haul.feature.reviews.domain.ReviewError
import io.github.youndie.haul.haulWireJson
import io.github.youndie.kompot.encodeKompotAction
import io.github.youndie.kompot.standard.CloseAction
import io.github.youndie.kompot.standard.RefreshAction
import io.github.youndie.kompot.standard.SequenceAction
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.DeserializationStrategy
import org.koin.ktor.ext.inject

/**
 * feature-reviews' commands (endpoint-reviews), in the customer tier: a request without a verified token
 * never reaches here (`401 unauthenticated`). Reading reviews and questions is the product page's `tab`
 * (`/ui/p/{id}?tab=reviews`, the catalog's). Both commands answer `201` with kompot's `sequence` of
 * `close` and `refresh` — the dialog goes and the page is drawn again with what was written — and every
 * refusal is a [ReviewError].
 */
internal fun Route.reviewsRouting() {
    val commands by inject<ReviewCommands>()
    val callers by inject<Callers>()

    suspend fun ApplicationCall.customer(): Customer =
        (callers.of(this) as? Caller.Customer)?.customer ?: throw IdentityError.Unauthenticated()

    post(ReviewPaths.REVIEWS) {
        val customer = call.customer()
        commands.post(customer, call.parameters["productId"]!!, call.body(ReviewEntry.serializer()))
        call.respondClosed()
    }

    post(ReviewPaths.QUESTIONS) {
        val customer = call.customer()
        commands.ask(customer, call.parameters["productId"]!!, call.body(QuestionEntry.serializer()))
        call.respondClosed()
    }
}

/** What a dialog's command answers once it is done: close the dialog, draw the page again. */
internal val CLOSE_AND_REFRESH: SequenceAction = SequenceAction(listOf(CloseAction, RefreshAction))

private suspend fun ApplicationCall.respondClosed() =
    respondText(
        haulWireJson.encodeKompotAction(CLOSE_AND_REFRESH),
        ContentType.Application.Json,
        HttpStatusCode.Created,
    )

/**
 * feature-reviews' paths: the server's strings (CLAUDE.md), handed to the client inside the dialogs'
 * components (`ReviewForm.url`, `QuestionForm.url`) rather than built by it.
 */
internal object ReviewPaths {
    const val REVIEWS = "/api/v1/products/{productId}/reviews"
    const val QUESTIONS = "/api/v1/products/{productId}/questions"

    fun reviews(productId: String): String = REVIEWS.replace("{productId}", productId)

    fun questions(productId: String): String = QUESTIONS.replace("{productId}", productId)
}

/**
 * A command's JSON body; one that does not parse is `400 validation_failed` naming `request` — not `body`,
 * which is the review's own field.
 */
private suspend fun <T> ApplicationCall.body(strategy: DeserializationStrategy<T>): T =
    try {
        haulWireJson.decodeFromString(strategy, receiveText())
    } catch (_: IllegalArgumentException) {
        throw ReviewError.Invalid("request", "The request body is not the JSON this command takes")
    }
