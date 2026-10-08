package io.github.youndie.haul.testing

import com.zaxxer.hikari.HikariDataSource
import io.github.youndie.haul.db.DatabaseConfig
import io.github.youndie.haul.db.Databases
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.concurrent.atomic.AtomicInteger

/**
 * One PostgreSQL container for the whole suite; each [freshDatabase] is a new, empty, migrated
 * database inside it, so a test that needs «fresh» gets fresh and not «truncated».
 */
internal object PostgresHarness {
    private const val IMAGE = "postgres:18-alpine"
    private val counter = AtomicInteger()

    private val container: PostgreSQLContainer<Nothing> =
        PostgreSQLContainer<Nothing>(DockerImageName.parse(IMAGE)).apply {
            withDatabaseName("haul")
            withUsername("haul")
            withPassword("haul")
            start()
        }

    fun freshDatabase(): HikariDataSource {
        val name = "haul_${counter.incrementAndGet()}"
        container.createConnection("").use { it.createStatement().execute("CREATE DATABASE $name") }
        val url = container.jdbcUrl.replace("/haul", "/$name")
        return Databases
            .dataSource(
                DatabaseConfig(url, container.username, container.password, maximumPoolSize = 4),
            ).also {
                val applied = Databases.migrate(it)
                check(applied > 0) { "Flyway applied no migrations — is db/migration on the classpath?" }
            }
    }
}
