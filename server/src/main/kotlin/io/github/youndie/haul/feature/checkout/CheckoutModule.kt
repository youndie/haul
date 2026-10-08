package io.github.youndie.haul.feature.checkout

import io.github.youndie.haul.feature.checkout.data.ExposedCheckoutRepository
import io.github.youndie.haul.feature.checkout.data.ExposedDeliverySlots
import io.github.youndie.haul.feature.checkout.domain.CheckoutCommands
import io.github.youndie.haul.feature.checkout.domain.CheckoutRepository
import io.github.youndie.haul.feature.checkout.domain.DeliverySlots
import io.github.youndie.haul.feature.checkout.screen.CheckoutScreen
import org.koin.dsl.module

internal val checkoutModule =
    module {
        single<CheckoutRepository> { ExposedCheckoutRepository(get()) }
        // An explicit lambda: the capacity is a defaulted parameter, which Koin would otherwise ask for.
        single<DeliverySlots> { ExposedDeliverySlots(get()) }
        single { CheckoutCommands(get(), get(), get(), get(), get()) }
        single { CheckoutScreen(get()) }
    }
