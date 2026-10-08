package io.github.youndie.haul.ui

import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.registry.KompotComponentMarker
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The pieces of the Account screen (feature-account, screen-account). The page is the frame's header and
// one [AccountBody]: the profile and the menu in a column of their own at the desktop width, a row and
// three tabs above the page on a phone, then the page's title and what the page is — the overview's
// tiles, active orders and the last of the history (`/account`), or the whole history with its filter
// (`/account/orders`), or the Saved list (`/account/saved`, B-20). One body covers Content, NotMember,
// Orders and NoOrders — and Saved's Content, PriceDrops and Empty: they are what the customer has, not
// separate builders. Reorder on a delivered row is the order page's own command, a
// `POST` to [HistoryRow.reorderUrl] answered with `navigate` to the cart.

/**
 * Everything under the header: who is signed in ([profile]), the [menu] — Overview, Orders, Saved; the
 * other sections are hidden in v1 (research D6) — and the page: the [title] with its [accent] in italics
 * («Hi, *Maya*», «Orders») and the [count] in an Acid pill at its top right («Saved 48»), the overview's
 * [tiles] and [active] orders, the [history], or the [saved] list.
 */
@Serializable
@SerialName("haul_account_body")
@KompotComponentMarker
public data class AccountBody(
    override val id: String,
    val profile: AccountProfile,
    val menu: List<AccountMenuItem>,
    val title: String,
    val accent: String? = null,
    val tiles: List<AccountTile> = emptyList(),
    val active: ActiveOrders? = null,
    val history: OrderHistory? = null,
    val count: String? = null,
    val saved: SavedList? = null,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/**
 * The avatar — [initials] on [tone] (Acid for a Plus member, else one of the canvas's tile tones) — the
 * [name] and the line under it ([subtitle]: «Plus member since 2023», «No membership», «Joined Oct 2025»).
 */
@Serializable
public data class AccountProfile(
    val initials: String,
    val tone: String,
    val name: String,
    val subtitle: String,
)

/**
 * One section of the account: its [label], the [count] beside it — the active orders' in a Cobalt badge
 * when [badge], the saved products' in grey otherwise — whether it is the page shown ([selected]), and
 * where it goes ([action]; none while the section has no page of its own).
 */
@Serializable
public data class AccountMenuItem(
    val label: String,
    val count: String? = null,
    val badge: Boolean = false,
    val selected: Boolean = false,
    val action: @Polymorphic KompotAction? = null,
)

/** Which tile it is, which is its colours and how large its figure is drawn. */
@Serializable
public enum class AccountTileKind {
    /** Acid: the points balance. */
    @SerialName("points")
    Points,

    /** Cobalt: a member's delivery savings. */
    @SerialName("plus")
    Plus,

    /** Cobalt: the trial offered to somebody who is not a member — a phrase rather than a number, and a button. */
    @SerialName("plus_offer")
    PlusOffer,

    /** White: the saved products that got cheaper. */
    @SerialName("price_drops")
    PriceDrops,
}

/**
 * A tile of the overview: the [label] in mono capitals, the [figure] in Bodoni with its [accent] in
 * italics («30 days *free*»), the [text] under it, and — on the trial offer — the [button] («Try 30 days
 * free»), drawn without an action until there is a trial to start (B-23).
 */
@Serializable
public data class AccountTile(
    val kind: AccountTileKind,
    val label: String,
    val figure: String,
    val text: String,
    val accent: String? = null,
    val button: Link? = null,
)

/**
 * «Active orders»: the orders not yet delivered, collected or cancelled, at most three ([orders]), or,
 * when there are none, the sentence that says so ([empty], «No active orders right now.»).
 */
@Serializable
public data class ActiveOrders(
    val title: String,
    val orders: List<ActiveOrder> = emptyList(),
    val empty: String? = null,
)

/**
 * An order on its way, as a card: the [meta] line («#HL-48211 · Placed Oct 5 · $512.00»), the [title] with
 * its [accent] («Arriving *tomorrow*, 15:00 – 18:00») and the [lead] under it; then either its lines'
 * tiles ([tones]) and its [steps], or — waiting at a point — its [pickup] code; and [details], the way to
 * the order's page.
 */
@Serializable
public data class ActiveOrder(
    val id: String,
    val meta: String,
    val title: String,
    val accent: String? = null,
    val lead: String? = null,
    val tones: List<String> = emptyList(),
    val steps: OrderSteps? = null,
    val pickup: AccountPickup? = null,
    val details: Link,
)

/** The pickup code on an order card: [label] «Pickup code» and the [code]. */
@Serializable
public data class AccountPickup(
    val label: String,
    val code: String,
)

/**
 * The orders' history: on the overview its [title] «Order history», the last four of the orders that are
 * no longer on their way and [all], «All orders», when that leaves some out; on the orders' page the
 * [filters] above every order. [empty] is drawn instead of the rows when there are none — a customer who
 * has never ordered (`Account_NoOrders`) — and [none] when a filter matches nothing.
 */
@Serializable
public data class OrderHistory(
    val title: String? = null,
    val all: Link? = null,
    val filters: List<HistoryFilter> = emptyList(),
    val rows: List<HistoryRow> = emptyList(),
    val empty: EmptyState? = null,
    val none: String? = null,
)

/**
 * A filter chip: «All 6», «Active 2» on the history, «Price dropped 6» on the Saved list; the [selected]
 * one is drawn in Ink, and each goes to its own address.
 */
@Serializable
public data class HistoryFilter(
    val label: String,
    val count: String,
    val selected: Boolean = false,
    val action: @Polymorphic KompotAction? = null,
)

/** What a status chip says about the order, which is its fill. */
@Serializable
public enum class HistoryStatusKind {
    /** Acid: still on its way, or waiting to be collected. */
    @SerialName("active")
    Active,

    /** Paper: arrived, or cancelled. */
    @SerialName("done")
    Done,

    /** Blush: sent back. */
    @SerialName("returned")
    Returned,
}

/**
 * One order in the history: the day it was placed ([date]), its [number], one tile per line ([tones]), the
 * [total], its [status] in a chip of [statusKind], and the way on — [actionLabel] «Track» or «Details»
 * going to the order's page ([action]), or «Reorder», a `POST` to [reorderUrl] answered with `navigate` to
 * the cart.
 */
@Serializable
public data class HistoryRow(
    val id: String,
    val date: String,
    val number: String,
    val tones: List<String>,
    val total: String,
    val status: String,
    val statusKind: HistoryStatusKind,
    val actionLabel: String,
    val action: @Polymorphic KompotAction? = null,
    val reorderUrl: String? = null,
)
