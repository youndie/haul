package io.github.youndie.haul.feature.returns

import io.github.youndie.haul.feature.cart.screen.CartScreen
import io.github.youndie.haul.feature.checkout.DeliveryMethod
import io.github.youndie.haul.feature.order.domain.NewOrder
import io.github.youndie.haul.feature.order.domain.Order
import io.github.youndie.haul.feature.order.domain.OrderLine
import io.github.youndie.haul.feature.order.domain.OrderStatus
import io.github.youndie.haul.feature.returns.domain.ReturnRefunds
import io.github.youndie.haul.feature.returns.domain.ReturnWindow
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a returned line gives back and takes back (B-21): its part of the items as paid — the promo code's
 * discount shared over the lines — never the delivery, and the points in proportion; and the 30 days counted
 * in the store's days.
 */
class ReturnRefundsTest {
    private fun order(
        discountCents: Int,
        deliveryCents: Int,
        points: Int,
        vararg prices: Int,
    ): Order {
        val lines =
            prices.mapIndexed {
                index,
                cents,
                ->
                OrderLine("sku-$index", "seller", "Line $index", 1, cents, cents)
            }
        val items = prices.sum()
        return Order(
            NewOrder(
                id = "HL-1",
                sagaId = "saga-1",
                customerId = "maya",
                method = DeliveryMethod.Courier,
                addressId = null,
                address = null,
                pointId = null,
                slotId = null,
                payment = "card-4821",
                promoCode = null,
                itemsCents = items,
                discountCents = discountCents,
                deliveryCents = deliveryCents,
                totalCents = items - discountCents + deliveryCents,
                points = points,
                placedAt = OffsetDateTime.parse("2025-10-07T19:47:23-04:00"),
                lines = lines,
            ),
            OrderStatus.Placed,
            cancelReason = null,
            shipments = emptyList(),
        )
    }

    /** #HL-44019 (`Order_Returned`): no code, free delivery — each line its price, all of it $87.50 and 174 points. */
    @Test
    fun `without a discount each line gives back its price`() {
        val order = order(0, 0, 174, 3_500, 2_450, 2_800)
        assertEquals(mapOf(0 to 3_500, 1 to 2_450, 2 to 2_800), ReturnRefunds.of(order))
        assertEquals(174, ReturnRefunds.points(order, 8_750))
    }

    /**
     * A $10.00 code over $30.00 and $70.00 of items, with $5.99 delivery: the code is shared in proportion —
     * $27.00 and $63.00 back, not the prices, which would give back more than was paid — and the delivery is
     * kept. The shares add up to the items as paid to the cent, even where a third does not divide.
     */
    @Test
    fun `a discount is shared over the lines and the delivery is not given back`() {
        assertEquals(mapOf(0 to 2_700, 1 to 6_300), ReturnRefunds.of(order(1_000, 599, 95, 3_000, 7_000)))
        val thirds = ReturnRefunds.of(order(100, 0, 0, 1_000, 1_000, 1_000))
        assertEquals(mapOf(0 to 966, 1 to 966, 2 to 968), thirds)
        assertEquals(2_900, thirds.values.sum())
    }

    /**
     * An order's items are at the list price and its discount holds the sale too (feature-cart): Sony's
     * headphones on sale at $349.00 from $399.00 give back $349.00, not $349.00 less a share of the $50.00 the
     * sale took off.
     */
    @Test
    fun `a sale price is given back as it was paid`() {
        val bought = order(0, 0, 0, 34_900, 13_900)
        val sale =
            bought.copy(
                placed =
                    bought.placed.copy(
                        itemsCents = 53_800,
                        discountCents = 5_000,
                        lines = listOf(bought.placed.lines[0].copy(listCents = 39_900), bought.placed.lines[1]),
                    ),
            )
        assertEquals(mapOf(0 to 34_900, 1 to 13_900), ReturnRefunds.of(sale))
    }

    /** #HL-46102's sweater, $80.00 of $103.00 earning 206 points: 160 go back (the canvas's «87 × 2 = 174» rule). */
    @Test
    fun `the points taken back are the order's in proportion to the refund`() {
        val order = order(0, 0, 206, 8_000, 2_300)
        assertEquals(160, ReturnRefunds.points(order, 8_000))
        assertEquals(206, ReturnRefunds.points(order, 10_300))
        assertEquals(0, ReturnRefunds.points(order(0, 0, 0, 8_000), 8_000))
    }

    /** Delivered on Sep 26 in New York: returns are open all of Oct 26 and shut on Oct 27, whatever the hour in UTC. */
    @Test
    fun `the window is thirty store days and the last one is whole`() {
        val delivered = Instant.parse("2025-09-26T18:30:00Z")
        assertTrue(ReturnWindow.open(delivered, Instant.parse("2025-10-27T03:59:00Z")), "Oct 26, 23:59 in New York")
        assertFalse(ReturnWindow.open(delivered, Instant.parse("2025-10-27T04:00:00Z")), "Oct 27, 00:00 in New York")
    }

    /** The dialog writes the refund itself (`exactDollars`, shared); it must read as the page writes money. */
    @Test
    fun `the dialog's amounts are written as the page writes them`() {
        listOf(0, 5, 8_000, 10_300, 120_450, 123_456_789).forEach {
            assertEquals(CartScreen.exact(it), exactDollars(it), "$it cents")
        }
    }
}
