package io.github.youndie.haul.feature.recommendations

import io.github.youndie.haul.feature.recommendations.data.ExposedPickSources
import io.github.youndie.haul.feature.recommendations.data.ExposedProductViews
import io.github.youndie.haul.feature.recommendations.domain.PickSources
import io.github.youndie.haul.feature.recommendations.domain.PickedForYou
import io.github.youndie.haul.feature.recommendations.domain.ProductViews
import io.github.youndie.haul.feature.recommendations.domain.RecordView
import io.github.youndie.haul.feature.recommendations.screen.PickedSection
import org.koin.dsl.module

/**
 * «Picked for you» (B-25): the views the product page records, the rule over them, and the block the home
 * page draws. No route of its own — the product page records, the home page reads.
 */
internal val recommendationsModule =
    module {
        single<ProductViews> { ExposedProductViews(get()) }
        single<PickSources> { ExposedPickSources(get()) }
        single { PickedForYou(get(), get(), get()) }
        single { RecordView(get(), get()) }
        single { PickedSection(get(), get(), get()) }
    }
