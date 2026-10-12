package io.github.youndie.haul.db

import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** B-139: the pool's connections are opened by a driver that asks again while the database refuses. */
class ReconnectingDriverTest {
    private val time = TestTimeSource()
    private val driver = ScriptedPool()
    private val reconnecting = ReconnectingDriver(driver, patience = 1.seconds, pause = 1.milliseconds, time = time)

    @Test
    fun `a database that refuses and then answers is connected to in the same try`() {
        var refusals = 0
        driver.next = {
            if (refusals++ < 2) {
                time += 200.milliseconds
                throw REFUSED
            }
            CONNECTION
        }
        assertSame(CONNECTION, reconnecting.connection)
        assertEquals(3, driver.asked)
    }

    @Test
    fun `a database starting up is asked again`() {
        var answers = listOf(SQLException("the database system is starting up", "57P03"))
        driver.next = {
            answers.firstOrNull()?.let {
                answers = answers.drop(1)
                throw it
            } ?: CONNECTION
        }
        assertSame(CONNECTION, reconnecting.connection)
        assertEquals(2, driver.asked)
    }

    /** Past its patience the refusal is the pool's, which names it as the cause of its timeout (B-127's window). */
    @Test
    fun `a database that keeps refusing is given up after the patience`() {
        driver.next = {
            time += 300.milliseconds
            throw REFUSED
        }
        assertSame(REFUSED, assertFailsWith<SQLException> { reconnecting.connection })
        assertEquals(4, driver.asked, "asked at 0, 0.3, 0.6 and 0.9 s; not again past 1 s")
    }

    @Test
    fun `a refused password is the answer at once`() {
        val password = SQLException("password authentication failed for user \"haul\"", "28P01")
        driver.next = { throw password }
        assertSame(password, assertFailsWith<SQLException> { reconnecting.connection })
        assertEquals(1, driver.asked)
    }

    private companion object {
        /** PostgreSQL's driver when nothing listens on the port. */
        val REFUSED = SQLException("Connection to 127.0.0.1:5432 refused.", "08001")
    }
}
