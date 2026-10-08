package io.github.youndie.haul.di

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.account.accountModule
import io.github.youndie.haul.feature.account.domain.Loyalty
import io.github.youndie.haul.feature.account.domain.SavedLists
import io.github.youndie.haul.feature.account.screen.AccountScreen
import io.github.youndie.haul.feature.cart.cartModule
import io.github.youndie.haul.feature.cart.domain.CartCommands
import io.github.youndie.haul.feature.cart.domain.CartRepository
import io.github.youndie.haul.feature.cart.screen.CartScreen
import io.github.youndie.haul.feature.catalog.catalogModule
import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.feature.catalog.screen.CatalogScreen
import io.github.youndie.haul.feature.catalog.screen.DealsScreen
import io.github.youndie.haul.feature.catalog.screen.HomeScreen
import io.github.youndie.haul.feature.catalog.screen.ProductScreen
import io.github.youndie.haul.feature.checkout.checkoutModule
import io.github.youndie.haul.feature.checkout.domain.CheckoutCommands
import io.github.youndie.haul.feature.checkout.domain.CheckoutRepository
import io.github.youndie.haul.feature.checkout.domain.DeliverySlots
import io.github.youndie.haul.feature.checkout.screen.CheckoutScreen
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentPace
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentRepository
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentSimulator
import io.github.youndie.haul.feature.fulfilment.domain.OrderTracking
import io.github.youndie.haul.feature.fulfilment.fulfilmentModule
import io.github.youndie.haul.feature.identity.Callers
import io.github.youndie.haul.feature.identity.domain.Customers
import io.github.youndie.haul.feature.identity.domain.Guests
import io.github.youndie.haul.feature.identity.identityModule
import io.github.youndie.haul.feature.order.domain.OrderRepository
import io.github.youndie.haul.feature.order.domain.Placement
import io.github.youndie.haul.feature.order.domain.Reorder
import io.github.youndie.haul.feature.order.domain.StockReservations
import io.github.youndie.haul.feature.order.orderModule
import io.github.youndie.haul.feature.order.saga.SagaStorage
import io.github.youndie.haul.feature.order.screen.OrderScreen
import io.github.youndie.haul.feature.payment.domain.PaymentProcessor
import io.github.youndie.haul.feature.payment.paymentModule
import io.github.youndie.haul.feature.returns.domain.RequestReturn
import io.github.youndie.haul.feature.returns.domain.ReturnRepository
import io.github.youndie.haul.feature.returns.domain.ReturnSimulator
import io.github.youndie.haul.feature.returns.returnsModule
import io.github.youndie.haul.feature.reviews.domain.ReviewCommands
import io.github.youndie.haul.feature.reviews.domain.ReviewRepository
import io.github.youndie.haul.feature.reviews.reviewsModule
import io.github.youndie.haul.feature.reviews.screen.ReviewTabs
import io.github.youndie.haul.feature.search.domain.RecentSearches
import io.github.youndie.haul.feature.search.screen.SearchScreen
import io.github.youndie.haul.feature.search.searchModule
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.shell.Viewers
import io.github.youndie.haul.testing.SAGA_CLOCK
import io.github.youndie.haul.testing.SeededDatabase
import io.github.youndie.petich.PetichEngine
import io.github.youndie.petich.SuspendedPetichSweeper
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Every type a route injects resolves. Koin creates a `single` on first use, so a binding that cannot
 * be built otherwise fails at the first request of one route while the server starts healthy.
 *
 * The graph is closed at the end, and that is load-bearing: a `single` resolved here is cached in the
 * shared module object (`catalogModule`, …), not in this Koin, until a Koin that loaded the module
 * closes. Left open, the next test application served this graph's instances — the repositories over
 * the suite's seeded database instead of its own (B-11: a cart test over a fresh database read the
 * shared one; and, before it, a stub address here surfaced as a `500` on `/ui/home` naming port 1).
 */
class KoinGraphTest {
    @Test
    fun `every screen resolves`() {
        val application =
            koinApplication {
                modules(
                    module {
                        single { Databases.connect(SeededDatabase.dataSource) }
                        single<DataSource> { SeededDatabase.dataSource }
                        single { StoreClock { CatalogSeed.NOW.toZonedDateTime() } }
                        single { DeliveryCalendar { CatalogSeed.NOW.toZonedDateTime() } }
                        single { ProductPhotos(null) }
                        single { SAGA_CLOCK }
                        single { FulfilmentPace.STORE }
                    },
                    catalogModule,
                    searchModule,
                    identityModule,
                    cartModule,
                    checkoutModule,
                    paymentModule,
                    orderModule,
                    fulfilmentModule,
                    reviewsModule,
                    accountModule,
                    returnsModule,
                )
            }
        try {
            resolveEverything(application.koin)
        } finally {
            application.close()
        }
    }

    private fun resolveEverything(koin: Koin) {
        assertNotNull(koin.get<CatalogRepository>())
        assertNotNull(koin.get<HomeScreen>())
        assertNotNull(koin.get<CatalogScreen>())
        assertNotNull(koin.get<ProductScreen>())
        assertNotNull(koin.get<DealsScreen>())
        assertNotNull(koin.get<SearchScreen>())
        assertNotNull(koin.get<RecentSearches>())
        assertNotNull(koin.get<Guests>())
        assertNotNull(koin.get<Customers>())
        assertNotNull(koin.get<Callers>())
        assertNotNull(koin.get<Viewers>())
        assertNotNull(koin.get<CartRepository>())
        assertNotNull(koin.get<CartCommands>())
        assertNotNull(koin.get<CartScreen>())
        assertNotNull(koin.get<CheckoutRepository>())
        assertNotNull(koin.get<DeliverySlots>())
        assertNotNull(koin.get<CheckoutCommands>())
        assertNotNull(koin.get<CheckoutScreen>())
        assertNotNull(koin.get<PaymentProcessor>())
        assertNotNull(koin.get<OrderRepository>())
        assertNotNull(koin.get<StockReservations>())
        assertNotNull(koin.get<SagaStorage>())
        assertNotNull(koin.get<PetichEngine>())
        assertNotNull(koin.get<SuspendedPetichSweeper>())
        assertNotNull(koin.get<Placement>())
        assertNotNull(koin.get<FulfilmentRepository>())
        assertNotNull(koin.get<FulfilmentSimulator>())
        assertNotNull(koin.get<OrderTracking>())
        assertNotNull(koin.get<ReviewRepository>())
        assertNotNull(koin.get<ReviewCommands>())
        assertNotNull(koin.get<ReviewTabs>())
        assertNotNull(koin.get<OrderScreen>())
        assertNotNull(koin.get<Reorder>())
        assertNotNull(koin.get<Loyalty>())
        assertNotNull(koin.get<SavedLists>())
        assertNotNull(koin.get<AccountScreen>())
        assertNotNull(koin.get<ReturnRepository>())
        assertNotNull(koin.get<RequestReturn>())
        assertNotNull(koin.get<ReturnSimulator>())
    }
}
