package io.github.youndie.haul.feature.identity

import kotlinx.serialization.Serializable

/** The header a guest names itself with on every request until sign-in (research D5). */
public const val GUEST_HEADER: String = "X-Haul-Guest"

/** A guest the server issued (`POST /api/v1/guests`, `201`): the id the client keeps and sends in [GUEST_HEADER]. */
@Serializable
public data class GuestDto(
    val id: String,
)
