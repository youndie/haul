package io.github.youndie.haul.feature.identity

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.feature.cart.CartPaths
import io.github.youndie.haul.feature.cart.domain.CartCommands
import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.identity.domain.Guests
import io.github.youndie.haul.feature.identity.domain.IdentityError
import io.github.youndie.haul.haulWireJson
import io.github.youndie.kompot.ktor.respondKompotAction
import io.github.youndie.kompot.standard.RefreshAction
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.koin.ktor.ext.inject

/**
 * Guests and the browser's sign-in settings (endpoint-identity), in the public tier: anyone may become
 * a guest, and anyone may read where to sign in. [signIn] is `null` on a server with sign-in off.
 */
internal fun Route.identityRouting(signIn: SignInConfig?) {
    val guests by inject<Guests>()
    val clock by inject<StoreClock>()

    post(GUESTS) {
        val id = guests.create(clock.now().toOffsetDateTime())
        call.respondText(
            haulWireJson.encodeToString(GuestDto.serializer(), GuestDto(id)),
            ContentType.Application.Json,
            HttpStatusCode.Created,
        )
    }

    get(SIGN_IN_SETTINGS) {
        val settings = signIn?.settings() ?: throw IdentityError.SignInOff()
        call.respondText(
            haulWireJson.encodeToString(SignInSettings.serializer(), settings),
            ContentType.Application.Json,
        )
    }
}

/**
 * The customer tier's half of identity: sign-in's merge of the guest cart. The route is mounted under
 * the bearer check, so the caller here is always a customer; the guest is named by `X-Haul-Guest`,
 * and one the server never issued is `404 guest_not_found`.
 */
internal fun Route.customerIdentityRouting() {
    val callers by inject<Callers>()
    val guests by inject<Guests>()
    val commands by inject<CartCommands>()

    post(CartPaths.MERGE) {
        val customer = (callers.of(call) as? Caller.Customer)?.customer ?: throw IdentityError.Unauthenticated()
        val guest = callers.guestId(call)?.takeIf { guests.exists(it) } ?: throw IdentityError.GuestNotFound()
        commands.merge(CartOwner.Guest(guest), CartOwner.Customer(customer.id, customer.plus))
        call.respondKompotAction(haulWireJson, RefreshAction)
    }
}

/** Where a guest is created; the one path a client calls before it has a tree to follow. */
internal const val GUESTS = "/api/v1/guests"

/** Where the browser reads [SignInSettings] before it opens the provider's page. */
internal const val SIGN_IN_SETTINGS = "/api/v1/sign-in"
