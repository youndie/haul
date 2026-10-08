package io.github.youndie.haul.e2e

import io.github.youndie.haul.ErrorBody
import io.github.youndie.haul.StorefrontPage
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
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * The storefront as its client sees it: an origin, the trees it draws, the commands it sends. Every path
 * but the entry comes from somewhere the server put it — a `NavigateAction`'s deeplink, a component's
 * `url` — and every body is the contract's type, encoded with [haulWireJson]; nothing here knows the
 * server's routes.
 *
 * A shopper is a guest ([guest], sent as [GUEST_HEADER]) until they hold a [token]; then the bearer.
 */
internal class Storefront(
    private val origin: String,
) {
    var guest: String? = null
    var token: String? = null

    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

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

    /** A command: [method] to [url] — the server's string, from a tree — with [body] already encoded. */
    fun send(
        method: String,
        url: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): Answer {
        val request =
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
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        return Answer("$method $url", response.statusCode(), response.body())
    }
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
