package io.github.youndie.haul.feature.returns

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.fulfilment.domain.OrderProgress
import io.github.youndie.haul.feature.order.OrderPaths
import io.github.youndie.haul.feature.returns.domain.ReturnStatus
import io.github.youndie.haul.feature.reviews.CLOSE_AND_REFRESH
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.testing.FulfilmentWorld
import io.github.youndie.haul.testing.Ledger
import io.github.youndie.haul.testing.ShildikHarness
import io.github.youndie.haul.testing.TestClock
import io.github.youndie.haul.testing.assertError
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.ui.OrderBody
import io.github.youndie.haul.ui.OrderFactKind
import io.github.youndie.haul.ui.ReturnForm
import io.github.youndie.kompot.decodeKompotAction
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.standard.PresentAction
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Returns over HTTP (feature-orders, endpoint-orders `POST /api/v1/me/orders/{id}/returns`, B-21), against
 * shildik and a seeded PostgreSQL of each test's own. Maya's order — Sony's headphones $349.00, Brooklyn Home
 * Co.'s duvet cover $139.00 and mugs $24.00, $512.00 and 1,024 points — is placed and moved by the production
 * graph on a clock the test holds: Sony is delivered 48 hours in (Oct 9, by the store's days), Brooklyn Home
 * Co. 72 hours in (Oct 10). The route is asked on that same clock, as the simulator's stamps are compared
 * with it; between requests the world's returns simulator moves the return along.
 */
class ReturnRoutesTest {
    private val maya by lazy {
        ShildikHarness.accessToken(ShildikHarness.person("Maya Kowalski", SampleCustomers.MAYA))
    }
    private val sam by lazy { ShildikHarness.accessToken(ShildikHarness.person("Sam Ortiz", SampleCustomers.SAM)) }
    private val clock = TestClock()

    /** Maya's order placed in a database of its own and moved [moved] along — both shipments delivered by default. */
    private fun delivered(
        moved: Duration = 72.hours,
        choice: CheckoutChoice = CheckoutChoice(slotId = "2025-10-08T15"),
        block: (orderId: String, database: DataSource) -> Unit,
    ) = seededFreshDatabase().use { database ->
        val orderId =
            world(database) { world ->
                world.place(choice).also {
                    world.advance(Duration.ZERO)
                    world.advance(moved)
                }
            }
        block(orderId, database)
    }

    private fun <T> world(
        database: DataSource,
        block: (FulfilmentWorld) -> T,
    ): T = FulfilmentWorld(database, clock).use(block)

    /** [block] over HTTP with the world's clock at [at]: the clock a return's 30 days are counted on. */
    private fun http(
        database: DataSource,
        at: Duration,
        block: suspend HttpClient.() -> Unit,
    ) {
        clock.at(at)
        haulTest(database, signIn = ShildikHarness.signIn, sagaClock = clock, block = block)
    }

    private suspend fun HttpClient.requestReturn(
        token: String?,
        orderId: String,
        body: String,
    ): HttpResponse =
        post(ReturnPaths.returns(orderId)) {
            token?.let(::bearerAuth)
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun HttpClient.requestReturn(
        token: String?,
        orderId: String,
        entry: ReturnEntry,
    ): HttpResponse = requestReturn(token, orderId, haulWireJson.encodeToString(ReturnEntry.serializer(), entry))

    private suspend fun HttpClient.page(orderId: String): OrderBody {
        val response = get("/ui" + OrderPaths.page(orderId)) { bearerAuth(maya) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return haulWireJson.decodeKompotComponent(response.bodyAsText()).only<OrderBody>()
    }

    private suspend fun HttpResponse.assertAccepted() {
        val body = bodyAsText()
        assertEquals(HttpStatusCode.Created, status, body)
        assertEquals(CLOSE_AND_REFRESH, haulWireJson.decodeKompotAction(body))
    }

    /**
     * Scenario «Late return» (feature-orders): Sony's headphones were delivered on Oct 9, and Maya asks to
     * return them 31 days later — the server answers `422 return_window_closed`, says the day the window
     * closed, and nothing is written.
     */
    @Test
    fun `late return`() =
        delivered { orderId, database ->
            http(database, 48.hours + 31.days) {
                val refused =
                    requestReturn(maya, orderId, ReturnEntry(listOf(0), "doesnt_fit"))
                        .assertError(HttpStatusCode.UnprocessableEntity, ErrorCode.ReturnWindowClosed)
                assertEquals("Returns for this order closed on Nov 8", refused.message)
            }
            assertEquals(0, Ledger(database).returns(), "a refused return was written")
        }

    /**
     * The 30 days are the store's days, the last one whole, and each line's run from its own shipment: on
     * Nov 9 Sony's window (Oct 9 + 30) is shut while Brooklyn Home Co.'s, delivered a day later, still takes
     * its lines.
     */
    @Test
    fun `each line is returnable to the last day of its own window`() =
        delivered { orderId, database ->
            http(database, 48.hours + 31.days) {
                requestReturn(maya, orderId, ReturnEntry(listOf(0, 1), "doesnt_fit"))
                    .assertError(HttpStatusCode.UnprocessableEntity, ErrorCode.ReturnWindowClosed)
                requestReturn(maya, orderId, ReturnEntry(listOf(1, 2), "changed_mind")).assertAccepted()
            }
            assertEquals(Triple(ReturnStatus.REQUESTED, 16_300, 326), Ledger(database).storedReturn(orderId))
        }

    /**
     * Maya returns Sony's headphones: the order reads «Return requested» with the headphones in a card of
     * their own and the rest where it was; nothing is refunded when the courier collects them, and $349.00 —
     * no more — goes back to the card a day later, when the seller has them. The page then reads «Return
     * refunded», what was paid net of it, and the 698 points the line earned taken back (B-23 holds no
     * ledger yet: the number is the return's). The order's progress is `returned` (what the history reads).
     */
    @Test
    fun `a returned line is refunded when the seller has it back`() =
        delivered { orderId, database ->
            val ledger = Ledger(database)
            http(database, 72.hours) {
                requestReturn(maya, orderId, ReturnEntry(listOf(0), "doesnt_fit")).assertAccepted()

                val requested = page(orderId)
                assertEquals("Return requested", requested.title)
                assertEquals(
                    "A courier picks it up for free. $349.00 goes back to your card ···· 4821 once the seller has it.",
                    requested.lead,
                )
                assertEquals(listOf("Requested", "Picked up", "Refunded"), requested.steps?.labels)
                assertEquals(0, requested.steps?.current)
                assertEquals(listOf("Brooklyn Home Co.", "Returned items"), requested.shipments.map { it.seller })
                assertEquals(listOf(2, 1), requested.shipments.map { it.items.size })
                assertEquals(listOf(false, true), requested.shipments.map { it.returned })
                assertNull(requested.summary.returnAction, "a second return is offered")
                assertNull(requested.summary.reorderUrl)
            }
            assertEquals(Triple(ReturnStatus.REQUESTED, 34_900, 698), ledger.storedReturn(orderId))

            world(database) { it.advanceReturns(96.hours) }
            assertEquals(ReturnStatus.PICKED_UP, ledger.storedReturn(orderId)?.first)
            assertEquals(emptyMap(), ledger.refunds(orderId), "refunded when it is collected, not when it is back")

            world(database) { world ->
                world.advanceReturns(120.hours)
                assertEquals(OrderProgress.Returned, world.track(orderId)?.progress)
            }
            assertEquals(ReturnStatus.REFUNDED, ledger.storedReturn(orderId)?.first)
            assertEquals(mapOf("refund:$orderId" to 34_900), ledger.refunds(orderId))

            http(database, 120.hours) {
                val refunded = page(orderId)
                assertEquals("Return refunded", refunded.title)
                assertEquals("$349.00 is back on your card ···· 4821.", refunded.lead)
                assertEquals(true, refunded.steps?.arrived)
                assertEquals("Refunded Oct 12", refunded.shipments.last().eta)
                val summary = refunded.summary
                assertEquals("Refunded" to "−$349.00", summary.rows.last().let { it.label to it.value })
                assertEquals("Paid" to "$163.00", summary.totalLabel to summary.total)
                assertEquals(
                    listOf(
                        OrderFactKind.Card to "$349.00 refunded Oct 12",
                        OrderFactKind.Points to "Points earned on the returned lines were reversed",
                    ),
                    summary.facts.map { it.kind to it.detail },
                )
                assertEquals("−698 points", summary.facts.last().title)
            }
        }

    /**
     * «Return items» on a delivered order is the dialog: the lines with what each gives back, the reasons, the
     * card the refund goes to, and where to send it — the dialog the client draws (`Order_ReturnDialog`).
     */
    @Test
    fun `a delivered order presents the return dialog`() =
        delivered { orderId, database ->
            http(database, 72.hours) {
                val summary = page(orderId).summary
                assertEquals("Return items", summary.returnLabel)
                val form = (summary.returnAction as? PresentAction)?.content as? ReturnForm
                assertNotNull(form, "«Return items» is not the dialog: ${summary.returnAction}")
                assertEquals("#$orderId · delivered Oct 10 · returns until Nov 9", form.meta)
                assertEquals(
                    listOf(0 to 34_900, 1 to 13_900, 2 to 2_400),
                    form.lines.map {
                        it.position to
                            it.refundCents
                    },
                )
                assertEquals("Refund {amount} to card ···· 4821", form.refund)
                assertEquals(ReturnPaths.returns(orderId), form.url)
                assertEquals("doesnt_fit", form.reasons.first().id)
            }
        }

    /**
     * What a return is refused for besides its window: an order still on its way (`422 not_delivered`),
     * another customer's (`404 order_not_found`, as the order's page answers it — Sam learns nothing and
     * writes nothing), a second return of the same order (`409 already_returned`), and no token (`401`).
     */
    @Test
    fun `a return is refused for what has not arrived and what is not yours`() =
        delivered(moved = 30.hours) { orderId, database ->
            http(database, 30.hours) {
                requestReturn(maya, orderId, ReturnEntry(listOf(0), "doesnt_fit"))
                    .assertError(HttpStatusCode.UnprocessableEntity, ErrorCode.NotDelivered)
            }
            world(database) { it.advance(72.hours) }
            http(database, 72.hours) {
                requestReturn(sam, orderId, ReturnEntry(listOf(0), "doesnt_fit"))
                    .assertError(HttpStatusCode.NotFound, ErrorCode.OrderNotFound)
                requestReturn(null, orderId, ReturnEntry(listOf(0), "doesnt_fit"))
                    .assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
                assertEquals(0, Ledger(database).returns(), "Sam returned Maya's order")

                requestReturn(maya, orderId, ReturnEntry(listOf(0), "doesnt_fit")).assertAccepted()
                requestReturn(maya, orderId, ReturnEntry(listOf(1), "damaged"))
                    .assertError(HttpStatusCode.Conflict, ErrorCode.AlreadyReturned)
            }
            assertEquals(Triple(ReturnStatus.REQUESTED, 34_900, 698), Ledger(database).storedReturn(orderId))
        }

    /**
     * A form that cannot be sent is `400 validation_failed` with every field at fault — nothing ticked and no
     * reason, a reason the dialog does not offer, a line the order does not have, a body that is not JSON.
     */
    @Test
    fun `a return form is refused with every field at fault`() =
        delivered { orderId, database ->
            http(database, 72.hours) {
                val empty =
                    requestReturn(maya, orderId, ReturnEntry())
                        .assertError(HttpStatusCode.BadRequest, ErrorCode.ValidationFailed)
                assertEquals(
                    listOf("lines" to ErrorCode.FieldRequired, "reason" to ErrorCode.FieldRequired),
                    empty.fields.map { it.field to it.code },
                )
                val reason =
                    requestReturn(maya, orderId, ReturnEntry(listOf(0), "too_expensive"))
                        .assertError(HttpStatusCode.BadRequest, ErrorCode.ValidationFailed)
                assertEquals(listOf("reason" to ErrorCode.FieldInvalid), reason.fields.map { it.field to it.code })
                val line =
                    requestReturn(maya, orderId, ReturnEntry(listOf(7), "damaged"))
                        .assertError(HttpStatusCode.BadRequest, ErrorCode.ValidationFailed)
                assertEquals("lines", line.field)
                requestReturn(maya, orderId, "{ not json")
                    .assertError(HttpStatusCode.BadRequest, ErrorCode.ValidationFailed)
            }
            assertEquals(0, Ledger(database).returns())
        }
}
