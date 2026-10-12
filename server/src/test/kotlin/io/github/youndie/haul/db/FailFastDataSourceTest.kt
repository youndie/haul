package io.github.youndie.haul.db

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.sql.SQLException
import java.sql.SQLTransientConnectionException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class FailFastDataSourceTest {
    private val time = TestTimeSource()
    private val pool = ScriptedPool()
    private val failFast = FailFastDataSource(pool, window = 2.seconds, time = time)

    @Test
    fun `an unreachable database refuses the next asks at once, for the window, with its cause`() {
        pool.next = { throw UNREACHABLE }
        assertSame(UNREACHABLE, assertFailsWith<SQLTransientConnectionException> { failFast.connection })

        pool.next = { error("the pool was asked again inside the window") }
        time += 1999.milliseconds
        val refused = assertFailsWith<SQLTransientConnectionException> { failFast.connection }
        assertSame(UNREACHABLE, refused.cause)
        assertEquals("08001", refused.sqlState)
        assertEquals(1, pool.asked)
    }

    @Test
    fun `past the window the pool is asked again, and a connection it gives ends the refusals`() {
        pool.next = { throw UNREACHABLE }
        assertFailsWith<SQLTransientConnectionException> { failFast.connection }

        time += 2.seconds
        pool.next = { CONNECTION }
        assertSame(CONNECTION, failFast.connection)
        assertSame(CONNECTION, failFast.connection)
        assertEquals(3, pool.asked)
    }

    /** A pool out of connections under load times out with no cause: refusing everyone would make it an outage. */
    @Test
    fun `a pool that ran out of connections refuses nobody else`() {
        pool.next =
            { throw SQLTransientConnectionException("Connection is not available, request timed out after 2000ms") }
        assertFailsWith<SQLTransientConnectionException> { failFast.connection }

        pool.next = { CONNECTION }
        assertSame(CONNECTION, failFast.connection)
        assertEquals(2, pool.asked)
    }

    @Test
    fun `an interrupted wait refuses nobody else`() {
        pool.next = { throw SQLException("Interrupted during connection acquisition", InterruptedException()) }
        assertFailsWith<SQLException> { failFast.connection }

        pool.next = { CONNECTION }
        assertSame(CONNECTION, failFast.connection)
    }

    /**
     * B-139: saga work is not refused by the window another request opened, and an ask the pool times out on is made
     * again — the database came back on the third, and the saga has its connection.
     */
    @Test
    fun `saga work goes past the window and asks the pool again until it gives a connection`() {
        pool.next = { throw UNREACHABLE }
        assertFailsWith<SQLTransientConnectionException> { failFast.connection }

        var timeouts = 0
        pool.next = {
            if (timeouts++ < 2) {
                time += 2.seconds
                throw UNREACHABLE
            }
            CONNECTION
        }
        assertSame(CONNECTION, patiently(perAsk = 10.seconds) { failFast.connection })
        assertEquals(4, pool.asked)

        pool.next = { CONNECTION }
        assertSame(CONNECTION, failFast.connection, "a connection had by saga work ends the refusals")
    }

    @Test
    fun `saga work waits no longer than its patience for one connection`() {
        pool.next = {
            time += 2.seconds
            throw UNREACHABLE
        }
        val failed =
            assertFailsWith<SQLTransientConnectionException> { patiently(perAsk = 5.seconds) { failFast.connection } }

        assertSame(UNREACHABLE, failed)
        assertEquals(3, pool.asked, "asked at 0 s, 2 s and 4 s; not again at 6 s")
        assertFailsWith<SQLTransientConnectionException> { failFast.connection }
        assertEquals(3, pool.asked, "its timeouts opened the window for everyone else")
    }

    /** Placement's pass: once its time in all is spent, its next ask is an ordinary one, refused in the window. */
    @Test
    fun `saga work waits no longer than its whole pass may`() {
        pool.next = {
            time += 2.seconds
            throw UNREACHABLE
        }
        val patience = ConnectionPatience(perAsk = 10.seconds, within = 3.seconds, time = time)
        runBlocking(patience) {
            assertFailsWith<SQLTransientConnectionException> { failFast.connection }
            assertEquals(2, pool.asked, "asked at 0 s and 2 s; at 4 s the pass's 3 s were spent")

            val refused = assertFailsWith<SQLTransientConnectionException> { failFast.connection }
            assertSame(UNREACHABLE, refused.cause)
            assertEquals(2, pool.asked)
        }
    }

    /** A member cut at its phase timeout: its coroutine is cancelled, and the transaction's retries wait no more. */
    @Test
    fun `saga work whose coroutine was cancelled waits no more`() {
        val member = Job()
        pool.next = {
            time += 2.seconds
            member.cancel()
            throw UNREACHABLE
        }
        var asks = 0
        assertFailsWith<CancellationException> {
            runBlocking(member + ConnectionPatience(perAsk = 10.seconds, time = time)) {
                assertFailsWith<SQLTransientConnectionException> { failFast.connection }
                asks = pool.asked
                assertFailsWith<SQLTransientConnectionException> { failFast.connection }
            }
        }
        assertEquals(1, asks, "the pool was asked again after the member was cancelled")
        assertEquals(1, pool.asked, "a cancelled member's next ask went to the pool")
    }

    /** The patience is the coroutine's: a request that runs on the thread a saga ran on is refused as before. */
    @Test
    fun `the patience is only the coroutine's it was given to`() =
        runBlocking {
            pool.next = {
                time += 2.seconds
                throw UNREACHABLE
            }
            assertFailsWith<SQLTransientConnectionException> { failFast.connection }

            val gate = CompletableDeferred<Unit>()
            val saga = launch(ConnectionPatience(perAsk = 10.seconds, time = time)) { gate.await() }
            yield()
            assertFailsWith<SQLTransientConnectionException> { failFast.connection }
            assertEquals(1, pool.asked, "a request on the thread a saga had run on waited as the saga does")
            gate.complete(Unit)
            saga.join()
        }

    private fun <T> patiently(
        perAsk: Duration,
        block: () -> T,
    ): T = runBlocking(ConnectionPatience(perAsk, time = time)) { block() }

    private companion object {
        /** Hikari's timeout as it comes when the database refused: the connection failure is its cause. */
        val UNREACHABLE =
            SQLTransientConnectionException(
                "HikariPool-1 - Connection is not available, request timed out after 2001ms",
                "08001",
                SQLException("Connection to 127.0.0.1:5432 refused.", "08001"),
            )
    }
}
