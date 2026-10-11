package io.github.youndie.haul.feature.cart

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.feature.identity.GUEST_HEADER
import io.github.youndie.haul.shell.assertNoAnswer
import io.github.youndie.haul.shell.told
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.standard.RefreshAction
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The cart's commands as the browser sends them (endpoint-cart), against a server played by a mock
 * engine: each command's method, address and body, the headers sign-in's `send` adds, the `refresh`
 * the server answers, and a refusal.
 */
class CartCommandsTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var answer: Pair<HttpStatusCode, String> = HttpStatusCode.OK to """{"type":"refresh"}"""
    private var unreachable = false

    private val http =
        HttpClient(
            MockEngine { request ->
                requests += request
                if (unreachable) throw IOException("connection refused")
                respond(answer.second, answer.first, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )

    /** As `Identity.send` does for a guest: the request built with the guest's header. */
    private val commands =
        ktorCartCommands(http, "http://haul.test/") { request -> request(mapOf(GUEST_HEADER to "g-1")) }

    private fun HttpRequestData.text(): String? = (body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString()

    private fun sent(command: CartCommand): Pair<KompotAction, HttpRequestData> =
        runBlocking { commands.send(command) to requests.last() }

    /** The URL is the tree's, on the page's origin; the method and the body are endpoint-cart's. */
    @Test
    fun `each command goes where the tree says with its method and body`() {
        val line = "/api/v1/cart/lines/p-stoneware-mug-0"
        val cases =
            listOf(
                CartCommand.ChangeLine(line, LineChange(quantity = 2)) to (HttpMethod.Put to """{"quantity":2}"""),
                CartCommand.ChangeLine(line, LineChange(selected = false)) to
                    (HttpMethod.Put to """{"selected":false}"""),
                CartCommand.RemoveLines("/api/v1/cart/lines", LinesRemoval(listOf("p-stoneware-mug-0"))) to
                    (HttpMethod.Delete to """{"skuIds":["p-stoneware-mug-0"]}"""),
                CartCommand.Acknowledge("$line/acknowledge") to (HttpMethod.Post to null),
                CartCommand.ApplyPromo("/api/v1/cart/promo", PromoEntry("autumn10")) to
                    (HttpMethod.Put to """{"code":"autumn10"}"""),
                CartCommand.RemovePromo("/api/v1/cart/promo") to (HttpMethod.Delete to null),
                CartCommand.Reorder("/api/v1/me/orders/HL-46102/reorder") to (HttpMethod.Post to null),
            )
        cases.forEach { (command, expected) ->
            val (action, request) = sent(command)
            assertEquals(RefreshAction, action, "the server's answer for $command")
            assertEquals("http://haul.test${command.url}", request.url.toString())
            assertEquals(expected.first, request.method, "$command")
            assertEquals(expected.second, request.text(), "$command")
            if (expected.second != null) {
                assertEquals(ContentType.Application.Json, request.body.contentType?.withoutParameters(), "$command")
            }
            assertEquals("g-1", request.headers[GUEST_HEADER], "the guest id sign-in's send adds")
        }
    }

    @Test
    fun `a refusal is the server's code and reason`() {
        answer =
            HttpStatusCode.UnprocessableEntity to
            """{"code":"promo_expired","message":"This code has expired","field":"code"}"""
        val refused =
            assertFailsWith<CartRefused> {
                runBlocking { commands.send(CartCommand.ApplyPromo("/api/v1/cart/promo", PromoEntry("SUMMER5"))) }
            }
        assertEquals(422, refused.status)
        assertEquals(ErrorCode.PromoExpired, refused.code)
        assertEquals("This code has expired", refused.reason)
    }

    /**
     * What the screen follows after a press: the last answer; after a refusal a `refresh` all the same, and
     * the batch stops there, with the server's words in a message (B-92) — but a refused promo code the server
     * keeps with its reason for the next tree (Cart_PromoError) is said under the field alone.
     */
    @Test
    fun `a batch is answered by its last command and a refusal redraws and says why`() {
        val lines =
            listOf(
                "a",
                "b",
                "c",
            ).map { CartCommand.ChangeLine("/api/v1/cart/lines/$it", LineChange(selected = true)) }
        assertEquals(RefreshAction, runBlocking { commands.run(lines) })
        assertEquals(3, requests.size)

        requests.clear()
        answer =
            HttpStatusCode.Conflict to """{"code":"out_of_stock","message":"Only 3 left in stock","field":"quantity"}"""
        assertEquals(told("Only 3 left in stock"), runBlocking { commands.run(lines) })
        assertEquals(1, requests.size, "the batch went on after a refusal")

        val apply = listOf(CartCommand.ApplyPromo("/api/v1/cart/promo", PromoEntry("SUMMER5")))
        answer =
            HttpStatusCode.UnprocessableEntity to
            """{"code":"promo_expired","message":"This code has expired","field":"code"}"""
        assertEquals(RefreshAction, runBlocking { commands.run(apply) }, "the field says a stored refusal")
        answer =
            HttpStatusCode.Conflict to
            """{"code":"promo_already_applied","message":"Only one code per order: remove AUTUMN10 first"}"""
        assertEquals(told("Only one code per order: remove AUTUMN10 first"), runBlocking { commands.run(apply) })
    }

    /**
     * No answer leaves the page and says so (B-92), with «Retry» when every command of the press is a `PUT`
     * or a `DELETE`; a `POST` — «Reorder», which would add the lines twice — gets none.
     */
    @Test
    fun `no answer says so with Retry only for a press that may be sent twice`() {
        unreachable = true
        var again = 0
        val press = { again += 1 }
        val line = "/api/v1/cart/lines/p-stoneware-mug-0"
        listOf(
            CartCommand.ChangeLine(line, LineChange(quantity = 2)),
            CartCommand.RemoveLines("/api/v1/cart/lines", LinesRemoval(listOf("p-stoneware-mug-0"))),
            CartCommand.ApplyPromo("/api/v1/cart/promo", PromoEntry("autumn10")),
            CartCommand.RemovePromo("/api/v1/cart/promo"),
            CartCommand.Heart("/api/v1/me/saved/p-stoneware-mug", save = true),
            CartCommand.Heart("/api/v1/me/saved/p-stoneware-mug", save = false),
        ).forEach { command ->
            assertNoAnswer(runBlocking { commands.run(listOf(command), again = press) }, retry = true)
                ?.press
                ?.invoke()
        }
        assertEquals(6, again)
        listOf(
            CartCommand.Reorder("/api/v1/me/orders/HL-46102/reorder"),
            CartCommand.SaveForLater("$line/save-for-later"),
            CartCommand.Acknowledge("$line/acknowledge"),
        ).forEach { command ->
            assertNoAnswer(runBlocking { commands.run(listOf(command), again = press) }, retry = false)
        }
    }
}
