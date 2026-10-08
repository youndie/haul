package io.github.youndie.haul

import io.github.youndie.haul.db.DatabaseConfig

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

    private fun required(name: String): String =
        env(name) ?: error("$name is not set — the server cannot start without it")

    private companion object {
        const val DEFAULT_PORT = 8080
    }
}
