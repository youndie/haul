package io.github.youndie.haul.feature.saved

import io.github.youndie.haul.feature.saved.data.ExposedSavedItems
import io.github.youndie.haul.feature.saved.domain.SavedCommands
import io.github.youndie.haul.feature.saved.domain.SavedListing
import io.github.youndie.haul.feature.saved.domain.SavedRepository
import io.github.youndie.haul.feature.saved.screen.SavedScreen
import org.koin.dsl.module

/**
 * The Saved list's graph (B-20): its storage, its commands, the listing the account reads its counts
 * through (`SavedLists`, bound in the account's module), and the list's part of the account's page.
 */
internal val savedModule =
    module {
        single<SavedRepository> { ExposedSavedItems(get()) }
        single { SavedCommands(get(), get(), get(), get()) }
        single { SavedListing(get(), get()) }
        single { SavedScreen(get(), get(), get()) }
    }
