package io.github.youndie.haul.feature.cart

import io.github.youndie.haul.feature.cart.data.ExposedCartRepository
import io.github.youndie.haul.feature.cart.domain.CartCommands
import io.github.youndie.haul.feature.cart.domain.CartRepository
import io.github.youndie.haul.feature.cart.screen.CartScreen
import org.koin.dsl.module

internal val cartModule =
    module {
        single<CartRepository> { ExposedCartRepository(get()) }
        single { CartCommands(get(), get(), get()) }
        single { CartScreen(get(), get(), get(), get(), get()) }
    }
