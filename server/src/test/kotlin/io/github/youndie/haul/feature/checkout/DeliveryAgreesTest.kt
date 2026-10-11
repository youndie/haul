package io.github.youndie.haul.feature.checkout

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.cart.data.ExposedCartRepository
import io.github.youndie.haul.feature.cart.domain.CartCommands
import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.cart.screen.CartScreen
import io.github.youndie.haul.feature.catalog.data.ExposedCatalogRepository
import io.github.youndie.haul.feature.catalog.data.ExposedProductSales
import io.github.youndie.haul.feature.catalog.domain.BoughtThisMonth
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.feature.catalog.screen.ProductScreen
import io.github.youndie.haul.feature.catalog.screen.ProductTab
import io.github.youndie.haul.feature.checkout.data.ExposedCheckoutRepository
import io.github.youndie.haul.feature.checkout.data.ExposedDeliverySlots
import io.github.youndie.haul.feature.checkout.domain.CheckoutCommands
import io.github.youndie.haul.feature.checkout.domain.CheckoutError
import io.github.youndie.haul.feature.checkout.screen.CheckoutScreen
import io.github.youndie.haul.feature.identity.Caller
import io.github.youndie.haul.feature.identity.Callers
import io.github.youndie.haul.feature.identity.data.ExposedCustomers
import io.github.youndie.haul.feature.identity.data.ExposedGuests
import io.github.youndie.haul.feature.membership.data.ExposedPointsLedger
import io.github.youndie.haul.feature.reviews.data.ExposedReviews
import io.github.youndie.haul.feature.reviews.screen.ReviewTabs
import io.github.youndie.haul.feature.saved.data.ExposedSavedItems
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCatalog.DUVET_COVER
import io.github.youndie.haul.seed.SampleCatalog.SONY_HEADPHONES
import io.github.youndie.haul.seed.SampleCatalog.STONEWARE_MUG
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.shell.Viewers
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.SeededDatabase
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.ui.CartGroup
import io.github.youndie.haul.ui.DeliveryMethods
import io.github.youndie.haul.ui.DeliverySlots
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.ProductDetails
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * B-98: the product page, the cart and checkout name one day for a delivery, one fee for it and one place
 * for it. The days are the delivery calendar's (research D7) at the canvas's «now», Tue Oct 7 19:47: the
 * headphones come tomorrow, Wed Oct 8; the duvet cover and the mugs, whose seller dispatches in a day, on
 * Thu Oct 9. After the 23:30 cut-off every day is one later.
 */
class DeliveryAgreesTest {
    private val database = Databases.connect(SeededDatabase.dataSource)
    private val carts = ExposedCartRepository(database)
    private val customers = ExposedCustomers(database)
    private val checkouts = ExposedCheckoutRepository(database)

    private inner class Store(
        clock: StoreClock,
    ) {
        val catalog = ExposedCatalogRepository(database, clock)
        val calendar = DeliveryCalendar(clock::now)
        val cart = CartCommands(carts, catalog, clock)
        val cartScreen = CartScreen(carts, cart, catalog, calendar, clock, ProductPhotos(null))
        val commands =
            CheckoutCommands(
                checkouts,
                ExposedDeliverySlots(database),
                carts,
                cart,
                ExposedPointsLedger(database),
                clock,
                calendar,
            )
        val checkout = CheckoutScreen(commands, ProductPhotos(null))
        val product =
            ProductScreen(
                catalog,
                calendar,
                ProductPhotos(null),
                ReviewTabs(ExposedReviews(database)),
                BoughtThisMonth(ExposedProductSales(database), clock),
            )

        suspend fun details(
            productId: String,
            viewer: Viewer = Viewer(),
        ): ProductDetails = product.build(productId, null, ProductTab.Description, viewer).only()

        /** A new customer's cart holding one of each of [productIds]. */
        suspend fun cartOf(vararg productIds: String): CartOwner.Customer {
            val id = customers.signedIn("c-${UUID.randomUUID()}", "Test Customer", CatalogSeed.NOW).id
            val owner = CartOwner.Customer(id, plus = false)
            productIds.forEach { cart.changeLine(owner, "$it-0", LineChange(quantity = 1)) }
            return owner
        }
    }

    private val canvas = Store(CANVAS_NOW)
    private val afterCutoff =
        Store {
            CatalogSeed.NOW
                .toZonedDateTime()
                .with(LocalTime.of(23, 45))
        }

    private fun DeliveryMethods.detail(method: DeliveryMethod): String? = options.single { it.method == method }.detail

    @Test
    fun `checkout offers its windows from the day the product page and the cart promise`() =
        runBlocking<Unit> {
            val owner = canvas.cartOf(DUVET_COVER)

            assertEquals("Courier · Thu, Oct 9", canvas.details(DUVET_COVER).delivery[0].title)
            assertEquals("Pickup point · Fri, Oct 10", canvas.details(DUVET_COVER).delivery[1].title)
            assertEquals(
                "Courier · Thu, Oct 9",
                canvas.cartScreen
                    .build(owner)
                    .only<CartGroup>()
                    .delivery,
            )

            val state = canvas.commands.state(owner)
            assertEquals(LocalDate.parse("2025-10-09"), state.firstDay)
            assertEquals(
                LocalDate.parse("2025-10-09"),
                state.slots
                    .first()
                    .slot.day,
                "no window before Thursday",
            )
            val tree = canvas.checkout.build(owner)
            val methods = tree.only<DeliveryMethods>()
            assertEquals("Thu, Oct 9", methods.detail(DeliveryMethod.Courier), "not «Tomorrow, Oct 8»")
            assertEquals("Fri, Oct 10 · 240 m away", methods.detail(DeliveryMethod.PickupPoint))
            assertEquals(
                "Thu",
                tree
                    .only<DeliverySlots>()
                    .days
                    .first()
                    .weekday,
            )

            assertFailsWith<CheckoutError.SlotNotFound>("Wednesday's window is not the duvet's") {
                canvas.commands.choose(owner, CheckoutChoice(slotId = "2025-10-08T15"))
            }
        }

