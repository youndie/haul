package io.github.youndie.haul.feature.account

import io.github.youndie.haul.feature.account.domain.Loyalty
import io.github.youndie.haul.feature.account.domain.SavedLists
import io.github.youndie.haul.feature.account.screen.AccountScreen
import io.github.youndie.haul.feature.saved.domain.SavedListing
import io.github.youndie.haul.seed.SampleLoyalty
import org.koin.dsl.module

/**
 * The account's graph (B-19): the screen over the orders, the Saved list's counts — read from the list
 * itself (B-20, `SavedListing`) — and the points and the membership (B-23), which have no store yet and
 * are bound to the canvas's numbers for the sample customers until that item binds its own.
 */
internal val accountModule =
    module {
        single<Loyalty> { SampleLoyalty }
        single<SavedLists> { get<SavedListing>() }
        single {
            AccountScreen(
                orders = get(),
                tracking = get(),
                orderScreen = get(),
                catalog = get(),
                loyalty = get(),
                saved = get(),
                savedScreen = get(),
            )
        }
    }
