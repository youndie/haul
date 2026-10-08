package io.github.youndie.haul.feature.order

import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.checkout.DeliveryMethod
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentPace
import io.github.youndie.haul.feature.fulfilment.domain.OrderProgress
import io.github.youndie.haul.feature.fulfilment.domain.ShipmentShares
import io.github.youndie.haul.feature.fulfilment.domain.TrackedOrder
import io.github.youndie.haul.feature.fulfilment.domain.TrackedShipment
import io.github.youndie.haul.feature.order.domain.CancelReason
import io.github.youndie.haul.feature.order.domain.NewOrder
import io.github.youndie.haul.feature.order.domain.Order
import io.github.youndie.haul.feature.order.domain.OrderLine
import io.github.youndie.haul.feature.order.domain.OrderStatus
import io.github.youndie.haul.feature.order.domain.Shipment
import io.github.youndie.haul.feature.order.domain.ShipmentStatus
import io.github.youndie.haul.feature.order.screen.Bought
import io.github.youndie.haul.feature.order.screen.OrderScreen
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.SampleCheckout
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.testing.FulfilmentWorld
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.encodeKompotComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.toJavaDuration

/**
 * The client draws the Order screen's goldens from wire bodies (`composeApp/src/desktopTest/resources/
 * bodies/order_*.json`), and this holds each one equal to the tree the server builds for the artboard's
 * order — the same reason as the cart's and the checkout's (`CartFixturesTest`): a body written by hand
 * drifts from the wire without a sound. A mismatch writes what the server built to `build/order-fixtures/`.
 *
 * `Order_Placed` is Maya's cart placed by courier for Wed 8, 15:00–18:00 — #HL-48302, the first order a
 * fresh store gives — through placement itself, at the canvas's «now». The other orders are research §6's
 * history, which no seed holds: #HL-48211 in transit, #HL-47960 waiting at 214 Bedford Ave, #HL-46102
 * delivered, #HL-48303 declined. Each is written here as the order it is — its lines, its shipments and
 * where they are — and drawn by the same builder the route uses ([OrderScreen.page]), over the seeded
 * catalog, Maya's address and the points. Three of them bought products the seed does not sell (the yoga
 * mat, the sweater, the serum), so their tiles, options and sellers are given with the order.
 *
 * Every page is under the header the artboards draw: Maya, three in the cart.
 */
class OrderFixturesTest {
    private val bodies = File(System.getProperty("haul.clientBodies") ?: error("haul.clientBodies is not set"))
    private val pretty = Json(haulWireJson) { prettyPrint = true }
    private val canvasViewer = Viewer(firstName = "Maya", cartCount = 3, customerId = SampleCustomers.MAYA)

