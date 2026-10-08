package io.github.youndie.haul.di

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.catalog.catalogModule
import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.screen.CatalogScreen
import io.github.youndie.haul.feature.catalog.screen.HomeScreen
import io.github.youndie.haul.feature.catalog.screen.ProductScreen
import io.github.youndie.haul.feature.search.screen.SearchScreen
import io.github.youndie.haul.feature.search.searchModule
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.testing.SeededDatabase
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Every type a route injects resolves. Koin creates a `single` on first use, so a binding that cannot
 * be built otherwise fails at the first request of one route while the server starts healthy.
 *
 * The database is the suite's seeded one, not a stub address: `Database.connect` registers globally in
 * Exposed, and a stub registered here was picked up by the next test's queries (a `500` on `/ui/home`
 * naming port 1).
 */
class KoinGraphTest {
    @Test
    fun `every screen resolves`() {
        val koin =
            koinApplication {
                modules(
                    module {
                        single { Databases.connect(SeededDatabase.dataSource) }
                        single<DataSource> { SeededDatabase.dataSource }
                        single { StoreClock { CatalogSeed.NOW.toZonedDateTime() } }
                        single { DeliveryCalendar { CatalogSeed.NOW.toZonedDateTime() } }
                    },
                    catalogModule,
                    searchModule,
                )
            }.koin
        assertNotNull(koin.get<CatalogRepository>())
        assertNotNull(koin.get<HomeScreen>())
        assertNotNull(koin.get<CatalogScreen>())
        assertNotNull(koin.get<ProductScreen>())
        assertNotNull(koin.get<SearchScreen>())
    }
}
