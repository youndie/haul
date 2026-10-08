package io.github.youndie.haul.feature.cart

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.cart.data.ExposedCartRepository
import io.github.youndie.haul.feature.cart.domain.CartCommands
import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.cart.screen.CartScreen
import io.github.youndie.haul.feature.catalog.data.ExposedCatalogRepository
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.SeededDatabase
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.ui.OrderSummary
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A customer's cart, below the routes: nothing names a customer over HTTP until sign-in (B-12), but
 * the points line and the checkout button are this item's tree. Maya is Plus, Sam is not (research §6).
 */
class CustomerCartTest {
    private val database = Databases.connect(SeededDatabase.dataSource)
    private val catalog = ExposedCatalogRepository(database)
    private val carts = ExposedCartRepository(database)
    private val commands = CartCommands(carts, catalog, CANVAS_NOW)
    private val screen =
        CartScreen(carts, commands, catalog, DeliveryCalendar(CANVAS_NOW::now), CANVAS_NOW, ProductPhotos(null))

    private fun summaryOf(plus: Boolean): OrderSummary =
        runBlocking {
            val owner = CartOwner.Customer("c-${UUID.randomUUID()}", plus)
            listOf(SampleCatalog.SONY_HEADPHONES, SampleCatalog.DUVET_COVER, SampleCatalog.STONEWARE_MUG).forEach {
                commands.changeLine(owner, "$it-0", LineChange(quantity = 1))
            }
            screen.build(owner).only()
        }

    @Test
    fun `a Plus member earns double points on the total`() {
        val summary = summaryOf(plus = true)
        assertEquals("$512.00", summary.total)
        assertEquals("You'll earn 1,024 points", summary.points)
        assertEquals("Checkout", summary.checkoutLabel)
    }

    @Test
    fun `a customer without Plus earns a point per whole dollar`() {
        assertEquals("You'll earn 512 points", summaryOf(plus = false).points)
    }
}
