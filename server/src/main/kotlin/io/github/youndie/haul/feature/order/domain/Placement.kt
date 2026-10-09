package io.github.youndie.haul.feature.order.domain

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.checkout.PlaceOrderRequest
import io.github.youndie.haul.feature.checkout.domain.CheckoutCommands
import io.github.youndie.haul.feature.checkout.domain.CheckoutError
import io.github.youndie.haul.feature.checkout.domain.Quote
import io.github.youndie.haul.feature.order.saga.ORDER_SAGA
import io.github.youndie.haul.feature.order.saga.OrderPayload
import io.github.youndie.haul.feature.order.saga.Refused
import io.github.youndie.petich.Petich
import io.github.youndie.petich.PetichEngine
import io.github.youndie.petich.PetichRepository
import io.github.youndie.petich.PetichStatus
import io.github.youndie.petich.idempotency.IdempotencyCheck
import io.github.youndie.petich.idempotency.IdempotencyGuard
import io.github.youndie.petich.idempotency.IdempotencyRepository
import io.github.youndie.petich.idempotency.RequestFingerprint
import io.github.youndie.petich.isTerminal
import java.security.MessageDigest

/**
 * Placement (feature-checkout, endpoint-checkout): the quote the shopper saw, placed once per key.
 *
 * The key is the customer's own: the saga's id is derived from the customer and the key, so one
 * customer's key never names another's order. A key whose saga exists is answered from that saga — the
 * same order, whatever the cart holds now — after petich-idempotency checks that it arrives with the
 * request it was first sent with; a new key places the quote, computed again by checkout
 * (`CheckoutCommands.quote`), only when its fingerprint is the one the shopper saw and the checkout does
 * not hold «Place order» (`CheckoutState.placeable`: `409 checkout_held` for a refused address form by
 * courier). A refusal before the saga spends no key, so the same request under the same key places once
 * the hold is lifted.
 *
 * The saga runs inside the request: every member is in-process, so the answer can say what happened —
 * a window that filled or stock that ran out is the shopper's to fix on the checkout (`409`), anything
 * else sends them to the order. Every step is written to the saga's row as it completes, so a process
 * that dies mid-way leaves a saga the sweeper carries on (research D4).
 */
