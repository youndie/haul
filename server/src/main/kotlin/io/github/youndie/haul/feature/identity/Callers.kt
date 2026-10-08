package io.github.youndie.haul.feature.identity

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.feature.identity.domain.Customer
import io.github.youndie.haul.feature.identity.domain.Customers
import io.github.youndie.haul.feature.identity.domain.Guests
import io.github.youndie.haul.feature.identity.domain.IdentityError
import io.github.youndie.shildik.oidc.OidcPrincipal
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import kotlinx.serialization.json.JsonPrimitive

/** Who made a request (feature-identity, research D5). */
internal sealed interface Caller {
    /** Neither a verified token nor a guest id the server issued. */
    data object Nobody : Caller

    data class Guest(
        val id: String,
    ) : Caller

    data class Customer(
        val customer: io.github.youndie.haul.feature.identity.domain.Customer,
    ) : Caller
}

/**
 * Tells a request's [Caller]. A verified bearer token wins: «a request with both a valid bearer and
 * a guest id acts as the customer; the guest id is used only by the merge». The token itself was
 * checked by the tier the route is mounted under (`installSignIn`); here its `sub` becomes a
 * [Customer], created on the first request it makes.
 */
internal class Callers(
    private val guests: Guests,
    private val customers: Customers,
    private val clock: StoreClock,
) {
    suspend fun of(call: ApplicationCall): Caller {
        call.principal<OidcPrincipal>()?.let { return Caller.Customer(customer(it)) }
        val guest = guestId(call) ?: return Caller.Nobody
        return if (guests.exists(guest)) Caller.Guest(guest) else Caller.Nobody
    }

    /** The guest id the request names, as sent; whether the server issued it is [Guests.exists]. */
    fun guestId(call: ApplicationCall): String? =
        call.request.headers[GUEST_HEADER]
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private suspend fun customer(principal: OidcPrincipal): Customer {
        // A person's token always has a subject; one without is a service's, and is not a shopper.
        val sub = principal.subject?.takeIf { it.isNotBlank() } ?: throw IdentityError.Unauthenticated()
        return customers.signedIn(sub, name(principal, sub), clock.now().toOffsetDateTime())
    }

    /**
     * The token's `name`; a person imported without one is greeted by their e-mail's local part, and
     * one with neither by their subject, rather than by nothing.
     */
    private fun name(
        principal: OidcPrincipal,
        sub: String,
    ): String =
        (principal.claims["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
            ?: principal.email?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: sub
}
