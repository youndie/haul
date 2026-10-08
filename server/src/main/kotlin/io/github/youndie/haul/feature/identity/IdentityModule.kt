package io.github.youndie.haul.feature.identity

import io.github.youndie.haul.feature.identity.data.ExposedGuests
import io.github.youndie.haul.feature.identity.domain.Guests
import org.koin.dsl.module

internal val identityModule =
    module {
        single<Guests> { ExposedGuests(get()) }
    }
