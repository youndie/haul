package io.github.youndie.haul.feature.checkout

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.FieldError
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.standard.NavigateAction
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * The checkout's commands as the browser sends them (endpoint-checkout, endpoint-identity), against a
 * server played by a mock engine: each command's method, address and body, the bearer token sign-in's
 * `send` adds, placement's key, and a refusal with its fields.
 */
class CheckoutCommandsTest {
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

    /** As `Identity.send` does for a customer: the request built with the bearer token. */
    private val commands =
        ktorCheckoutCommands(http, "http://haul.test/") { request -> request(mapOf(HttpHeaders.Authorization to "Bearer t-1")) }

    private fun HttpRequestData.text(): String? = (body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString()

    private fun sent(command: CheckoutCommand): Pair<KompotAction, HttpRequestData> =
        runBlocking { commands.send(command) to requests.last() }

    /** The URL is the tree's, on the page's origin; the method and the body are endpoint-checkout's. */
    @Test
    fun `each command goes where the tree says with its method and body`() {
        val cases =
            listOf(
                CheckoutCommand.Choose(CHOICE, CheckoutChoice(method = DeliveryMethod.PickupPoint)) to
                    (HttpMethod.Put to """{"method":"pickup_point"}"""),
                CheckoutCommand.Choose(CHOICE, CheckoutChoice(slotId = "2025-10-08T15")) to
                    (HttpMethod.Put to """{"slotId":"2025-10-08T15"}"""),
                CheckoutCommand.SaveAddress(ADDRESSES, AddressEntry("148 Wythe Avenue", "4F", "Brooklyn, NY", "11211")) to
                    (
                        HttpMethod.Post to
                            """{"street":"148 Wythe Avenue","apt":"4F","city":"Brooklyn, NY","zip":"11211"}"""
                    ),
            )
        cases.forEach { (command, expected) ->
            val (action, request) = sent(command)
            assertEquals(RefreshAction, action, "the server's answer for $command")
            assertEquals("http://haul.test${command.url}", request.url.toString())
            assertEquals(expected.first, request.method, "$command")
            assertEquals(expected.second, request.text(), "$command")
            assertEquals(ContentType.Application.Json, request.body.contentType?.withoutParameters(), "$command")
            assertEquals("Bearer t-1", request.headers[HttpHeaders.Authorization], "the token sign-in's send adds")
            assertNull(request.headers[IDEMPOTENCY_KEY_HEADER], "$command carried a placement key")
        }
    }

    /**
     * «Place order» sends the quote the shopper saw under its key, and follows where the server says the
     * order is (B-16's `202` with a `navigate`).
     */
    @Test
    fun `placing sends the quote under its key`() {
        answer = HttpStatusCode.Accepted to """{"type":"navigate","deeplink":"/orders/HL-48302"}"""
        val (action, request) = sent(CheckoutCommand.Place("/api/v1/orders", "q-1", "k-1"))
        assertEquals(NavigateAction("/orders/HL-48302"), action)
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("http://haul.test/api/v1/orders", request.url.toString())
        assertEquals("""{"quote":"q-1"}""", request.text())
        assertEquals("k-1", request.headers[IDEMPOTENCY_KEY_HEADER])
        assertNotEquals(newIdempotencyKey(), newIdempotencyKey(), "two quotes got one key")
    }

    /** `Checkout_Validation`: the refusal names every field at fault. */
    @Test
    fun `a refused address is the server's code and fields`() {
        answer =
            HttpStatusCode.BadRequest to
            """{"code":"validation_failed","message":"Check the address","field":"street",""" +
            """"fields":[{"field":"street","code":"field_required","message":"Enter the street address"}]}"""
        val refused =
            assertFailsWith<CheckoutRefused> {
                runBlocking { commands.send(CheckoutCommand.SaveAddress(ADDRESSES, AddressEntry(city = "Brooklyn, NY"))) }
            }
        assertEquals(400, refused.status)
        assertEquals(ErrorCode.ValidationFailed, refused.code)
        assertEquals(listOf(FieldError("street", ErrorCode.FieldRequired, "Enter the street address")), refused.fields)
    }

    /**
     * What the screen follows: the answer; after a refusal a `refresh` all the same — the server keeps a
     * refused form and clears a window that filled up — and after no answer nothing.
     */
    @Test
    fun `a refusal still redraws and no answer leaves the page`() {
        val choose = CheckoutCommand.Choose(CHOICE, CheckoutChoice(slotId = "2025-10-08T15"))
        assertEquals(RefreshAction, runBlocking { commands.run(choose) })
        answer = HttpStatusCode.Conflict to """{"code":"slot_unavailable","message":"That delivery window just filled up — pick another"}"""
        assertEquals(RefreshAction, runBlocking { commands.run(choose) })
        unreachable = true
        assertNull(runBlocking { commands.run(choose) })
    }

    private companion object {
        const val CHOICE = "/api/v1/me/checkout"
        const val ADDRESSES = "/api/v1/me/addresses"
    }
}
