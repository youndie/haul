package io.github.youndie.haul.db

import kotlinx.coroutines.Job
import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Work that waits for a connection instead of failing fast (B-139): the order saga's, whose member failing
 * compensates an order the shopper meant to place, where a page that failed is pressed again.
 *
 * Under it, an ask [FailFastDataSource] would refuse at once — the database could not be reached a moment ago —
 * goes to the pool, and an ask the pool times out on is made again, for as long as all three hold:
 *
 * - **the coroutine that asked is active.** petich cancels a member at its phase timeout, so that is the bound a
 *   member and its compensation are held to — their own, as the owner decided;
 * - **[perAsk] has not passed since the ask began**, the bound of what no phase times: the engine's writes between
 *   the members, the sweeper's queries;
 * - **[within], when given, has not passed since the element was made**: placement's pass, whose shopper waits for
 *   the answer, waits that long in all, however many asks the outage meets.
 *
 * The pool is still asked for its own `connectionTimeout` at a time ([DatabaseConfig.CONNECTION_TIMEOUT]), so the
 * last ask may end that much past whichever bound ended it. Once the work's time is up an ask is an ordinary one,
 * refused at once while the database is known to be away — so Exposed's retries of a cancelled member's
 * transaction cost nothing.
 *
 * A thread-context element, because the asks are made by blocking JDBC code on whichever thread the coroutine runs
 * on: Exposed's `transaction` inside `withContext(Dispatchers.IO)`, which inherits it. Everything outside it — the
 * pages, the commands, the simulators — keeps B-127's bound.
 */
internal class ConnectionPatience(
    private val perAsk: Duration,
    within: Duration? = null,
    private val time: TimeSource = TimeSource.Monotonic,
) : AbstractCoroutineContextElement(Key),
    ThreadContextElement<ConnectionPatience.Waiting?> {
    private val until: TimeMark? = within?.let { time.markNow() + it }

    override fun updateThreadContext(context: CoroutineContext): Waiting? {
        val before = current.get()
        current.set(Waiting(context[Job]))
        return before
    }

    override fun restoreThreadContext(
        context: CoroutineContext,
        oldState: Waiting?,
    ) {
        if (oldState == null) current.remove() else current.set(oldState)
    }

    /** The patience as the coroutine running on this thread holds it: [job] is that coroutine's. */
    inner class Waiting(
        private val job: Job?,
    ) {
        fun ask(): Ask? = Ask(time.markNow() + perAsk).takeIf { it.lasts() }

        /** One connection's wait, until [end] at the latest. */
        inner class Ask(
            private val end: TimeMark,
        ) {
            /** Whether the pool is asked again after a timeout. */
            fun lasts(): Boolean = job?.isActive != false && !end.hasPassedNow() && until?.hasPassedNow() != true
        }
    }

    companion object Key : CoroutineContext.Key<ConnectionPatience> {
        private val current = ThreadLocal<Waiting?>()

        /**
         * The wait the work on this thread may make for one connection, starting now; `null` outside patient work, or
         * once that work's own time is up.
         */
        fun ask(): Waiting.Ask? = current.get()?.ask()
    }
}
