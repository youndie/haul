package io.github.youndie.haul.feature.payment

import io.github.youndie.haul.feature.fulfilment.domain.FulfilmentPace
import io.github.youndie.haul.feature.payment.data.ExposedInstalments
import io.github.youndie.haul.feature.payment.data.ExposedPaymentSimulator
import io.github.youndie.haul.feature.payment.domain.HaulPayPlans
import io.github.youndie.haul.feature.payment.domain.InstalmentRepository
import io.github.youndie.haul.feature.payment.domain.PaymentProcessor
import org.koin.dsl.module

/**
 * The card processor the store simulates (research D4) — the order saga authorises, the fulfilment simulator
 * captures, the returns' simulator refunds — and Haul Pay's plans charged out of it (B-24), on the world's pace.
 */
internal val paymentModule =
    module {
        single<PaymentProcessor> { ExposedPaymentSimulator(get(), get()) }
        single<InstalmentRepository> { ExposedInstalments(get()) }
        single {
            val pace = get<FulfilmentPace>()
            HaulPayPlans(
                plans = get(),
                payments = get(),
                clock = get(),
                interval = pace.instalmentInterval,
                retry = pace.instalmentRetry,
            )
        }
    }
