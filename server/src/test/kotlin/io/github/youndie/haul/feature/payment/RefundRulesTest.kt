package io.github.youndie.haul.feature.payment

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.checkout.domain.PaymentMethod
import io.github.youndie.haul.feature.payment.data.ExposedPaymentSimulator
import io.github.youndie.haul.feature.payment.domain.Authorisation
import io.github.youndie.haul.feature.payment.domain.Capture
import io.github.youndie.haul.feature.payment.domain.Refund
import io.github.youndie.haul.feature.payment.domain.RefundOutcome
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.PostgresHarness
import io.github.youndie.haul.testing.TestClock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The payment simulator's refund rules (B-21), against its ledger in PostgreSQL, as [CaptureRulesTest] holds
 * the captures: a refund gives back part of what an order was charged, named by its key, and the refunds
 * never add up to more than the captures.
 */
class RefundRulesTest {
    private val at = TestClock.START

    private fun simulator(block: suspend (ExposedPaymentSimulator) -> Unit) =
        PostgresHarness.freshDatabase().use { dataSource ->
            runBlocking { block(ExposedPaymentSimulator(Databases.connect(dataSource), CANVAS_NOW)) }
        }

    /** [cents] of [order] authorised by card and [captured] of it taken, one capture per amount. */
    private suspend fun ExposedPaymentSimulator.charged(
        order: String,
        cents: Int,
        vararg captured: Int,
    ) {
        authorise("auth-$order", Authorisation(order, PaymentMethod.Card, cents))
        captured.forEachIndexed { index, part -> capture("$order-${index + 1}", Capture(order, part, at)) }
    }

    /**
     * A refund that would give back more than was charged — a refund of a line not yet shipped, or a second
     * return of the same money — is refused with what is left, and gives nothing; the money held but not yet
     * captured is not the refund's to give.
     */
    @Test
    fun `refunds never add up to more than the captures`() =
        simulator { payments ->
            payments.charged("HL-1", 1_000, 600)

            assertEquals(RefundOutcome.Exceeds(600), payments.refund("refund:a", Refund("HL-1", 700, at)))
            assertEquals(RefundOutcome.Refunded(400), payments.refund("refund:b", Refund("HL-1", 400, at)))
            assertEquals(RefundOutcome.Exceeds(200), payments.refund("refund:c", Refund("HL-1", 300, at)))
            assertEquals(RefundOutcome.Refunded(200), payments.refund("refund:c", Refund("HL-1", 200, at)))
            assertEquals(mapOf("refund:b" to 400, "refund:c" to 200), payments.refunded("HL-1"))
        }

    /** What two shipments' captures took is one pool: a refund may be larger than either capture. */
    @Test
    fun `a refund is given back out of every capture of its order`() =
        simulator { payments ->
            payments.charged("HL-2", 1_000, 600, 400)

            assertEquals(RefundOutcome.Refunded(900), payments.refund("refund:HL-2", Refund("HL-2", 900, at)))
            assertEquals(mapOf("refund:HL-2" to 900), payments.refunded("HL-2"))
        }

    /** The same key asked again — a pass re-run after a restart — answers what it gave and gives nothing more. */
    @Test
    fun `a key refunded once answers what it gave and gives nothing more`() =
        simulator { payments ->
            payments.charged("HL-3", 1_000, 1_000)

            payments.refund("refund:HL-3", Refund("HL-3", 300, at))
            assertEquals(RefundOutcome.Refunded(300), payments.refund("refund:HL-3", Refund("HL-3", 500, at)))
            assertEquals(mapOf("refund:HL-3" to 300), payments.refunded("HL-3"))
        }

    /** Nothing is given back of an order never charged: only authorised, declined, or not there at all. */
    @Test
    fun `nothing is refunded without a capture`() =
        simulator { payments ->
            payments.charged("HL-4", 1_000)
            payments.authorise("auth-HL-5", Authorisation("HL-5", PaymentMethod.TestCard, 1_000))

            listOf("HL-4", "HL-5", "HL-6").forEach { order ->
                assertEquals(RefundOutcome.NotCaptured, payments.refund("refund:$order", Refund(order, 100, at)), order)
                assertEquals(emptyMap(), payments.refunded(order), order)
            }
        }
}
