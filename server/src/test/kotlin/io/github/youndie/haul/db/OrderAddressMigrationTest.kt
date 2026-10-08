package io.github.youndie.haul.db

import io.github.youndie.haul.feature.checkout.AddressEntry
import io.github.youndie.haul.feature.order.data.ExposedOrders
import io.github.youndie.haul.testing.PostgresHarness
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * V14 copies the address of every order placed before it into the order (B-40). From then on the address
 * form edits a saved address in place, and an order that kept only the id would read whatever the
 * shopper typed next; an order the migration missed reads no address at all.
 */
class OrderAddressMigrationTest {
    @Test
    fun `an order placed before V14 keeps the address its id named`() =
        PostgresHarness.freshDatabase(upTo = "12").use { dataSource ->
            dataSource.connection.use { connection ->
                connection.createStatement().use {
                    it.execute(
                        """
                        INSERT INTO customers (id, name, plus, created_at) VALUES ('maya', 'Maya', true, now());
                        INSERT INTO addresses (id, customer_id, street, apt, city, zip, door_code, courier_note, created_at)
                        VALUES ('address-maya', 'maya', '148 Wythe Avenue', '4F', 'Brooklyn, NY', '11211', NULL, 'Ring twice', now());
                        INSERT INTO orders (id, saga_id, customer_id, status, method, address_id, payment, items_cents,
                                            discount_cents, delivery_cents, total_cents, points, placed_at)
                        VALUES ('HL-1', 'saga-1', 'maya', 'placed', 'courier', 'address-maya', 'card-4821', 0, 0, 0, 0, 0, now()),
                               ('HL-2', 'saga-2', 'maya', 'placed', 'pickup_point', NULL, 'card-4821', 0, 0, 0, 0, 0, now());
                        """.trimIndent(),
                    )
                }
                connection.commit()
            }

            Databases.migrate(dataSource)

            val orders = ExposedOrders(Databases.connect(dataSource))
            val courier = assertNotNull(runBlocking { orders.order("HL-1") }).placed
            assertEquals(
                AddressEntry("148 Wythe Avenue", "4F", "Brooklyn, NY", "11211", courierNote = "Ring twice"),
                courier.address,
                "the order placed before V14 lost the address its id named",
            )
            val pickup = assertNotNull(runBlocking { orders.order("HL-2") }).placed
            assertEquals(null, pickup.address, "an order with no address was given one")
        }
}
