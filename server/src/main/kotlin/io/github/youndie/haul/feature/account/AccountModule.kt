package io.github.youndie.haul.feature.account

import io.github.youndie.haul.feature.account.domain.Loyalty
import io.github.youndie.haul.feature.account.domain.SavedLists
import io.github.youndie.haul.feature.account.screen.AccountScreen
import io.github.youndie.haul.feature.saved.domain.SavedListing
import org.koin.dsl.module

/**
 * The account's graph (B-19): the screen over the orders and the two sources it reads — the Saved list's
 * counts, read from the list itself (B-20, `SavedListing`), and the points and the membership ([Loyalty]),
 * bound by feature-membership's module to its ledger and memberships (B-23).
 */
internal val accountModule =
    module {
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