    @Test
    fun `a cart of two sellers is offered windows from the earlier one's day, as the canvas draws Maya's`() =
        runBlocking {
            val owner = canvas.cartOf(SONY_HEADPHONES, DUVET_COVER)
            assertEquals(
                listOf("Courier · Tomorrow", "Courier · Thu, Oct 9"),
                canvas.cartScreen
                    .build(owner)
                    .all()
                    .filterIsInstance<CartGroup>()
                    .map { it.delivery },
            )
            assertEquals("Courier · Tomorrow", canvas.details(SONY_HEADPHONES).delivery[0].title)
            val methods = canvas.checkout.build(owner).only<DeliveryMethods>()
            assertEquals("Tomorrow, Oct 8", methods.detail(DeliveryMethod.Courier))
            assertEquals("Thu, Oct 9 · 240 m away", methods.detail(DeliveryMethod.PickupPoint))
        }

    @Test
    fun `after the cut-off checkout moves with the product page`() =
        runBlocking {
            val owner = afterCutoff.cartOf(SONY_HEADPHONES)
            assertEquals("Courier · Thu, Oct 9", afterCutoff.details(SONY_HEADPHONES).delivery[0].title)
            assertEquals(
                "Courier · Thu, Oct 9",
                afterCutoff.cartScreen
                    .build(owner)
                    .only<CartGroup>()
                    .delivery,
            )
            val state = afterCutoff.commands.state(owner)
            assertEquals(
                LocalDate.parse("2025-10-09"),
                state.slots
                    .first()
                    .slot.day,
            )
            assertEquals(
                "Thu, Oct 9",
                afterCutoff.checkout
                    .build(owner)
                    .only<DeliveryMethods>()
                    .detail(DeliveryMethod.Courier),
            )
        }

    @Test
    fun `the product page charges for delivery what the cart would, by the viewer's membership`() =
        runBlocking {
            val joined = customers.signedIn("c-${UUID.randomUUID()}", "Plus Member", CatalogSeed.NOW)
            val member = Viewer(customerId = joined.id, customer = joined.copy(plus = true))
            val mugs = canvas.details(STONEWARE_MUG)
            assertEquals(listOf("$5.99", "$5.99"), mugs.delivery.take(2).map { it.price }, "$24 is under $35")
            val memberMugs = canvas.details(STONEWARE_MUG, member)
            assertEquals(listOf("Free", "Free"), memberMugs.delivery.take(2).map { it.price }, "a member pays none")
            assertEquals(
                listOf("Free", "Free"),
                canvas
                    .details(SONY_HEADPHONES)
                    .delivery
                    .take(2)
                    .map { it.price },
                "the control: $349 is over $35",
            )

            // The cart charges the same: $5.99 under $35 to a non-member, nothing to a member.
            val owner = canvas.cartOf(STONEWARE_MUG)
            assertEquals(
                599,
                canvas.commands
                    .quote(owner)
                    .totals.deliveryCents,
            )
            assertEquals(
                0,
                canvas.commands
                    .quote(owner.copy(plus = true))
                    .totals.deliveryCents,
            )
        }

    @Test
    fun `the header and the product page name the customer's own place`() =
        runBlocking {
            val viewers =
                Viewers(
                    Callers(ExposedGuests(database), customers, CANVAS_NOW),
                    carts,
                    ExposedSavedItems(database),
                    checkouts,
                )
            val customer = customers.signedIn("c-${UUID.randomUUID()}", "Ana Ruiz", CatalogSeed.NOW)
            val before = viewers.of(Caller.Customer(customer))
            assertNull(before.place, "no address yet")
            assertEquals("Brooklyn, NY 11211", canvas.product.header(before).deliverTo, "the store's default")

            checkouts.saveAddress(
                customer.id,
                AddressEntry("600 Congress Ave", "", "Austin, TX", "78701"),
                null,
                CatalogSeed.NOW,
            )
            val viewer = viewers.of(Caller.Customer(customer))
            assertEquals("Austin, TX 78701", viewer.place)
            assertEquals("Austin, TX 78701", canvas.product.header(viewer).deliverTo)
            // After the cut-off the courier line names the place instead of the time left.
            assertEquals(
                "To Austin, TX 78701",
                afterCutoff.details(SONY_HEADPHONES, viewer).delivery[0].detail,
            )
            assertEquals(
                "To Brooklyn, NY 11211",
                afterCutoff.details(SONY_HEADPHONES).delivery[0].detail,
                "a guest is told the store's default place",
            )
        }

    private suspend fun ProductScreen.header(viewer: Viewer): HaulHeader =
        build(SONY_HEADPHONES, null, ProductTab.Description, viewer).only()
}
