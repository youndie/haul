package io.github.youndie.haul.feature.identity.data

import io.github.youndie.haul.feature.identity.DemoShopperConfig
import io.github.youndie.haul.feature.identity.DemoShopperTokens
import io.github.youndie.haul.feature.identity.SignInConfig
import io.github.youndie.haul.feature.identity.domain.DemoAccountFailure
import io.github.youndie.haul.feature.identity.domain.DemoAccounts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
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
 * Demo shoppers in a shildik realm (B-162), through what shildik already offers — no grant or route of its own:
 *
 * - the management API ([DemoShopperConfig.management], its bearer [DemoShopperConfig.managementToken]) imports the
 *   person under the id the store gives them and sets a password of 32 random bytes, made here, used once, never
 *   stored, logged or sent anywhere but to the provider;
 * - the person is then signed in exactly as the storefront's popup signs a shopper in — authorization code with PKCE
 *   at the realm's public client ([SignInConfig.clientId]), the provider's own form posted from the issuer's origin,
 *   the code read off the redirect to [DemoShopperConfig.redirectUri] and exchanged — so the tokens are the
 *   storefront's own kind: `azp` the storefront, a refresh token for `offline_access`, renewed by the browser at the
 *   provider as any other sign-in's;
 * - turning one off re-imports the person with `enabled: false` and no ways in, after which shildik refuses their
 *   sign-in and the renewal of any token they hold. shildik 0.4.1 has no way to delete a person.
 *
 * **Nothing secret is in a message**: a refusal names the step and the status, never the password, the code, a
 * token or the management bearer.
 */
