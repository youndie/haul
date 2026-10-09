package io.github.youndie.haul.shell

import io.github.youndie.haul.registry.haulJson
import io.github.youndie.kompot.realtime.KompotRealtimeSource
import io.github.youndie.kompot.realtime.UpdateComponentMessage
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readLine
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// A live screen's updates (B-29): kompot draws them (`KompotRealtimeProvider` swaps a node by its id), and
// leaves the transport to the application — here the server's stream of server-sent events, kompot's
// reference transport (SPEC §16.6), over the same client and headers as the screens.

/** The server's stream of a channel's frames, `?topic=` the channel a screen named; a screen route like `/ui/home`. */
internal const val UPDATES_PATH: String = "/ui/updates"

/** A channel the server will not stream: the order is not the shopper's (`404`), or the sign-in is gone (`401`). */
public class LiveUpdatesRefused(
    topic: String,
    public val status: Int,
) : Exception("the updates of $topic were refused: status $status")

/**
 * kompot's source of live updates over [http] against [origin], each connection through [send] —
 * `Identity.send`, as for the screens, so it carries the shopper's token and is tried once more after a `401`.
 *
 * **It reconnects, and catches up by itself.** A stream that ends or never answered — the network, a deploy,
 * a server error — is opened again after [retry] (a second, doubling to thirty while it keeps failing), for as
 * long as the screen listens. Updates are not replayed, but the server opens every stream with the screen's
 * node as it is then, so the page is where the server is again without loading itself anew. Only a refusal
 * ends it ([LiveUpdatesRefused]): asking again would be refused again, and kompot reports it while the page
 * keeps what it last drew.
 *
 * A frame this build cannot read is skipped: the page stays as it was, which is what it would be with no
 * stream at all.
 */
public fun ktorRealtime(
    http: HttpClient,
    origin: String,
    send: suspend (
        request: suspend (headers: Map<String, String>) -> HttpResponse,
    ) -> HttpResponse = { it(emptyMap()) },
    retry: (failures: Int) -> Duration = ::backoff,
): KompotRealtimeSource =
    KompotRealtimeSource { topic ->
        channelFlow {
            var failures = 0
            while (true) {
                val status = connect(http, origin, topic, send) { failures = 0 }
                if (status == HttpStatusCode.Unauthorized.value || status == HttpStatusCode.NotFound.value) {
                    throw LiveUpdatesRefused(topic, status)
                }
                delay(retry(failures++))
            }
        }
    }

/**
 * One connection: the stream's frames into this flow until it ends; the status it was answered with, or `null`
 * for no answer at all. [connected] is told once the server has said yes.
 */
@Suppress(
    "ktlint:kapkan:swallowed-failure",
    "A stream that broke is the reason this reconnects; the next stream opens with the node as it is, so nothing is lost by not saying why.",
)
private suspend fun ProducerScope<UpdateComponentMessage>.connect(
    http: HttpClient,
    origin: String,
    topic: String,
    through: suspend (request: suspend (headers: Map<String, String>) -> HttpResponse) -> HttpResponse,
    connected: () -> Unit,
): Int? =
    try {
        through { headers ->
            http
                .prepareGet(origin.trimEnd('/') + UPDATES_PATH) {
                    parameter("topic", topic)
                    headers.forEach { (name, value) -> header(name, value) }
                }.execute { response ->
                    if (response.status == HttpStatusCode.OK) {
                        connected()
                        response.bodyAsChannel().events { data -> frame(data)?.let { send(it) } }
                    }
                    response
                }
        }.status.value
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        // Throwable: in the browser a failed or broken fetch is a JavaScript error, no `Exception` on Wasm.
        null
    }

@Suppress(
    "ktlint:kapkan:swallowed-failure",
    "An unreadable frame leaves the page as it was, the same as no frame; the next one, or the next stream, brings it up to date.",
)
private fun frame(data: String): UpdateComponentMessage? =
    try {
        haulJson.decodeFromString(UpdateComponentMessage.serializer(), data)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

/**
 * The `data` of each event on this stream that has some, in order: `data:` lines joined, an event dispatched
 * at the blank line that ends it; a `ping` carries none and is passed over (SPEC §16.6).
 */
internal suspend fun ByteReadChannel.events(onData: suspend (String) -> Unit) {
    val data = StringBuilder()
    var named: String? = null
    while (true) {
        val line = readLine() ?: return
        when {
            line.isEmpty() -> {
                if (data.isNotEmpty() && (named == null || named == "message")) onData(data.toString())
                data.clear()
                named = null
            }

            line.startsWith(":") -> {}

            else -> {
                val field = line.substringBefore(':')
                val value = line.substringAfter(':', "").removePrefix(" ")
                when (field) {
                    "data" -> {
                        if (data.isNotEmpty()) data.append('\n')
                        data.append(value)
                    }

                    "event" -> {
                        named = value
                    }
                }
            }
        }
    }
}

/** A second after the first failure, doubling, never more than thirty. */
internal fun backoff(failures: Int): Duration = (1L shl failures.coerceIn(0, 5)).seconds.coerceAtMost(30.seconds)
