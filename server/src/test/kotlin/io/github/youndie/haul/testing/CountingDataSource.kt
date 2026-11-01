package io.github.youndie.haul.testing

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.sql.Connection
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

/**
 * [origin] counting every statement its connections prepare or create: one a query, whichever way Exposed sends it.
 * What a page that must stay cheap is held by — the number of statements does not depend on the machine (B-159,
 * B-164).
 */
internal class CountingDataSource(
    private val origin: DataSource,
) : DataSource by origin {
    val statements = AtomicInteger()

    override fun getConnection(): Connection = counting(origin.connection)

    override fun getConnection(
        username: String?,
        password: String?,
    ): Connection = counting(origin.getConnection(username, password))

    private fun counting(connection: Connection): Connection =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Connection::class.java)) { _, method, args ->
            if (method.name == "prepareStatement" || method.name == "createStatement" ||
                method.name == "prepareCall"
            ) {
                statements.incrementAndGet()
            }
            try {
                method.invoke(connection, *(args ?: emptyArray()))
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        } as Connection
}
