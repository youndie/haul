package io.github.youndie.haul

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.catalog.data.PhotoStoreException
import io.github.youndie.haul.feature.catalog.data.S3PhotoStore
import io.github.youndie.haul.feature.checkout.domain.TestCardAccess
import io.github.youndie.haul.feature.identity.DemoOffer
import io.github.youndie.haul.ops.Replicas
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SaleRedater
import io.github.youndie.haul.seed.SeedPhotos
import io.github.youndie.haul.seed.Seeder
import io.github.youndie.petich.PetichClock
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

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
    log.info(if (photoStore == null) "no object storage: seed pictures only" else "photos in {}", config.photos)
    if (config.seed) {
        val database = Databases.connect(dataSource)
        // The sale is dated from the store's day (B-58): a stand seeded today has the canvas's sale today.
        val day = CatalogSeed.dayOf(clock.now())
        val catalog = CatalogSeed.generate(day)
        val seeded = Seeder.seedIfEmpty(database, catalog)
        log.info(if (seeded) "catalog seeded, its sale from {}" else "catalog already present, not seeded", day)
        if (!seeded && Seeder.redateSale(database, catalog)) log.info("the sample sale had ended: moved to {}", day)
        photoStore?.let { seedPhotos(database, it) }
    }

    val demo = config.demoShoppers
    log.info(
        if (demo ==
            null
        ) {
            "no demo shoppers: HAUL_DEMO_* is not set"
        } else {
            "demo shoppers made in the realm, at most {} a day"
        },
        demo?.dailyLimit,
    )

    // However many replicas the chart runs, this is one of them (B-174): the leader election's own pool and the bus's
    // own connection, beside the requests' pool.
    val leases = Databases.leaseSource(config.database)
    val replicas = Replicas.Shared(config.replica, Databases.listener(config.database), leases)
    log.info("replica {}: moves shared through the database, the simulated world on the elected leader", config.replica)

    embeddedServer(CIO, port = config.port) {
        monitor.subscribe(ApplicationStopped) {
            leases.close()
            dataSource.close()
        }
        haulModule(
            dataSource,
            clock,
            config.commit,
            config.observability,
            config.webDir,
            photoStore,
            config.signIn,
            sagaClock = sagaClock(),
            fulfilment = config.fulfilment,
            testCard = TestCardAccess(config.testCardKey),
            // A seeded stand keeps its sale on the store's day while it runs, not only at start (B-70).
            saleLook = if (config.seed) SaleRedater.LOOK else null,
            demo = demo,
            demoSweep = DemoOffer.SWEEP_EVERY,
            replicas = replicas,
        )
    }.start(wait = true)
}

/**
 * The sample products' photos, stored once. A store that refuses or cannot be reached does not stop
 * the start: photos are decoration, and the server serves the seed's pictures without them (B-78).
 */
private fun seedPhotos(
    database: org.jetbrains.exposed.v1.jdbc.Database,
    store: S3PhotoStore,
) {
    try {
        val stored = runBlocking { SeedPhotos.attach(database, store) }
        log.info("sample photos stored: {}", stored)
    } catch (e: PhotoStoreException) {
        log.warn("sample photos not stored, the products keep their pictures: {}", e.message)
    }
}

/**
 * How long readiness waits for the database: under the readiness probe's timeout, Kubernetes' default second
 * (`charts/haul/templates/server.yaml`). A probe the kubelet stopped waiting for is a failure all the same, but
 * the check went on holding a thread and a place in the pool's queue — for 32 s, Hikari's timeout and more,
 * before B-127.
 */
internal val READINESS_BUDGET: Duration = 750.milliseconds

/** The liveness check on a connection once it is had, in JDBC's whole seconds. */
private const val VALIDATION_TIMEOUT_SECONDS = 1

/**
 * Whether the database answers within [within]. Any failure to get a connection is «no», and so is no
 * answer in time: the wait is interrupted, which Hikari answers with an `SQLException`. The blocking JDBC
 * call runs on the IO dispatcher, and only SQL failures are caught, so a cancelled probe is still cancelled.
 */
internal suspend fun databaseAnswers(
    dataSource: DataSource,
    within: Duration = READINESS_BUDGET,
): Boolean =
    withTimeoutOrNull(within) {
        runInterruptible(Dispatchers.IO) {
            try {
                dataSource.connection.use { it.isValid(VALIDATION_TIMEOUT_SECONDS) }
            } catch (_: SQLException) {
                false
            }
        }
    } ?: false

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
