package io.github.youndie.haul.e2e

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The stack the whole path runs against, started from the test: PostgreSQL, shildik with a realm, and
 * the server's own image — the one `scripts/image-build.sh` builds, named by `haul.e2e.image` — with
 * the configuration a stand gives it (`ServerConfig`): the database, the seed, the realm, and the
 * fulfilment clock run [FULFILMENT_SPEED] times the store's pace.
 *
 * **Why the server shares the host's network.** The issuer is a URL both sides must reach by the same
 * name: shildik writes it into every token as `iss`, the server reads the keys from the discovery that
 * URL serves, and this test signs in against it. On a bridge network the server would know shildik by a
 * container name this test cannot resolve, and the test would know it by a mapped port the server
 * cannot reach. With the server on the host's network, `127.0.0.1` is the same place for all three —
 * which needs Docker on Linux, as CI's runners and the build box are.
 *
 * Started once per test JVM; Testcontainers' reaper removes every container when the JVM exits.
 */
internal object ComposedStack {
    /**
     * A courier order takes about two days in the store's world (`FulfilmentPace.STORE`) and a return two
     * more; at a day a second both take seconds, and the simulator then looks every second.
     */
    const val FULFILMENT_SPEED = 86_400

    private const val POSTGRES = "postgres:18-alpine"

    /** The provider's image, the release the server's own suite signs in against (`ShildikHarness`). */
    private const val SHILDIK = "ghcr.io/youndie/shildik-sqlite:0.4.1"
    private const val BOOTSTRAP = "bootstrap"
    const val REALM = "haul"

    /** The storefront's public client in the realm, as a stand registers it. */
    const val CLIENT = "haul-web"

    /** Where the code comes back to: never opened, the test reads the code off the redirect. */
    const val REDIRECT = "http://127.0.0.1/signed-in.html"

    private val image: String =
        checkNotNull(System.getProperty("haul.e2e.image")) {
            "no image under test: run with -Phaul.e2e.image=<tag> (scripts/e2e.sh builds one)"
        }

    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    private val postgres: PostgreSQLContainer<Nothing> =
        PostgreSQLContainer<Nothing>(DockerImageName.parse(POSTGRES)).apply {
            withDatabaseName("haul")
            withUsername("haul")
            withPassword("haul")
            start()
        }

    private val shildikPort: Int = freePort()
    private val shildikOrigin = "http://127.0.0.1:$shildikPort"

    /** The realm's issuer: what the server is configured with, and what every token says it came from. */
    val issuer = "$shildikOrigin/realms/$REALM"

    private val shildik: GenericContainer<*> =
        GenericContainer(DockerImageName.parse(SHILDIK)).apply {
            withExposedPorts(8080, 9000)
            withEnv("SHILDIK_ISSUER", shildikOrigin)
            withEnv("SHILDIK_PORT", "8080")
            withEnv("SHILDIK_MANAGEMENT_PORT", "9000")
            withEnv("SHILDIK_DB_PATH", "/app/shildik.db")
            withEnv("SHILDIK_MASTER_KEYS", "e2e-master-key")
            withEnv("SHILDIK_BOOTSTRAP_TOKEN", BOOTSTRAP)
            // The issuer names a host port chosen before the start, so the port is bound to it rather
            // than to one Docker picks: a mapped port is known only after the issuer was fixed.
            withCreateContainerCmdModifier { command ->
                command.hostConfig!!.withPortBindings(
                    PortBinding(Ports.Binding.bindPort(shildikPort), ExposedPort.tcp(8080)),
                    PortBinding(Ports.Binding.empty(), ExposedPort.tcp(9000)),
                )
            }
            waitingFor(Wait.forHttp("/ready").forPort(9000).withStartupTimeout(Duration.ofSeconds(60)))
            start()
        }

    /** shildik's management API, where the test sets the realm up — the realm itself is test setup only. */
    val shildikAdmin =
        Shildik("http://${shildik.host}:${shildik.getMappedPort(9000)}", BOOTSTRAP, REALM).also {
            it.realm(CLIENT, REDIRECT)
        }

    private val serverPort: Int = freePort()

    /** Where the storefront answers: the page, the trees under `/ui`, the commands under `/api`. */
    val origin = "http://127.0.0.1:$serverPort"

    private val server: GenericContainer<*> =
        GenericContainer(DockerImageName.parse(image)).apply {
            withNetworkMode("host")
            withEnv("PORT", serverPort.toString())
            withEnv("HAUL_DB_URL", "jdbc:postgresql://127.0.0.1:${postgres.getMappedPort(5432)}/haul")
            withEnv("HAUL_DB_USER", postgres.username)
            withEnv("HAUL_DB_PASSWORD", postgres.password)
            withEnv("HAUL_SEED", "true")
            withEnv("HAUL_OIDC_ISSUER", issuer)
            withEnv("HAUL_OIDC_CLIENT_ID", CLIENT)
            withEnv("HAUL_FULFILMENT_SPEED", FULFILMENT_SPEED.toString())
            withEnv("HAUL_ENVIRONMENT", "e2e")
            withEnv("HAUL_COMMIT", "e2e")
            // On the host's network there is no mapped port to wait on; readiness is polled below.
            start()
        }

    init {
        awaitReady()
    }

    /** What the server printed, for a failure message: the last [lines] of it. */
    fun serverLog(lines: Int = 80): String =
        server.logs
            .lines()
            .takeLast(lines)
            .joinToString("\n")

    /**
     * Until `/readyz` answers 200 — the database reached, the migrations and the seed done — or a
     * minute, which is far past a start with the AOT cache and a fresh seed.
     */
    private fun awaitReady() {
        val started = TimeSource.Monotonic.markNow()
        while (started.elapsedNow() < 1.minutes) {
            check(server.isRunning) { "the server's container stopped before it was ready:\n${serverLog()}" }
            val status =
                runCatching {
                    http
                        .send(
                            HttpRequest.newBuilder(URI("$origin/readyz")).timeout(Duration.ofSeconds(2)).build(),
                            HttpResponse.BodyHandlers.discarding(),
                        ).statusCode()
                }.getOrNull()
            if (status == 200) return
            Thread.sleep(0.5.seconds.inWholeMilliseconds)
        }
        error("the server was not ready within a minute:\n${serverLog()}")
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }
}
