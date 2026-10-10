package io.github.youndie.haul.seed

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

private val log = LoggerFactory.getLogger("io.github.youndie.haul.seed")

/**
 * Keeps a running stand's sample sale on the store's day (B-70). The seeded deals of the day end at the store's
 * midnight and the sale on its eighth; [Seeder.redateSale] moved them only at start, so a stand that ran past
 * midnight drew no deals — and a guest's home no product at all — until it was restarted.
 *
 * Each look is the start-up re-date, unchanged, for the store's day at that moment: the seed's own rows only, under
 * the seeding lock, never under a live deal and at most once a day — so replicas looking together move the sale
 * once, and a sale a look finds live is left where it is. It looks at each store midnight, the moment the day's
 * deals end, and at least every `interval` beside, so a clock that jumped or a process that slept past a midnight
 * is caught up by the next look.
 *
 * Run only where `main` seeds (`HAUL_SEED`): a database that was never seeded is never re-dated.
 */
internal class SaleRedater(
    private val database: Database,
    private val clock: StoreClock,
) {
    /**
     * Moves the sample sale to the store's day now if every sample deal has ended, and returns whether it did —
     * exactly what a start on this day does ([Seeder.redateSale] with a fresh seed of the day).
     */
    fun redate(): Boolean = Seeder.redateSale(database, CatalogSeed.generate(CatalogSeed.dayOf(clock.now())))

    /** Looks at each store midnight and at least every [interval], in [scope], until the scope ends. */
    fun start(
        scope: CoroutineScope,
        interval: Duration = LOOK,
    ): Job =
        scope.launch {
            while (isActive) {
                delay(untilNextLook(clock.now(), interval))
                try {
                    if (withContext(Dispatchers.IO) { redate() }) {
                        log.info("the sample sale had ended: moved to {}", CatalogSeed.dayOf(clock.now()))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.warn("the sample sale was not re-dated, the next look tries again: {}", e.message, e)
                }
            }
        }

    companion object {
        /** How long the sale goes unlooked at, at most, between two midnights. */
        val LOOK: Duration = 1.hours

        /** The shortest wait: a look that woke a moment before midnight looks again just after it. */
        private val SHORTEST = 1.seconds

        /** How long from [now] to the next look: the store's next midnight, or [interval] if that comes first. */
        fun untilNextLook(
            now: ZonedDateTime,
            interval: Duration,
        ): Duration {
            val midnight =
                now
                    .withZoneSameInstant(DeliveryCalendar.STORE)
                    .toLocalDate()
                    .plusDays(1)
                    .atStartOfDay(DeliveryCalendar.STORE)
            val untilMidnight =
                java.time.Duration
                    .between(now, midnight)
                    .toKotlinDuration()
            return minOf(untilMidnight.coerceAtLeast(SHORTEST), interval)
        }
    }
}
