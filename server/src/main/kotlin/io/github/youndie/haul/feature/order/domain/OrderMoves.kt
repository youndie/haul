package io.github.youndie.haul.feature.order.domain

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The orders the simulated world moved away from their customer's requests (B-29): a shipment a step on,
 * a return collected or refunded, a Haul Pay payment taken — and the placement saga's ending (B-56), the
 * order placed or cancelled. The simulators say so through [moved] once a pass has made the move, the
 * saga's members once their write is committed; the order's live page is drawn again from it
 * ([io.github.youndie.haul.feature.order.LiveOrders]), and so is «Live orders».
 *
 * **Moves made on another replica arrive through [heard]** (B-174): the replicas' bus
 * ([io.github.youndie.haul.ops.PostgresBus]) sends what this process [moved] ([madeHere]) to the others and hands what they
 * made to [heard]. [orders] is both — every move a page connected here should see, wherever it was made — so a
 * page is live whichever replica it is connected to. Alone, nothing is heard and [orders] is this process's moves.
 *
 * **Telling never holds a move up and never fails it.** [moved] only offers the id: with nobody listening
 * — a test that assembles the graph without the application — it goes nowhere, and when the listener falls
 * more than [CAPACITY] behind the oldest is dropped. A page that missed a move is a page one step behind
 * until the next move or its next connection, which starts with the order as it is; a simulator stalled by
 * a slow page would be every order behind. The same holds for the bus: a move it could not send is a move
 * the other replicas' pages miss, never one this process's pages miss or one the simulator waits for.
 */
internal class OrderMoves {
    private val flow =
        MutableSharedFlow<String>(extraBufferCapacity = CAPACITY, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val made =
        MutableSharedFlow<String>(extraBufferCapacity = CAPACITY, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * The ids of the orders moved — here and, through the bus, on the other replicas — in the order they were
     * heard; one already listening sees every one after.
     */
    val orders: Flow<String> = flow.asSharedFlow()

    /** The ids of the orders this process moved, and only those: what the bus sends to the other replicas. */
    val madeHere: Flow<String> = made.asSharedFlow()

    /** This process moved [orderId]: its pages hear it now, the other replicas' through the bus. */
    fun moved(orderId: String) {
        flow.tryEmit(orderId)
        made.tryEmit(orderId)
    }

    /** Another replica moved [orderId]: this process's pages hear it, and it is not sent on again. */
    fun heard(orderId: String) {
        flow.tryEmit(orderId)
    }

    private companion object {
        const val CAPACITY = 256
    }
}
