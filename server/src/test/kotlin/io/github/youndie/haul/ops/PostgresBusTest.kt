package io.github.youndie.haul.ops

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.testing.PostgresHarness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The replicas' bus (B-174): what one replica made reaches the others through PostgreSQL's `LISTEN`/`NOTIFY`, and
 * not itself; a burst larger than one notification arrives whole; a listening session the database ended is opened
 * again.
 */
class PostgresBusTest {
    @Test
    fun `a move told on one replica is heard on the other and not on itself`() =
        replicas { a, b ->
            a.outgoing.emit("HL-48302")
            assertEquals("HL-48302", withTimeout(5.seconds) { b.heard.receive() })
            assertNull(withTimeoutOrNull(500.milliseconds) { a.heard.receive() }, "a replica heard its own move again")
        }

    @Test
    fun `a burst longer than one notification arrives whole`() {
        // About 9 kB of ids: three notifications at the bus's 4 000 bytes.
        val ids = (1..900).map { "HL-${100_000 + it}" }
        replicas(outgoingOfA = flowOf(*ids.toTypedArray())) { _, b ->
            val heard = withTimeout(10.seconds) { List(ids.size) { b.heard.receive() } }
            assertEquals(ids.toSet(), heard.toSet())
            assertEquals(ids.size, heard.size, "an id came twice")
        }
    }

    @Test
    fun `a listening session the database ended is opened again`() =
        replicas { a, b ->
            PostgresHarness.config(database).let { config ->
                val ended =
                    java.sql.DriverManager.getConnection(config.url, config.user, config.password).use {
                        it
                            .createStatement()
                            .executeQuery(
                                "SELECT count(pg_terminate_backend(pid)) FROM pg_stat_activity WHERE application_name = 'b bus'",
                            ).use { rows -> rows.next().let { rows.getInt(1) } }
                    }
                assertEquals(1, ended, "no listening session of b to end")
            }
            // Told again until b hears: its first moves after the end fall into the gap, as the bus says they do.
            val heard =
                withTimeout(10.seconds) {
                    var number = 0
                    var got: String? = null
                    while (got == null) {
                        number += 1
                        a.outgoing.emit("HL-$number")
                        got = withTimeoutOrNull(200.milliseconds) { b.heard.receive() }
                    }
                    got
                }
            assertEquals(true, heard.startsWith("HL-"))
        }

    private class Replica(
        val outgoing: MutableSharedFlow<String> = MutableSharedFlow(extraBufferCapacity = 64),
        val heard: Channel<String> = Channel(Channel.UNLIMITED),
    )

    private lateinit var database: com.zaxxer.hikari.HikariDataSource

    /**
     * Two replicas' buses on one fresh database, both listening before [block] runs; b listens before a starts, so
     * what a sends from its first moment — [outgoingOfA], when given — is b's to hear.
     */
    private fun replicas(
        outgoingOfA: Flow<String>? = null,
        block: suspend CoroutineScope.(Replica, Replica) -> Unit,
    ) = PostgresHarness.freshDatabase().use { pool ->
        database = pool
        val config = PostgresHarness.config(pool)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            runBlocking {
                val a = Replica()
                val b = Replica()
                val busB = PostgresBus(Replicas.MOVES, Databases.listener(config.copy(applicationName = "b")), pool)
                busB.start(scope, b.outgoing, b.heard::trySend)
                withTimeout(10.seconds) { busB.connected.first { it } }
                val busA = PostgresBus(Replicas.MOVES, Databases.listener(config.copy(applicationName = "a")), pool)
                busA.start(scope, outgoingOfA ?: a.outgoing, a.heard::trySend)
                withTimeout(10.seconds) { busA.connected.first { it } }
                block(a, b)
            }
        } finally {
            scope.cancel()
        }
    }
}
