package io.github.youndie.haul.feature.identity.domain

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.feature.identity.GUEST_HEADER
import java.time.OffsetDateTime

/** What identity can refuse with; the application answers each with its status and body. */
internal sealed class IdentityError(
    val code: ErrorCode,
    override val message: String,
    val field: String? = null,
) : Exception(message) {
    /**
     * Neither a customer token nor a guest id (endpoint-cart: «neither is `401`»), or a guest on a page
     * only a customer may open: the client keeps the guest it has and asks for a sign-in.
     */
    class Unauthenticated : IdentityError(ErrorCode.Unauthenticated, "Create a guest or sign in first")

    /**
     * A cart route named a guest the server never issued, or one whose guest is gone — a stand reseeded
     * under a browser: `401 guest_not_found`, the one answer on which the client replaces its guest (B-115).
     */
    class UnknownGuest : IdentityError(ErrorCode.GuestNotFound, "No such guest: create a new one", GUEST_HEADER)

    /** The guest cart to merge names no guest the server issued (endpoint-identity). */
    class GuestNotFound : IdentityError(ErrorCode.GuestNotFound, "No such guest to merge", GUEST_HEADER)

    /** Sign-in asked of a server that has none configured: `503 unavailable`, the deployment's condition. */
    class SignInOff : IdentityError(ErrorCode.Unavailable, "Sign-in is not configured on this server")

    /** A demo shopper asked of a server whose demo is off (B-162): `503 unavailable`, as sign-in off is. */
    class DemoOff : IdentityError(ErrorCode.Unavailable, "The demo shopper is not available on this store")

    /** As many demo shoppers were made today as the store makes in a day (B-162): `503 unavailable` until some go. */
    class DemoFull : IdentityError(ErrorCode.Unavailable, "Every demo shopper for today is taken — try again later")

    /** The provider did not make or sign in the demo shopper (B-162): `503 unavailable`, nothing kept here. */
    class DemoNotMade : IdentityError(ErrorCode.Unavailable, "The demo shopper could not be made just now — try again")
}

/** The guests the server issued (research D5). A guest is an id and the cart it owns. */
internal interface Guests {
    /** Issues a new guest id. */
    suspend fun create(at: OffsetDateTime): String

    suspend fun exists(id: String): Boolean
}
