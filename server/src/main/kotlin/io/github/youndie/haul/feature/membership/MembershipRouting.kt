package io.github.youndie.haul.feature.membership

import io.github.youndie.haul.feature.identity.Caller
import io.github.youndie.haul.feature.identity.Callers
import io.github.youndie.haul.feature.identity.domain.IdentityError
import io.github.youndie.haul.feature.membership.domain.PlusCommands
import io.github.youndie.haul.haulWireJson
import io.github.youndie.kompot.encodeKompotAction
import io.github.youndie.kompot.standard.CloseAction
import io.github.youndie.kompot.standard.RefreshAction
import io.github.youndie.kompot.standard.SequenceAction
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.koin.ktor.ext.inject

/**
 * feature-membership's command (endpoint-membership), in the customer tier: a request without a verified
 * token never reaches here (`401 unauthenticated`). «Start trial» is `POST` [MembershipPaths.TRIAL] with
 * no body, answered `201` with kompot's `sequence` of `close` and `refresh` — the dialog goes and the page
 * is drawn again for a member — or `409 already_member`.
 */
internal fun Route.membershipRouting() {
    val plus by inject<PlusCommands>()
    val callers by inject<Callers>()

    post(MembershipPaths.TRIAL) {
        val customer = (callers.of(call) as? Caller.Customer)?.customer ?: throw IdentityError.Unauthenticated()
        plus.startTrial(customer)
        call.respondText(
            haulWireJson.encodeKompotAction(SequenceAction(listOf(CloseAction, RefreshAction))),
            ContentType.Application.Json,
            HttpStatusCode.Created,
        )
    }
}

/** feature-membership's paths: the server's strings (CLAUDE.md), handed to the client in `PlusTrialDialog.url`. */
internal object MembershipPaths {
    const val TRIAL = "/api/v1/me/plus/trial"
}
