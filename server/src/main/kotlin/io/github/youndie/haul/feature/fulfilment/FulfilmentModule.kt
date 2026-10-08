package io.github.youndie.haul.feature.fulfilment

import io.github.youndie.haul.feature.fulfilment.data.ExposedFulfilment
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentRepository
import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentSimulator
import io.github.youndie.haul.feature.fulfilment.domain.OrderTracking
import org.koin.dsl.module

/**
 * The simulated world after placement (research D4): the shipments' storage, the simulator that moves them
 * and captures each one's share, and the order's state as its page reads it. The pace comes from the
 * application ([FulfilmentSettings]), the clock is the saga's.
 */
internal val fulfilmentModule =
    module {
        single<FulfilmentRepository> { ExposedFulfilment(get()) }
        single {
            FulfilmentSimulator(
                shipments = get(),
                orders = get(),
                payments = get(),
                plans = get(),
                points = get(),
                clock = get(),
                pace = get(),
            )
        }
        single {
            OrderTracking(orders = get(), shipments = get(), payments = get(), returns = get(), plans = get())
        }
    }
