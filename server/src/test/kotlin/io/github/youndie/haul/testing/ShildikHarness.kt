package io.github.youndie.haul.testing

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import io.github.youndie.haul.feature.identity.SignInConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.net.ServerSocket
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.UUID

/**
 * A shildik for the suite: the provider's published image (SQLite, one container), a realm `haul`
 * with the storefront's public client and a second client of the same realm, and people created per
 * test. One container for the whole suite, like [PostgresHarness].
 *
 * **The issuer is an address both sides reach.** It goes into every token as `iss`, and the server
 * under test reads the keys from the `jwks_uri` discovery builds from it — so the container's port is
 * bound to a host port chosen before it starts, and the issuer names that port. A mapped port chosen
 * by Docker would be known only after the issuer had been fixed.
 */
internal object ShildikHarness {
    /** The provider's image, by its release tag: the version the stand's realm would run. */
    private const val IMAGE = "ghcr.io/youndie/shildik-sqlite:0.4.1"
    private const val BOOTSTRAP = "bootstrap"
    private const val REALM = "haul"
    const val CLIENT = "haul-web"

    /** Another public client of the same realm: its tokens verify, and are not the storefront's. */
    const val OTHER_CLIENT = "other-app"
    const val REDIRECT = "http://127.0.0.1/signed-in.html"
    private const val PASSWORD = "correct-horse-battery-staple"

    private val port: Int = ServerSocket(0).use { it.localPort }
    private val origin = "http://127.0.0.1:$port"
    val issuer = "$origin/realms/$REALM"

    private val http: HttpClient =
        HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build()

    private val container: GenericContainer<*> =
        GenericContainer(DockerImageName.parse(IMAGE)).apply {
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

    private val management by lazy { "http://${container.host}:${container.getMappedPort(9000)}" }

    /** The realm and its two clients, created once. */
    private val realm: Unit by lazy {
        admin("POST", "/admin/tenants", """{"realm":"$REALM"}""")
        listOf(CLIENT, OTHER_CLIENT).forEach {
            admin(
                "POST",
                "/admin/tenants/$REALM/clients",
                """{"clientId":"$it","public":true,"redirectUris":["$REDIRECT"]}""",
            )
        }
    }

    /** What the server under test is configured with. */
    val signIn: SignInConfig get() = realm.let { SignInConfig(issuer, CLIENT) }

    /** A new person in the realm, named [name]; returns their id, which is the `sub` of their tokens. */
    fun person(name: String): String {
        realm
        val id = "u-${UUID.randomUUID()}"
        admin("POST", "/admin/tenants/$REALM/users", """{"id":"$id","email":"$id@example.test","name":"$name"}""")
        admin("PUT", "/admin/tenants/$REALM/users/$id/password", """{"password":"$PASSWORD"}""")
        return id
    }

    /**
     * Signs [person] in through [client] the way the browser does — authorization code with PKCE —
     * and returns the access token. The two middle steps are the person on shildik's page: the page
     * is fetched and its form posted, from the issuer's own origin, as a browser would.
     */
    fun accessToken(
        person: String,
        client: String = CLIENT,
    ): String {
        realm
        val discovery = json(get("$issuer/.well-known/openid-configuration"))
        val verifier = random(32)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val state = random(16)
        val authorize =
            discovery.text("authorization_endpoint") + "?" +
                form(
                    "client_id" to client,
                    "redirect_uri" to REDIRECT,
                    "response_type" to "code",
                    "scope" to "openid profile email offline_access",
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
                            form("state" to parked, "login" to "$person@example.test", "password" to PASSWORD),
                        ),
                    ).build(),
            )
        val location = signedIn.headers().firstValue("Location").orElse("")
        check(signedIn.statusCode() == 302 && location.startsWith(REDIRECT)) {
            "signing in answered ${signedIn.statusCode()} to «$location»: ${signedIn.body().take(300)}"
        }
        val query = URI(location).rawQuery.split('&').associate { it.substringBefore('=') to it.substringAfter('=') }
        check(query["state"] == state) { "the state did not come back" }
        val code = checkNotNull(query["code"]) { "no code in $location" }

        val tokens =
            send(
                HttpRequest
                    .newBuilder(URI(discovery.text("token_endpoint")))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(
                        HttpRequest.BodyPublishers.ofString(
                            form(
                                "grant_type" to "authorization_code",
                                "code" to java.net.URLDecoder.decode(code, Charsets.UTF_8),
                                "redirect_uri" to REDIRECT,
                                "client_id" to client,
                                "code_verifier" to verifier,
                            ),
                        ),
                    ).build(),
            )
        check(
            tokens.statusCode() == 200,
        ) { "the token endpoint answered ${tokens.statusCode()}: ${tokens.body().take(300)}" }
        return json(tokens.body()).text("access_token")
    }

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

    private fun get(url: String): String =
        send(HttpRequest.newBuilder(URI(url)).GET().build())
            .also {
                check(it.statusCode() == 200) { "$url answered ${it.statusCode()}" }
            }.body()

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
