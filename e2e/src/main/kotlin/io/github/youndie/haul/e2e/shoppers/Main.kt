package io.github.youndie.haul.e2e.shoppers

import io.github.youndie.haul.e2e.SignIn
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * The synthetic shoppers (B-31): a [Walk] started every `interval`, as the people the configuration names
 * in turn, so the stand has orders and sagas to measure (research D6, risk 5). Not a load test: the rate is
 * one walk per interval, at most `inFlight` at once, and a start that would pass that is skipped and said.
 *
 * Every walk ends in one line — what it bought, the order, how long the clock took — or in the step it
 * failed at; a failure never stops the next walk. With `walks` set it stops after that many and exits 1
 * if any failed (the throwaway checks run it that way); a stand runs it with no end.
 *
 * A configuration that cannot run exits 2 naming the variable, before any request.
 */
public fun main() {
    val config =
        try {
            ShoppersConfig.from(System.getenv())
        } catch (e: IllegalArgumentException) {
            log("refused: ${e.message}")
            exitProcess(2)
        }
    exitProcess(if (Shoppers(config).run()) 0 else 1)
}

internal class Shoppers(
    private val config: ShoppersConfig,
) {
    private val signIn = SignIn(config.redirect)
    private val carts = ConcurrentHashMap<String, ReentrantLock>()
    private val inFlight = AtomicInteger()
    private val failed = AtomicInteger()

    /** Walks until [ShoppersConfig.walks] have finished, or forever; `true` when none failed. */
    fun run(): Boolean {
        log(
            "walking ${config.origin} as ${config.people.joinToString()}: one walk every ${config.interval}, " +
                "at most ${config.inFlight} at once, the order read every ${config.poll} for up to ${config.wait}" +
                if (config.walks > 0) ", ${config.walks} walks" else "",
        )
        val finished = CountDownLatch(config.walks)
        var number = 0
        while (config.walks == 0 || number < config.walks) {
            if (inFlight.get() >= config.inFlight) {
                log("skipped a start: ${config.inFlight} walks are still waiting on their orders")
            } else {
                number += 1
                val walk = walkNumber(number)
                inFlight.incrementAndGet()
                Thread.ofVirtual().name("walk $number").start {
                    try {
                        take(walk)
                    } finally {
                        inFlight.decrementAndGet()
                        finished.countDown()
                    }
                }
            }
            if (config.walks == 0 || number < config.walks) Thread.sleep(config.interval.inWholeMilliseconds)
        }
        finished.await()
        log("${config.walks} walks, ${failed.get()} failed")
        return failed.get() == 0
    }

    private fun walkNumber(number: Int): Walk {
        val login = config.people[(number - 1) % config.people.size]
        return Walk(
            number = number,
            origin = config.origin,
            signIn = signIn,
            login = login,
            password = config.password,
            cart = carts.computeIfAbsent(login) { ReentrantLock() },
            poll = config.poll,
            wait = config.wait,
        )
    }

    private fun take(walk: Walk) {
        try {
            val done = walk.take()
            val order = done.order.substringAfterLast('/')
            log(
                "walk ${done.number} as ${done.login}: «${done.product}», order $order " +
                    "placed after ${done.placedAfter.short()}, delivered after ${done.deliveredAfter.short()}, " +
                    "refunded after ${done.refundedAfter.short()}",
            )
        } catch (e: WalkFailed) {
            failed.incrementAndGet()
            log("walk ${walk.number} as ${walk.login} failed at ${e.message}")
        } catch (e: Exception) {
            failed.incrementAndGet()
            log("walk ${walk.number} as ${walk.login} failed: $e")
        }
    }
}

private fun Duration.short(): String = toString(DurationUnit.SECONDS, 1)

/** One line to standard output; the time is the log collector's (`kubectl logs --timestamps`), not this clock's. */
internal fun log(line: String) = println(line)
