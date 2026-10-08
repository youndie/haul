package io.github.youndie.haul.feature.identity

import io.github.youndie.haul.feature.identity.data.ExposedCustomers
import io.github.youndie.haul.feature.identity.data.ExposedGuests
import io.github.youndie.haul.feature.identity.domain.Customers
import io.github.youndie.haul.feature.identity.domain.Guests
import io.github.youndie.haul.shell.Viewers
import org.koin.dsl.module

internal val identityModule =
    module {
        single<Guests> { ExposedGuests(get()) }
        single<Customers> { ExposedCustomers(get()) }
        single { Callers(get(), get(), get()) }
        single { Viewers(get(), get(), get()) }
    }
