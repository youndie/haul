package io.github.youndie.haul.e2e

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.youndie.haul.feature.checkout.IDEMPOTENCY_KEY_HEADER
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * What stands in front of the replicas in the composed stack (B-174), as the stand's Service and Traefik do: each
 * request goes to the next [upstreams] in turn, streamed back as it comes — a live page's server-sent events included.
 *
 * - **A replica that refuses the connection is skipped** for that request and from then on ([down]): the request
 *   never reached it, so the next one is asked. A Service does the same once the pod's readiness fails; here the test
 *   that kills a replica also says so at once, so no new request is sent its way.
 * - **A request in flight when its replica went away is cut**: answered `502` when nothing was sent back yet, and
 *   counted ([cut]), as a client behind a Service sees a pod that died under it. Whether to send it again is the
 *   client's business.
 *
 * [inFlight] counts the placements (a `POST` with an `Idempotency-Key`) each replica is working on now, so a test can
 * kill one while it is.
 */
internal class RoundRobinFront(
    private val upstreams: List<String>,
) : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val next = AtomicInteger()
    private val down = ConcurrentHashMap.newKeySet<String>()
    private val placements = ConcurrentHashMap<String, AtomicInteger>()
    private val client: HttpClient =
        HttpClient
            .newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2))
            .build()

    /** The requests cut by a replica that went away before it answered them. */
    val cut: MutableList<Cut> = java.util.Collections.synchronizedList(mutableListOf())

    val origin: String

    init {
        server.executor = Executors.newVirtualThreadPerTaskExecutor()
        server.createContext("/") { exchange -> exchange.use { handle(it) } }
        server.start()
        origin = "http://127.0.0.1:${server.address.port}"
    }

    fun down(upstream: String) {
        down += upstream
    }

    fun up(upstream: String) {
        down -= upstream
    }

    /** The placements [upstream] is working on now. */
    fun inFlight(upstream: String): Int = placements[upstream]?.get() ?: 0

    private fun handle(exchange: HttpExchange) {
        val body = exchange.requestBody.readAllBytes()
        val path = exchange.requestURI.rawPath + (exchange.requestURI.rawQuery?.let { "?$it" } ?: "")
        val placement = exchange.requestMethod == "POST" && exchange.requestHeaders.containsKey(IDEMPOTENCY_KEY_HEADER)
        val start = next.getAndIncrement()
        val order = upstreams.indices.map { upstreams[(start + it) % upstreams.size] }.filter { it !in down }
        for (upstream in order) {
            val counter = placements.computeIfAbsent(upstream) { AtomicInteger() }
            if (placement) counter.incrementAndGet()
            try {
                val response =
                    try {
                        client.send(request(exchange, upstream + path, body), HttpResponse.BodyHandlers.ofInputStream())
                    } catch (e: ConnectException) {
                        // Never reached it: the next replica is asked, and this one is left out until it is back.
                        down(upstream)
                        continue
                    }
                relay(exchange, response)
            } catch (e: Exception) {
                // The replica went away with the request — before or while it answered — and nothing was sent back yet.
                if (e !is UpstreamGone && e !is IOException) throw e
                down(upstream)
                cut += Cut("${exchange.requestMethod} $path", upstream, resendable(exchange))
                runCatching { exchange.sendResponseHeaders(BAD_GATEWAY, -1) }
            } finally {
                if (placement) counter.decrementAndGet()
            }
            return
        }
        exchange.sendResponseHeaders(SERVICE_UNAVAILABLE, -1)
    }

    private fun request(
        exchange: HttpExchange,
        url: String,
        body: ByteArray,
    ): HttpRequest =
        HttpRequest
            .newBuilder(URI(url))
            .apply {
                exchange.requestHeaders.forEach { (name, values) ->
                    if (name.lowercase() !in HOP_BY_HOP) values.forEach { header(name, it) }
                }
            }.method(
                exchange.requestMethod,
                if (body.isEmpty()) {
                    HttpRequest.BodyPublishers.noBody()
                } else {
                    HttpRequest.BodyPublishers.ofByteArray(
                        body,
                    )
                },
            ).build()

    /** The answer as it comes: a stream of events is passed on chunk by chunk, anything else whole. */
    private fun relay(
        exchange: HttpExchange,
        response: HttpResponse<java.io.InputStream>,
    ) {
        response.headers().map().forEach { (name, values) ->
            if (name.lowercase() !in HOP_BY_HOP && !name.startsWith(":")) exchange.responseHeaders[name] = values
        }
        response.body().use { upstream ->
            val streamed =
                response
                    .headers()
                    .firstValue("content-type")
                    .orElse("")
                    .startsWith("text/event-stream")
            if (streamed) {
                exchange.sendResponseHeaders(response.statusCode(), 0)
                val buffer = ByteArray(BUFFER)
                // Until either side ends it: the replica, or the page that stopped listening. Neither is a cut — a live
                // page reconnects by itself — and the answer has begun, so nothing else can be said.
                runCatching {
                    while (true) {
                        val read = upstream.read(buffer)
                        if (read < 0) break
                        exchange.responseBody.write(buffer, 0, read)
                        exchange.responseBody.flush()
                    }
                }
            } else {
                val bytes =
                    try {
                        upstream.readAllBytes()
                    } catch (e: IOException) {
                        throw UpstreamGone(e)
                    }
                // A client that stopped waiting is its own business, not the replica's.
                runCatching {
                    exchange.sendResponseHeaders(
                        response.statusCode(),
                        if (bytes.isEmpty()) -1 else bytes.size.toLong(),
                    )
                    if (bytes.isNotEmpty()) exchange.responseBody.write(bytes)
                }
            }
        }
    }

    /** The replica's answer broke off before it was whole. */
    private class UpstreamGone(
        cause: IOException,
    ) : Exception(cause)

    override fun close() = server.stop(0)

    /** A request cut by [upstream] going away; [resendable] when sending it again cannot do a thing twice. */
    data class Cut(
        val request: String,
        val upstream: String,
        val resendable: Boolean,
    )

    private companion object {
        const val BAD_GATEWAY = 502
        const val SERVICE_UNAVAILABLE = 503
        const val BUFFER = 8192

        /** What the JDK's client sets itself and refuses from the caller, and what is the connection's, not the request's. */
        val HOP_BY_HOP =
            setOf("connection", "content-length", "expect", "host", "upgrade", "transfer-encoding", "keep-alive")

        fun resendable(exchange: HttpExchange): Boolean =
            exchange.requestMethod in setOf("GET", "PUT") || exchange.requestHeaders.containsKey(IDEMPOTENCY_KEY_HEADER)
    }
}
