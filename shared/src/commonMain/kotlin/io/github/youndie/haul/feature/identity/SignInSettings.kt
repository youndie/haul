package io.github.youndie.haul.feature.identity

import kotlinx.serialization.Serializable

/**
 * How the browser signs a shopper in (feature-identity, research D5): the shildik realm's [issuer]
 * (discovery is `{issuer}/.well-known/openid-configuration`), the public [clientId] registered for
 * the storefront, the [scope] to ask for, and where the guest cart is merged once a token is in hand
 * — `POST` to [mergeUrl] with the token and [GUEST_HEADER], answered with kompot's `refresh`.
 *
 * The client reads this from `GET /api/v1/sign-in` before it opens the provider's page; a server
 * with sign-in switched off answers `503 unavailable` there.
 */
@Serializable
public data class SignInSettings(
    val issuer: String,
    val clientId: String,
    val scope: String,
    val mergeUrl: String,
)
