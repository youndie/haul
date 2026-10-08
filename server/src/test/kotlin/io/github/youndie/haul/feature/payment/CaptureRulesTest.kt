package io.github.youndie.haul.feature.payment

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.checkout.domain.PaymentMethod
import io.github.youndie.haul.feature.payment.data.ExposedPaymentSimulator
import io.github.youndie.haul.feature.payment.domain.Authorisation
import io.github.youndie.haul.feature.payment.domain.Capture
import io.github.youndie.haul.feature.payment.domain.CaptureOutcome
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.PostgresHarness
import io.github.youndie.haul.testing.TestClock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The payment simulator's capture rules (B-17), against its ledger in PostgreSQL: a capture is a part of
 * an authorisation, named by its key, and the parts never add up to more than the whole.
 */
class CaptureRulesTest {
    private val at = TestClock.START

    private fun simulator(block: suspend (ExposedPaymentSimulator) -> Unit) =
        PostgresHarness.freshDatabase().use { dataSource ->
            runBlocking { block(ExposedPaymentSimulator(Databases.connect(dataSource), CANVAS_NOW)) }
        }

    private suspend fun ExposedPaymentSimulator.authorised(
        order: String,
        cents: Int,
        method: PaymentMethod = PaymentMethod.Card,
    ): String = "auth-$order".also { authorise(it, Authorisation(order, method, cents)) }

    /** A shipment whose share no longer fits — a bug in the shares, or a second order's — is refused, not overdrawn. */
    @Test
    fun `captures never add up to more than the authorisation`() =
        simulator { payments ->
            payments.authorised("HL-1", 1_000)

            assertEquals(CaptureOutcome.Captured(600), payments.capture("HL-1-1", Capture("HL-1", 600, at)))
            assertEquals(CaptureOutcome.Exceeds(400), payments.capture("HL-1-2", Capture("HL-1", 500, at)))
            assertEquals(CaptureOutcome.Captured(400), payments.capture("HL-1-2", Capture("HL-1", 400, at)))
            assertEquals(mapOf("HL-1-1" to 600, "HL-1-2" to 400), payments.captured("HL-1"))
        }

    /** The same key asked again — a pass re-run after a restart — answers what it took and takes nothing more. */
    @Test
    fun `a key captured once answers what it took and takes nothing more`() =
        simulator { payments ->
            payments.authorised("HL-2", 1_000)

            payments.capture("HL-2-1", Capture("HL-2", 600, at))
            assertEquals(CaptureOutcome.Captured(600), payments.capture("HL-2-1", Capture("HL-2", 300, at)))
            assertEquals(mapOf("HL-2-1" to 600), payments.captured("HL-2"))
        }

    /** Money is taken only out of a hold: a declined card, a voided hold or no hold at all captures nothing. */
    @Test
    fun `nothing is captured without an authorisation`() =
        simulator { payments ->
            payments.authorised("HL-3", 1_000, PaymentMethod.TestCard)
            val voided = payments.authorised("HL-4", 1_000)
            assertTrue(payments.void(voided))

            listOf("HL-3", "HL-4", "HL-5").forEach { order ->
                assertEquals(CaptureOutcome.NotAuthorised, payments.capture("$order-1", Capture(order, 100, at)), order)
                assertEquals(emptyMap(), payments.captured(order))
            }
        }

    /** A hold part of which was taken is not voided: the money left the card, and only a refund brings it back. */
    @Test
    fun `an authorisation partly captured is not voided`() =
        simulator { payments ->
            val key = payments.authorised("HL-6", 1_000)
            payments.capture("HL-6-1", Capture("HL-6", 600, at))

            assertFalse(payments.void(key))
            assertEquals(CaptureOutcome.Captured(400), payments.capture("HL-6-2", Capture("HL-6", 400, at)))
        }
}
