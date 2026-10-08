package io.github.youndie.haul.feature.returns

import io.github.youndie.haul.feature.returns.data.ExposedReturns
import io.github.youndie.haul.feature.returns.domain.RequestReturn
import io.github.youndie.haul.feature.returns.domain.ReturnRepository
import io.github.youndie.haul.feature.returns.domain.ReturnSimulator
import org.koin.dsl.module

/**
 * Returns and refunds (B-21): their storage, the request, and the simulated courier that collects and refunds
 * them. The clock is the saga's and the fulfilment's (`PetichClock`), the pace the fulfilment's.
 */
internal val returnsModule =
    module {
        single<ReturnRepository> { ExposedReturns(get()) }
        single { RequestReturn(orders = get(), shipments = get(), returns = get(), clock = get()) }
        single { ReturnSimulator(returns = get(), orders = get(), payments = get(), clock = get(), pace = get()) }
    }
