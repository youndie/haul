package io.github.youndie.haul.feature.fulfilment.domain

import io.github.youndie.haul.feature.order.domain.Order
import io.github.youndie.haul.feature.order.domain.ShipmentStatus
import java.nio.ByteBuffer
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * How long each step of a shipment takes in the simulated world (feature-orders): `placed → packed →
 * in_transit → delivered` for a courier, `… → in_transit → ready_for_pickup → picked_up` for a point or a
 * locker. Leaving for the road waits for the seller's dispatch days as well — the most of the shipment's
 * products' (`products.dispatch_days`) — so of Maya's two shipments Sony's ships a day before Brooklyn
 * Home Co.'s, as the canvas promises them (research §6).
 *
 * A return goes the other way (B-21): the courier collects it [returnPickup] after it is asked for, and it
 * is refunded [returnRefund] after that, when the seller has it back.
 *
 * [STORE] is the store's own pace, a decision rather than an observation (research D4, «Decided in
 * B-17»); [faster] is the same schedule run [faster]'s times quicker — the fast clock of a stand or a test.
 */
internal data class FulfilmentPace(
    val packing: Duration,
    val dispatch: Duration,
    val dispatchDay: Duration,
    val transit: Duration,
    val collection: Duration,
    val returnPickup: Duration = 1.days,
    val returnRefund: Duration = 1.days,
) {
    init {
        require(
            listOf(packing, dispatch, dispatchDay, transit, collection, returnPickup, returnRefund).all {
                it.isPositive()
            },
        ) {
            "every step of the fulfilment takes some time: $this"
        }
    }

    /** The next step of a shipment in [status], or `null` when it has arrived (or was cancelled). */
    fun next(
        status: String,
        pickup: Boolean,
        dispatchDays: Int,
    ): Step? =
        when (status) {
            ShipmentStatus.PLACED -> {
                Step(ShipmentStatus.PACKED, packing)
            }

            ShipmentStatus.PACKED -> {
                Step(ShipmentStatus.IN_TRANSIT, dispatch + dispatchDay * dispatchDays)
            }

            ShipmentStatus.IN_TRANSIT -> {
                Step(
                    if (pickup) ShipmentStatus.READY_FOR_PICKUP else ShipmentStatus.DELIVERED,
                    transit,
                )
            }

            ShipmentStatus.READY_FOR_PICKUP -> {
                Step(ShipmentStatus.PICKED_UP, collection)
            }

            else -> {
                null
            }
        }

    /** The same schedule, [speed] times quicker. */
    fun faster(speed: Double): FulfilmentPace {
        require(speed.isFinite() && speed > 0) { "a fulfilment speed is a positive number, not $speed" }
        return FulfilmentPace(
            packing / speed,
            dispatch / speed,
            dispatchDay / speed,
            transit / speed,
            collection / speed,
            returnPickup / speed,
            returnRefund / speed,
        )
    }

    /**
     * How often the simulator looks: a tenth of the shortest step, between a second and a minute — a step
     * is late by at most a tenth of itself, and a slow store is not polled every second for nothing.
     */
    val pollInterval: Duration
        get() = (listOf(packing, dispatch, transit, collection).min() / POLLS_PER_STEP).coerceIn(1.seconds, 1.minutes)

    /** A shipment's move to [to], due [after] it entered its current status. */
    data class Step(
        val to: String,
        val after: Duration,
    )

    companion object {
        private const val POLLS_PER_STEP = 10

        /**
         * The store's pace: packed four hours after the seller sees the order, on the road twenty hours
         * later plus a day per dispatch day, delivered or ready to collect a day after that, collected two
         * days later — inside the five days a point holds it ([HELD_FOR]). A return is collected a day after it
         * is asked for and refunded a day after that.
         */
        val STORE: FulfilmentPace =
            FulfilmentPace(
                packing = 4.hours,
                dispatch = 20.hours,
                dispatchDay = 1.days,
                transit = 1.days,
                collection = 2.days,
            )

        /** How long a point or a locker keeps a shipment from `ready_for_pickup` (feature-orders). */
        val HELD_FOR: Duration = 5.days
    }
}

/** A shipment of a placed order still on its way, as the simulator's pass reads it. */
internal data class ActiveShipment(
    val id: String,
    val orderId: String,
    val sagaId: String,
    val status: String,
    /** When it entered [status]; `null` while the simulated seller has not seen it yet. */
    val since: Instant?,
    val pickup: Boolean,
    val dispatchDays: Int,
)

/** The shipments' side of fulfilment: what is on its way, and every move it makes, each written once. */
internal interface FulfilmentRepository {
    /** The shipments of `placed` orders that have neither arrived nor been cancelled. */
    suspend fun active(): List<ActiveShipment>

    /** Stamps [shipmentId] as seen by its seller at [at], in `placed`; `false` when it was seen already. */
    suspend fun receive(
        shipmentId: String,
        at: Instant,
    ): Boolean

    /**
     * Moves [shipmentId] from [from] to [to] as of [at], with [pickupCode] when it is given; `false` when the
     * shipment is no longer in [from] — another pass moved it first — and nothing is written.
     */
    suspend fun move(
        shipmentId: String,
        from: String,
        to: String,
        at: Instant,
        pickupCode: String? = null,
    ): Boolean

    /** When each shipment of [orderId] entered each status it has passed through, by shipment id. */
    suspend fun history(orderId: String): Map<String, Map<String, Instant>>
}

/**
 * Each shipment's part of what its order charges (B-17): the order's total shared in proportion to what
 * each seller's lines cost at the price paid, so a promo code and the delivery fee are shared the same way.
 * Every share but the last is rounded down and the last takes the rest, so the shares always add up to the
 * total, cent for cent — the authorisation is taken whole, in parts, and never more.
 */
internal object ShipmentShares {
    fun of(order: Order): Map<String, Int> {
        val costs =
            order.shipments.map { shipment ->
                shipment.id to
                    order.placed.lines
                        .filter { it.sellerId == shipment.sellerId }
                        .sumOf { it.priceCents.toLong() * it.quantity }
            }
        val cost = costs.sumOf { it.second }
        if (costs.isEmpty() || cost == 0L) return costs.associate { it.first to 0 }
        val total = order.placed.totalCents.toLong()
        val shares = costs.dropLast(1).map { (id, part) -> id to (total * part / cost).toInt() }
        return (shares + (costs.last().first to (total - shares.sumOf { it.second.toLong() }).toInt())).toMap()
    }
}

/**
 * A pickup shipment's four-digit code (feature-orders). Deterministic, so a pass that runs twice writes the
 * same code and a fixture can name it; keyed by the order's saga id — a hash of the customer and their
 * `Idempotency-Key` that never leaves the server — so the code cannot be worked out from the shipment id the
 * shopper's page shows.
 */
internal object PickupCodes {
    private const val ALGORITHM = "HmacSHA256"
    private const val CODES = 10_000L
    private const val DIGITS = 4

    fun of(
        sagaId: String,
        shipmentId: String,
    ): String {
        val mac = Mac.getInstance(ALGORITHM).apply { init(SecretKeySpec(sagaId.toByteArray(), ALGORITHM)) }
        val digest = mac.doFinal(shipmentId.toByteArray())
        val number = ByteBuffer.wrap(digest).int.toLong() and 0xFFFF_FFFFL
        return (number % CODES).toString().padStart(DIGITS, '0')
    }
}
