package io.github.youndie.haul.shell

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.sse.SSEServerContent
import io.ktor.server.sse.heartbeat
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

/**
 * Where a live page's frames stream (kompot's SPEC §16.6, `?topic=` the channel its tree names): the order's page
 * (B-29) and «Live orders» (B-164). One address for both, so the client builds one URL for every live page; the
 * route's tier is the topic's, decided before the stream opens.
 */
internal const val UPDATES: String = "/ui/updates"

/**
 * Streams a live page's frames as server-sent events: [subscribe] is told the channel the frames go into before
 * [first] — the page as it is now — is sent, so a move between the two is heard rather than lost; then every frame
 * after, until the client leaves, when [unsubscribe] is told. A `ping` every [HEARTBEAT] keeps an idle stream
 * through a proxy.
 *
 * The channel is conflated: every frame is the whole node, so a client that fell behind needs only the last.
 */
internal suspend fun ApplicationCall.respondUpdates(
    subscribe: suspend (Channel<String>) -> Unit,
    unsubscribe: suspend (Channel<String>) -> Unit,
    first: suspend () -> String?,
) {
    response.header(HttpHeaders.CacheControl, "no-store")
    // A proxy that buffers would hold every frame back until the stream ends.
    response.header("X-Accel-Buffering", "no")
    respond(
        SSEServerContent(this) {
            heartbeat {
                period = HEARTBEAT
                event = ServerSentEvent(event = "ping")
            }
            val frames = Channel<String>(Channel.CONFLATED)
            subscribe(frames)
            try {
                first()?.let { send(ServerSentEvent(data = it)) }
                for (frame in frames) send(ServerSentEvent(data = frame))
            } finally {
                withContext(NonCancellable) { unsubscribe(frames) }
            }
        },
    )
}

/** How often an idle stream says it is alive: kompot's reference interval (SPEC §16.6). */
private val HEARTBEAT = 20.seconds
