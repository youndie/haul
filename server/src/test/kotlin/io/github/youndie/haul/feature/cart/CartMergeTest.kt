package io.github.youndie.haul.feature.cart

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.cart.data.ExposedCartRepository
import io.github.youndie.haul.feature.cart.domain.CartCommands
import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.catalog.data.ExposedCatalogRepository
import io.github.youndie.haul.feature.identity.data.ExposedCustomers
import io.github.youndie.haul.feature.identity.data.ExposedGuests
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.seededFreshDatabase
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sign-in's merge rules (feature-identity), below the routes: the same SKU's quantities summed and
 * capped at ten and at the stock, the guest's code kept when the customer has none, the guest cart
 * gone. The stock changes under the carts here, so the test has a database of its own.
 */
class CartMergeTest {
    private val dataSource = seededFreshDatabase()
    private val database = Databases.connect(dataSource)
    private val carts = ExposedCartRepository(database)
    private val commands = CartCommands(carts, ExposedCatalogRepository(database, CANVAS_NOW), CANVAS_NOW)
    private val mug = "${SampleCatalog.STONEWARE_MUG}-0"
    private val duvet = "${SampleCatalog.DUVET_COVER}-0"

    private fun parties(): Pair<CartOwner.Guest, CartOwner.Customer> =
        runBlocking {
            val guest = ExposedGuests(database).create(CatalogSeed.NOW)
            val customer =
                ExposedCustomers(
                    database,
                ).signedIn("c-${UUID.randomUUID()}", "Test Customer", CatalogSeed.NOW)
            CartOwner.Guest(guest) to CartOwner.Customer(customer.id, plus = false)
        }

    private suspend fun quantities(owner: CartOwner): List<Pair<String, Int>> =
        carts.cart(owner).lines.map {
            it.skuId to
                it.quantity
        }

    @Test
    fun `the same SKU sums and stops at ten`() =
        runBlocking {
            val (guest, customer) = parties()
            commands.changeLine(guest, mug, LineChange(quantity = 6))
            commands.changeLine(customer, mug, LineChange(quantity = 7))

            commands.merge(guest, customer)

            assertEquals(listOf(mug to 10), quantities(customer))
            assertEquals(emptyList(), quantities(guest))
        }

    @Test
    fun `the same SKU sums and stops at the stock`() =
        runBlocking {
            val (guest, customer) = parties()
            commands.changeLine(guest, duvet, LineChange(quantity = 2))
            commands.changeLine(customer, duvet, LineChange(quantity = 2))
            dataSource.connection.use { c ->
                c.createStatement().use { it.executeUpdate("UPDATE skus SET stock = 3 WHERE id = '$duvet'") }
                c.commit()
            }

            commands.merge(guest, customer)

            assertEquals(listOf(duvet to 3), quantities(customer))
        }

    @Test
    fun `the guest's code comes along only when the customer has none`() =
        runBlocking {
            val (guest, customer) = parties()
            commands.changeLine(guest, mug, LineChange(quantity = 1))
            commands.applyPromo(guest, "AUTUMN10")

            commands.merge(guest, customer)

            assertEquals("AUTUMN10", carts.cart(customer).promoCode)

            val (second, _) = parties()
            commands.changeLine(second, duvet, LineChange(quantity = 1))
            carts.setPromo(second, "SUMMER5")
            carts.setPromo(customer, null)
            commands.applyPromo(customer, "AUTUMN10")
            commands.merge(second, customer)
            assertEquals("AUTUMN10", carts.cart(customer).promoCode)
            assertTrue(quantities(customer).containsAll(listOf(mug to 1, duvet to 1)))
        }
}
