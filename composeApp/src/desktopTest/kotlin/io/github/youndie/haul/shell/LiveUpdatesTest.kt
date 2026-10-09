package io.github.youndie.haul.shell

import io.github.youndie.haul.registry.haulJson
import io.github.youndie.kompot.realtime.UpdateComponentMessage
import io.github.youndie.kompot.standard.TextComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The order page's stream as the browser reads it (B-29), against a server played by a mock engine: the frames
 * of each event, the `ping`s passed over, the headers sign-in's `send` adds, a stream that ends or never answers
 * opened again, and a refusal that ends it.
 */
class LiveUpdatesTest {
    private val requests = CopyOnWriteArrayList<HttpRequestData>()
    private val answers = ArrayDeque<() -> Pair<HttpStatusCode, String>>()

    private val http =
        HttpClient(
            MockEngine { request ->
                requests += request
                val (status, body) = (answers.removeFirstOrNull() ?: { HttpStatusCode.ServiceUnavailable to "" })()
                respond(ByteReadChannel(body), status, headersOf(HttpHeaders.ContentType, "text/event-stream"))
            },
        )

    /** As `Identity.send` does for a customer: the request built with the bearer token; reconnecting at once. */
    private val source =
        ktorRealtime(
            http,
            "http://haul.test/",
            send = { request -> request(mapOf(HttpHeaders.Authorization to "Bearer t-1")) },
            retry = { Duration.ZERO },
        )

    private fun frame(text: String): String =
        haulJson.encodeToString(
            UpdateComponentMessage.serializer(),
            UpdateComponentMessage("order", TextComponent(id = "order", text = text)),
        )

    private fun text(message: UpdateComponentMessage): String = (message.component as TextComponent).text

    /**
     * Each event's data is one update, a `ping` is none; and a stream that ended is opened again — the server
     * starts each with the order as it is, which is how a page that lost its connection catches up.
     */
    @Test
    fun `each event is an update and a stream that ends is opened again`() {
        answers += { HttpStatusCode.OK to "event: ping\n\ndata: ${frame("placed")}\n\n: comment\nevent: ping\n\n" }
        answers += { HttpStatusCode.OK to "data: ${frame("in transit")}\n\n" }

        val heard = runBlocking { withTimeout(5.seconds) { source.subscribe(TOPIC).take(2).toList() } }

        // The second frame is only on the second stream: hearing it is the stream opened again.
        assertEquals(listOf("placed", "in transit"), heard.map(::text))
        requests.forEach {
            assertEquals("/ui/updates", it.url.encodedPath)
            assertEquals(TOPIC, it.url.parameters["topic"])
            assertEquals("Bearer t-1", it.headers[HttpHeaders.Authorization])
        }
    }

    /** No answer and a server error are tried again; the frame that finally comes is heard. */
    @Test
    fun `a stream that never answered is tried again`() {
        answers += { throw IOException("connection refused") }
        answers += { HttpStatusCode.InternalServerError to "" }
        answers += { HttpStatusCode.OK to "data: ${frame("back")}\n\n" }

        val heard = runBlocking { withTimeout(5.seconds) { source.subscribe(TOPIC).take(1).toList() } }

        // Only the third answer has a frame.
        assertEquals(listOf("back"), heard.map(::text))
    }

    /**
     * An order that is not the shopper's — or a sign-in gone past renewing — is refused, and asking again would
     * be refused again: the subscription ends, which kompot reports while the page keeps what it drew.
     */
    @Test
    fun `a refused stream is not asked for again`() {
        answers += { HttpStatusCode.NotFound to """{"code":"order_not_found","message":"No order"}""" }

        val refused =
            assertFailsWith<LiveUpdatesRefused> {
                runBlocking { withTimeout(5.seconds) { source.subscribe(TOPIC).toList() } }
            }

        assertEquals(404, refused.status)
        assertEquals(1, requests.size)
    }

    @Test
    fun `the wait between attempts doubles from a second to thirty`() {
        assertEquals(listOf(1, 2, 4, 8, 16, 30, 30), (0..6).map { backoff(it).inWholeSeconds.toInt() })
    }

    private companion object {
        const val TOPIC = "order:HL-48302"
    }
}
