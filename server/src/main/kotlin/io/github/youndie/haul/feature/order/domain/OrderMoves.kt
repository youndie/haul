package io.github.youndie.haul.feature.order.domain

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The orders the simulated world moved away from their customer's requests (B-29): a shipment a step on,
 * a return collected or refunded, a Haul Pay payment taken. The simulators say so through [moved] once a
 * pass has made the move; the order's live page is drawn again from it
 * ([io.github.youndie.haul.feature.order.LiveOrders]).
 *
 * **Telling never holds a move up and never fails it.** [moved] only offers the id: with nobody listening
 * — a test that assembles the graph without the application — it goes nowhere, and when the listener falls
 * more than [CAPACITY] behind the oldest is dropped. A page that missed a move is a page one step behind
 * until the next move or its next connection, which starts with the order as it is; a simulator stalled by
 * a slow page would be every order behind.
 */
internal class OrderMoves {
    private val flow =
        MutableSharedFlow<String>(extraBufferCapacity = CAPACITY, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** The ids of the orders moved, in the order they moved; one already listening sees every one after. */
    val orders: Flow<String> = flow.asSharedFlow()

    fun moved(orderId: String) {
        flow.tryEmit(orderId)
    }

    private companion object {
        const val CAPACITY = 256
    }
}