internal class Placement(
    private val checkout: CheckoutCommands,
    private val orders: OrderRepository,
    private val engine: PetichEngine,
    private val sagas: PetichRepository,
    private val keys: IdempotencyRepository,
    private val clock: StoreClock,
) {
    /** The order [owner]'s placement under [key] went to: placed, cancelled because the card was declined, or still placing. */
    suspend fun place(
        owner: CartOwner.Customer,
        key: String?,
        request: PlaceOrderRequest,
    ): String {
        val idempotencyKey = key?.trim()?.takeIf { it.isNotEmpty() } ?: throw OrderError.KeyMissing()
        if (idempotencyKey.length > MAX_KEY_LENGTH) {
            throw OrderError.Invalid("Idempotency-Key", "An Idempotency-Key is at most $MAX_KEY_LENGTH characters")
        }
        val sagaId = sagaId(owner.id, idempotencyKey)
        val fingerprint = RequestFingerprint.of(request.quote)

        sagas.findById(sagaId)?.let { existing ->
            guard(sagaId, fingerprint)
            // Carried on if it is still running — this process's pass, or one a dead process left behind.
            if (!existing.status.isTerminal()) engine.process(existing)
            return answer(sagaId)
        }

        val state = checkout.state(owner)
        // The window the shopper chose filled since the page was drawn (`Checkout_PlaceError`): the tree
        // they go back to has it cleared and says so.
        if (state.slotFilled) throw CheckoutError.SlotUnavailable()
        val quote = state.quote
        if (quote.fingerprint != request.quote) throw OrderError.QuoteChanged()
        // The button's own rule (B-39): a refused address form leaves the quote complete with the address
        // before it — same fingerprint — so a client that ignores the held button, or a second tab, would
        // otherwise place to the address the shopper is changing.
        if (!state.placeable) {
            throw if (quote.complete) {
                OrderError.CheckoutHeld()
            } else {
                OrderError.Invalid("quote", "Choose where and when to receive the order first")
            }
        }

        // Claimed only by a placement that got this far: a key refused above was never used for an order.
        guard(sagaId, fingerprint)
        val payload = payload(owner, quote, orders.nextId())
        engine.process(Petich(id = sagaId, type = ORDER_SAGA, status = PetichStatus.DRAFT, payload = payload))
        return answer(sagaId)
    }

    private suspend fun guard(
        sagaId: String,
        fingerprint: String,
    ) {
        if (IdempotencyGuard.check(keys, sagaId, fingerprint) == IdempotencyCheck.FingerprintMismatch) {
            throw OrderError.KeyReused()
        }
    }

    /**
     * What the saga under [sagaId] answers, read from its row — so a replay answers what the first
     * request did. The order is in its payload: two requests racing under one new key both run the saga
     * the first one stored, and the second's own order number is never used.
     */
    private suspend fun answer(sagaId: String): String {
        val saga = sagas.findById(sagaId) ?: error("the saga $sagaId was never stored")
        val orderId = (saga.payload as OrderPayload).orderId
        return when (saga.status) {
            PetichStatus.REJECTED -> {
                when (
                    saga.stepRecords.values
                        .filterIsInstance<Refused>()
                        .firstOrNull()
                        ?.reason
                ) {
                    Refused.SLOT_UNAVAILABLE -> throw CheckoutError.SlotUnavailable()

                    Refused.OUT_OF_STOCK -> throw OrderError.OutOfStock()

                    // The balance was spent by another order between the quote and the saga: the quote
                    // the shopper saw is no longer the one they would get.
                    Refused.POINTS_SHORT -> throw OrderError.QuoteChanged()

                    // A declined card: the order exists, cancelled for that reason, and the shopper is shown it.
                    else -> orderId
                }
            }

            PetichStatus.FAILED, PetichStatus.COMPENSATION_FAILED -> {
                error("placing $orderId failed and was undone (saga $sagaId, ${saga.status})")
            }

            else -> {
                orderId
            }
        }
    }

    private fun payload(
        owner: CartOwner.Customer,
        quote: Quote,
        orderId: String,
    ): OrderPayload =
        OrderPayload(
            orderId = orderId,
            customerId = owner.id,
            plus = owner.plus,
            method = quote.method,
            addressId = quote.address?.id,
            address = quote.address?.entry(),
            pointId = quote.point?.id,
            slotId = quote.slot?.id,
            payment = quote.payment.id,
            promoCode = quote.promo?.code,
            itemsCents = quote.totals.itemsCents,
            discountCents = quote.totals.discountCents,
            deliveryCents = quote.totals.deliveryCents,
            totalCents = quote.totalCents,
            points = quote.points,
            pointsRedeemed = quote.pointsRedeemed,
            deliveryWaivedCents = quote.totals.deliveryWaivedCents,
            placedAt = clock.now().toOffsetDateTime().toString(),
            lines =
                quote.lines.map {
                    OrderPayload.Line(
                        skuId = it.sku.id,
                        sellerId = it.item.product.sellerId,
                        // The listing name it is bought under, kept by the order line (B-45).
                        title = it.item.product.listingName,
                        quantity = it.stored.quantity,
                        priceCents = it.priceCents,
                        listCents = it.listCents,
                    )
                },
        )

    companion object {
        const val MAX_KEY_LENGTH = 128

        /** The saga's id: a customer's key, hashed to a fixed length that fits petich's 255 characters. */
        fun sagaId(
            customerId: String,
            key: String,
        ): String {
            val digest = MessageDigest.getInstance("SHA-256").digest("$customerId\n$key".toByteArray())
            return "order-" + digest.take(SAGA_ID_BYTES).joinToString("") { "%02x".format(it) }
        }

        private const val SAGA_ID_BYTES = 20
    }
}
