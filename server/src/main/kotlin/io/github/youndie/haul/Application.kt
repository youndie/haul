package io.github.youndie.haul

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.Seeder
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.sql.SQLException
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("io.github.youndie.haul.Application")

/**
 * The server's entry point: configuration, the database migrated (and seeded when asked) before the
 * first request, then the routes.
 */
public fun main() {
    val config = ServerConfig()
    val dataSource = Databases.dataSource(config.database)
    val applied = Databases.migrate(dataSource)
    log.info("migrations applied: {}", applied)
    if (config.seed) {
        val seeded = Seeder.seedIfEmpty(Databases.connect(dataSource), CatalogSeed.generate())
        log.info(if (seeded) "catalog seeded" else "catalog already present, not seeded")
    }

    embeddedServer(CIO, port = config.port) {
        monitor.subscribe(ApplicationStopped) { dataSource.close() }
        haulModule(dataSource, systemClock(), config.commit, config.observability, config.webDir)
    }.start(wait = true)
}

private const val VALIDATION_TIMEOUT_SECONDS = 2

/**
 * Whether the database answers within two seconds. Any failure to get a connection is «no»; a
 * blocking JDBC call runs on the IO dispatcher, and only SQL failures are caught, so a cancelled
 * probe is still cancelled.
 */
internal suspend fun databaseAnswers(dataSource: DataSource): Boolean =
    withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { it.isValid(VALIDATION_TIMEOUT_SECONDS) }
        } catch (_: SQLException) {
            false
        }
    }

/**
 * The one place the server reads the wall clock; everything else is handed a [StoreClock]. Research
 * §1.6: the demo stand's «now» is the real one, the fixtures' is the canvas's.
 */
@Suppress(
    "ktlint:kapkan:wall-clock",
    "The composition root is the one reader of the clock: delivery days are this store's local dates, not a value another party must agree with.",
)
private fun systemClock(): StoreClock = StoreClock { java.time.ZonedDateTime.now() }
