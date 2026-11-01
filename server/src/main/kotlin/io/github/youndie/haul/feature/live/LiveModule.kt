package io.github.youndie.haul.feature.live

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.feature.live.data.ExposedFeed
import io.github.youndie.haul.feature.live.domain.FeedRepository
import io.github.youndie.haul.feature.live.screen.LiveScreen
import org.koin.dsl.module

/**
 * «Live orders» (B-164): the feed's own reading of the orders, the page drawn in the store's zone, and its channel
 * over the order page's broadcaster and moves (`orderModule`). The application starts the channel.
 */
internal val liveModule =
    module {
        single<FeedRepository> { ExposedFeed(get()) }
        single {
            val clock = get<StoreClock>()
            LiveScreen { clock.now().zone }
        }
        // An explicit lambda: the pause and the capacity are defaulted parameters.
        single { LiveFeed(moves = get(), feed = get(), screen = get(), broadcaster = get()) }
    }
