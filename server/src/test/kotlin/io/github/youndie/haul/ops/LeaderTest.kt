package io.github.youndie.haul.ops

import com.zaxxer.hikari.HikariDataSource
import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.order.domain.ShipmentStatus.DELIVERED
import io.github.youndie.haul.feature.order.domain.ShipmentStatus.IN_TRANSIT
import io.github.youndie.haul.feature.order.domain.ShipmentStatus.PACKED
import io.github.youndie.haul.feature.order.domain.ShipmentStatus.PLACED
import io.github.youndie.haul.testing.FulfilmentWorld
import io.github.youndie.haul.testing.PostgresHarness
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.vojak.Election
import io.github.youndie.vojak.ElectionTiming
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Which replica runs the simulated world (B-174): of two, exactly one; the other takes over once the first's lock is
 * gone — its session ended, as a killed pod's is, or its election closed, as a stopped one's is — and never while the
 * first still runs; and the world's passes, handed over in the middle of an order, move every shipment and charge
 * every share once.
 *
 * The timing is vojak's proportions on a shorter scale — a three-second lease, a poll every 200 ms — so a handover
 * takes seconds, not the stand's fifteen.
 */
class LeaderTest {
    @Test
    fun `one of two replicas leads and the other takes over once its session is gone`() =
        twoReplicas { database, replicas ->
            val first = awaitOneRunning(replicas)
            hold(1.seconds) { assertEquals(listOf(first), replicas.running(), "the leader changed with nothing wrong") }

            val since = TimeSource.Monotonic.markNow()
            assertEquals(1, endLeaseSession(database, first), "no lease session of $first to end")
            val second = awaitOneRunning(replicas, other = first)
            println("B-174 handover after a session ended: ${since.elapsedNow()}")
            assertTrue(second != first)
            assertEquals(listOf(), replicas.overlaps, "two replicas ran the world at once")
        }

    @Test
    fun `a leader that stops hands over within a poll`() =
        twoReplicas { _, replicas ->
            val first = awaitOneRunning(replicas)
            val since = TimeSource.Monotonic.markNow()
            replicas.elections.getValue(first).close()
            awaitOneRunning(replicas, other = first)
            val took = since.elapsedNow()
            println("B-174 handover after a clean stop: $took")
            // A clean release spares the next leader vojak's `localLease` (1.5 s here): two polls and the take.
            assertTrue(took < TIMING.localLease, "the next leader waited $took, as for a leader that died")
            assertEquals(listOf(), replicas.overlaps, "two replicas ran the world at once")
        }

