package io.github.youndie.haul.db

import java.io.PrintWriter
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.SQLException
import java.sql.SQLTransientConnectionException
import java.util.logging.Logger
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
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

    private class ScriptedPool : DataSource {
        var next: () -> Connection = { error("nothing scripted") }
        var asked = 0

        override fun getConnection(): Connection {
            asked++
            return next()
        }

        override fun getConnection(
            username: String?,
            password: String?,
        ): Connection = getConnection()

        override fun getLogWriter(): PrintWriter? = null

        override fun setLogWriter(out: PrintWriter?) = Unit

        override fun setLoginTimeout(seconds: Int) = Unit

        override fun getLoginTimeout(): Int = 0

        override fun getParentLogger(): Logger = Logger.getGlobal()

        override fun <T : Any?> unwrap(iface: Class<T>?): T = throw SQLException("not a wrapper")

        override fun isWrapperFor(iface: Class<*>?): Boolean = false
    }

    private companion object {
        /** Hikari's timeout as it comes when the database refused: the connection failure is its cause. */
        val UNREACHABLE =
            SQLTransientConnectionException(
                "HikariPool-1 - Connection is not available, request timed out after 2001ms",
                "08001",
                SQLException("Connection to 127.0.0.1:5432 refused.", "08001"),
            )

        val CONNECTION: Connection =
            Proxy.newProxyInstance(
                Connection::class.java.classLoader,
                arrayOf(Connection::class.java),
            ) { proxy, method, args ->
                when (method.name) {
                    "toString" -> "a connection"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.single()
                    else -> null
                }
            } as Connection
    }
}
