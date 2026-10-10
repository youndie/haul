package io.github.youndie.haul.feature.identity

import org.publicvalue.multiplatform.oidc.ExperimentalOpenIdConnect
import org.publicvalue.multiplatform.oidc.OpenIdConnectClient
import org.publicvalue.multiplatform.oidc.appsupport.WebCodeAuthFlowFactory

/**
 * The browser's sign-in (research risk 2, decided in B-12): kotlin-multiplatform-oidc, the client
 * shildik's app contour was accepted against. The provider's page opens in a popup; it returns to
 * [redirectUri] — `signed-in.html`, a static page beside the bundle that posts the address back to
 * this window and closes — and the code is exchanged for tokens here, with PKCE, at the realm's token
 * endpoint, which shildik lets any page read.
 *
 * The popup is the one named [windowTarget]: the storefront opens it first and watches it
 * ([PopupSignInFlow], B-46), because this library's flow does not notice the shopper closing it.
 */
@OptIn(ExperimentalOpenIdConnect::class)
public class OidcSignInFlow(
    private val redirectUri: String,
    windowTarget: String,
) : SignInFlow {
    private val factory = WebCodeAuthFlowFactory(windowTarget = windowTarget)

    override suspend fun signIn(settings: suspend () -> SignInSettings): Tokens {
        val response = factory.createAuthFlow(oidcClient(settings(), redirectUri)).getAccessToken()
        return Tokens(response.access_token, response.refresh_token)
    }

    override suspend fun refresh(
        settings: SignInSettings,
        refreshToken: String,
    ): Tokens {
        val response = oidcClient(settings, redirectUri).refreshToken(refreshToken)
        // shildik rotates refresh tokens; a provider that does not answers none, and the old one stands.
        return Tokens(response.access_token, response.refresh_token ?: refreshToken)
    }
}

/** The realm's client as [settings] name it, returning to [returnTo], its endpoints discovered. */
internal suspend fun oidcClient(
    settings: SignInSettings,
    returnTo: String,
): OpenIdConnectClient =
    OpenIdConnectClient(discoveryUri = "${settings.issuer}/.well-known/openid-configuration") {
        clientId = settings.clientId
        redirectUri = returnTo
        scope = settings.scope
    }.apply { discover() }
