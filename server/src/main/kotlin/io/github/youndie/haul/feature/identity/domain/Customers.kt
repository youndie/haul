package io.github.youndie.haul.feature.identity.domain

import java.time.OffsetDateTime

/**
 * A signed-in shopper (research §5): [id] is the shildik `sub`, [name] what the token named them on
 * their first sign-in, [plus] their Haul Plus membership (which doubles points and frees delivery).
 */
internal data class Customer(
    val id: String,
    val name: String,
    val plus: Boolean,
) {
    /** What the header greets them with: «Maya» for «Maya Kowalski». */
    val firstName: String get() = name.trim().substringBefore(' ')
}

/** The customers the server knows; one is created by the first request their token makes (feature-identity). */
internal interface Customers {
    /**
     * The customer [id] names, created with [name] and no membership when this is the first time the
     * server sees them. A customer already known keeps the name they were created with.
     */
    suspend fun signedIn(
        id: String,
        name: String,
        at: OffsetDateTime,
    ): Customer
}
