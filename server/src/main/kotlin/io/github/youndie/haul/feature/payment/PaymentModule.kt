package io.github.youndie.haul.feature.payment

import io.github.youndie.haul.feature.payment.data.ExposedPaymentSimulator
import io.github.youndie.haul.feature.payment.domain.PaymentProcessor
import org.koin.dsl.module

/** The card processor the store simulates (research D4); the order saga is its one caller. */
internal val paymentModule =
    module {
        single<PaymentProcessor> { ExposedPaymentSimulator(get(), get()) }
    }
