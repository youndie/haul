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
}

/** The guests the server issued (research D5). A guest is an id and the cart it owns. */
internal interface Guests {
    /** Issues a new guest id. */
    suspend fun create(at: OffsetDateTime): String

    suspend fun exists(id: String): Boolean
}
