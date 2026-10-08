package io.github.youndie.haul.feature.search

import io.github.youndie.haul.feature.search.data.ExposedRecentSearches
import io.github.youndie.haul.feature.search.data.PostgresSearchRepository
import io.github.youndie.haul.feature.search.domain.RecentSearches
import io.github.youndie.haul.feature.search.domain.SearchRepository
import io.github.youndie.haul.feature.search.screen.SearchScreen
import org.koin.dsl.module

internal val searchModule =
    module {
        single<SearchRepository> { PostgresSearchRepository(get()) }
        single<RecentSearches> { ExposedRecentSearches(get()) }
        single { SearchScreen(get(), get(), get(), get(), get(), get()) }
    }
