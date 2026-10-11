package io.github.youndie.haul.feature.order

import io.github.youndie.haul.feature.checkout.CheckoutChoice
import io.github.youndie.haul.feature.order.domain.Order
import io.github.youndie.haul.feature.order.domain.OrderStatus
import io.github.youndie.haul.feature.order.domain.Shipment
import io.github.youndie.haul.feature.order.domain.ShipmentStatus
import io.github.youndie.haul.feature.order.screen.OrderScreen
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.testing.FulfilmentWorld
import io.github.youndie.haul.testing.SampleOrders
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.ui.OrderBody
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-98: the store's deliveries are simulated, and the simulator's pace does not wait for the window chosen
 * (research, «Decided in B-17»), so an order can arrive before the day it was booked for — on the stand, an
 * order with the window Thu Oct 15 was «Delivered Oct 10». The page keeps the day it arrived and says why it
 * is earlier than the window; an order that arrived on its day reads as before.
 *
 * Maya's cart is placed at the canvas's «now», Tue Oct 7 19:47: the headphones are due Wed Oct 8, the duvet
 * cover and the mugs Thu Oct 9 (research D7).
 */
class EarlyDeliveryTest {
    private fun lead(
        slotId: String?,
        deliveredAt: String,
    ): Pair<String, String?> =
        seededFreshDatabase().use { dataSource ->
            FulfilmentWorld(dataSource).use { world ->
                runBlocking {
                    val placed = checkNotNull(world.order(world.place(CheckoutChoice(slotId = "2025-10-08T15")))).placed
                    val order =
                        Order(
                            placed.copy(slotId = slotId),
                            OrderStatus.Placed,
                            cancelReason = null,
                            shipments =
                                placed.lines.map { it.sellerId }.distinct().mapIndexed { i, seller ->
                                    Shipment("${placed.id}-${i + 1}", seller, ShipmentStatus.DELIVERED)
                                },
                        )
                    val at = Instant.parse(deliveredAt)
                    val tracked =
                        SampleOrders.tracked(order, captured = true) {
                            it.copy(history = mapOf(ShipmentStatus.DELIVERED to at))
                        }
                    val screen = world.koin.get<OrderScreen>()
                    val view =
                        screen
                            .view(SampleCustomers.MAYA, tracked, "Maya")
                            .copy(now = ZonedDateTime.parse("2025-10-16T10:00:00-04:00"))
                    val body =
                        screen
                            .page(
                                view,
                                Viewer(firstName = "Maya", customerId = SampleCustomers.MAYA),
                            ).only<OrderBody>()
                    body.title to body.lead
                }
            }
        }

    @Test
    fun `an order delivered before its window says the delivery was simulated`() {
        val (title, lead) = lead(slotId = "2025-10-11T15", deliveredAt = "2025-10-09T16:00:00Z")
        assertEquals("Delivered Oct 9", title, "the day it really arrived")
        assertEquals(
            "Deliveries here are simulated: this one came ahead of Sat, Oct 11, the window it was booked for. " +
                "Returns are open until Nov 8.",
            lead,
        )
    }

    @Test
    fun `an order delivered before the day it was due says so without a window`() {
        val (title, lead) = lead(slotId = null, deliveredAt = "2025-10-08T16:00:00Z")
        assertEquals("Delivered Oct 8", title)
        assertEquals(
            "Deliveries here are simulated: this one came ahead of Thu, Oct 9, the day it was due. " +
                "Returns are open until Nov 7.",
            lead,
        )
    }

    @Test
    fun `an order delivered on its window's day reads as before`() {
        val (title, lead) = lead(slotId = "2025-10-11T15", deliveredAt = "2025-10-11T20:00:00Z")
        assertEquals("Delivered Oct 11", title)
        assertEquals("Returns are open until Nov 10.", lead)
    }
}
