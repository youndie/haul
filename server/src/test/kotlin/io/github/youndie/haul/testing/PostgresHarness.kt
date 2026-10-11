package io.github.youndie.haul.testing

import com.zaxxer.hikari.HikariDataSource
import io.github.youndie.haul.db.DatabaseConfig
import io.github.youndie.haul.db.Databases
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.concurrent.atomic.AtomicInteger

/**
 * One PostgreSQL container for the whole suite; each [freshDatabase] is a new, empty, migrated
 * database inside it, so a test that needs «fresh» gets fresh and not «truncated».
 *
 * Each database comes with a pool that holds [POOL_SIZE] connections open until it is closed, and the
 * container takes PostgreSQL's 100 at most, so the test that opens one closes it (`use`, or `@AfterTest`
 * for a database the test class holds). One left open is not felt where it is left: it holds its
 * connections for the rest of the suite, and the suite fails once enough have piled up — in whichever test
 * opens the next database, with «sorry, too many clients already» (B-100). [freshDatabase] refuses to open
 * more than [MAX_OPEN] at once and names where the open ones were opened, so a leak fails with its address.
 */
internal object PostgresHarness {
    private const val IMAGE = "postgres:18-alpine"
    private const val POOL_SIZE = 4

    /**
     * The suite's own databases that stay open to its end — the shared seeded one and a test class's
     * `object` or `companion` database, three today — and the two a test may hold at once, with room left.
     */
    private const val MAX_OPEN = 8
    private val counter = AtomicInteger()

    /** Every database handed out and not closed yet, with where it was opened. */
    private val open = mutableMapOf<HikariDataSource, Throwable>()

    private val container: PostgreSQLContainer<Nothing> =
        PostgreSQLContainer<Nothing>(DockerImageName.parse(IMAGE)).apply {
            withDatabaseName("haul")
            withUsername("haul")
            withPassword("haul")
            start()
        }

    /**
     * A new database, migrated to the end — or only up to the version [upTo], for a test of what a later
     * migration does to rows written before it (the rest is then `Databases.migrate`).
     */
    fun freshDatabase(upTo: String? = null): HikariDataSource {
        checkNoneLeft()
        val name = "haul_${counter.incrementAndGet()}"
        container.createConnection("").use { it.createStatement().execute("CREATE DATABASE $name") }
        val url = container.jdbcUrl.replace("/haul", "/$name")
        return Databases
            .dataSource(
                DatabaseConfig(url, container.username, container.password, maximumPoolSize = POOL_SIZE),
            ).also {
                synchronized(open) { open[it] = Throwable(name) }
                val applied =
                    if (upTo == null) {
                        Databases.migrate(it)
                    } else {
                        Flyway
                            .configure()
                            .dataSource(it)
                            .locations("classpath:db/migration")
                            .target(upTo)
                            .load()
                            .migrate()
                            .migrationsExecuted
                    }
                check(applied > 0) { "Flyway applied no migrations — is db/migration on the classpath?" }
            }
    }

    private fun checkNoneLeft() =
        synchronized(open) {
            open.keys.removeIf { it.isClosed }
            check(open.size < MAX_OPEN) {
                "${open.size} test databases are open, each holding $POOL_SIZE connections to the end of the " +
                    "suite: a test did not close the one it opened. Opened at:\n" +
                    open.values.joinToString("\n") { "  ${it.message} by ${it.openedBy()}" }
            }
        }

    /** The test sources that asked for the database: the helper, if one did, and what called it. */
    private fun Throwable.openedBy(): String =
        stackTrace
            .filter { it.className.startsWith("io.github.youndie.haul.") && "PostgresHarness" !in it.className }
            .take(2)
            .joinToString(" ← ") { "${it.fileName}:${it.lineNumber}" }
}
