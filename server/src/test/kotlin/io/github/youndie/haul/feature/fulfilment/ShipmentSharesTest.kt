package io.github.youndie.haul.feature.fulfilment

import io.github.youndie.haul.feature.checkout.DeliveryMethod
import io.github.youndie.haul.feature.fulfilment.domain.ShipmentShares
import io.github.youndie.haul.feature.order.domain.NewOrder
import io.github.youndie.haul.feature.order.domain.Order
import io.github.youndie.haul.feature.order.domain.OrderLine
import io.github.youndie.haul.feature.order.domain.OrderStatus
import io.github.youndie.haul.feature.order.domain.Shipment
import io.github.youndie.haul.feature.order.domain.ShipmentStatus
import io.github.youndie.haul.seed.CatalogSeed
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How an order's total is shared between its shipments for the capture (B-17): in proportion to what each
 * seller's lines cost, so a promo code and a delivery fee are shared too, and always to the cent.
 */
class ShipmentSharesTest {
    private fun order(
        totalCents: Int,
        vararg sellers: Pair<String, Int>,
    ) = Order(
        placed =
            NewOrder(
                id = "HL-1",
                sagaId = "saga",
                customerId = "maya",
                method = DeliveryMethod.Courier,
                addressId = null,
                pointId = null,
                slotId = null,
                payment = "card-4821",
                promoCode = null,
                itemsCents = 0,
                discountCents = 0,
                deliveryCents = 0,
                totalCents = totalCents,
                points = 0,
                placedAt = CatalogSeed.NOW,
                lines = sellers.map { (seller, cents) -> OrderLine("sku-$seller", seller, seller, 1, cents, cents) },
            ),
        status = OrderStatus.Placed,
        cancelReason = null,
        shipments = sellers.mapIndexed { i, (seller, _) -> Shipment("HL-1-${i + 1}", seller, ShipmentStatus.PLACED) },
    )

    /** Maya's $512.00 at the prices paid: $349.00 and $163.00, nothing to share out. */
    @Test
    fun `an order paid at its prices is shared by them`() {
        assertEquals(
            mapOf("HL-1-1" to 34_900, "HL-1-2" to 16_300),
            ShipmentShares.of(
                order(
                    51_200,
                    "sony" to 34_900,
                    "home" to 16_300,
                ),
            ),
        )
    }

    /**
     * A discount that does not divide evenly: every share but the last is rounded down and the last takes
     * the rest, so the captures add up to the authorisation and never to a cent more.
     */
    @Test
    fun `a promo code and a fee are shared and the parts add up to the cent`() {
        val shares = ShipmentShares.of(order(2_999, "a" to 1_000, "b" to 1_000, "c" to 1_000))

        assertEquals(mapOf("HL-1-1" to 999, "HL-1-2" to 999, "HL-1-3" to 1_001), shares)
        assertEquals(2_999, shares.values.sum())
    }
}
