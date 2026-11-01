package io.github.youndie.haul.ui

import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.registry.KompotComponentMarker
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// «Live orders» (`/live`, B-164): the last orders placed on the stand, each the saga it ran and what the simulated
// world did with it since, drawn again as it moves. Every word is the server's; the client draws them and keeps only
// which rows are open.

/**
 * The page's body: an [eyebrow], a [title] with its [accent], the [lead] that says what the rows are — the petich saga
 * of every order — and its [links] (the saga's source on GitHub, opened in a tab of its own); then the [orders],
 * newest first, or [empty] when there are none. [open] and [close] are what a row's control says to show and to hide
 * its trail, [note] the line under the rows.
 *
 * The page names a live channel, and every move of an order on the stand sends this body again, whole: the rows are a
 * few dozen kilobytes at most, and a new order changes all of them.
 */
@Serializable
@SerialName("haul_live_orders")
@KompotComponentMarker
public data class LiveOrdersBody(
    override val id: String,
    val eyebrow: String,
    val title: String,
    val accent: String? = null,
    val lead: String,
    val links: List<Link> = emptyList(),
    val orders: List<LiveOrderRow>,
    val empty: String? = null,
    val open: String,
    val close: String,
    val note: String? = null,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/**
 * One order: its [number] «#HL-48302», when it was [placed], its [sellers] and [summary] («3 items · $512.40»), its
 * [status] in words and whether it ended [undone] — cancelled, the saga's steps given back — then its [lanes], the saga's
 * and one per seller's shipment and the return's, and its [trail], every step with its time, which the row shows when
 * opened. [id] tells the rows apart across updates, so a row the visitor opened stays open.
 *
 * Anonymised by construction: nothing here names the customer, their address, their window or the code that
 * collects a parcel.
 */
@Serializable
public data class LiveOrderRow(
    val id: String,
    val number: String,
    val placed: String,
    val sellers: String,
    val summary: String,
    val status: String,
    val undone: Boolean = false,
    val lanes: List<LiveLane>,
    val trail: List<LiveTrailLine>,
)

/** A row of steps: the saga's, a seller's shipment, the return — its [title] and its [steps] in their order. */
@Serializable
public data class LiveLane(
    val title: String,
    val steps: List<LiveStep>,
)

/**
 * One step: its [label] «Reserve stock», how it stands ([state]) and that said in words, [word] — «done», «refused: card
 * declined», «undone», «nothing to hold», «not yet» — and [at], when it happened, when that is worth a time of its own.
 */
@Serializable
public data class LiveStep(
    val label: String,
    val state: LiveStepState,
    val word: String,
    val at: String? = null,
)

/**
 * One line of an order's trail: [at], its time to the second; [text], what happened; [member], the saga member's key
 * (`reserve-stock`) when it was one; [state], how it is drawn.
 */
@Serializable
public data class LiveTrailLine(
    val at: String,
    val text: String,
    val member: String? = null,
    val state: LiveStepState,
)

/** How a step of a [LiveLane] stands, which is how it is drawn. */
@Serializable
public enum class LiveStepState {
    /** It happened. */
    @SerialName("done")
    Done,

    /** The step that failed or refused, the one the saga turned back at (drawn in the error colour). */
    @SerialName("failed")
    Failed,

    /** It happened and was given back by the saga's compensation. */
    @SerialName("undone")
    Undone,

    /** It ran and had nothing to do: a pickup holds no window, an order without points takes none. */
    @SerialName("skipped")
    Skipped,

    /** Not yet, or never reached. */
    @SerialName("pending")
    Pending,
}
