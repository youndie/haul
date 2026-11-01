package io.github.youndie.haul.e2e

import io.github.youndie.haul.ErrorBody
import io.github.youndie.haul.StorefrontPage
import io.github.youndie.haul.feature.checkout.IDEMPOTENCY_KEY_HEADER
import io.github.youndie.haul.feature.identity.GUEST_HEADER
import io.github.youndie.haul.haulWireJson
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotAction
import io.github.youndie.kompot.standard.NavigateAction
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import kotlin.concurrent.thread

/**
 * The storefront as its client sees it: an origin, the trees it draws, the commands it sends. Every path
 * but the entry comes from somewhere the server put it — a `NavigateAction`'s deeplink, a component's
 * `url` — and every body is the contract's type, encoded with [haulWireJson]; nothing here knows the
 * server's routes.
 *
 * A shopper is a guest ([guest], sent as [GUEST_HEADER]) until they hold a [token]; then the bearer.
 *
 * **A bearer outlives nothing**: shildik's access token lives five minutes. With [renew] set, a request whose
 * bearer is answered `401` gets a new [token] from it and is sent once more — what the storefront's client
 * does with its refresh token (`Identity`). A walk that waits on the fulfilment clock longer than that (twenty
 * minutes at the stand's speed, B-31) needs it; without [renew] the `401` is the answer.
 *
 * [transport] is how a request reaches the server: the JDK's client, unless a test stands between them.
 *
 * **With [resend], a request that may be sent again is** (B-174): one that broke off or was answered `502`, `503` or
 * `504` — a replica that died under it, or a rollout's — is sent once a second up to [RESENDS] more times when sending
 * it twice cannot do a thing twice: a `GET`, a `PUT` (every one here sets a value), and a placement, whose
 * `Idempotency-Key` answers the order the first one placed. What the storefront's client does on the stand; off, a
 * test sees the first answer, whatever it was.
 */
internal class Storefront(
    private val origin: String,
    private val transport: Transport = Transport.DIRECT,
    private val resend: Boolean = false,
) {
    var guest: String? = null
    var token: String? = null

    /** The next bearer, when the one held is refused; `null` — nobody to ask — leaves the `401` as it is. */
    var renew: (() -> String)? = null

    /** How many times [renew] was asked. */
    var renewals: Int = 0
        private set

    /**
     * The tree of the page at [address] — a deeplink as a `NavigateAction` carries it — where the client
     * finds it (`StorefrontPage`: each page's tree at the same path under `/ui`, `/` at `/ui/home`).
     */
    fun page(address: String): Tree {
        val path = address.substringBefore('?')
        checkNotNull(pageOf(address)) { "«$address» is not a storefront address" }
        val screen = if (path == "/") "/ui/home" + address.removePrefix("/") else "/ui$address"
        val response = send("GET", screen)
        check(response.status == 200) { "the tree at $screen answered ${response.status}: ${response.body.take(500)}" }
        return Tree(screen, haulWireJson.parseToJsonElement(response.body))
    }

    /**
     * Listens on [topic] — the channel a live page's tree names (B-29) — where the client listens: the stream
     * at `/ui/updates`, `?topic=` the channel. Every event's data is kept, in order, until [LiveStream.close].
     */
    fun listen(topic: String): LiveStream {
        val request =
            HttpRequest
                .newBuilder(URI("$origin/ui/updates?topic=" + URLEncoder.encode(topic, Charsets.UTF_8)))
                .timeout(Duration.ofSeconds(30))
                .apply { token?.let { header("Authorization", "Bearer $it") } }
                .GET()
                .build()
        val response = Transport.HTTP.send(request, HttpResponse.BodyHandlers.ofLines())
        check(response.statusCode() == 200) { "the stream of $topic answered ${response.statusCode()}" }
        return LiveStream(topic, response.body())
    }

    /** A command: [method] to [url] — the server's string, from a tree — with [body] already encoded. */
    fun send(
        method: String,
        url: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): Answer {
        fun request() =
            HttpRequest
                .newBuilder(URI(origin + url))
                .timeout(Duration.ofSeconds(30))
                .apply {
                    guest?.let { header(GUEST_HEADER, it) }
                    token?.let { header("Authorization", "Bearer $it") }
                    headers.forEach { (name, value) -> header(name, value) }
                    if (body != null) header("Content-Type", "application/json")
                }.method(
                    method,
                    body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody(),
                ).build()
        val again = resend && (method in RESENDABLE || IDEMPOTENCY_KEY_HEADER in headers)
        var response = deliver(again, ::request)
        val renew = renew
        if (response.statusCode() == UNAUTHORIZED && token != null && renew != null) {
            token = renew()
            renewals += 1
            response = deliver(again, ::request)
        }
        return Answer("$method $url", response.statusCode(), response.body())
    }

    /** How many times a request that may be sent again was: what a walk across a replica's death shows. */
    var resent: Int = 0
        private set

    private fun deliver(
        again: Boolean,
        request: () -> HttpRequest,
    ): HttpResponse<String> {
        var left = if (again) RESENDS else 0
        while (true) {
            val response =
                try {
                    transport.send(request())
                } catch (e: IOException) {
                    if (left == 0) throw e
                    null
                }
            if (response != null && (left == 0 || response.statusCode() !in GONE)) return response
            left -= 1
            resent += 1
            Thread.sleep(RESEND_AFTER_MILLIS)
        }
    }

    companion object {
        private const val UNAUTHORIZED = 401

        /** How many more times a request that may be sent again is. */
        const val RESENDS = 5
        private const val RESEND_AFTER_MILLIS = 1_000L
        private val GONE = setOf(502, 503, 504)
        private val RESENDABLE = setOf("GET", "PUT")
    }
}

