package io.github.youndie.haul.feature.checkout

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.cart.data.ExposedCartRepository
import io.github.youndie.haul.feature.cart.domain.CartCommands
import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.catalog.data.ExposedCatalogRepository
import io.github.youndie.haul.feature.checkout.data.ExposedCheckoutRepository
import io.github.youndie.haul.feature.checkout.data.ExposedDeliverySlots
import io.github.youndie.haul.feature.checkout.domain.CheckoutCommands
import io.github.youndie.haul.feature.checkout.screen.CheckoutScreen
import io.github.youndie.haul.feature.identity.data.ExposedCustomers
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.SeededDatabase
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.ui.CheckoutNotice
import io.github.youndie.haul.ui.CheckoutSummary
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The quote below the routes, where the clock can move: a promo code applied while it was valid and
 * expired since (endpoint-cart's quirk — the cart's field still shows it applied). Checkout must not
 * honour it: the total is without it, and the shopper is told why.
 */
class CheckoutQuoteTest {
    private val database = Databases.connect(SeededDatabase.dataSource)
    private val carts = ExposedCartRepository(database)
    private val catalog = ExposedCatalogRepository(database)
    private val customers = ExposedCustomers(database)

    private fun commands(clock: StoreClock) =
        CheckoutCommands(
            ExposedCheckoutRepository(database),
            ExposedDeliverySlots(database),
            carts,
            CartCommands(carts, catalog, clock),
            clock,
        )

    @Test
    fun `a promo code that expired after it was applied is not in the quote`() =
        runBlocking {
            val owner =
                CartOwner.Customer(
                    customers.signedIn("c-${UUID.randomUUID()}", "Test Customer", CatalogSeed.NOW).id,
                    false,
                )
            val cart = CartCommands(carts, catalog, CANVAS_NOW)
            cart.changeLine(owner, "${SampleCatalog.SONY_HEADPHONES}-0", LineChange(quantity = 1))
            cart.applyPromo(owner, "AUTUMN10")

            val applied = commands(CANVAS_NOW).quote(owner)
            assertEquals("AUTUMN10", applied.promo?.code)
            assertEquals(34_900 - 3_490, applied.totals.totalCents, "10 % off $349 while the code runs")

            // AUTUMN10 ends at midnight before Oct 15 (research §6).
            val later = StoreClock { CatalogSeed.NOW.plusDays(8).toZonedDateTime() }
            val expired = commands(later).state(owner)
            assertNull(expired.quote.promo)
            assertEquals("AUTUMN10", expired.expiredPromo)
            assertEquals(34_900, expired.quote.totals.totalCents)

            val tree = CheckoutScreen(commands(later)).build(owner)
            assertEquals("Code AUTUMN10 has expired and is not in the total", tree.only<CheckoutNotice>().text)
            assertEquals("$349", tree.only<CheckoutSummary>().total)
        }
}
