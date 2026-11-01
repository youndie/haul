package io.github.youndie.haul.e2e

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.sql.Connection
import java.sql.DriverManager
import java.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The stack the whole path runs against, started from the test: PostgreSQL, shildik with a realm, and
 * the server's own image, twice — two [replicas] behind a [front] that takes turns, as a stand with
 * `server.replicas: 2` runs (B-174) — the one `scripts/image-build.sh` builds, named by `haul.e2e.image` — with
 * the configuration a stand gives it (`ServerConfig`): the database, the seed, the realm, the
 * fulfilment clock run [FULFILMENT_SPEED] times the store's pace, the key the shoppers' declining walks
 * choose the test card with ([TEST_CARD_KEY]), and the realm's management port the demo shoppers are made through
 * (B-162).
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

    /** The key that opens the test card ···· 0002 to the synthetic shoppers (B-89), as a stand's chart derives one. */
    const val TEST_CARD_KEY = "e2e-test-card-key-0002"

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

    /**
     * The server, twice (B-174): two replicas of the image on one database, as a stand runs them with
     * `server.replicas: 2` — each serving every route and every live page, the moves shared through the database, the
     * simulated world on the one elected leader. Started together, as a rollout starts them: the migrations and the
     * seed take their advisory locks, and the second waits.
     */
    val replicas: List<Replica> = listOf(Replica("a", freePort()), Replica("b", freePort()))

    /**
     * Where the storefront answers: the page, the trees under `/ui`, the commands under `/api` — through [front], which
     * hands each request to the next replica in turn, as the stand's Service does, so a path walks both.
     */
    val front: RoundRobinFront = RoundRobinFront(replicas.map { it.origin })
    val origin: String = front.origin

    /** One replica of the server: [name] is its `HAUL_REPLICA`, which names its sessions in `pg_stat_activity`. */
    internal class Replica(
        val name: String,
        val port: Int,
    ) {
        val origin = "http://127.0.0.1:$port"

        @Volatile
        var container: GenericContainer<*> = container()
            private set

        /** Ends the process at once, as a node that dies or `kubectl delete --grace-period=0` does: SIGKILL. */
        fun kill() {
            DockerClientFactory
                .instance()
                .client()
                .killContainerCmd(container.containerId)
                .exec()
        }

        /** A new container in place of a killed one, on the same port and name, once it is ready. */
        fun replace() {
            // Testcontainers removes the killed one; it was never running a moment ago, and stopping it says nothing.
            container.stop()
            container = container()
            awaitReady(this)
        }

        fun log(lines: Int): String =
            container.logs
                .lines()
                .takeLast(lines)
                .joinToString("\n")

        private fun container(): GenericContainer<*> =
            GenericContainer(DockerImageName.parse(image)).apply {
                withNetworkMode("host")
                withEnv("PORT", port.toString())
                withEnv("HAUL_REPLICA", name)
                withEnv("HAUL_DB_URL", "jdbc:postgresql://127.0.0.1:${postgres.getMappedPort(5432)}/haul")
                withEnv("HAUL_DB_USER", postgres.username)
                withEnv("HAUL_DB_PASSWORD", postgres.password)
                withEnv("HAUL_SEED", "true")
                withEnv("HAUL_OIDC_ISSUER", issuer)
                withEnv("HAUL_OIDC_CLIENT_ID", CLIENT)
                withEnv("HAUL_FULFILMENT_SPEED", FULFILMENT_SPEED.toString())
                withEnv("HAUL_ENVIRONMENT", "e2e")
                withEnv("HAUL_COMMIT", "e2e")
                withEnv("HAUL_TEST_CARD_KEY", TEST_CARD_KEY)
                // Demo shoppers made in the realm through its management port (B-162), as a stand's server reaches its
                // release's shildik: on the host's network the port Docker mapped is on 127.0.0.1.
                withEnv("HAUL_DEMO_MANAGEMENT_URL", "http://127.0.0.1:${shildik.getMappedPort(9000)}")
                withEnv("HAUL_DEMO_MANAGEMENT_TOKEN", BOOTSTRAP)
                withEnv("HAUL_DEMO_REDIRECT_URI", REDIRECT)
                // On the host's network there is no mapped port to wait on; readiness is polled below.
                start()
            }
    }

    init {
        replicas.forEach(::awaitReady)
    }

    /** The replica that leads — holds the election's lock (`vojak <name>` in `pg_stat_activity`) — or `null` for none. */
    fun leader(): Replica? =
        query("SELECT application_name FROM pg_stat_activity WHERE application_name LIKE 'vojak %'") { it.getString(1) }
            .singleOrNull()
            ?.removePrefix("vojak ")
            ?.let { name -> replicas.single { it.name == name } }

    /** A connection to the server's database, past the server: for what a test must hold or read there itself. */
    fun connection(): Connection = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)

    /**
     * Every SKU's stock, read off the server's database (B-88): the wire never carries the count — a product
     * page says only whether it is in stock — so a test that holds the stack to «the walk leaves the stock as it
     * was» reads the rows, as the server's own suite reads them past its repositories.
     */
    fun stock(): Map<String, Int> = query("SELECT id, stock FROM skus") { it.getString(1) to it.getInt(2) }.toMap()

    /** The units placement took for [orderId] and holds: its rows in `stock_reservations`. */
    fun reserved(orderId: String): Int =
        query(
            "SELECT coalesce(sum(quantity), 0) FROM stock_reservations WHERE holder = ?",
            orderId,
        ) { it.getInt(1) }.single()

    /** The order's status and why it was cancelled, `null` for one that was not: its row in `orders`. */
    fun orderStatus(orderId: String): Pair<String, String?> =
        query("SELECT status, cancel_reason FROM orders WHERE id = ?", orderId) {
            it.getString(1) to it.getString(2)
        }.single()

    /** [orderId]'s lines as units per SKU: what it bought. */
    fun linesOf(orderId: String): Map<String, Int> =
        query("SELECT sku_id, sum(quantity) FROM order_lines WHERE order_id = ? GROUP BY sku_id", orderId) {
            it.getString(1) to it.getInt(2)
        }.toMap()

    /** The rows [sql] reads with [parameters] — a string, or an array of them for `= ANY (?)` — each made by [row]. */
    fun <T> rows(
        sql: String,
        vararg parameters: Any,
        row: (java.sql.ResultSet) -> T,
    ): List<T> = query(sql, *parameters, row = row)

    private fun <T> query(
        sql: String,
        vararg parameters: Any,
        row: (java.sql.ResultSet) -> T,
    ): List<T> =
        connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                parameters.forEachIndexed { index, value ->
                    when (value) {
                        is Array<*> -> statement.setArray(index + 1, connection.createArrayOf("text", value))
                        else -> statement.setObject(index + 1, value)
                    }
                }
                statement.executeQuery().use { rows ->
                    generateSequence { if (rows.next()) row(rows) else null }.toList()
                }
            }
        }

    /** What the servers printed, for a failure message: the last [lines] of each replica's. */
    fun serverLog(lines: Int = 80): String = replicas.joinToString("\n") { "--- replica ${it.name}:\n${it.log(lines)}" }

    /**
     * Until [replica]'s `/readyz` answers 200 — the database reached, the migrations and the seed done — or a
     * minute, which is far past a start with the AOT cache and a fresh seed.
     */
    private fun awaitReady(replica: Replica) {
        val started = TimeSource.Monotonic.markNow()
        while (started.elapsedNow() < 1.minutes) {
            check(replica.container.isRunning) {
                "the replica ${replica.name}'s container stopped before it was ready:\n${replica.log(80)}"
            }
            val status =
                runCatching {
                    http
                        .send(
                            HttpRequest
                                .newBuilder(
                                    URI("${replica.origin}/readyz"),
                                ).timeout(Duration.ofSeconds(2))
                                .build(),
                            HttpResponse.BodyHandlers.discarding(),
                        ).statusCode()
                }.getOrNull()
            if (status == 200) return
            Thread.sleep(0.5.seconds.inWholeMilliseconds)
        }
        error("the replica ${replica.name} was not ready within a minute:\n${replica.log(80)}")
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }
}
