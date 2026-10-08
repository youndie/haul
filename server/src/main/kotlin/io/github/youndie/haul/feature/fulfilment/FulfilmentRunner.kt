package io.github.youndie.haul.feature.fulfilment

import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentPace
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentSimulator
import io.github.youndie.haul.feature.returns.domain.ReturnSimulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

private val log = LoggerFactory.getLogger("io.github.youndie.haul.fulfilment")

/**
 * How the simulated world runs in this process (research D4): at [pace], looked at every [interval] — or
 * not at all when [interval] is `null`, which is what a test that drives [FulfilmentSimulator.advance] by
 * hand assembles the application with.
 */
internal data class FulfilmentSettings(
    val pace: FulfilmentPace,
    val interval: Duration?,
) {
    companion object {
        /** The store's pace with nothing running: every pass is a test's own call. */
        val MANUAL: FulfilmentSettings = FulfilmentSettings(FulfilmentPace.STORE, interval = null)

        /** The store's pace [speed] times quicker (`HAUL_FULFILMENT_SPEED`), polled as often as it needs. */
        fun running(speed: Double): FulfilmentSettings =
            FulfilmentPace.STORE.faster(speed).let { FulfilmentSettings(it, it.pollInterval) }
    }
}

/**
 * Runs [simulator]'s pass, then [returns]' (B-21), every [interval] in the application's scope, from the
 * first moment it serves until it stops. A pass that fails is said at `warn` and the next one tries again — a
 * failing pass and an idle one look the same from outside, and every move a pass makes is safe to make again.
 */
internal class FulfilmentRunner(
    private val simulator: FulfilmentSimulator,
    private val returns: ReturnSimulator,
    private val interval: Duration,
) {
    fun start(scope: CoroutineScope): Job =
        scope.launch {
            while (isActive) {
                try {
                    val moves = simulator.advance()
                    if (moves > 0) log.debug("fulfilment pass moved {} shipments a step", moves)
                    val returned = returns.advance()
                    if (returned > 0) log.debug("fulfilment pass moved {} returns a step", returned)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.warn("fulfilment pass failed, the next one tries again: {}", e.message, e)
                }
                delay(interval)
            }
        }
}
