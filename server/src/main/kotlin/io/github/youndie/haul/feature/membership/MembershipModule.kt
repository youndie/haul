package io.github.youndie.haul.feature.membership

import io.github.youndie.haul.feature.account.domain.Loyalty
import io.github.youndie.haul.feature.membership.data.ExposedMemberships
import io.github.youndie.haul.feature.membership.data.ExposedPointsLedger
import io.github.youndie.haul.feature.membership.domain.Memberships
import io.github.youndie.haul.feature.membership.domain.PlusCommands
import io.github.youndie.haul.feature.membership.domain.PointsLedger
import org.koin.dsl.module

/**
 * Haul Plus and points (B-23): the memberships and the points ledger, and the use case that is the
 * account's [Loyalty] — the source B-19 read the canvas's numbers from until now.
 */
internal val membershipModule =
    module {
        single<Memberships> { ExposedMemberships(get()) }
        single<PointsLedger> { ExposedPointsLedger(get()) }
        single { PlusCommands(memberships = get(), points = get(), orders = get(), clock = get()) }
        single<Loyalty> { get<PlusCommands>() }
    }
