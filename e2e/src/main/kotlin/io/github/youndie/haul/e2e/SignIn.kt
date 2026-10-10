package io.github.youndie.haul.e2e

import io.github.youndie.haul.feature.identity.SignInSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64

/**
 * Signing a person in the way the storefront's popup does: authorization code with PKCE through the
 * realm's public client the server names (`SignInSettings`), the person's form on the provider's own page
 * posted from the issuer's origin, the code read off the redirect to [redirect] and exchanged at the token
 * endpoint. No password grant and no client of its own, so a token the storefront could not get fails here
 * too. The whole path (`WholePathTest`) and the synthetic shoppers sign in through this one walk.
 *
 * **Nothing secret is ever in a message**: a refusal names the status and the step, never the password,
 * the code or a token — the shoppers' log is read by whoever reads the stand's logs.
 */
internal class SignIn(
    private val redirect: String,
) {
    private val http: HttpClient =
        HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build()

    /** Signs [login] in where [settings] say and returns the access token. */
    fun accessToken(
        settings: SignInSettings,
        login: String,
        password: String,
    ): String {
        val discovery = json(get("${settings.issuer}/.well-known/openid-configuration"))
        val origin = URI(settings.issuer).let { "${it.scheme}://${it.authority}" }
        val verifier = random(32)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val state = random(16)
        val authorize =
            discovery.text("authorization_endpoint") + "?" +
                form(
                    "client_id" to settings.clientId,
                    "redirect_uri" to redirect,
                    "response_type" to "code",
                    "scope" to settings.scope,
                    "state" to state,
                    "nonce" to random(16),
                    "code_challenge" to challenge,
                    "code_challenge_method" to "S256",
                )
        val page = send(HttpRequest.newBuilder(URI(authorize)).GET().build())
        check(page.statusCode() == 200) { "the authorize page answered ${page.statusCode()}: ${page.body().take(300)}" }
        val action = checkNotNull(FORM_ACTION.find(page.body())) { "no form on the sign-in page" }.groupValues[1]
        val parked = checkNotNull(PARKED_STATE.find(page.body())) { "no parked state on the page" }.groupValues[1]

        val signedIn =
            send(
                HttpRequest
                    .newBuilder(URI(origin + action.replace("&amp;", "&")))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Origin", origin)
                    .POST(
                        HttpRequest.BodyPublishers.ofString(
                            form(
                                "state" to parked,
                                "login" to login,
                                "password" to password,
                            ),
                        ),
                    ).build(),
            )
        val location = signedIn.headers().firstValue("Location").orElse("")
        // The query of a redirect carries the code, so only where it goes is named.
        check(signedIn.statusCode() == 302 && location.startsWith(redirect)) {
            "signing $login in answered ${signedIn.statusCode()} to «${location.substringBefore('?')}»"
        }
        val query = URI(location).rawQuery.split('&').associate { it.substringBefore('=') to it.substringAfter('=') }
        check(query["state"] == state) { "the state did not come back" }
        val code = checkNotNull(query["code"]) { "no code came back to the redirect" }

        val tokens =
            send(
                HttpRequest
                    .newBuilder(URI(discovery.text("token_endpoint")))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(
                        HttpRequest.BodyPublishers.ofString(
                            form(
                                "grant_type" to "authorization_code",
                                "code" to URLDecoder.decode(code, Charsets.UTF_8),
                                "redirect_uri" to redirect,
                                "client_id" to settings.clientId,
                                "code_verifier" to verifier,
                            ),
                        ),
                    ).build(),
            )
        // The body carries the tokens, so a refusal is told by its status alone.
        check(tokens.statusCode() == 200) { "the token endpoint answered ${tokens.statusCode()}" }
        return json(tokens.body()).text("access_token")
    }

    private fun get(url: String): String =
        send(HttpRequest.newBuilder(URI(url)).GET().build())
            .also { check(it.statusCode() == 200) { "$url answered ${it.statusCode()}" } }
            .body()

    private fun send(request: HttpRequest): HttpResponse<String> =
        http.send(request, HttpResponse.BodyHandlers.ofString())

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun JsonObject.text(name: String): String =
        checkNotNull(this[name]) {
            "no $name in the answer"
        }.jsonPrimitive.content

    private companion object {
        val FORM_ACTION = Regex("""<form method="post" action="([^"]+)"""")
        val PARKED_STATE = Regex("""name="state" value="([^"]+)"""")
    }
}

internal fun form(vararg pairs: Pair<String, String>): String =
    pairs.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, Charsets.UTF_8) }

private fun random(bytes: Int): String = base64Url(ByteArray(bytes).also { SecureRandom().nextBytes(it) })

private fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