/** How the storefront's requests reach the server. */
internal fun interface Transport {
    fun send(request: HttpRequest): HttpResponse<String>

    companion object {
        val HTTP: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

        /** Straight to the server, with the JDK's client. */
        val DIRECT = Transport { HTTP.send(it, HttpResponse.BodyHandlers.ofString()) }
    }
}

/**
 * A live page's stream as it arrives: a thread reads its lines, and each event's data waits in [frames] for
 * [await]. A stream that ended or broke stops adding; [close] stops reading.
 */
internal class LiveStream(
    private val topic: String,
    lines: Stream<String>,
) : AutoCloseable {
    private val frames = LinkedBlockingQueue<String>()
    private val stream = lines

    init {
        thread(isDaemon = true, name = "live $topic") {
            var data: String? = null
            stream.forEach { line ->
                when {
                    line.startsWith("data:") -> data = line.removePrefix("data:").trim()
                    line.isEmpty() -> data?.let(frames::put).also { data = null }
                }
            }
        }
    }

    /** The first frame from now holding a [T] for which [done] holds, within [within]; frames before it are spent. */
    fun <T : KompotComponent> await(
        serializer: KSerializer<T>,
        within: kotlin.time.Duration,
        done: (T) -> Boolean,
    ): T {
        val deadline = System.nanoTime() + within.inWholeNanoseconds
        val seen = mutableListOf<T>()
        while (true) {
            val left = deadline - System.nanoTime()
            val frame =
                frames.poll(left.coerceAtLeast(0), TimeUnit.NANOSECONDS)
                    ?: error("no frame of $topic for which it holds within $within; it carried $seen")
            val component = Tree(topic, haulWireJson.parseToJsonElement(frame)).one(serializer)
            if (done(component)) return component
            seen += component
        }
    }

    override fun close() = stream.close()
}

/** What a request was answered with. */
internal data class Answer(
    val request: String,
    val status: Int,
    val body: String,
) {
    /** The kompot action a command answers, after checking it answered [expected]. */
    fun action(expected: Int = 200): KompotAction {
        check(status == expected) { "$request answered $status, not $expected: ${body.take(500)}" }
        return haulWireJson.decodeKompotAction(body)
    }

    /** The refusal, after checking it is one with [expected]. */
    fun error(expected: Int): ErrorBody {
        check(status == expected) { "$request answered $status, not $expected: ${body.take(500)}" }
        return haulWireJson.decodeFromString(ErrorBody.serializer(), body)
    }
}

/**
 * A screen's tree as it came over the wire, searched by the contract's own types: every component of a
 * type wherever it sits — in kompot's containers or inside a Haul component that holds others — is
 * found by its serial name and decoded with the contract's serializer. Walking the JSON rather than
 * a hand-kept list of containers means a new container cannot hide a component from the test.
 */
internal class Tree(
    val screen: String,
    private val root: JsonElement,
) {
    /** The channel the screen names for its updates (kompot's `realtimeTopic`, B-29); `null` for a page with none. */
    val realtimeTopic: String?
        get() = ((root as? JsonObject)?.get("realtimeTopic") as? JsonPrimitive)?.takeIf { it.isString }?.content

    fun <T : KompotComponent> all(serializer: KSerializer<T>): List<T> {
        val name = serializer.descriptor.serialName
        val found = mutableListOf<JsonObject>()

        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    if ((element["type"] as? JsonPrimitive)?.content == name) found += element
                    element.values.forEach(::walk)
                }

                is JsonArray -> {
                    element.forEach(::walk)
                }

                else -> {}
            }
        }
        walk(root)
        return found.map { haulWireJson.decodeFromJsonElement(serializer, it) }
    }

    /** The one component of a type the screen has; a screen with none or several is a failure that names it. */
    fun <T : KompotComponent> one(serializer: KSerializer<T>): T =
        all(serializer).let {
            check(it.size == 1) { "$screen has ${it.size} ${serializer.descriptor.serialName}, not one" }
            it.single()
        }
}

/** Which page of the storefront [address] is — a deeplink, its query included — or `null` for none. */
internal fun pageOf(address: String): StorefrontPage? = StorefrontPage.of(address.substringBefore('?'))

/** Where a `NavigateAction` goes; anything else where one was expected is a failure. */
internal fun KompotAction?.deeplink(what: String): String =
    (this as? NavigateAction)?.deeplink ?: error("$what is not a navigate action: $this")

/**
 * Cents from an amount as the trees write it — «$349», «$1,204.50», «−$140.00» — so the money on one
 * screen can be held against the money on the next.
 */
internal fun cents(amount: String): Int {
    val match = checkNotNull(MONEY.matchEntire(amount.trim())) { "«$amount» is not an amount" }
    val (sign, dollars, fraction) = match.destructured
    val value = dollars.replace(",", "").toInt() * 100 + (fraction.ifEmpty { "0" }.padEnd(2, '0').toInt())
    return if (sign.isEmpty()) value else -value
}

private val MONEY = Regex("""([−-]?)\$([\d,]+)(?:\.(\d{1,2}))?""")
