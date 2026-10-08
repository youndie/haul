package io.github.youndie.haul.feature.identity

import io.github.youndie.haul.feature.cart.CartPaths
import io.github.youndie.shildik.oidc.JWT_AUTH_OIDC
import io.github.youndie.shildik.oidc.OidcConfig
import io.github.youndie.shildik.oidc.configureAuth
import io.ktor.client.engine.HttpClientEngine
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer

/**
 * Where customers sign in: a shildik realm, named by its [issuer] (`{base}/realms/{realm}`, the value
 * its tokens carry in `iss`), and the storefront's public client there, [clientId].
 *
 * The issuer is the address both the browser and this server reach: the server reads the realm's
 * keys from the `jwks_uri` its discovery names, which is built from the same address.
 */
internal data class SignInConfig(
    val issuer: String,
    val clientId: String,
) {
    init {
        require(REALMS in issuer && realm.isNotEmpty()) {
            "HAUL_OIDC_ISSUER=$issuer is not a shildik realm's issuer, {base}/realms/{realm}"
        }
    }

    val base: String get() = issuer.substringBeforeLast(REALMS)

    val realm: String get() = issuer.substringAfterLast(REALMS).trimEnd('/')

    /** What the browser is told (`GET /api/v1/sign-in`). */
    fun settings(): SignInSettings =
        SignInSettings(issuer = issuer.trimEnd('/'), clientId = clientId, scope = SCOPE, mergeUrl = CartPaths.MERGE)

    private companion object {
        const val REALMS = "/realms/"

        /** `offline_access`: shildik issues a refresh token only when asked, and its access token lives five minutes. */
        const val SCOPE = "openid profile email offline_access"
    }
}

/**
 * The customer tier's bearer check, under shildik's name [JWT_AUTH_OIDC]: the token's signature
 * against the realm's keys, its lifetime and its issuer (shildik's `TokenVerifier`), and here its
 * `azp` — a token the realm issued to another of its clients is not a storefront sign-in.
 *
 * With sign-in off ([config] `null`) the same name is installed refusing every token, so the tiers
 * mount the same way and a bearer is `401` rather than ignored. [engine] is for tests that swap the
 * client fetching the realm's keys; the server uses the platform's.
 */
internal fun Application.installSignIn(
    config: SignInConfig?,
    engine: HttpClientEngine? = null,
) {
    if (config == null) {
        log.info("sign-in is off: HAUL_OIDC_ISSUER is not set, every bearer token is refused")
        install(Authentication) { bearer(JWT_AUTH_OIDC) { authenticate { null } } }
        return
    }
    configureAuth(
        OidcConfig(realm = config.realm, url = config.base, clientId = config.clientId),
        engine,
    ) { it.azp == config.clientId }
}
