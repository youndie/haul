package io.github.youndie.haul.db

import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException
import java.sql.SQLTransientConnectionException
import javax.sql.DataSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * The pool as the server's requests and workers see it (B-127): once a connection could not be had because the
 * database could not be reached, every connection asked for during the next [window] is refused at once, with
 * the same cause, instead of waiting out the pool's timeout again.
 *
 * That is what makes a database outage one wait per request rather than one per query: Exposed runs a
 * transaction up to three times on an `SQLException`, and a page asks for a connection more than once. The
 * first ask pays the pool's timeout; the retries and the rest of the page fail on the spot, and so does every
 * other request in that window. A statement that failed is untouched — Exposed's retries still cover it.
 *
 * Only an unreachable database opens the window: Hikari's timeout carrying the connection failure it last met.
 * A pool that merely ran out of connections under load times out with no cause, and refusing everyone for it
 * would turn a slow moment into an outage. An interrupted wait (a readiness check giving up) says nothing either.
 *
 * **Saga work is the exception (B-139):** an ask made under [ConnectionPatience] is never refused by the window,
 * and one the pool times out on is made again while that patience lasts — so a short outage is waited out by the
 * order saga instead of compensating the order. Its failed asks still open the window for everybody else.
 *
 * **And saga work is handed a connection that is alive (B-140).** Hikari skips its own check on a connection used in
 * the last half second, so when the database goes away right after a busy moment the pool hands out the
 * connections the outage closed, one after another, and each fails at its first statement — not at the ask, where
 * the patience is. Exposed's three tries of the member's transaction were spent on them in milliseconds and the
 * order compensated: what failed `PlacementOutageTest` on a fast runner. Under the patience a connection is asked
 * whether it is alive ([Connection.isValid], one round trip) and a dead one is evicted from the pool and the ask
 * made again; everybody else keeps Hikari's check, and the statement that fails on a dead connection is theirs to
 * retry as before.
 */
internal class FailFastDataSource(
    private val pool: DataSource,
    private val window: Duration = DatabaseConfig.CONNECTION_TIMEOUT,
    private val time: TimeSource = TimeSource.Monotonic,
) : DataSource by pool {
    @Volatile
    private var unreachable: Unreachable? = null

    override fun getConnection(): Connection = connect { pool.connection }

    override fun getConnection(
        username: String?,
        password: String?,
    ): Connection = connect { pool.getConnection(username, password) }

    private inline fun connect(open: () -> Connection): Connection {
        val ask = ConnectionPatience.ask()
        if (ask == null) unreachable?.takeUnless { it.until.hasPassedNow() }?.let { throw it.refusal() }
        val began = time.markNow()
        var waited = false
        while (true) {
            val attempt = time.markNow()
            val connection =
                try {
                    open()
                } catch (e: SQLTransientConnectionException) {
                    noteUnreachable(e)
                    if (ask == null || !ask.lasts()) throw e
                    waited = true
                    // A pool that answered at once is not asked again at once.
                    pauseAfter(attempt, e)
                    continue
                }
            if (ask != null && ask.lasts() && !connection.alive()) {
                discard(connection)
                waited = true
                continue
            }
            if (waited) log.info("saga work had its connection after waiting {} for the database", began.elapsedNow())
            unreachable = null
            return connection
        }
    }

    private fun noteUnreachable(e: SQLTransientConnectionException) {
        if (e.cause !is SQLException) return
        // Said once an outage, not once a window: the requests it fails say the rest.
        if (unreachable == null) {
            log.warn("the database cannot be reached, connections refused for {} at a time: {}", window, e.message)
        }
        unreachable = Unreachable(time.markNow() + window, e)
    }

    private fun Connection.alive(): Boolean =
        try {
            isValid(VALIDATION_SECONDS)
        } catch (_: SQLException) {
            false
        }

    /** Out of the pool, not back into it: returned, it would be handed out again unchecked. */
    private fun discard(connection: Connection) {
        try {
            if (pool.isWrapperFor(HikariDataSource::class.java)) {
                pool.unwrap(HikariDataSource::class.java).evictConnection(connection)
            } else {
                connection.close()
            }
        } catch (_: SQLException) {
            // It is dead already; there is nothing left to give back.
        }
    }

    private fun pauseAfter(
        attempt: TimeMark,
        failure: SQLException,
    ) {
        val rest = MIN_ATTEMPT - attempt.elapsedNow()
        if (!rest.isPositive()) return
        try {
            Thread.sleep(rest.inWholeMilliseconds)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw failure
        }
    }

    private class Unreachable(
        val until: TimeMark,
        val cause: SQLTransientConnectionException,
    ) {
        fun refusal() =
            SQLTransientConnectionException("the database could not be reached a moment ago", cause.sqlState, cause)
    }

    private companion object {
        private val log = LoggerFactory.getLogger(FailFastDataSource::class.java)

        /** The least one ask of saga work takes before the next: the pool's own timeout is what paces it. */
        val MIN_ATTEMPT = 100.milliseconds

        /** How long saga work's check of a connection may take ([DatabaseConfig.VALIDATION_TIMEOUT], in whole seconds). */
        val VALIDATION_SECONDS =
            DatabaseConfig.VALIDATION_TIMEOUT.inWholeSeconds
                .toInt()
                .coerceAtLeast(1)
    }
}
