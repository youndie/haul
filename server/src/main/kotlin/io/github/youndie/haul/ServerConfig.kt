package io.github.youndie.haul

import io.github.youndie.haul.db.DatabaseConfig
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
            )

    /** Seed the catalog on start when it is empty. Off by default: a production database is not a demo. */
    val seed: Boolean get() = env("HAUL_SEED")?.toBooleanStrict() ?: false

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
     * Who watches this server. An agent is on when both its endpoint and its key are set, off when
     * neither is, and a start with one of the two is refused: a deployment that believes it is
     * observed and is silent looks exactly like one that is working.
     */
    val observability: ObservabilitySettings
        get() =
            ObservabilitySettings(
                release = commit,
                environment = env("HAUL_ENVIRONMENT") ?: "local",
                instance = env("HOSTNAME")?.takeIf { it.isNotBlank() } ?: "local",
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
    }
}
