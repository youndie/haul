package io.github.youndie.haul.db

import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * How the pool opens a connection (B-139): the driver, asked again every [pause] while the database refuses it,
 * for up to [patience] before the refusal is the pool's.
 *
 * **Why: Hikari's own retry finds a database that came back seconds late.** Refused, its connection creator sleeps
 * 10 ms, 20 ms, 40 ms, … doubling to 5 s between tries, and it is the only thing that opens connections — so a
 * database back after a five-second outage was found up to five seconds later (B-127 measured a page back 4.1 s
 * after PostgreSQL accepted connections). The order saga waits for a connection within its member's 10 s phase
 * ([ConnectionPatience]), and that wait was spent on Hikari's sleep. A try that keeps asking for [patience] keeps
 * the creator's tries [pause] apart for the first ten seconds or so of an outage; past that Hikari's sleep between
 * them grows again, to its 5 s.
 *
 * [patience] stays under the pool's `connectionTimeout`: Hikari names the failure its last try met as the cause of
 * a timeout, and [FailFastDataSource] opens its window only on a timeout with a cause — a first try still asking
 * when the first request's wait ran out would leave that request's timeout without one.
 *
 * Only a refusal of the connection is asked again — PostgreSQL's class `08` and `57P03`, «the database system is
 * starting up» or «shutting down». A refused password or an unknown database is the answer at once.
 */
internal class ReconnectingDriver(
    private val driver: DataSource,
    private val patience: Duration = PATIENCE,
    private val pause: Duration = PAUSE,
    private val time: TimeSource = TimeSource.Monotonic,
) : DataSource by driver {
    override fun getConnection(): Connection = reconnecting { driver.connection }

    override fun getConnection(
        username: String?,
        password: String?,
    ): Connection = reconnecting { driver.getConnection(username, password) }

    private inline fun reconnecting(open: () -> Connection): Connection {
        val until = time.markNow() + patience
        while (true) {
            try {
                return open()
            } catch (e: SQLException) {
                if (!e.refused() || (until - pause).hasPassedNow()) throw e
                try {
                    Thread.sleep(pause.inWholeMilliseconds)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw e
                }
            }
        }
    }

    private fun SQLException.refused(): Boolean =
        sqlState?.let { it.startsWith("08") || it == CANNOT_CONNECT_NOW } == true

    companion object {
        /** Under [DatabaseConfig.CONNECTION_TIMEOUT]'s 2 s, by as much as a first refusal takes to be told. */
        val PATIENCE: Duration = 1.seconds

        val PAUSE: Duration = 200.milliseconds

        /** PostgreSQL's «the database system is starting up» (and «… shutting down»). */
        private const val CANNOT_CONNECT_NOW = "57P03"
    }
}
