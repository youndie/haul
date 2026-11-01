package io.github.youndie.haul.feature.identity

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.net.ServerSocket
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
 * The stand's provider for the desktop's sign-in (B-178), from the server suite's `ShildikHarness`: shildik's image at
 * the chart's `shildik.version`, a realm `haul` whose public client `haul-web` returns where the chart registers it —
 * the browser's page and the chart's `shildik.desktopRedirectUri` — and Maya with a password. Both are read off
 * `charts/haul/values.yaml`, so this test signs in against what a release would deploy.
 *
 * The issuer names a host port chosen before the container starts (the harness's KDoc says why), and [play] is the
 * person at the system's browser: it opens the provider's page, posts the password form from the issuer's origin, and
 * follows the redirect to wherever the provider sends it — the desktop's loopback listener.
 */
internal object ShildikRealm {
    private val values = File("../charts/haul/values.yaml").readText()

    /** The chart's provider release, which the stand runs. */
    val version: String = chartValue("version")

    /** The desktop's return address as the chart registers it, without a port. */
    val desktopRedirect: String = chartValue("desktopRedirectUri")
    const val WEB_REDIRECT = "https://haul.example/signed-in.html"
    const val CLIENT = "haul-web"
    const val SCOPE = "openid profile email offline_access"
    private const val REALM = "haul"
    private const val BOOTSTRAP = "bootstrap"
    private const val PASSWORD = "correct-horse-battery-staple"
    const val MAYA = "maya"

    private val port: Int = ServerSocket(0).use { it.localPort }
    private val origin = "http://127.0.0.1:$port"
    val issuer = "$origin/realms/$REALM"

    private val http: HttpClient =
        HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build()

    private val container: GenericContainer<*> by lazy {
        GenericContainer(DockerImageName.parse("ghcr.io/youndie/shildik-sqlite:$version")).apply {
            withExposedPorts(8080, 9000)
            withEnv("SHILDIK_ISSUER", origin)
            withEnv("SHILDIK_PORT", "8080")
            withEnv("SHILDIK_MANAGEMENT_PORT", "9000")
            withEnv("SHILDIK_DB_PATH", "/app/shildik.db")
            withEnv("SHILDIK_MASTER_KEYS", "test-master-key")
            withEnv("SHILDIK_BOOTSTRAP_TOKEN", BOOTSTRAP)
            withCreateContainerCmdModifier { command ->
                command.hostConfig!!.withPortBindings(
                    PortBinding(Ports.Binding.bindPort(port), ExposedPort.tcp(8080)),
                    PortBinding(Ports.Binding.empty(), ExposedPort.tcp(9000)),
                )
            }
            waitingFor(Wait.forHttp("/ready").forPort(9000).withStartupTimeout(Duration.ofSeconds(60)))
            start()
        }
    }

    private val management by lazy { "http://${container.host}:${container.getMappedPort(9000)}" }

    /** The realm, its client with both return addresses, and Maya — created once, by the provider's management API. */
    private val realm: Unit by lazy {
        admin("POST", "/admin/tenants", """{"realm":"$REALM","registrationOpen":false}""")
        admin(
            "POST",
            "/admin/tenants/$REALM/clients",
            """{"clientId":"$CLIENT","public":true,"redirectUris":["$WEB_REDIRECT","$desktopRedirect"]}""",
        )
        admin(
            "POST",
            "/admin/tenants/$REALM/users",
            """{"id":"$MAYA","email":"maya@example.test","name":"Maya Kowalski"}""",
        )
        admin("PUT", "/admin/tenants/$REALM/users/$MAYA/password", """{"password":"$PASSWORD"}""")
    }

    /** What the store's `GET /api/v1/sign-in` answers for this realm. */
    val settings: SignInSettings
        get() = realm.let { SignInSettings(issuer, CLIENT, SCOPE, "/api/v1/me/cart/merge") }

    /**
     * The person at the browser the app opened on [authorize]: the provider's page fetched, Maya's password posted,
     * the redirect followed. Returns where the provider sent the browser and what was answered there.
     */
    fun play(authorize: String): Return {
        val page = send(HttpRequest.newBuilder(URI(authorize)).GET().build())
        check(page.statusCode() == 200) { "the authorize page answered ${page.statusCode()}: ${page.body().take(300)}" }
        val location = postPassword(page.body())
        val landed = send(HttpRequest.newBuilder(URI(location)).GET().build())
        return Return(location, landed.statusCode(), landed.body())
    }

    /** Where the browser was sent once signed in, and the page it was answered there. */
    data class Return(
        val location: String,
        val status: Int,
        val page: String,
    )

    /**
     * Maya's tokens as the browser's sign-in gets them — and the server's demo shopper (B-162, `ShildikDemoAccounts`):
     * the code flow with PKCE at the same client, returning to the browser's page, the code exchanged by hand.
     */
    fun browserTokens(): Tokens {
        realm
        val discovery = discovery()
        val verifier = random(32)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val authorize =
            discovery.text("authorization_endpoint") + "?" +
                form(
                    "client_id" to CLIENT,
                    "redirect_uri" to WEB_REDIRECT,
                    "response_type" to "code",
                    "scope" to SCOPE,
                    "state" to random(16),
                    "code_challenge" to challenge,
                    "code_challenge_method" to "S256",
                )
        val page = send(HttpRequest.newBuilder(URI(authorize)).GET().build())
        val location = postPassword(page.body())
        check(location.startsWith(WEB_REDIRECT)) { "the browser's sign-in returned to $location" }
        val code = URLDecoder.decode(query(location).getValue("code"), Charsets.UTF_8)
        val tokens =
            tokenRequest(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to WEB_REDIRECT,
                "client_id" to CLIENT,
                "code_verifier" to verifier,
            )
        check(tokens.statusCode() == 200) { "the token endpoint answered ${tokens.statusCode()}" }
        val body = json(tokens.body())
        return Tokens(body.text("access_token"), body.text("refresh_token"))
    }

    /** A token's `sub`, read off its payload: whose it is. */
    fun subject(accessToken: String): String = claim(accessToken, "sub")

    /** One claim of a token's payload, as text. */
    fun claim(
        accessToken: String,
        name: String,
    ): String = json(String(Base64.getUrlDecoder().decode(accessToken.split('.')[1]))).text(name)

    fun query(address: String): Map<String, String> =
        URI(address).rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.associate {
            it.substringBefore('=') to it.substringAfter('=')
        }

    private fun postPassword(page: String): String {
        val action = checkNotNull(FORM_ACTION.find(page)) { "no form on the sign-in page" }.groupValues[1]
        val parked = checkNotNull(PARKED_STATE.find(page)) { "no parked state on the page" }.groupValues[1]
        val signedIn =
            send(
                HttpRequest
                    .newBuilder(URI(origin + action.replace("&amp;", "&")))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Origin", origin)
                    .POST(
                        HttpRequest.BodyPublishers.ofString(
                            form("state" to parked, "login" to "maya@example.test", "password" to PASSWORD),
                        ),
                    ).build(),
            )
        check(
            signedIn.statusCode() == 302,
        ) { "signing in answered ${signedIn.statusCode()}: ${signedIn.body().take(300)}" }
        return signedIn.headers().firstValue("Location").orElse("")
    }

    private fun tokenRequest(vararg pairs: Pair<String, String>): HttpResponse<String> =
        send(
            HttpRequest
                .newBuilder(URI(discovery().text("token_endpoint")))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(*pairs)))
                .build(),
        )

    private fun discovery(): JsonObject =
        json(send(HttpRequest.newBuilder(URI("$issuer/.well-known/openid-configuration")).GET().build()).body())

    private fun admin(
        method: String,
        path: String,
        body: String,
    ) {
        val response =
            send(
                HttpRequest
                    .newBuilder(URI(management + path))
                    .header("Authorization", "Bearer $BOOTSTRAP")
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body))
                    .build(),
            )
        check(
            response.statusCode() in 200..299,
        ) { "$method $path answered ${response.statusCode()}: ${response.body()}" }
    }

    private fun chartValue(name: String): String =
        checkNotNull(Regex("""(?ms)^shildik:.*?^  $name: "([^"]*)"""").find(values)) {
            "no shildik.$name in charts/haul/values.yaml"
        }.groupValues[1]

    private fun send(request: HttpRequest): HttpResponse<String> =
        http.send(request, HttpResponse.BodyHandlers.ofString())

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun JsonObject.text(name: String): String =
        checkNotNull(this[name]) {
            "no $name in $this"
        }.jsonPrimitive.content

    private fun form(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, Charsets.UTF_8) }

    private fun random(bytes: Int): String = base64Url(ByteArray(bytes).also { SecureRandom().nextBytes(it) })

    private fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private val FORM_ACTION = Regex("""<form method="post" action="([^"]+)"""")
    private val PARKED_STATE = Regex("""name="state" value="([^"]+)"""")
}