    @Test
    fun `the client's order bodies are the trees the server builds`() =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                runBlocking {
                    val screen = world.koin.get<OrderScreen>()
                    val mismatches = mutableListOf<String>()

                    fun check(
                        name: String,
                        tree: KompotComponent,
                    ) {
                        val built = Json.parseToJsonElement(haulWireJson.encodeKompotComponent(tree))
                        val drawn =
                            File(bodies, name).takeIf { it.exists() }?.let { Json.parseToJsonElement(it.readText()) }
                        if (drawn != built) {
                            File("build/order-fixtures").apply { mkdirs() }.resolve(name).writeText(
                                pretty.encodeToString(JsonElement.serializer(), built) + "\n",
                            )
                            mismatches += name
                        }
                    }

                    suspend fun page(
                        tracked: TrackedOrder,
                        products: Map<String, Bought> = emptyMap(),
                        sellers: Map<String, String> = emptyMap(),
                    ): KompotComponent {
                        val view = screen.view(SampleCustomers.MAYA, tracked, "Maya")
                        return screen.page(
                            view.copy(products = view.products + products, sellers = view.sellers + sellers),
                            canvasViewer,
                        )
                    }

                    val placedId = world.place(CheckoutChoice(slotId = WEDNESDAY_3PM))
                    check(PLACED, checkNotNull(screen.build(SampleCustomers.MAYA, placedId, canvasViewer)))
                    val mayas = checkNotNull(world.order(placedId)).placed

                    check(IN_TRANSIT, page(inTransit(mayas)))
                    check(READY_FOR_PICKUP, page(readyForPickup(), YOGA_MAT, mapOf(FLOWFIT to "FlowFit Studio")))
                    check(
                        DELIVERED,
                        page(
                            delivered(mayas),
                            SWEATER + SERUM,
                            mapOf(
                                NORTHLINE to "Northline Knitwear",
                                CLEAR_SKIN to "Clear Skin Lab",
                            ),
                        ),
                    )
                    check(CANCELLED, page(cancelled(mayas)))

                    assertEquals(
                        emptyList(),
                        mismatches,
                        "bodies that are not the server's trees (see build/order-fixtures)",
                    )
                }
            }
        }

    /** #HL-48211: Maya's three things again, placed on Sunday the 5th for the same window, both shipments on the road. */
    private fun inTransit(mayas: NewOrder): TrackedOrder {
        val order =
            Order(
                mayas.copy(
                    id = "HL-48211",
                    sagaId = "saga-48211",
                    placedAt = OffsetDateTime.parse("2025-10-05T11:20:00-04:00"),
                ),
                OrderStatus.Placed,
                cancelReason = null,
                shipments =
                    listOf(
                        Shipment("HL-48211-1", mayas.lines[0].sellerId, ShipmentStatus.IN_TRANSIT),
                        Shipment("HL-48211-2", mayas.lines[1].sellerId, ShipmentStatus.IN_TRANSIT),
                    ),
            )
        return tracked(order, captured = true)
    }

    /** #HL-47960: FlowFit Studio's yoga mat to 214 Bedford Ave, ready since Sunday the 5th with code 4821. */
    private fun readyForPickup(): TrackedOrder {
        val ready = Instant.parse("2025-10-05T16:00:00Z")
        val order =
            Order(
                NewOrder(
                    id = "HL-47960",
                    sagaId = "saga-47960",
                    customerId = SampleCustomers.MAYA,
                    method = DeliveryMethod.PickupPoint,
                    addressId = null,
                    pointId = SampleCheckout.BEDFORD,
                    slotId = null,
                    payment = "card-4821",
                    promoCode = null,
                    itemsCents = 5_800,
                    discountCents = 0,
                    deliveryCents = 0,
                    totalCents = 5_800,
                    points = 116,
                    placedAt = OffsetDateTime.parse("2025-10-03T09:05:00-04:00"),
                    lines = listOf(OrderLine(YOGA_MAT_SKU, FLOWFIT, "Natural Rubber Yoga Mat, 6 mm", 1, 5_800, 5_800)),
                ),
                OrderStatus.Placed,
                cancelReason = null,
                shipments =
                    listOf(
                        Shipment("HL-47960-1", FLOWFIT, ShipmentStatus.READY_FOR_PICKUP, pickupCode = "4821"),
                    ),
            )
        return tracked(order, captured = true) {
            it.copy(
                history = mapOf(ShipmentStatus.READY_FOR_PICKUP to ready),
                pickupCode = "4821",
                heldUntil = ready.plus(FulfilmentPace.HELD_FOR.toJavaDuration()),
            )
        }
    }

    /** #HL-46102: Northline Knitwear's sweater and Clear Skin Lab's serum, by courier, both delivered on Friday the 26th. */
    private fun delivered(mayas: NewOrder): TrackedOrder {
        val delivered = Instant.parse("2025-09-26T18:30:00Z")
        val order =
            Order(
                mayas.copy(
                    id = "HL-46102",
                    sagaId = "saga-46102",
                    slotId = null,
                    promoCode = null,
                    itemsCents = 10_300,
                    discountCents = 0,
                    deliveryCents = 0,
                    totalCents = 10_300,
                    points = 206,
                    placedAt = OffsetDateTime.parse("2025-09-24T20:10:00-04:00"),
                    lines =
                        listOf(
                            OrderLine(SWEATER_SKU, NORTHLINE, "Merino Wool Crewneck Sweater, Unisex", 1, 8_000, 8_000),
                            OrderLine(SERUM_SKU, CLEAR_SKIN, "Vitamin C Brightening Serum, 30 ml", 1, 2_300, 2_300),
                        ),
                ),
                OrderStatus.Placed,
                cancelReason = null,
                shipments =
                    listOf(
                        Shipment("HL-46102-1", NORTHLINE, ShipmentStatus.DELIVERED),
                        Shipment("HL-46102-2", CLEAR_SKIN, ShipmentStatus.DELIVERED),
                    ),
            )
        return tracked(order, captured = true) { it.copy(history = mapOf(ShipmentStatus.DELIVERED to delivered)) }
    }

    /** #HL-48303: Maya's three things paid with the test card ···· 0002, declined and undone. */
    private fun cancelled(mayas: NewOrder): TrackedOrder {
        val order =
            Order(
                mayas.copy(id = "HL-48303", sagaId = "saga-48303", payment = "card-0002"),
                OrderStatus.Cancelled,
                cancelReason = CancelReason.PAYMENT_DECLINED,
                shipments =
                    listOf(
                        Shipment("HL-48303-1", mayas.lines[0].sellerId, ShipmentStatus.CANCELLED),
                        Shipment("HL-48303-2", mayas.lines[1].sellerId, ShipmentStatus.CANCELLED),
                    ),
            )
        return tracked(order, captured = false)
    }

    /** [order] as tracking reads it: each shipment's share, all of it charged when [captured]. */
    private fun tracked(
        order: Order,
        captured: Boolean,
        change: (TrackedShipment) -> TrackedShipment = { it },
    ): TrackedOrder {
        val shares = ShipmentShares.of(order)
        return TrackedOrder(
            order,
            OrderProgress.of(order),
            order.shipments.map {
                change(
                    TrackedShipment(
                        id = it.id,
                        sellerId = it.sellerId,
                        status = it.status,
                        history = emptyMap(),
                        shareCents = shares.getValue(it.id),
                        capturedCents = if (captured) shares.getValue(it.id) else 0,
                        pickupCode = null,
                        heldUntil = null,
                    ),
                )
            },
        )
    }

    private companion object {
        const val PLACED = "order_placed.json"
        const val IN_TRANSIT = "order_in_transit.json"
        const val READY_FOR_PICKUP = "order_ready_for_pickup.json"
        const val DELIVERED = "order_delivered.json"
        const val CANCELLED = "order_cancelled.json"

        const val WEDNESDAY_3PM = "2025-10-08T15"

        // Research §6's orders bought from sellers the seed does not have.
        const val FLOWFIT = "s-flowfit-studio"
        const val NORTHLINE = "s-northline-knitwear"
        const val CLEAR_SKIN = "s-clear-skin-lab"
        const val YOGA_MAT_SKU = "p-yoga-mat-0"
        const val SWEATER_SKU = "p-merino-sweater-0"
        const val SERUM_SKU = "p-vitamin-c-serum-0"

        val YOGA_MAT = mapOf(YOGA_MAT_SKU to Bought("p-yoga-mat", "Black", "#E3F5D8", dispatchDays = 0))
        val SWEATER = mapOf(SWEATER_SKU to Bought("p-merino-sweater", "Moss · M", "#FFE5DD", dispatchDays = 0))
        val SERUM = mapOf(SERUM_SKU to Bought("p-vitamin-c-serum", "30 ml", "#F1E4F5", dispatchDays = 0))
    }
}
