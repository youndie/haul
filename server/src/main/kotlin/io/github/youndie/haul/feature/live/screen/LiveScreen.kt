package io.github.youndie.haul.feature.live.screen

import io.github.youndie.haul.feature.about.screen.AboutScreen
import io.github.youndie.haul.feature.catalog.domain.items
import io.github.youndie.haul.feature.catalog.domain.money
import io.github.youndie.haul.feature.checkout.DeliveryMethod
import io.github.youndie.haul.feature.checkout.domain.PaymentMethod
import io.github.youndie.haul.feature.live.domain.FeedOrder
import io.github.youndie.haul.feature.live.domain.FeedReturn
import io.github.youndie.haul.feature.live.domain.FeedShipment
import io.github.youndie.haul.feature.order.domain.CancelReason
import io.github.youndie.haul.feature.order.domain.OrderStatus
import io.github.youndie.haul.feature.order.domain.ShipmentStatus
import io.github.youndie.haul.feature.order.saga.OrderSagaMembers
import io.github.youndie.haul.feature.returns.domain.ReturnStatus
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.LiveLane
import io.github.youndie.haul.ui.LiveOrderRow
import io.github.youndie.haul.ui.LiveOrdersBody
import io.github.youndie.haul.ui.LiveStep
import io.github.youndie.haul.ui.LiveStepState
import io.github.youndie.haul.ui.LiveTrailLine
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.OpenUrlAction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * «Live orders» (`/live`, B-164): the last [LIMIT] orders placed on the stand, each the petich saga it ran and what the
 * simulated world has done with it since — and, opened, its trail, every step with its time. Drawn in [zone], the
 * store's.
 *
 * **What a row says is read off the order's own rows**, not off its trail in tracy (`OrderTrail`, B-109): the saga
 * runs inside the request that places the order and leaves the order `placed`, or `cancelled` with its reason; the
 * shipments' history, their charges and the return are stamped as they happen. So the steps are what the saga's members
 * do by the code ([OrderSagaMembers]) and the order's outcome: a placed order ran them all; a declined card refused at
 * `authorise-payment` and undid the four before it, in reverse; an order cancelled as `failed` undid the same four,
 * and which later member failed is the one thing a row does not know — its trail in tracy does. A member that had
 * nothing to take — a pickup holds no window, an order without points takes none, pay on delivery holds no money —
 * is drawn as having nothing to do.
 *
 * **Anonymised by construction:** a row is built from [FeedOrder], which holds no customer, address, window, pickup
 * point or code.
 */
