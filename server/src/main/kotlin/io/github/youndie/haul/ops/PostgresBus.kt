package io.github.youndie.haul.ops

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.postgresql.PGConnection
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID
import javax.sql.DataSource
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private val log = LoggerFactory.getLogger("io.github.youndie.haul.ops.bus")

/**
 * What the replicas of the server tell each other (B-174): ids — orders that moved — over PostgreSQL's
 * `LISTEN`/`NOTIFY` on [channel], the database every replica already has. **Ids, never frames**: a frame is a
 * customer's page drawn for that customer, up to tens of kilobytes, and a notification holds 8000 bytes; each
 * replica that has a page listening draws the frame itself, from the same rows, as it does for its own moves.
 *
 * - **Sending** ([start]'s `outgoing`): the ids this process made, gathered while one notification is sent and
 *   sent together, as many as fit in [PAYLOAD_BYTES], through [pool] — a statement of its own, after the move's
 *   write has committed. One that cannot be sent (the database away) is said at `warn` and dropped: the other
 *   replicas' pages are a step behind until the next move or their next connection, which starts with the order as
 *   it is; nothing here waits, and nothing a simulator does waits for it.
 * - **Listening**: one connection of its own ([connect], outside the pool — a pooled connection is lent, and this
 *   one is never given back), `LISTEN`ing, its notifications read every [poll]. Each carries the sender's [origin]:
 *   this process's own come back to it and are dropped, since its pages heard them when they were made. A
 *   connection that fails — the database restarted, the session ended — or that stops answering the check made
 *   every [check] (a network that drops packets fails nothing by itself) is closed and opened again after [retry],
 *   for as long as the scope lives. What was sent while it was away is not heard: the pages here are a step
 *   behind, as above.
 */
internal class PostgresBus(
    private val channel: String,
    private val connect: () -> Connection,
    private val pool: DataSource,
    private val origin: String = UUID.randomUUID().toString(),
    private val poll: Duration = 250.milliseconds,
    private val check: Duration = 10.seconds,
    private val retry: Duration = 1.seconds,
) {
    init {
        require(CHANNEL.matches(channel)) { "a channel must match ${CHANNEL.pattern}, got '$channel'" }
        require(origin.none(Char::isWhitespace) && origin.isNotEmpty()) { "an origin is one word, got '$origin'" }
    }

    private val listening = MutableStateFlow(false)

    /** Whether the listening connection is open and `LISTEN`ing now; what a test waits for before it sends. */
    val connected: StateFlow<Boolean> = listening.asStateFlow()

    /**
     * Sends every id [outgoing] gives to the other replicas and hands every id they send to [incoming], from now
     * until [scope] ends. An id is one word: no whitespace, at most [PAYLOAD_BYTES] long.
     */
    fun start(
        scope: CoroutineScope,
        outgoing: Flow<String>,
        incoming: (String) -> Unit,
    ): Job =
        scope.launch {
            val pending = Channel<String>(PENDING, BufferOverflow.DROP_OLDEST)
            launch { outgoing.collect { pending.trySend(it) } }
            launch { send(pending) }
            launch(Dispatchers.IO) { listen(incoming) }
        }

    private suspend fun send(pending: Channel<String>) {
        var carried: String? = null
        while (true) {
            val first = carried ?: pending.receive()
            carried = null
            val ids = linkedSetOf(first)
            var bytes = origin.length + first.length + 1
            while (true) {
                val next = pending.tryReceive().getOrNull() ?: break
                if (next in ids) continue
                if (bytes + next.length + 1 > PAYLOAD_BYTES) {
                    carried = next
                    break
                }
                ids += next
                bytes += next.length + 1
            }
            notify(ids)
        }
    }

    private suspend fun notify(ids: Collection<String>) {
        val payload = (listOf(origin) + ids.filter(::isWord)).joinToString(" ")
        try {
            withContext(Dispatchers.IO) {
                pool.connection.use { connection ->
                    connection.prepareStatement("SELECT pg_notify(?, ?)").use {
                        it.setString(1, channel)
                        it.setString(2, payload)
                        it.execute()
                    }
                    if (!connection.autoCommit) connection.commit()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("{} moves not sent to the other replicas: {}", ids.size, e.message)
        }
    }

    private fun isWord(id: String): Boolean =
        (id.isNotEmpty() && id.none(Char::isWhitespace) && id.length < PAYLOAD_BYTES).also {
            if (!it) log.warn("not sent to the other replicas, not one word: «{}»", id.take(LOGGED))
        }

    private suspend fun listen(incoming: (String) -> Unit) {
        var lost = false
        while (currentCoroutineContext().isActive) {
            try {
                connect().use { connection ->
                    connection.autoCommit = true
                    connection.createStatement().use { it.execute("LISTEN $channel") }
                    val notifications = connection.unwrap(PGConnection::class.java)
                    listening.value = true
                    if (lost) log.info("listening to the other replicas again on {}", channel)
                    var checked = TimeSource.Monotonic.markNow()
                    while (currentCoroutineContext().isActive) {
                        notifications.getNotifications(poll.inWholeMilliseconds.toInt())?.forEach { note ->
                            if (note.name == channel) deliver(note.parameter, incoming)
                        }
                        if (checked.elapsedNow() >= check) {
                            if (!connection.isValid(
                                    CHECK_SECONDS,
                                )
                            ) {
                                throw SQLException("the listening connection stopped answering")
                            }
                            checked = TimeSource.Monotonic.markNow()
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("not listening to the other replicas, again in {}: {}", retry, e.message)
                lost = true
            } finally {
                listening.value = false
            }
            delay(retry)
        }
    }

    private fun deliver(
        payload: String,
        incoming: (String) -> Unit,
    ) {
        val words = payload.split(' ')
        if (words.first() == origin) return
        words.drop(1).forEach { id ->
            try {
                incoming(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("a move from another replica was not handed on: {}", e.message, e)
            }
        }
    }

    companion object {
        /**
         * The most a notification carries here: half of PostgreSQL's 8000 bytes, so an id never needs measuring in
         * anything but characters — every id the store makes is ASCII.
         */
        const val PAYLOAD_BYTES = 4000

        /** How many ids wait to be sent while a notification is on its way; past it the oldest is dropped. */
        private const val PENDING = 1024

        /** The listening connection's check, in JDBC's whole seconds. */
        private const val CHECK_SECONDS = 2

        private const val LOGGED = 80

        /** An identifier `LISTEN` takes unquoted, and the same one `pg_notify` is given as a string. */
        private val CHANNEL = Regex("[a-z_][a-z0-9_]{0,62}")
    }
}
