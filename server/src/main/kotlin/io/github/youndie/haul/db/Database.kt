package io.github.youndie.haul.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.util.DriverDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties
import javax.sql.DataSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [connectionTimeout] is how long a caller waits for a connection before it is told there is none (B-127).
 * Hikari's default is 30 s, and with the database stopped a page paid it on each of Exposed's three attempts:
 * 97 s before its `503`. Two seconds is far above what a healthy pool in the same cluster takes, and it also
 * bounds how long opening a new connection may take (Hikari's login timeout follows it). The start is not what
 * waits for a database that comes up later: the chart's init container does (B-27), and a server started without
 * one still exits at its first connection, as before.
 */
internal data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val maximumPoolSize: Int = DEFAULT_POOL_SIZE,
    val connectionTimeout: Duration = CONNECTION_TIMEOUT,
    /** What the pool's sessions are called in `pg_stat_activity` (B-174: which replica a backend is). */
    val applicationName: String = "haul",
) {
    companion object {
        const val DEFAULT_POOL_SIZE = 10

        /** The wait for a connection; [FailFastDataSource] refuses for as long after the database was not there. */
        val CONNECTION_TIMEOUT: Duration = 2.seconds

        /**
         * How long a pooled connection's liveness check may take before the connection is taken as dead. No longer than
         * [CONNECTION_TIMEOUT]: Hikari's default, 5 s, would outlast it.
         */
        val VALIDATION_TIMEOUT: Duration = 1.seconds
    }
}

internal object Databases {
    /**
     * The pool. Its connections are opened by a [ReconnectingDriver] over the driver Hikari would have used for the
     * URL itself, so a database back after an outage is found within a fraction of a second, not after Hikari's
     * backoff (B-139).
     */
    fun dataSource(config: DatabaseConfig): HikariDataSource =
        HikariDataSource(
            HikariConfig().apply {
                dataSource =
                    ReconnectingDriver(
                        DriverDataSource(
                            config.url,
                            "org.postgresql.Driver",
                            Properties().apply { setProperty("ApplicationName", config.applicationName) },
                            config.user,
                            config.password,
                        ),
                    )
                maximumPoolSize = config.maximumPoolSize
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_READ_COMMITTED"
                connectionTimeout = config.connectionTimeout.inWholeMilliseconds
                validationTimeout =
                    minOf(DatabaseConfig.VALIDATION_TIMEOUT, config.connectionTimeout).inWholeMilliseconds
            },
        )

    /**
     * The leader election's own pool (B-174, `ops/Leader.kt`): a held leadership keeps one connection for as long as
     * it lasts, and its epoch is bumped on a second, so two — and the requests' pool keeps all of its own.
     */
    fun leaseSource(config: DatabaseConfig): HikariDataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.url
                username = config.user
                password = config.password
                poolName = "haul-leader"
                maximumPoolSize = LEASE_POOL_SIZE
                minimumIdle = 1
                connectionTimeout = config.connectionTimeout.inWholeMilliseconds
                validationTimeout =
                    minOf(DatabaseConfig.VALIDATION_TIMEOUT, config.connectionTimeout).inWholeMilliseconds
            },
        )

    /**
     * A connection of its own each time, outside every pool: the replicas' bus listens on one for as long as the
     * process lives (B-174, `ops/PostgresBus.kt`), which a pool would count as a leak.
     */
    fun listener(config: DatabaseConfig): () -> Connection =
        {
            DriverManager.getConnection(
                config.url,
                Properties().apply {
                    setProperty("user", config.user)
                    setProperty("password", config.password)
                    setProperty("ApplicationName", "${config.applicationName} bus")
                    setProperty(
                        "connectTimeout",
                        config.connectionTimeout.inWholeSeconds
                            .coerceAtLeast(1)
                            .toString(),
                    )
                    setProperty("tcpKeepAlive", "true")
                },
            )
        }

    private const val LEASE_POOL_SIZE = 2

    /**
     * Applies the migrations in `db/migration`. Flyway takes PostgreSQL's advisory lock itself, so a
     * second replica starting during a rollout waits instead of migrating twice.
     */
    fun migrate(dataSource: DataSource): Int =
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .validateOnMigrate(true)
            .cleanDisabled(true)
            .load()
            .migrate()
            .migrationsExecuted

    fun connect(dataSource: DataSource): Database = Database.connect(dataSource)
}
