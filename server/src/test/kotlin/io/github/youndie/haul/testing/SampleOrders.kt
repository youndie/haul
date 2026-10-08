package io.github.youndie.haul.testing

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
import io.github.youndie.haul.seed.SampleCheckout
import io.github.youndie.haul.seed.SampleCustomers
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.time.toJavaDuration

/**
 * Research §6's order history, which no seed holds (B-18, B-19): each order written as tracking reads it —
 * its lines, its shipments and where they are — for the fixture tests that draw the canvas's Order and
 * Account artboards through the routes' own builders. Maya's in-transit, declined and delivered orders
 * repeat her placed cart ([mayas], #HL-48302, placed by the test itself) where the canvas does.
 *
 * Most of them bought products the seed does not sell — the yoga mat, the sweater and the serum the order
 * page names, and the lines the account draws only as tiles — so their tiles' tones are given here
 * ([TONES], by SKU), from the canvas.
 */
internal object SampleOrders {
    /** #HL-48211: Maya's three things again, placed on Sunday the 5th for the same window, both shipments on the road. */
    fun inTransit(mayas: NewOrder): TrackedOrder {
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
    fun readyForPickup(): TrackedOrder {
        val ready = Instant.parse("2025-10-05T16:00:00Z")
        val order =
            Order(
                NewOrder(
                    id = "HL-47960",
                    sagaId = "saga-47960",
                    customerId = SampleCustomers.MAYA,
                    method = DeliveryMethod.PickupPoint,
                    addressId = null,
                    address = null,
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
    fun delivered(mayas: NewOrder): TrackedOrder =
        deliveredOrder(
            mayas,
            "HL-46102",
            "2025-09-24T20:10:00-04:00",
            "2025-09-26T18:30:00Z",
            listOf(
                OrderLine(SWEATER_SKU, NORTHLINE, "Merino Wool Crewneck Sweater, Unisex", 1, 8_000, 8_000),
                OrderLine(SERUM_SKU, CLEAR_SKIN, "Vitamin C Brightening Serum, 30 ml", 1, 2_300, 2_300),
            ),
        )

    /** #HL-48303: Maya's three things paid with the test card ···· 0002, declined and undone. */
    fun cancelled(mayas: NewOrder): TrackedOrder {
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

    /**
     * Maya's history as the account draws it (`Account_Content`, `Account_Orders`), newest first: #HL-48211
     * in transit, #HL-47960 waiting at the point, #HL-46102, #HL-45277 and #HL-42860 delivered, and #HL-44019,
     * which the canvas draws returned — delivered here: no order is returned until the returns exist (B-21),
     * and the fixture says it is the returned one ([RETURNED]).
     */
    fun mayasHistory(mayas: NewOrder): List<TrackedOrder> =
        listOf(
            inTransit(mayas),
            readyForPickup(),
            delivered(mayas),
            deliveredOrder(
                mayas,
                "HL-45277",
                "2025-09-11T13:40:00-04:00",
                "2025-09-13T17:00:00Z",
                listOf(OrderLine(VACUUM_SKU, HOMEWORKS, "Cordless Stick Vacuum V8", 1, 29_900, 29_900)),
            ),
            deliveredOrder(
                mayas,
                RETURNED,
                "2025-08-30T09:15:00-04:00",
                "2025-09-02T16:00:00Z",
                listOf(
                    OrderLine(CANDLES_SKU, HOMEWORKS, "Beeswax Taper Candles, set of 6", 1, 2_450, 2_450),
                    OrderLine(PLANTER_SKU, HOMEWORKS, "Ceramic Planter, 6 in", 1, 2_800, 2_800),
                    OrderLine(NAPKINS_SKU, HOMEWORKS, "Linen Napkins, set of 4", 1, 3_500, 3_500),
                ),
            ),
            deliveredOrder(
                mayas,
                "HL-42860",
                "2025-08-12T18:05:00-04:00",
                "2025-08-14T15:00:00Z",
                listOf(OrderLine(THROW_SKU, NORTHLINE, "Cotton Throw Blanket", 1, 4_200, 4_200)),
            ),
        )

    /** #HL-45890: Sam's one order (`Account_NotMember`), two things delivered on the 20th. */
    fun samsHistory(mayas: NewOrder): List<TrackedOrder> =
        listOf(
            deliveredOrder(
                mayas.copy(customerId = SampleCustomers.SAM),
                "HL-45890",
                "2025-09-18T12:30:00-04:00",
                "2025-09-20T17:00:00Z",
                listOf(
                    OrderLine(VACUUM_SKU, HOMEWORKS, "Cordless Stick Vacuum V8, filter set", 1, 6_500, 6_500),
                    OrderLine(THROW_SKU, NORTHLINE, "Cotton Throw Blanket, travel size", 1, 3_800, 3_800),
                ),
            ),
        )

    /** A courier order of [lines], every shipment delivered at [deliveredAt]; the rest is Maya's order's. */
    private fun deliveredOrder(
        base: NewOrder,
        id: String,
        placedAt: String,
        deliveredAt: String,
        lines: List<OrderLine>,
    ): TrackedOrder {
        val items = lines.sumOf { it.priceCents * it.quantity }
        val order =
            Order(
                base.copy(
                    id = id,
                    sagaId = "saga-$id",
                    slotId = null,
                    promoCode = null,
                    itemsCents = items,
                    discountCents = 0,
                    deliveryCents = 0,
                    totalCents = items,
                    points = items / 100 * 2,
                    placedAt = OffsetDateTime.parse(placedAt),
                    lines = lines,
                ),
                OrderStatus.Placed,
                cancelReason = null,
                shipments =
                    lines.map { it.sellerId }.distinct().mapIndexed { index, seller ->
                        Shipment("$id-${index + 1}", seller, ShipmentStatus.DELIVERED)
                    },
            )
        val at = Instant.parse(deliveredAt)
        return tracked(order, captured = true) { it.copy(history = mapOf(ShipmentStatus.DELIVERED to at)) }
    }

    /** [order] as tracking reads it: each shipment's share, all of it charged when [captured]. */
    fun tracked(
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

    /** The order the canvas draws returned (`Account_Orders`). */
    const val RETURNED = "HL-44019"

    // Sellers and products the seed does not have.
    const val FLOWFIT = "s-flowfit-studio"
    const val NORTHLINE = "s-northline-knitwear"
    const val CLEAR_SKIN = "s-clear-skin-lab"
    const val HOMEWORKS = "s-homeworks"
    const val YOGA_MAT_SKU = "p-yoga-mat-0"
    const val SWEATER_SKU = "p-merino-sweater-0"
    const val SERUM_SKU = "p-vitamin-c-serum-0"
    const val VACUUM_SKU = "p-stick-vacuum-0"
    const val CANDLES_SKU = "p-taper-candles-0"
    const val PLANTER_SKU = "p-ceramic-planter-0"
    const val NAPKINS_SKU = "p-linen-napkins-0"
    const val THROW_SKU = "p-cotton-throw-0"

    /** The tiles the canvas draws for the products the seed does not sell, by SKU. */
    val TONES: Map<String, String> =
        mapOf(
            YOGA_MAT_SKU to "#E3F5D8",
            SWEATER_SKU to "#FFE5DD",
            SERUM_SKU to "#F1E4F5",
            VACUUM_SKU to "#E0EEF7",
            CANDLES_SKU to "#FFF1C9",
            PLANTER_SKU to "#E3F5D8",
            NAPKINS_SKU to "#E6E4FF",
            THROW_SKU to "#ECE9E2",
        )
}
