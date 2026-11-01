package io.github.youndie.haul.feature.identity

import kotlinx.serialization.Serializable

/**
 * A demo shopper made for the browser that asked (`POST /api/v1/demo-shoppers`, `201`, B-162): a customer of its
 * own, signed in at the realm's storefront client as the popup's sign-in would be, so the tokens are the same kind —
 * the [accessToken] sent as a bearer, the [refreshToken] renewing it at the provider. Nobody else holds them, and no
 * password ever leaves the server. The browser merges its guest cart with them as after any sign-in.
 */
@Serializable
public data class DemoShopperTokens(
    val accessToken: String,
    val refreshToken: String? = null,
) {
    /** Both are secrets: printed, they say nothing. */
    override fun toString(): String = "DemoShopperTokens(<hidden>)"
}
