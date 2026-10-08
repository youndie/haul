package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.feature.catalog.data.ExposedCatalogRepository
import io.github.youndie.haul.feature.catalog.domain.Browse
import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.screen.CatalogScreen
import io.github.youndie.haul.feature.catalog.screen.HomeScreen
import io.github.youndie.haul.feature.catalog.screen.ProductScreen
import org.koin.dsl.module

internal val catalogModule =
    module {
        single<CatalogRepository> { ExposedCatalogRepository(get()) }
        single { Browse(get()) }
        single { HomeScreen(get(), get()) }
        single { CatalogScreen(get(), get(), get()) }
        single { ProductScreen(get(), get()) }
    }
