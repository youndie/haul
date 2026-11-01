package io.github.youndie.haul.feature.identity.domain

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.feature.checkout.AddressEntry
import io.github.youndie.haul.feature.checkout.domain.CheckoutRepository
import io.github.youndie.haul.feature.checkout.domain.PaymentMethod
import io.github.youndie.haul.feature.identity.DemoShopperTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration

/**
 * The provider's half of a demo shopper (B-162): a person made in the realm and signed in at the storefront's
 * client, and later turned off. shildik's is `ShildikDemoAccounts`; a test's answers what it is told.
 */
internal interface DemoAccounts {
    /**
     * Makes the person [id], named [name], with a password nobody is ever told, and signs them in the way the
     * browser's popup does; answers their tokens. Throws [DemoAccountFailure] when the provider refuses a step.
     */
    suspend fun create(
        id: String,
        name: String,
    ): DemoShopperTokens

    /**
     * Turns the person [id] off: no sign-in and no renewal of a token they hold, from now on. A person the provider
     * does not know is off already. Throws [DemoAccountFailure] when the provider refuses.
     */
    suspend fun disable(id: String)
}

/** A step of the provider's that refused or did not answer; [message] names the step and the status, never a secret. */
internal class DemoAccountFailure(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** The store's half of the demo shoppers: when each was made, and everything one leaves behind (B-162). */
internal interface DemoShopperRepository {
    /** How many demo shoppers made at [at] or later the store still holds. */
    suspend fun madeSince(at: OffsetDateTime): Int

    /** Records the customer [id] as a demo shopper made at [at]; the customer exists already. */
    suspend fun record(
        id: String,
        at: OffsetDateTime,
    )

    /** The demo shoppers made before [before] that hold no order still being placed: the sweep's, oldest first. */
    suspend fun due(before: OffsetDateTime): List<String>

    /**
     * Forgets the demo shopper [id] in one transaction, and everything that is theirs: orders — what they reserved
     * given back to the shelf and the windows, their payments and saga rows — cart, Saved list, views, searches,
     * addresses, checkout, points, their reviews, questions and votes with the product's counts moved back. `false`,
     * and nothing changed, when an order of theirs is still being placed or they are gone already.
     */
    suspend fun forget(id: String): Boolean
}

/**
 * Demo shoppers (B-162, research D5 «Decided in B-162»): a customer of the visitor's own, made in one press — a
 * person in the realm, signed in the way the browser signs in, so every route takes them as it takes anyone — with
 * an address and Haul Pay already chosen, so checkout is ready at once. Never one account for many visitors: each
 * press is a new person, whose tokens only the browser that pressed holds. After [LIFETIME] they are swept: turned
 * off at the provider first, then forgotten here, so a token one of them still holds is refused at both ends.
 *
 * At most [dailyLimit] are made in any [LIFETIME]; past it, `503` until the sweep makes room.
 */
internal class DemoShoppers(
    private val accounts: DemoAccounts,
    private val repository: DemoShopperRepository,
    private val customers: Customers,
    private val checkouts: CheckoutRepository,
    private val clock: StoreClock,
    private val dailyLimit: Int,
) {
    /** A new demo shopper, ready to check out; their tokens. [IdentityError.DemoFull] past the day's limit. */
    suspend fun make(): DemoShopperTokens {
        val now = clock.now().toOffsetDateTime()
        if (repository.madeSince(now - LIFETIME.toJavaDuration()) >= dailyLimit) throw IdentityError.DemoFull()
        val id = DEMO_PREFIX + UUID.randomUUID()
        val tokens =
            try {
                accounts.create(id, NAME)
            } catch (e: DemoAccountFailure) {
                log.warn("a demo shopper was not made: {}", e.message)
                throw IdentityError.DemoNotMade()
            }
        try {
            customers.signedIn(id, NAME, now)
            repository.record(id, now)
            checkouts.saveAddress(id, ADDRESS, editing = null, at = now)
            checkouts.save(id, checkouts.checkout(id).copy(payment = PaymentMethod.HaulPayPlan.id))
        } catch (e: Exception) {
            // The person exists at the provider and nobody here knows them, so no sweep would find them: off at once.
            withContext(NonCancellable) { turnOff(id) }
            throw e
        }
        return tokens
    }

    @Suppress(
        "ktlint:kapkan:swallowed-failure",
        "The failure being answered is the store's, already on its way to the caller; a person the provider kept on holds tokens nobody was given.",
    )
    private suspend fun turnOff(id: String) {
        try {
            accounts.disable(id)
        } catch (e: DemoAccountFailure) {
            log.warn("a demo shopper the store did not keep was not turned off either: {}", e.message)
        }
    }

    /**
     * Sweeps the demo shoppers made more than [LIFETIME] ago: each turned off at the provider, then forgotten. One the
     * provider did not turn off is left for the next sweep — forgotten here first, a token of theirs would still renew
     * there — and so is one whose order is still being placed. Answers how many were forgotten.
     */
    suspend fun sweep(): Int {
        val before = clock.now().toOffsetDateTime() - LIFETIME.toJavaDuration()
        return repository.due(before).count { id ->
            try {
                accounts.disable(id)
                repository.forget(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: DemoAccountFailure) {
                log.warn("a demo shopper was not turned off, swept next time: {}", e.message)
                false
            }
        }
    }

    companion object {
        /** How long a demo shopper lasts: a day, then they and everything they did are gone. */
        val LIFETIME = 1.days

        /**
         * The start of every demo shopper's id — the provider's `sub` and the customer's id. A token with such a `sub`
         * and no customer behind it is a swept demo shopper's, refused rather than made a new customer (`Callers`).
         */
        const val DEMO_PREFIX = "demo-"

        /** What the header greets them with: «Demo». */
        const val NAME = "Demo Shopper"

        /** The sample customers' street (research §6), so the delivery strip and the windows read as the canvas does. */
        val ADDRESS = AddressEntry("148 Wythe Avenue", "4F", "Brooklyn, NY", "11211", "", "")

        private val log = LoggerFactory.getLogger(DemoShoppers::class.java)
    }
}