internal class LiveScreen(
    private val zone: () -> ZoneId,
) {
    fun page(
        viewer: Viewer,
        navigation: List<Link>,
        orders: List<FeedOrder>,
    ): KompotComponent =
        Frame.page(
            "live-page",
            viewer,
            navigation,
            listOf(body(orders)),
            Frame.title("Live orders"),
            footer = true,
        )

    fun body(orders: List<FeedOrder>): LiveOrdersBody {
        val zone = zone()
        return LiveOrdersBody(
            id = BODY_ID,
            eyebrow = "Live orders",
            title = "Every order here is a saga",
            accent = "a saga",
            lead =
                "The last $LIMIT orders placed on this stand — the synthetic shoppers’ and anybody’s — as " +
                    "they happen. Each is a petich saga: the stock, a delivery window, the points and the card " +
                    "are taken in turn, and when a step fails, the ones before it are given back in reverse. " +
                    "Then the simulated sellers pack and ship each part, the card is charged as it leaves, and " +
                    "a return is refunded once it is back. One shopper in ten pays with a card that is always " +
                    "declined, so the undoing is here too.",
            links = listOf(Link("Read the saga: OrderSaga.kt", OpenUrlAction(SAGA_SOURCE))),
            orders = orders.map { row(it, zone) },
            empty =
                if (orders.isEmpty()) {
                    "No orders yet. The synthetic shoppers place one every ten minutes — or place one yourself."
                } else {
                    null
                },
            open = "Show the trail",
            close = "Hide the trail",
            note =
                "No names, addresses, windows or pickup codes: an order is its number, its sellers, what it cost and " +
                    "what happened to it. The page is drawn again whenever an order moves, over the same stream " +
                    "an order’s own page is live on.",
        )
    }

    private fun row(
        order: FeedOrder,
        zone: ZoneId,
    ): LiveOrderRow {
        val saga = Saga.of(order)
        val lanes =
            buildList {
                add(LiveLane("The saga", saga.steps))
                if (order.status == OrderStatus.Placed) {
                    order.shipments.forEach { add(LiveLane(it.seller, shipmentSteps(order, it, zone))) }
                }
                order.returned?.let { add(LiveLane("The return", returnSteps(it, zone))) }
            }
        return LiveOrderRow(
            id = order.id,
            number = "#${order.id}",
            placed = SHORT.withZone(zone).format(order.placedAt),
            sellers =
                order.shipments
                    .map { it.seller }
                    .distinct()
                    .joinToString(" · "),
            summary = "${items(order.items)} · ${money(order.totalCents)}",
            status = status(order),
            undone = order.status == OrderStatus.Cancelled,
            lanes = lanes,
            trail = trail(order, saga, zone),
        )
    }

    /** One line of the saga's part of the trail: what [member] did, or `null` for a line no member is known for. */
    private data class SagaLine(
        val member: String?,
        val text: String,
        val state: LiveStepState,
    )

    /** The saga's members as this order ran them: the steps of its first lane, and its lines of the trail. */
    private class Saga(
        val steps: List<LiveStep>,
        val lines: List<SagaLine>,
    ) {
        companion object {
            fun of(order: FeedOrder): Saga {
                val steps = mutableListOf<LiveStep>()
                val lines = mutableListOf<SagaLine>()

                fun did(
                    member: String,
                    state: LiveStepState,
                    word: String,
                    text: String? = null,
                ) {
                    steps += LiveStep(LABELS.getValue(member), state, word)
                    text?.let { lines += SagaLine(member, it, state) }
                }

                // The four members before the payment: each took something, or had nothing to take.
                did(OrderSagaMembers.RESERVE_STOCK, LiveStepState.Done, "done", "Stock reserved for every line")
                if (order.heldWindow) {
                    did(
                        OrderSagaMembers.RESERVE_SLOT,
                        LiveStepState.Done,
                        "done",
                        "A place in the courier’s window held",
                    )
                } else {
                    did(
                        OrderSagaMembers.RESERVE_SLOT,
                        LiveStepState.Skipped,
                        "no window",
                        "No window to hold: it is collected",
                    )
                }
                if (order.redeemedPoints) {
                    did(OrderSagaMembers.REDEEM_POINTS, LiveStepState.Done, "done", "Points taken off the balance")
                } else {
                    did(OrderSagaMembers.REDEEM_POINTS, LiveStepState.Skipped, "no points", "No points to take")
                }
                did(OrderSagaMembers.OPEN_ORDER, LiveStepState.Done, "done", "The order opened, a shipment per seller")
                val holdsMoney = PaymentMethod.byId(order.payment)?.card ?: true
                when {
                    order.status == OrderStatus.Placing -> {
                        did(
                            OrderSagaMembers.AUTHORISE_PAYMENT,
                            LiveStepState.Pending,
                            "waiting",
                            "Waiting for the card’s answer",
                        )
                        did(OrderSagaMembers.CONFIRM, LiveStepState.Pending, "not yet")
                    }

                    order.status == OrderStatus.Cancelled && order.cancelReason == CancelReason.PAYMENT_DECLINED -> {
                        did(
                            OrderSagaMembers.AUTHORISE_PAYMENT,
                            LiveStepState.Failed,
                            "refused: card declined",
                            "The card was declined: the saga turns back",
                        )
                        did(OrderSagaMembers.CONFIRM, LiveStepState.Pending, "not reached")
                    }

                    order.status == OrderStatus.Cancelled -> {
                        // Which member after the order opened failed is in the order's trail in tracy, not in its rows.
                        did(OrderSagaMembers.AUTHORISE_PAYMENT, LiveStepState.Pending, "unknown")
                        did(OrderSagaMembers.CONFIRM, LiveStepState.Failed, "failed")
                        lines +=
                            SagaLine(
                                null,
                                "A step after the order opened failed: the saga turns back",
                                LiveStepState.Failed,
                            )
                    }

                    holdsMoney -> {
                        did(
                            OrderSagaMembers.AUTHORISE_PAYMENT,
                            LiveStepState.Done,
                            "done",
                            "${money(order.totalCents)} held on the card",
                        )
                        did(OrderSagaMembers.CONFIRM, LiveStepState.Done, "done", "Confirmed: the order is placed")
                    }

                    else -> {
                        did(
                            OrderSagaMembers.AUTHORISE_PAYMENT,
                            LiveStepState.Skipped,
                            "pays on delivery",
                            "Nothing to hold: it is paid on delivery",
                        )
                        did(OrderSagaMembers.CONFIRM, LiveStepState.Done, "done", "Confirmed: the order is placed")
                    }
                }
                if (order.status == OrderStatus.Cancelled) {
                    // The compensations, in reverse: each member that took something gives it back.
                    UNDOING.forEach { member ->
                        val index = OrderSagaMembers.STEPS.indexOf(member)
                        if (steps[index].state == LiveStepState.Done) {
                            steps[index] = steps[index].copy(state = LiveStepState.Undone, word = "undone")
                            lines += SagaLine(member, UNDONE.getValue(member), LiveStepState.Undone)
                        }
                    }
                }
                return Saga(steps, lines)
            }
        }
    }

    private fun shipmentSteps(
        order: FeedOrder,
        shipment: FeedShipment,
        zone: ZoneId,
    ): List<LiveStep> =
        way(order).map { status ->
            val at = shipment.entered[status]
            val word =
                when {
                    at == null -> {
                        "not yet"
                    }

                    status == ShipmentStatus.IN_TRANSIT && shipment.capturedCents != null -> {
                        "charged ${money(shipment.capturedCents)}"
                    }

                    else -> {
                        "done"
                    }
                }
            LiveStep(
                SHIPMENT_LABELS.getValue(status),
                if (at == null) LiveStepState.Pending else LiveStepState.Done,
                word,
                at?.let { SHORT.withZone(zone).format(it) },
            )
        }

    private fun returnSteps(
        back: FeedReturn,
        zone: ZoneId,
    ): List<LiveStep> =
        listOf(
            Triple("Asked for", back.requestedAt, "done"),
            Triple("Picked up", back.pickedUpAt, "done"),
            Triple("Refunded", back.refundedAt, "${money(back.refundCents)} back"),
        ).map { (label, at, word) ->
            LiveStep(
                label,
                if (at == null) LiveStepState.Pending else LiveStepState.Done,
                if (at == null) "not yet" else word,
                at?.let { SHORT.withZone(zone).format(it) },
            )
        }

    /** Every line of [order]'s trail, oldest first; lines of one moment keep the order they happened in. */
    private fun trail(
        order: FeedOrder,
        saga: Saga,
        zone: ZoneId,
    ): List<LiveTrailLine> {
        val at = LONG.withZone(zone)
        val lines = mutableListOf<Pair<Instant, LiveTrailLine>>()
        val placed = at.format(order.placedAt)
        saga.lines.forEach { lines += order.placedAt to LiveTrailLine(placed, it.text, it.member, it.state) }
        if (order.status == OrderStatus.Placed) {
            order.shipments.forEach { shipment ->
                way(order).forEach { status ->
                    shipment.entered[status]?.let { moment ->
                        lines += moment to
                            LiveTrailLine(
                                at.format(moment),
                                MOVES.getValue(status).replace(SELLER, shipment.seller),
                                state = LiveStepState.Done,
                            )
                    }
                }
                val cents = shipment.capturedCents
                val captured = shipment.capturedAt
                if (cents != null && captured != null) {
                    lines += captured to
                        LiveTrailLine(
                            at.format(captured),
                            "${shipment.seller}’s share charged as it left: ${money(cents)}",
                            state = LiveStepState.Done,
                        )
                }
            }
        }
        order.returned?.let { back ->
            lines += back.requestedAt to
                LiveTrailLine(
                    at.format(back.requestedAt),
                    "A return asked for: ${money(back.refundCents)} to give back",
                    state = LiveStepState.Done,
                )
            back.pickedUpAt?.let {
                lines +=
                    it to
                    LiveTrailLine(at.format(it), "The return picked up by the courier", state = LiveStepState.Done)
            }
            back.refundedAt?.let {
                lines += it to
                    LiveTrailLine(
                        at.format(it),
                        "Refunded ${money(back.refundCents)}; the units back in stock",
                        state = LiveStepState.Done,
                    )
            }
        }
        return lines.sortedBy { it.first }.map { it.second }
    }

    private fun status(order: FeedOrder): String =
        when {
            order.status == OrderStatus.Placing -> {
                "Placing"
            }

            order.status == OrderStatus.Cancelled -> {
                if (order.cancelReason == CancelReason.PAYMENT_DECLINED) {
                    "Cancelled: card declined"
                } else {
                    "Cancelled: the saga failed"
                }
            }

            order.returned?.status == ReturnStatus.REFUNDED -> {
                "Returned"
            }

            order.returned != null -> {
                "Returning"
            }

            else -> {
                order.shipments
                    .map { ShipmentStatus.LIFECYCLE.indexOf(it.status) }
                    .filter { it >= 0 }
                    .minOrNull()
                    ?.let { STATUSES.getValue(ShipmentStatus.LIFECYCLE[it]) } ?: "Placed"
            }
        }

    /** The statuses a shipment of [order] passes through after the seller sees it, by how it is delivered. */
    private fun way(order: FeedOrder): List<String> =
        if (order.method == DeliveryMethod.Courier) {
            listOf(ShipmentStatus.PACKED, ShipmentStatus.IN_TRANSIT, ShipmentStatus.DELIVERED)
        } else {
            listOf(
                ShipmentStatus.PACKED,
                ShipmentStatus.IN_TRANSIT,
                ShipmentStatus.READY_FOR_PICKUP,
                ShipmentStatus.PICKED_UP,
            )
        }

    companion object {
        /** The page's address, which the About page and the demo strip lead to. */
        const val ADDRESS = AboutScreen.LIVE_ORDERS

        /** How many orders the page shows: the last, newest first. */
        const val LIMIT = 20

        /** The body's id: what the live channel's updates replace. */
        const val BODY_ID = "live-orders"

        /** The saga's definition in the repository, which the page's intro links to. */
        const val SAGA_SOURCE =
            "${AboutScreen.REPOSITORY}/blob/main/server/src/main/kotlin/io/github/youndie/haul/feature/order/saga/OrderSaga.kt"

        private val SHORT = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.US)
        private val LONG = DateTimeFormatter.ofPattern("MMM d, HH:mm:ss", Locale.US)

        private val LABELS =
            mapOf(
                OrderSagaMembers.RESERVE_STOCK to "Reserve stock",
                OrderSagaMembers.RESERVE_SLOT to "Hold the window",
                OrderSagaMembers.REDEEM_POINTS to "Take the points",
                OrderSagaMembers.OPEN_ORDER to "Open the order",
                OrderSagaMembers.AUTHORISE_PAYMENT to "Authorise the card",
                OrderSagaMembers.CONFIRM to "Confirm",
            )

        /** The members a rollback undoes, in the order it undoes them: the reverse of theirs. */
        private val UNDOING =
            listOf(
                OrderSagaMembers.OPEN_ORDER,
                OrderSagaMembers.REDEEM_POINTS,
                OrderSagaMembers.RESERVE_SLOT,
                OrderSagaMembers.RESERVE_STOCK,
            )

        private val UNDONE =
            mapOf(
                OrderSagaMembers.OPEN_ORDER to "Undone: the order cancelled, its shipments with it",
                OrderSagaMembers.REDEEM_POINTS to "Undone: the points given back",
                OrderSagaMembers.RESERVE_SLOT to "Undone: the window released",
                OrderSagaMembers.RESERVE_STOCK to "Undone: the stock released",
            )

        private const val SELLER = "{seller}"

        private val MOVES =
            mapOf(
                ShipmentStatus.PACKED to "$SELLER packed its part",
                ShipmentStatus.IN_TRANSIT to "$SELLER’s parcel left for the road",
                ShipmentStatus.READY_FOR_PICKUP to "$SELLER’s parcel is ready to collect",
                ShipmentStatus.DELIVERED to "$SELLER’s parcel delivered",
                ShipmentStatus.PICKED_UP to "$SELLER’s parcel collected",
            )

        private val SHIPMENT_LABELS =
            mapOf(
                ShipmentStatus.PACKED to "Packed",
                ShipmentStatus.IN_TRANSIT to "On its way",
                ShipmentStatus.READY_FOR_PICKUP to "Ready",
                ShipmentStatus.DELIVERED to "Delivered",
                ShipmentStatus.PICKED_UP to "Collected",
            )

        private val STATUSES =
            mapOf(
                ShipmentStatus.PLACED to "Placed",
                ShipmentStatus.PACKED to "Packed",
                ShipmentStatus.IN_TRANSIT to "In transit",
                ShipmentStatus.READY_FOR_PICKUP to "Ready for pickup",
                ShipmentStatus.DELIVERED to "Delivered",
                ShipmentStatus.PICKED_UP to "Collected",
            )
    }
}
