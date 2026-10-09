package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.feature.catalog.data.ExposedCatalogRepository
import io.github.youndie.haul.feature.catalog.data.ExposedProductSales
import io.github.youndie.haul.feature.catalog.domain.BoughtThisMonth
import io.github.youndie.haul.feature.catalog.domain.Browse
import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.domain.ProductSales
import io.github.youndie.haul.feature.catalog.screen.CatalogScreen
import io.github.youndie.haul.feature.catalog.screen.DealsScreen
import io.github.youndie.haul.feature.catalog.screen.HomeScreen
import io.github.youndie.haul.feature.catalog.screen.LineAnswers
import io.github.youndie.haul.feature.catalog.screen.ProductScreen
import org.koin.dsl.module

internal val catalogModule =
    module {
        single<CatalogRepository> { ExposedCatalogRepository(get(), get()) }
        single { Browse(get()) }
        single { HomeScreen(get(), get(), get(), get(), get(), get()) }
        single { CatalogScreen(get(), get(), get(), get()) }
        single<ProductSales> { ExposedProductSales(get()) }
        single { BoughtThisMonth(get(), get()) }
        single { ProductScreen(get(), get(), get(), get(), get()) }
        single { DealsScreen(get(), get(), get()) }
        single { LineAnswers(get(), get(), get(), get()) }
    }
