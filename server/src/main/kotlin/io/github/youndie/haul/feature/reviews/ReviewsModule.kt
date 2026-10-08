package io.github.youndie.haul.feature.reviews

import io.github.youndie.haul.feature.reviews.data.ExposedReviews
import io.github.youndie.haul.feature.reviews.domain.ReviewCommands
import io.github.youndie.haul.feature.reviews.domain.ReviewRepository
import io.github.youndie.haul.feature.reviews.screen.ReviewTabs
import org.koin.dsl.module

internal val reviewsModule =
    module {
        single<ReviewRepository> { ExposedReviews(get()) }
        single { ReviewCommands(get(), get(), get()) }
        single { ReviewTabs(get()) }
    }
