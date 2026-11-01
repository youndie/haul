package io.github.youndie.haul

import io.github.youndie.haul.db.DatabaseConfig
import io.github.youndie.haul.feature.catalog.data.S3Config
import io.github.youndie.haul.feature.fulfilment.FulfilmentSettings
import io.github.youndie.haul.feature.identity.DemoShopperConfig
import io.github.youndie.haul.feature.identity.SignInConfig
import io.github.youndie.haul.ops.AgentEndpoint
import io.github.youndie.haul.ops.ObservabilitySettings
import java.io.File

/**
 * The server's configuration, read from the environment through [env] so a test builds one in code.
 *
 * A required value has no default: a server that starts without its database and serves errors is
 * harder to notice than one that refuses to start and says why.
 */
internal class ServerConfig(
    private val env: (String) -> String? = System::getenv,
) {
    val port: Int get() = env("PORT")?.toInt() ?: DEFAULT_PORT

    val database: DatabaseConfig
        get() =
            DatabaseConfig(
                url = required("HAUL_DB_URL"),
                user = required("HAUL_DB_USER"),
                password = required("HAUL_DB_PASSWORD"),
                maximumPoolSize = env("HAUL_DB_POOL_SIZE")?.toInt() ?: DatabaseConfig.DEFAULT_POOL_SIZE,
                applicationName = "haul $replica",
            )

    /**
     * Which replica this is (B-174): `HAUL_REPLICA`, else the pod's name (Kubernetes' `HOSTNAME`), else `local`. What
     * the leader election shows (`vojak <replica>`), what the pool's sessions are named in `pg_stat_activity`
     * (`haul <replica>`) and the agents' instance — never what decides anything.
     */
    val replica: String
        get() =
            env("HAUL_REPLICA")?.takeIf { it.isNotBlank() }
                ?: env("HOSTNAME")?.takeIf { it.isNotBlank() }
                ?: "local"

    /** Seed the catalog on start when it is empty. Off by default: a production database is not a demo. */
    val seed: Boolean get() = env("HAUL_SEED")?.toBooleanStrict() ?: false

    /**
     * The bucket product photos live in (B-30, research D8), or `null` when `HAUL_S3_ENDPOINT` is not
     * set: object storage is optional, and without it every product is its picture (B-78). Once the endpoint
     * is named, the bucket and both keys are required — a half-configured store is refused at start-up,
     * not discovered as a broken photo.
     */
    val photos: S3Config?
        get() =
            env("HAUL_S3_ENDPOINT")?.let {
                S3Config(
                    endpoint = it,
                    bucket = required("HAUL_S3_BUCKET"),
                    accessKey = required("HAUL_S3_ACCESS_KEY"),
                    secretKey = required("HAUL_S3_SECRET_KEY"),
                    region = env("HAUL_S3_REGION") ?: S3Config.DEFAULT_REGION,
                )
            }

    /**
     * How fast the simulated world moves shipments (B-17, research D4): `HAUL_FULFILMENT_SPEED` times the
     * store's own pace — `1`, the default, delivers a courier order in about two days; a stand that wants
     * an order delivered in minutes says `1440` (a day a minute). Anything but a positive number refuses
     * the start: a typo that froze every order would look like a store whose couriers never come.
     */
    val fulfilment: FulfilmentSettings
        get() {
            val speed =
                env("HAUL_FULFILMENT_SPEED")?.let { raw ->
                    raw.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
                        ?: error("HAUL_FULFILMENT_SPEED=$raw is not a positive number")
                } ?: 1.0
            return FulfilmentSettings.running(speed)
        }

    /**
     * The key a request must carry to choose the test card ···· 0002, which the payment simulator always declines
     * (B-89, `TestCardAccess`): the synthetic shoppers', so a share of their walks runs the order saga's
     * compensation. Unset, nobody may choose the card. A key shorter than [MIN_TEST_CARD_KEY] refuses the start: one
     * short enough to guess hands the declining card to any visitor.
     */
    val testCardKey: String?
        get() =
            env("HAUL_TEST_CARD_KEY")?.takeIf { it.isNotBlank() }?.also {
                require(it.length >= MIN_TEST_CARD_KEY) {
                    "HAUL_TEST_CARD_KEY is shorter than $MIN_TEST_CARD_KEY characters — a key that short can be guessed"
                }
            }

    /** The commit the image was built from, for `/version`; `dev` when nobody said. */
    val commit: String get() = env("HAUL_COMMIT") ?: "dev"

    /**
     * The browser bundle's directory, served at `/` (haul-web: «served by haul-server»). The image sets
     * it to the bundle it carries; unset, the server serves the API and no page — which is what a test
     * and `./gradlew :server:run` want. A directory that is named and missing refuses the start: a
     * stand whose page is a 404 behind green probes is the failure this exists to prevent.
     */
    val webDir: File?
        get() =
            env("HAUL_WEB_DIR")?.let { path ->
                File(path).also { require(it.isDirectory) { "HAUL_WEB_DIR=$path is not a directory" } }
            }

    /**
     * Where customers sign in (feature-identity): the shildik realm's issuer and the storefront's
     * public client, set together or not at all. Neither is a server without sign-in — every bearer
     * token is refused and `GET /api/v1/sign-in` says `unavailable` — which a shopper sees at once;
     * one of the two is refused at start, because it is a half-made deployment, not a choice.
     */
    val signIn: SignInConfig?
        get() {
            val issuer = env("HAUL_OIDC_ISSUER")?.takeIf { it.isNotBlank() }
            val clientId = env("HAUL_OIDC_CLIENT_ID")?.takeIf { it.isNotBlank() }
            check((issuer == null) == (clientId == null)) {
                "HAUL_OIDC_ISSUER and HAUL_OIDC_CLIENT_ID are set together or not at all"
            }
            return if (issuer != null && clientId != null) SignInConfig(issuer, clientId) else null
        }

    /**
     * The demo shoppers (B-162): «Try as a demo shopper» makes a customer of the visitor's own in the realm sign-in
     * names, through shildik's management API at `HAUL_DEMO_MANAGEMENT_URL` with the bearer
     * `HAUL_DEMO_MANAGEMENT_TOKEN`, and signs them in at the storefront's public client, whose return page is
     * `HAUL_DEMO_REDIRECT_URI` — the one the realm registered for it. The three are set together or not at all;
     * none is a server with no demo, whose header offers none and whose `POST /api/v1/demo-shoppers` is
     * `503 unavailable`. A demo without sign-in refuses the start: its customers would hold tokens no route accepts.
     * `HAUL_DEMO_DAILY_LIMIT` caps how many are made in a day (default [DemoShopperConfig.DEFAULT_DAILY_LIMIT]).
     */
    val demoShoppers: DemoShopperConfig?
        get() {
            val names = listOf("HAUL_DEMO_MANAGEMENT_URL", "HAUL_DEMO_MANAGEMENT_TOKEN", "HAUL_DEMO_REDIRECT_URI")
            val values = names.map { name -> env(name)?.takeIf { it.isNotBlank() } }
            check(values.all { it == null } || values.none { it == null }) {
                "${names.joinToString()} are set together or not at all"
            }
            val (management, token, redirect) = values
            if (management == null || token == null || redirect == null) return null
            check(signIn != null) { "a demo shopper needs sign-in: set HAUL_OIDC_ISSUER and HAUL_OIDC_CLIENT_ID too" }
            val limit =
                env("HAUL_DEMO_DAILY_LIMIT")?.let { raw ->
                    raw.toIntOrNull()?.takeIf { it > 0 } ?: error("HAUL_DEMO_DAILY_LIMIT=$raw is not a positive number")
                } ?: DemoShopperConfig.DEFAULT_DAILY_LIMIT
            return DemoShopperConfig(management, token, redirect, limit)
        }

    /**
     * Who watches this server. An agent is on when both its endpoint and its key are set, off when
     * neither is, and a start with one of the two is refused: a deployment that believes it is
     * observed and is silent looks exactly like one that is working.
     */
    val observability: ObservabilitySettings
        get() =
            ObservabilitySettings(
                release = commit,
                environment = env("HAUL_ENVIRONMENT") ?: "local",
                instance = replica,
                metrik = agent("METRIK"),
                tracy = agent("TRACY"),
                tracySampleRate =
                    env("HAUL_TRACY_SAMPLE_RATE")?.let { raw ->
                        raw.toDoubleOrNull()?.takeIf { it in 0.0..1.0 }
                            ?: error("HAUL_TRACY_SAMPLE_RATE=$raw is not a fraction between 0 and 1")
                    },
                katcher = agent("KATCHER"),
            )

    private fun agent(name: String): AgentEndpoint? {
        val endpoint = env("HAUL_${name}_ENDPOINT")?.takeIf { it.isNotBlank() }
        val key = env("HAUL_${name}_KEY")?.takeIf { it.isNotBlank() }
        check((endpoint == null) == (key == null)) {
            "HAUL_${name}_ENDPOINT and HAUL_${name}_KEY are set together or not at all — one without the other observes nothing"
        }
        return if (endpoint != null && key != null) AgentEndpoint(endpoint, key) else null
    }

    private fun required(name: String): String =
        env(name) ?: error("$name is not set — the server cannot start without it")

    private companion object {
        const val DEFAULT_PORT = 8080
        const val MIN_TEST_CARD_KEY = 16
    }
}
