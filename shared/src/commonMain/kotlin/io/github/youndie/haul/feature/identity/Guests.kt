package io.github.youndie.haul.feature.identity

import kotlinx.serialization.Serializable

/** How a guest is created and named (endpoint-identity, research D5). */
public object GuestRoutes {
    /** `POST`, no body: answers `201` with a [GuestDto]. */
    public const val GUESTS: String = "/api/v1/guests"

    /** The header a guest names itself with on every request until sign-in. */
    public const val HEADER: String = "X-Haul-Guest"
}

/** A guest the server issued: the id the client keeps and sends in [GuestRoutes.HEADER]. */
@Serializable
public data class GuestDto(
    val id: String,
)