    @Test
    fun `the world's passes handed over mid-order move each shipment and charge each share once`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                val order = world.place()
                val passes = ConcurrentHashMap<String, AtomicInteger>()
                twoReplicas(dataSource, work = { name ->
                    while (isActive) {
                        world.simulator.advance()
                        passes.getOrPut(name) { AtomicInteger() }.incrementAndGet()
                        delay(PASS)
                    }
                }) { database, replicas ->
                    val first = awaitOneRunning(replicas)
                    awaitStatuses(world, order, PLACED, PLACED)
                    world.clock.at(4.hours)
                    awaitStatuses(world, order, PACKED, PACKED)
                    world.clock.at(24.hours)
                    awaitStatuses(world, order, IN_TRANSIT, PACKED)

                    assertEquals(1, endLeaseSession(database, first))
                    val second = awaitOneRunning(replicas, other = first)
                    world.clock.at(48.hours)
                    awaitStatuses(world, order, DELIVERED, IN_TRANSIT)
                    world.clock.at(72.hours)
                    awaitStatuses(world, order, DELIVERED, DELIVERED)

                    assertTrue((passes[first]?.get() ?: 0) > 0 && (passes[second]?.get() ?: 0) > 0, "both led: $passes")
                    assertEquals(
                        mapOf("$order-1" to 34_900, "$order-2" to 16_300),
                        world.captures(order),
                        "each share charged once, across the handover",
                    )
                    assertEquals(listOf(), replicas.overlaps, "two replicas ran the world at once")
                }
            }
        }

    /** Two replicas' elections over one database, each running [work] while it leads, watched every few ms. */
    private class Replicas {
        val elections = ConcurrentHashMap<String, Election>()
        private val leading = ConcurrentHashMap.newKeySet<String>()
        val overlaps: MutableList<Set<String>> = java.util.Collections.synchronizedList(mutableListOf())

        fun running(): List<String> = leading.sorted()

        fun started(name: String) {
            leading += name
            if (leading.size > 1) overlaps += leading.toSet()
        }

        fun stopped(name: String) {
            leading -= name
        }
    }

    private fun twoReplicas(
        database: HikariDataSource? = null,
        work: suspend CoroutineScope.(String) -> Unit = { awaitCancellation() },
        block: suspend (DataSource, Replicas) -> Unit,
    ) {
        val pool = database ?: PostgresHarness.freshDatabase()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val leases = mutableListOf<HikariDataSource>()
        val replicas = Replicas()
        try {
            runBlocking {
                for (name in listOf("replica-a", "replica-b")) {
                    val lease = Databases.leaseSource(PostgresHarness.config(pool)).also { leases += it }
                    replicas.elections[name] =
                        Leader(lease, name, TIMING).elect(scope) { leading ->
                            leading.launch {
                                replicas.started(name)
                                try {
                                    work(name)
                                } finally {
                                    replicas.stopped(name)
                                }
                            }
                        }
                }
                block(pool, replicas)
            }
        } finally {
            // The elections first, while their pools are open, so each gives its lease back.
            runBlocking { replicas.elections.values.forEach { it.close() } }
            scope.cancel()
            leases.forEach { it.close() }
            if (database == null) pool.close()
        }
    }

    /** The one replica running, other than [other] when given, once the other is not; within ten seconds. */
    private suspend fun awaitOneRunning(
        replicas: Replicas,
        other: String? = null,
    ): String =
        withTimeout(10.seconds) {
            var running = replicas.running()
            while (running.size != 1 || running.single() == other) {
                delay(10.milliseconds)
                running = replicas.running()
            }
            running.single()
        }

    /** [check] holds every 10 ms for [duration]. */
    private suspend fun hold(
        duration: Duration,
        check: () -> Unit,
    ) {
        val since = TimeSource.Monotonic.markNow()
        while (since.elapsedNow() < duration) {
            check()
            delay(10.milliseconds)
        }
    }

    /** Until Sonora's shipment of [order] is [sonora] and Brooklyn Home Co.'s [brooklyn]; within ten seconds. */
    private suspend fun awaitStatuses(
        world: FulfilmentWorld,
        order: String,
        sonora: String,
        brooklyn: String,
    ) = withTimeout(10.seconds) {
        while (world.statuses(order) != mapOf("$order-1" to sonora, "$order-2" to brooklyn)) delay(PASS)
    }

    /** Ends [candidate]'s lease session as PostgreSQL ends a killed pod's: the number of sessions ended. */
    private fun endLeaseSession(
        database: DataSource,
        candidate: String,
    ): Int =
        database.connection.use { connection ->
            connection
                .prepareStatement(
                    "SELECT count(pg_terminate_backend(pid)) FROM pg_stat_activity WHERE application_name = ?",
                ).use {
                    it.setString(1, "vojak $candidate")
                    it.executeQuery().use { rows -> rows.next().let { rows.getInt(1) } }
                }.also { connection.commit() }
        }

    private companion object {
        val TIMING =
            ElectionTiming(
                serverLease = 3.seconds,
                localLease = 1.5.seconds,
                renewEvery = 500.milliseconds,
                pollEvery = 200.milliseconds,
            )
        val PASS = 50.milliseconds
    }
}
