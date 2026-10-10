package io.github.youndie.haul.e2e.shoppers

import io.github.youndie.haul.e2e.ComposedStack
import io.github.youndie.haul.e2e.SignIn
import io.github.youndie.haul.e2e.Storefront
import io.github.youndie.haul.e2e.Transport
import io.github.youndie.haul.e2e.deeplink
import io.github.youndie.haul.feature.identity.SignInSettings
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.ui.AccountBody
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.HistoryStatusKind
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
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

    /**
     * The stand's failure (B-31): at its speed (288) a walk waits about ten minutes for the delivery, and the
     * access token it placed the order with lives five, so its next read of the order page is answered `401`
     * and the walk stopped at «wait for the order to arrive». Here the clock is quick, so the token's age is
     * played instead: from the placement on, the bearer the order was placed with is sent spoiled, and the
     * server refuses it the way it refuses an expired one — «no valid token came with the request». The walk
     * must renew its sign-in and still reach the refund.
     */
    @Test
    fun `a walk whose bearer expires while it waits renews it and reaches the refund`() {
        val id = "shopper-${UUID.randomUUID()}"
        val login = ComposedStack.shildikAdmin.person(id, "Sid Synthetic", PASSWORD)
        val clock = ComposedStack.shildikAdmin.person("clock-${UUID.randomUUID()}", "Cleo Clock", PASSWORD)
        val settings =
            haulWireJson.decodeFromString(
                SignInSettings.serializer(),
                Storefront(ComposedStack.origin).send("GET", "/api/v1/sign-in").body,
            )
        val expiring =
            BearerExpiresAtPlacement(
                issuingSecond = {
                    issuedAt(ComposedStack.shildikAdmin.accessToken(settings, ComposedStack.REDIRECT, clock, PASSWORD))
                },
            )
        val walk =
            Walk(
                number = 1,
                origin = ComposedStack.origin,
                signIn = SignIn(ComposedStack.REDIRECT),
                login = login,
                password = Password(PASSWORD),
                cart = ReentrantLock(),
                poll = 500.milliseconds,
                wait = 2.minutes,
                transport = expiring,
            )
        val walked =
            try {
                walk.take()
            } catch (e: WalkFailed) {
                throw AssertionError("the walk stopped at ${e.message}\n${ComposedStack.serverLog()}", e)
            }
        assertTrue(expiring.refused > 0, "the spoiled bearer was never sent, so nothing was shown")
        assertEquals(1, walked.renewals, "one refusal, one renewal")
    }

    /**
     * Between a walk and the server: once a placement is answered `202`, the bearer it carried is spoiled on every
     * later request — a token that has outlived its five minutes, without waiting them out.
     *
     * **The spoiled bearer is told by its text, so the renewed one must differ from it**, and within the second
     * it was issued in it does not: shildik's access token carries no `jti`, its `iat` is in whole seconds and
     * RS256 signs deterministically, so a refresh in the same second hands back the very same token, which
     * would be spoiled again. In CI the walk went from its sign-in to its first read of the order in under a
     * second and failed that way; on the build machine it crossed a second and passed (B-31, iteration 3). A
     * real expiry comes five minutes after the issue, so there the renewed token is always a new one; here the
     * placement waits until shildik issues tokens in a later second than the bearer's ([issuingSecond]: the
     * `iat` of a token it issues now — shildik's clock, read off the wire, not this machine's) — at most one.
     */
    private class BearerExpiresAtPlacement(
        private val issuingSecond: () -> Long,
    ) : Transport {
        @Volatile private var expired: String? = null

        @Volatile var refused = 0
            private set

        override fun send(request: HttpRequest): HttpResponse<String> {
            val bearer = request.headers().firstValue(AUTHORIZATION).orElse(null)
            val sent =
                if (bearer != null && bearer == expired) {
                    HttpRequest
                        .newBuilder(request) { name, _ -> !name.equals(AUTHORIZATION, ignoreCase = true) }
                        .header(AUTHORIZATION, bearer.dropLast(SPOILED.length) + SPOILED)
                        .build()
                } else {
                    request
                }
            val response = Transport.DIRECT.send(sent)
            if (sent !== request && response.statusCode() == 401) refused += 1
            if (request.method() == "POST" && response.statusCode() == 202 && expired == null && bearer != null) {
                expired = bearer
                val issued = issuedAt(bearer.substringAfter(' '))
                while (issuingSecond() <= issued) Thread.sleep(TICK_MILLIS)
            }
            return response
        }

        private companion object {
            const val AUTHORIZATION = "Authorization"
            const val SPOILED = "AAAA"
            const val TICK_MILLIS = 100L
        }
    }

    private companion object {
        const val PASSWORD = "shoppers-horse-battery-staple"

        /** The second [token] was issued in: its `iat`, read from the payload without checking the signature. */
        fun issuedAt(token: String): Long {
            val payload = String(Base64.getUrlDecoder().decode(token.split('.')[1]))
            return haulWireJson
                .parseToJsonElement(payload)
                .jsonObject
                .getValue("iat")
                .jsonPrimitive.long
        }
    }
}