internal class ShildikDemoAccounts(
    private val signIn: SignInConfig,
    private val config: DemoShopperConfig,
) : DemoAccounts {
    private val http: HttpClient =
        HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build()

    private val management = config.management.trimEnd('/')
    private val realm = signIn.realm
    private val issuer = signIn.issuer.trimEnd('/')
    private val origin = URI(issuer).let { "${it.scheme}://${it.authority}" }

    override suspend fun create(
        id: String,
        name: String,
    ): DemoShopperTokens =
        io {
            val password = random(PASSWORD_BYTES)
            admin("importing the person", "POST", "/admin/tenants/$realm/users", person(id, name, enabled = true))
            admin(
                "setting the person's password",
                "PUT",
                "/admin/tenants/$realm/users/$id/password",
                buildJsonObject { put("password", password) },
            )
            signInAs(id, password)
        }

    override suspend fun disable(id: String) {
        io {
            val answer =
                send(
                    "turning the person off",
                    adminRequest("POST", "/admin/tenants/$realm/users", person(id, DISABLED_NAME, enabled = false)),
                )
            if (answer.statusCode() !in 200..299) {
                throw DemoAccountFailure("turning the person off answered ${answer.statusCode()}")
            }
        }
    }

    /** The provider's sign-in for [login], walked as the browser's popup walks it; the tokens it ends with. */
    private fun signInAs(
        login: String,
        password: String,
    ): DemoShopperTokens {
        val discovery = json(expect("reading discovery", get("$issuer/.well-known/openid-configuration"), 200).body())
        val verifier = random(32)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val state = random(16)
        val authorize =
            discovery.text("authorization_endpoint") + "?" +
                form(
                    "client_id" to signIn.clientId,
                    "redirect_uri" to config.redirectUri,
                    "response_type" to "code",
                    "scope" to SignInConfig.SCOPE,
                    "state" to state,
                    "nonce" to random(16),
                    "code_challenge" to challenge,
                    "code_challenge_method" to "S256",
                )
        val page = expect("opening the sign-in page", get(authorize), 200).body()
        val action =
            FORM_ACTION.find(page)?.groupValues?.get(1) ?: throw DemoAccountFailure("no form on the sign-in page")
        val parked =
            PARKED_STATE.find(page)?.groupValues?.get(1) ?: throw DemoAccountFailure("no state on the sign-in page")
        val signedIn =
            send(
                "posting the sign-in form",
                HttpRequest
                    .newBuilder(URI(origin + action.replace("&amp;", "&")))
                    .timeout(TIMEOUT)
                    .header("Content-Type", FORM)
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
        // The query of the redirect carries the code, so only the status is named.
        if (signedIn.statusCode() != 302 || !location.startsWith(config.redirectUri)) {
            throw DemoAccountFailure("the sign-in form answered ${signedIn.statusCode()}")
        }
        val query =
            URI(location).rawQuery.orEmpty().split('&').associate {
                it.substringBefore('=') to
                    it.substringAfter('=', "")
            }
        if (query["state"] != state) throw DemoAccountFailure("the sign-in's state did not come back")
        val code = query["code"] ?: throw DemoAccountFailure("no code came back from the sign-in")
        val tokens =
            send(
                "exchanging the code",
                HttpRequest
                    .newBuilder(URI(discovery.text("token_endpoint")))
                    .timeout(TIMEOUT)
                    .header("Content-Type", FORM)
                    .POST(
                        HttpRequest.BodyPublishers.ofString(
                            form(
                                "grant_type" to "authorization_code",
                                "code" to URLDecoder.decode(code, Charsets.UTF_8),
                                "redirect_uri" to config.redirectUri,
                                "client_id" to signIn.clientId,
                                "code_verifier" to verifier,
                            ),
                        ),
                    ).build(),
            )
        // The body is the tokens: a refusal is told by its status alone.
        if (tokens.statusCode() != 200) throw DemoAccountFailure("the token endpoint answered ${tokens.statusCode()}")
        val answer = json(tokens.body())
        return DemoShopperTokens(answer.text("access_token"), answer["refresh_token"]?.jsonPrimitive?.content)
    }

    private fun person(
        id: String,
        name: String,
        enabled: Boolean,
    ): JsonObject =
        buildJsonObject {
            put("id", id)
            put("name", name)
            put("enabled", enabled)
        }

    private fun admin(
        step: String,
        method: String,
        path: String,
        body: JsonObject,
    ) {
        expect(step, send(step, adminRequest(method, path, body)), 200, 201, 204)
    }

    private fun adminRequest(
        method: String,
        path: String,
        body: JsonObject,
    ): HttpRequest =
        HttpRequest
            .newBuilder(URI(management + path))
            .timeout(TIMEOUT)
            .header("Authorization", "Bearer ${config.managementToken}")
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()

    private fun get(url: String): HttpResponse<String> =
        send(
            "reading ${url.substringBefore('?')}",
            HttpRequest
                .newBuilder(URI(url))
                .timeout(TIMEOUT)
                .GET()
                .build(),
        )

    private fun send(
        step: String,
        request: HttpRequest,
    ): HttpResponse<String> =
        try {
            http.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: IOException) {
            throw DemoAccountFailure("$step got no answer: ${e.javaClass.simpleName}", e)
        }

    private fun expect(
        step: String,
        response: HttpResponse<String>,
        vararg statuses: Int,
    ): HttpResponse<String> {
        if (response.statusCode() !in statuses) throw DemoAccountFailure("$step answered ${response.statusCode()}")
        return response
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun JsonObject.text(name: String): String =
        this[name]?.jsonPrimitive?.content ?: throw DemoAccountFailure("no $name in the provider's answer")

    private companion object {
        /** 32 random bytes: far past shildik's twelve characters, and never typed by anyone. */
        const val PASSWORD_BYTES = 32

        /** What a person turned off is called in the realm: nothing of the shopper is kept there. */
        const val DISABLED_NAME = "Swept demo shopper"

        const val FORM = "application/x-www-form-urlencoded"

        val TIMEOUT: Duration = Duration.ofSeconds(10)

        // shildik's own sign-in page, as the e2e and the server's suite read it: the form's action and the parked state.
        val FORM_ACTION = Regex("""<form method="post" action="([^"]+)"""")
        val PARKED_STATE = Regex("""name="state" value="([^"]+)"""")

        val randomness = SecureRandom()

        fun random(bytes: Int): String = base64Url(ByteArray(bytes).also(randomness::nextBytes))

        fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        fun form(vararg pairs: Pair<String, String>): String =
            pairs.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, Charsets.UTF_8) }
    }
}
