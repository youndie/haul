package io.github.youndie.haul.feature.live

import io.github.youndie.haul.feature.checkout.DeliveryMethod
import io.github.youndie.haul.feature.live.domain.FeedOrder
import io.github.youndie.haul.feature.live.domain.FeedReturn
import io.github.youndie.haul.feature.live.domain.FeedShipment
import io.github.youndie.haul.feature.live.screen.LiveScreen
import io.github.youndie.haul.feature.order.domain.CancelReason
import io.github.youndie.haul.feature.order.domain.OrderStatus
import io.github.youndie.haul.feature.order.domain.ShipmentStatus
import io.github.youndie.haul.feature.returns.domain.ReturnStatus
import io.github.youndie.haul.haulWireJson
import io.github.youndie.kompot.encodeKompotComponent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The client draws the page's goldens (`Live_Content`, `Live_Content_Phone`) from a wire body
 * (`composeApp/src/desktopTest/resources/bodies/live_orders.json`), and this holds its `haul_live_orders` node to what
 * [LiveScreen] builds for [SAMPLE] — five orders in the states the stand's walks leave them in, at the canvas's
 * evening in New York. A mismatch writes what the server built to `build/live-fixtures/`.
 */
class LiveFixturesTest {
    private val bodies = File(System.getProperty("haul.clientBodies") ?: error("haul.clientBodies is not set"))
    private val pretty = Json(haulWireJson) { prettyPrint = true }

    @Test
    fun `the client's live orders body is the tree the server builds`() {
        val built = Json.parseToJsonElement(haulWireJson.encodeKompotComponent(LiveScreen { ZONE }.body(SAMPLE)))
        val drawn =
            Json
                .parseToJsonElement(File(bodies, "live_orders.json").readText())
                .jsonObject
                .getValue("children")
                .jsonArray
                .single { it.jsonObject["type"] == JsonPrimitive("haul_live_orders") }
        if (built != drawn) {
            File("build/live-fixtures").apply { mkdirs() }.resolve("live_orders_body.json").writeText(
                pretty.encodeToString(
                    kotlinx.serialization.json.JsonElement
                        .serializer(),
                    built,
                ),
            )
        }
        assertEquals(built, drawn)
    }

    internal companion object {
        val ZONE: ZoneId = ZoneId.of("America/New_York")

        private fun at(time: String): Instant = Instant.parse(time)

        /**
         * Newest first, as the feed reads them: one just placed and seen by its sellers, one declined by the test card
         * after it held a window and points, one on its way to a pickup point, one delivered and paid on delivery, one
         * returned and refunded. The sellers are the test catalogue's invented ones.
         */
        val SAMPLE: List<FeedOrder> =
            listOf(
                FeedOrder(
                    id = "HL-48311",
                    status = OrderStatus.Placed,
                    cancelReason = null,
                    method = DeliveryMethod.Courier,
                    payment = "card-4821",
                    heldWindow = true,
                    redeemedPoints = false,
                    totalCents = 21_450,
                    items = 3,
                    placedAt = at("2025-10-07T23:47:23Z"),
                    shipments =
                        listOf(
                            FeedShipment(
                                "HL-48311-1",
                                "Northline Knitwear",
                                ShipmentStatus.PLACED,
                                mapOf(ShipmentStatus.PLACED to at("2025-10-07T23:47:30Z")),
                            ),
                            FeedShipment(
                                "HL-48311-2",
                                "Homeworks",
                                ShipmentStatus.PLACED,
                                mapOf(ShipmentStatus.PLACED to at("2025-10-07T23:47:30Z")),
                            ),
                        ),
                ),
                FeedOrder(
                    id = "HL-48310",
                    status = OrderStatus.Cancelled,
                    cancelReason = CancelReason.PAYMENT_DECLINED,
                    method = DeliveryMethod.Courier,
                    payment = "card-0002",
                    heldWindow = true,
                    redeemedPoints = true,
                    totalCents = 8_900,
                    items = 1,
                    placedAt = at("2025-10-07T23:41:02Z"),
                    shipments =
                        listOf(FeedShipment("HL-48310-1", "FlowFit Studio", ShipmentStatus.CANCELLED, emptyMap())),
                ),
                FeedOrder(
                    id = "HL-48309",
                    status = OrderStatus.Placed,
                    cancelReason = null,
                    method = DeliveryMethod.PickupPoint,
                    payment = "card-4821",
                    heldWindow = false,
                    redeemedPoints = false,
                    totalCents = 6_400,
                    items = 1,
                    placedAt = at("2025-10-07T15:12:40Z"),
                    shipments =
                        listOf(
                            FeedShipment(
                                "HL-48309-1",
                                "FlowFit Studio",
                                ShipmentStatus.IN_TRANSIT,
                                mapOf(
                                    ShipmentStatus.PLACED to at("2025-10-07T15:12:45Z"),
                                    ShipmentStatus.PACKED to at("2025-10-07T19:12:45Z"),
                                    ShipmentStatus.IN_TRANSIT to at("2025-10-07T23:12:45Z"),
                                ),
                                capturedCents = 6_400,
                                capturedAt = at("2025-10-07T23:12:45Z"),
                            ),
                        ),
                ),
                FeedOrder(
                    id = "HL-48308",
                    status = OrderStatus.Placed,
                    cancelReason = null,
                    method = DeliveryMethod.Courier,
                    payment = "pay_on_delivery",
                    heldWindow = true,
                    redeemedPoints = false,
                    totalCents = 15_725,
                    items = 2,
                    placedAt = at("2025-10-06T14:03:11Z"),
                    shipments =
                        listOf(
                            FeedShipment(
                                "HL-48308-1",
                                "Homeworks",
                                ShipmentStatus.DELIVERED,
                                mapOf(
                                    ShipmentStatus.PLACED to at("2025-10-06T14:03:20Z"),
                                    ShipmentStatus.PACKED to at("2025-10-06T18:03:20Z"),
                                    ShipmentStatus.IN_TRANSIT to at("2025-10-07T14:03:20Z"),
                                    ShipmentStatus.DELIVERED to at("2025-10-07T21:30:00Z"),
                                ),
                            ),
                        ),
                ),
                FeedOrder(
                    id = "HL-48307",
                    status = OrderStatus.Placed,
                    cancelReason = null,
                    method = DeliveryMethod.Courier,
                    payment = "card-4821",
                    heldWindow = true,
                    redeemedPoints = false,
                    totalCents = 4_200,
                    items = 1,
                    placedAt = at("2025-10-02T16:20:05Z"),
                    shipments =
                        listOf(
                            FeedShipment(
                                "HL-48307-1",
                                "Clear Skin Lab",
                                ShipmentStatus.DELIVERED,
                                mapOf(
                                    ShipmentStatus.PLACED to at("2025-10-02T16:20:10Z"),
                                    ShipmentStatus.PACKED to at("2025-10-02T20:20:10Z"),
                                    ShipmentStatus.IN_TRANSIT to at("2025-10-03T16:20:10Z"),
                                    ShipmentStatus.DELIVERED to at("2025-10-04T19:05:00Z"),
                                ),
                                capturedCents = 4_200,
                                capturedAt = at("2025-10-03T16:20:10Z"),
                            ),
                        ),
                    returned =
                        FeedReturn(
                            status = ReturnStatus.REFUNDED,
                            refundCents = 4_200,
                            requestedAt = at("2025-10-05T13:00:00Z"),
                            pickedUpAt = at("2025-10-06T13:00:00Z"),
                            refundedAt = at("2025-10-07T13:00:00Z"),
                        ),
                ),
            )
    }
}
