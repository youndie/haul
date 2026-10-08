package io.github.youndie.haul.feature.identity

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.feature.identity.domain.Guests
import io.github.youndie.haul.haulWireJson
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.koin.ktor.ext.inject

/**
 * Guests (endpoint-identity), in the public tier: anyone may become a guest. The merge of a guest's
 * cart into a customer's arrives with sign-in (B-12).
 */
internal fun Route.identityRouting() {
    val guests by inject<Guests>()
    val clock by inject<StoreClock>()

    post(GuestRoutes.GUESTS) {
        val id = guests.create(clock.now().toOffsetDateTime())
        call.respondText(
            haulWireJson.encodeToString(GuestDto.serializer(), GuestDto(id)),
            ContentType.Application.Json,
            HttpStatusCode.Created,
        )
    }
}
