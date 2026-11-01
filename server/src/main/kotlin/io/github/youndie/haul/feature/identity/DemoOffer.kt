package io.github.youndie.haul.feature.identity

import io.github.youndie.haul.feature.identity.domain.DemoShoppers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Whether this server makes demo shoppers (B-162), and who makes them: [shoppers] is `null` on a server whose demo is
 * off — no `HAUL_DEMO_*` configuration — where no header offers one and the route answers `503 unavailable`.
 */
internal class DemoOffer(
    val shoppers: DemoShoppers?,
) {
    val on: Boolean get() = shoppers != null

    /**
     * The sweep, every [interval], in [scope] — the application's, so it stops with it. A sweep that fails is logged
     * and tried again at the next; nothing runs when the demo is off.
     */
    fun startSweeping(
        scope: CoroutineScope,
        interval: Duration = SWEEP_EVERY,
    ): Job? {
        val shoppers = shoppers ?: return null
        return scope.launch {
            while (isActive) {
                try {
                    val swept = shoppers.sweep()
                    if (swept > 0) log.info("demo shoppers swept: {}", swept)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.warn("the demo shoppers were not swept, the next look tries again: {}", e.message, e)
                }
                delay(interval)
            }
        }
    }

    companion object {
        /** An hour: a demo shopper is gone within a day and an hour of being made. */
        val SWEEP_EVERY: Duration = 1.hours

        private val log = LoggerFactory.getLogger(DemoOffer::class.java)
    }
}
