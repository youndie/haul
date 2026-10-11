package io.github.youndie.haul.feature.order.saga

import io.github.youndie.haul.ops.OrderLines
import io.github.youndie.haul.ops.OrderTrail
import io.github.youndie.petich.PetichAnnouncement
import io.github.youndie.petich.PetichAnnouncementContext
import io.github.youndie.petich.PetichDefinitionBuilder
import io.github.youndie.petich.PetichStep
import io.github.youndie.petich.PetichStepContext
import io.github.youndie.petich.recorded
import kotlin.coroutines.cancellation.CancellationException

/**
 * [step] under [key], each run a span and each outcome a line of the order's [trail] (B-109).
 *
 * Here and not in petich's `PetichTracer`, which the engine already has: a tracer's event names the saga,
 * and the saga's id is the customer's and their Idempotency-Key's, not the order's — only a member holds
 * the payload, so only a member can file a line under the order.
 */
internal fun PetichDefinitionBuilder<OrderPayload>.traced(
    trail: OrderTrail,
    key: String,
    step: PetichStep<OrderPayload>,
) = step(key, TracedStep(key, step, trail))

/** [announcement] under [key], its run a span and a line of the order's [trail], as [traced] does for a step. */
internal fun PetichDefinitionBuilder<OrderPayload>.tracedAnnouncement(
    trail: OrderTrail,
    key: String,
    announcement: PetichAnnouncement<OrderPayload>,
) = announce(key, TracedAnnouncement(key, announcement, trail))

/** A member's span: `saga reserve-stock`; its compensation's, `saga reserve-stock undo`. */
internal fun sagaSpan(key: String): String = "saga $key"

private const val UNDO = " undo"

/**
 * A step whose run says how it ended. A refusal is read off what the member recorded — every refusing member
 * records [Refused] before it rejects, and that record is the saga's own — so the step is not asked twice.
 */
private class TracedStep(
    private val key: String,
    private val step: PetichStep<OrderPayload>,
    private val trail: OrderTrail,
) : PetichStep<OrderPayload> {
    override suspend fun execute(
        ctx: PetichStepContext,
        payload: OrderPayload,
    ) {
        trail.span(sagaSpan(key)) { trail.recorded(key, payload) { step.execute(ctx, payload) } }
        val refused = ctx.recorded<Refused>()
        if (refused != null) {
            trail.record(
                payload.orderId,
                OrderLines.STEP_REFUSED,
                OrderLines.STEP to key,
                OrderLines.REASON to refused.reason,
            )
        } else {
            trail.record(payload.orderId, OrderLines.STEP_DONE, OrderLines.STEP to key)
        }
    }

    override suspend fun compensate(
        ctx: PetichStepContext,
        payload: OrderPayload,
    ) {
        trail.span(sagaSpan(key) + UNDO) { trail.recorded(key, payload) { step.compensate(ctx, payload) } }
        trail.record(payload.orderId, OrderLines.STEP_UNDONE, OrderLines.STEP to key)
    }
}

private class TracedAnnouncement(
    private val key: String,
    private val announcement: PetichAnnouncement<OrderPayload>,
    private val trail: OrderTrail,
) : PetichAnnouncement<OrderPayload> {
    override suspend fun announce(
        ctx: PetichAnnouncementContext,
        payload: OrderPayload,
    ) {
        trail.span(sagaSpan(key)) { trail.recorded(key, payload) { announcement.announce(ctx, payload) } }
        trail.record(payload.orderId, OrderLines.STEP_DONE, OrderLines.STEP to key)
    }
}

/**
 * [block], and a line when it throws — the throw itself carried on to the engine, which rolls the saga back.
 * A cancellation is not a member's failure (a timeout or the process stopping): it passes unwritten.
 */
private suspend fun OrderTrail.recorded(
    key: String,
    payload: OrderPayload,
    block: suspend () -> Unit,
) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        record(
            payload.orderId,
            OrderLines.STEP_THREW,
            OrderLines.STEP to key,
            OrderLines.REASON to (e::class.simpleName ?: "Exception"),
        )
        throw e
    }
}
