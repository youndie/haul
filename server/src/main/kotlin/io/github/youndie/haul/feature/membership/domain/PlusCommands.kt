package io.github.youndie.haul.feature.membership.domain

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.feature.account.domain.Loyalty
import io.github.youndie.haul.feature.account.domain.Membership
import io.github.youndie.haul.feature.account.domain.Standing
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.identity.domain.Customer
import io.github.youndie.haul.feature.order.domain.OrderRepository
import io.github.youndie.haul.feature.order.domain.OrderStatus
import java.time.Instant
import java.time.LocalDate

/**
 * Haul Plus and points (feature-membership): the trial, and a customer's standing — their points balance
 * from the ledger, their membership with what it saved on delivery this year and when it renews. It is
 * the account's [Loyalty] (B-19's port) and the home page's Plus block's source.
 *
 * Dates are the store's ([DeliveryCalendar.STORE]): a trial started on Oct 7 in New York is free until
 * Nov 6 there, wherever the server runs.
 */
internal class PlusCommands(
    private val memberships: Memberships,
    private val points: PointsLedger,
    private val orders: OrderRepository,
    private val clock: StoreClock,
) : Loyalty {
    /**
     * Starts [customer]'s 30-day trial today: free delivery and double points from the next quote on, then
     * «renewing» at $4.99 a month with nothing charged. A member — on a trial or paying — is refused
     * ([MembershipError.AlreadyMember]), whoever started first when two requests race.
     */
    suspend fun startTrial(customer: Customer): PlusMembership {
        if (customer.plus) throw MembershipError.AlreadyMember()
        val now = clock.now()
        val trial = PlusMembership.trial(customer.id, now.toOffsetDateTime(), today())
        if (!memberships.start(trial)) throw MembershipError.AlreadyMember()
        return trial
    }

    /**
     * [customer]'s points and, for a member, their membership: the year it began, the delivery fees it
     * waived on their orders placed this year — an order cancelled saved nothing — with what was carried
     * in from before the store kept orders, and its next renewal. A member flagged before memberships were
     * kept has no row: their year is the one they joined in, and no renewal is known.
     */
    override suspend fun standing(customer: Customer): Standing {
        val balance = points.balance(customer.id)
        if (!customer.plus) return Standing(balance, null)
        val today = today()
        val membership = memberships.membership(customer.id)
        val saved =
            orders
                .orders(customer.id)
                .filter { it.status == OrderStatus.Placed && local(it.placed.placedAt.toInstant()).year == today.year }
                .sumOf { it.placed.deliveryWaivedCents } + (membership?.carriedSavings(today.year) ?: 0)
        return Standing(
            balance,
            Membership(
                since = membership?.startedAt?.let { local(it.toInstant()).year } ?: customer.joined.year,
                savedCents = saved,
                renews = membership?.renews(today),
            ),
        )
    }

    private fun today(): LocalDate = clock.now().withZoneSameInstant(DeliveryCalendar.STORE).toLocalDate()

    private fun local(at: Instant): LocalDate = at.atZone(DeliveryCalendar.STORE).toLocalDate()
}
