package io.github.youndie.haul.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import javax.sql.DataSource

internal data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val maximumPoolSize: Int = DEFAULT_POOL_SIZE,
) {
    companion object {
        const val DEFAULT_POOL_SIZE = 10
    }
}

internal object Databases {
    fun dataSource(config: DatabaseConfig): HikariDataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.url
                username = config.user
                password = config.password
                driverClassName = "org.postgresql.Driver"
                maximumPoolSize = config.maximumPoolSize
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_READ_COMMITTED"
            },
        )

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
