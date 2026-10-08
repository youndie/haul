package io.github.youndie.haul.feature.identity.domain

import io.github.youndie.haul.ErrorCode
import java.time.OffsetDateTime

/** What identity can refuse with; the application answers each with its status and body. */
internal sealed class IdentityError(
    val code: ErrorCode,
    override val message: String,
    val field: String? = null,
) : Exception(message) {
    /**
     * Neither a guest id the server issued nor a customer token (endpoint-cart: «neither is `401`»).
     * An id the server never issued, or one whose guest is gone, is the same as none: the client
     * creates a new guest.
     */
    class Unauthenticated : IdentityError(ErrorCode.Unauthenticated, "Create a guest or sign in first")
}

/** The guests the server issued (research D5). A guest is an id and the cart it owns. */
internal interface Guests {
    /** Issues a new guest id. */
    suspend fun create(at: OffsetDateTime): String

    suspend fun exists(id: String): Boolean
}
