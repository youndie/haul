package io.github.youndie.haul.db

import java.io.PrintWriter
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.SQLException
import java.util.logging.Logger
import javax.sql.DataSource

/** A pool, or a driver, that answers each ask with [next] and counts the asks. */
internal class ScriptedPool : DataSource {
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

/** A connection that does nothing: what a scripted ask hands out. */
internal val CONNECTION: Connection =
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
