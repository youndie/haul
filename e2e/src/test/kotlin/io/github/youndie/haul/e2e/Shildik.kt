package io.github.youndie.haul.e2e

import io.github.youndie.haul.feature.identity.SignInSettings
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * The identity provider's two faces for the whole path: its management API, where the test creates the
 * realm, the storefront's public client and a shopper — setup a stand does once and the test does for
 * itself — and its sign-in, walked the way the browser's popup walks it ([SignIn]).
 *
 * **The token comes the way the storefront's comes**: authorization code with PKCE through the realm's
 * public client, no password grant and no client of the test's own (`SignIn` says how). The server's
 * suite signs in the same way (`ShildikHarness`).
 */
internal class Shildik(
    private val management: String,
    private val bootstrapToken: String,
    private val realm: String,
) {
    private val http: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()

    /** The realm and its one public client, redirecting to [redirect]. */
    fun realm(
        client: String,
        redirect: String,
    ) {
        admin("POST", "/admin/tenants", """{"realm":"$realm"}""")
        admin(
            "POST",
            "/admin/tenants/$realm/clients",
            """{"clientId":"$client","public":true,"redirectUris":["$redirect"]}""",
        )
    }

    /** A shopper named [name] with [password], signing in as `[id]@example.test`; [id] is their `sub`. */
    fun person(
        id: String,
        name: String,
        password: String,
    ): String {
        admin("POST", "/admin/tenants/$realm/users", """{"id":"$id","email":"$id@example.test","name":"$name"}""")
        admin("PUT", "/admin/tenants/$realm/users/$id/password", """{"password":"$password"}""")
        return "$id@example.test"
    }

    /**
     * Signs [login] in where [settings] say — the issuer, the client and the scope the server handed the
     * storefront — coming back to [redirect], and returns the access token ([SignIn], the walk the
     * synthetic shoppers take too).
     */
    fun accessToken(
        settings: SignInSettings,
        redirect: String,
        login: String,
        password: String,
    ): String = SignIn(redirect).accessToken(settings, login, password)

    private fun admin(
        method: String,
        path: String,
        body: String,
    ) {
        val response =
            send(
                HttpRequest
                    .newBuilder(URI(management + path))
                    .header("Authorization", "Bearer $bootstrapToken")
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body))
                    .build(),
            )
        check(
            response.statusCode() in 200..299,
        ) { "$method $path answered ${response.statusCode()}: ${response.body()}" }
    }

    private fun send(request: HttpRequest): HttpResponse<String> =
        http.send(request, HttpResponse.BodyHandlers.ofString())
}
