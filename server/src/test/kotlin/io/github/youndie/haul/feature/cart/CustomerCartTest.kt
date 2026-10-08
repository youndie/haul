package io.github.youndie.haul.feature.cart

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.cart.data.ExposedCartRepository
import io.github.youndie.haul.feature.cart.domain.CartCommands
import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.cart.screen.CartScreen
import io.github.youndie.haul.feature.catalog.data.ExposedCatalogRepository
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.feature.identity.data.ExposedCustomers
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.seed.SampleCatalog.DUVET_COVER
import io.github.youndie.haul.seed.SampleCatalog.SONY_HEADPHONES
import io.github.youndie.haul.seed.SampleCatalog.STONEWARE_MUG
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.SeededDatabase
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.ui.CartLine
import io.github.youndie.haul.ui.OrderSummary
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A customer's cart, below the routes (sign-in over HTTP is `IdentityRoutesTest`): the points line and
 * the checkout button. Maya is Plus, Sam is not (research §6); Maya's cart is in the seed.
 */
class CustomerCartTest {
    private val database = Databases.connect(SeededDatabase.dataSource)
    private val catalog = ExposedCatalogRepository(database)
    private val carts = ExposedCartRepository(database)
    private val commands = CartCommands(carts, catalog, CANVAS_NOW)
    private val screen =
        CartScreen(carts, commands, catalog, DeliveryCalendar(CANVAS_NOW::now), CANVAS_NOW, ProductPhotos(null))
    private val customers = ExposedCustomers(database)

    private fun summaryOf(plus: Boolean): OrderSummary =
        runBlocking {
            val id = customers.signedIn("c-${UUID.randomUUID()}", "Test Customer", CatalogSeed.NOW).id
            val owner = CartOwner.Customer(id, plus)
            listOf(SampleCatalog.SONY_HEADPHONES, SampleCatalog.DUVET_COVER, SampleCatalog.STONEWARE_MUG).forEach {
                commands.changeLine(owner, "$it-0", LineChange(quantity = 1))
            }
            screen.build(owner).only()
        }

    @Test
    fun `a Plus member earns double points on the total`() {
        val summary = summaryOf(plus = true)
        assertEquals("$512", summary.total)
        assertEquals("You'll earn 1,024 points on this order", summary.points)
        assertEquals("1,024 points", summary.pointsAccent)
        assertEquals("Checkout", summary.checkoutLabel)
    }

    @Test
    fun `Maya's seeded cart is the one the canvas draws`() =
        runBlocking {
            val tree = screen.build(CartOwner.Customer(SampleCustomers.MAYA, plus = true))
            val summary = tree.only<OrderSummary>()
            assertEquals("$652.00", summary.rows.first { it.label == "Items (3)" }.value)
            assertEquals("$512", summary.total)
            assertEquals("You'll earn 1,024 points on this order", summary.points)
            assertEquals(
                listOf("$SONY_HEADPHONES-0", "$DUVET_COVER-0", "$STONEWARE_MUG-0"),
                tree.all().filterIsInstance<CartLine>().map { it.skuId },
            )
        }

    @Test
    fun `a customer without Plus earns a point per whole dollar`() {
        assertEquals("You'll earn 512 points on this order", summaryOf(plus = false).points)
    }
}
