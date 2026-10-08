package io.github.youndie.haul

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.catalog.data.PhotoStoreException
import io.github.youndie.haul.feature.catalog.data.S3PhotoStore
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SeedPhotos
import io.github.youndie.haul.seed.Seeder
import io.github.youndie.petich.PetichClock
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
    val clock = systemClock()
    val dataSource = Databases.dataSource(config.database)
    val applied = Databases.migrate(dataSource)
    log.info("migrations applied: {}", applied)
    val photoStore = config.photos?.let { S3PhotoStore(it, now = { clock.now().toInstant() }) }
    log.info(if (photoStore == null) "no object storage: placeholder tiles only" else "photos in {}", config.photos)
    if (config.seed) {
        val database = Databases.connect(dataSource)
        val seeded = Seeder.seedIfEmpty(database, CatalogSeed.generate())
        log.info(if (seeded) "catalog seeded" else "catalog already present, not seeded")
        photoStore?.let { seedPhotos(database, it) }
    }

    embeddedServer(CIO, port = config.port) {
        monitor.subscribe(ApplicationStopped) { dataSource.close() }
        haulModule(
            dataSource,
            clock,
            config.commit,
            config.observability,
            config.webDir,
            photoStore,
            config.signIn,
            sagaClock = sagaClock(),
        )
    }.start(wait = true)
}

/**
 * The sample products' photos, stored once. A store that refuses or cannot be reached does not stop
 * the start: photos are decoration over the placeholder tiles, and the server serves without them.
 */
private fun seedPhotos(
    database: org.jetbrains.exposed.v1.jdbc.Database,
    store: S3PhotoStore,
) {
    try {
        val stored = runBlocking { SeedPhotos.attach(database, store) }
        log.info("sample photos stored: {}", stored)
    } catch (e: PhotoStoreException) {
        log.warn("sample photos not stored, the tiles stay placeholders: {}", e.message)
    }
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

/**
 * The order saga's clock: the wall clock, read here and nowhere else. What it stamps — when a saga's row
 * was last written — is compared with what another process stamped, so it is never the store's «now».
 */
@Suppress(
    "ktlint:kapkan:wall-clock",
    "The composition root is the one reader of the clock: the saga's stamps are compared across processes, which only the wall clock can do.",
)
private fun sagaClock(): PetichClock = PetichClock { System.currentTimeMillis() }
