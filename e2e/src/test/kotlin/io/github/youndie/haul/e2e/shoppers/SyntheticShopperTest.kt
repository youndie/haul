package io.github.youndie.haul.e2e.shoppers

import io.github.youndie.haul.e2e.ComposedStack
import io.github.youndie.haul.e2e.Storefront
import io.github.youndie.haul.e2e.deeplink
import io.github.youndie.haul.feature.identity.SignInSettings
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.ui.AccountBody
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.HistoryStatusKind
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * The synthetic shoppers (B-31) against the composed stack the whole path runs on: two walks of one
 * customer, started together, each buying a product, waiting out the fast clock, returning it and seeing
 * the refund. Afterwards the customer's own history — read with a token of its own, not the walks' — holds
 * two orders, both returned: two placement sagas run to the end, and the fulfilment and refund simulators
 * moved both.
 *
 * Not shown here: that the walks' per-person cart lock is needed. Two walks started together without it
 * still passed once (B-31's findings). It stays: the cart is one per customer, and nothing promises that two
 * merges and two placements interleaved still come out as two orders of one line each.
 */
class SyntheticShopperTest {
    @Test
    fun `two overlapping walks of one customer each place receive and return an order`() {
        val id = "shopper-${UUID.randomUUID()}"
        val login = ComposedStack.shildikAdmin.person(id, "Sid Synthetic", PASSWORD)
        val config =
            ShoppersConfig(
                origin = ComposedStack.origin,
                redirect = ComposedStack.REDIRECT,
                people = listOf(login),
                password = Password(PASSWORD),
                interval = Duration.ZERO,
                poll = 500.milliseconds,
                wait = 2.minutes,
                inFlight = 2,
                walks = 2,
            )
        assertTrue(Shoppers(config).run(), "a walk failed; its line above names the step\n${ComposedStack.serverLog()}")

        val shop = Storefront(ComposedStack.origin)
        val settings =
            haulWireJson.decodeFromString(
                SignInSettings.serializer(),
                shop.send("GET", "/api/v1/sign-in").body,
            )
        shop.token = ComposedStack.shildikAdmin.accessToken(settings, ComposedStack.REDIRECT, login, PASSWORD)
        val orders =
            shop
                .page("/")
                .one(HaulHeader.serializer())
                .orders
                .deeplink("the header's «Orders»")
        val rows =
            shop
                .page(orders)
                .one(AccountBody.serializer())
                .history
                ?.rows
                .orEmpty()
        assertEquals(2, rows.size, "the walks left ${rows.map { it.status }}")
        assertTrue(rows.all { it.statusKind == HistoryStatusKind.Returned }, "${rows.map { it.status }}")
    }

    private companion object {
        const val PASSWORD = "shoppers-horse-battery-staple"
    }
}
