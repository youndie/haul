package io.github.youndie.haul.feature.account

import io.github.youndie.haul.feature.account.domain.Loyalty
import io.github.youndie.haul.feature.account.domain.SavedLists
import io.github.youndie.haul.feature.account.screen.AccountScreen
import io.github.youndie.haul.seed.SampleLoyalty
import io.github.youndie.haul.seed.SampleSavedLists
import org.koin.dsl.module

/**
 * The account's graph (B-19): the screen over the orders, and the two sources it reads that have no store
 * yet — the points and the membership (B-23) and the Saved list's counts (B-20) — bound to the canvas's
 * numbers for the sample customers until those items bind their own.
 */
internal val accountModule =
    module {
        single<Loyalty> { SampleLoyalty }
        single<SavedLists> { SampleSavedLists }
        single {
            AccountScreen(
                orders = get(),
                tracking = get(),
                orderScreen = get(),
                catalog = get(),
                loyalty = get(),
                saved = get(),
            )
        }
    }
