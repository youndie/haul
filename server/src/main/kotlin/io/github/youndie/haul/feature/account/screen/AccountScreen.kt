package io.github.youndie.haul.feature.account.screen

import io.github.youndie.haul.feature.account.AccountPaths
import io.github.youndie.haul.feature.account.domain.Loyalty
import io.github.youndie.haul.feature.account.domain.SavedLists
import io.github.youndie.haul.feature.account.domain.SavedSummary
import io.github.youndie.haul.feature.account.domain.Standing
import io.github.youndie.haul.feature.cart.screen.CartScreen
import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.count
import io.github.youndie.haul.feature.catalog.domain.money
import io.github.youndie.haul.feature.catalog.screen.navigation
import io.github.youndie.haul.feature.fulfilment.domain.OrderProgress
import io.github.youndie.haul.feature.fulfilment.domain.OrderTracking
import io.github.youndie.haul.feature.fulfilment.domain.TrackedOrder
import io.github.youndie.haul.feature.identity.domain.Customer
import io.github.youndie.haul.feature.order.OrderPaths
import io.github.youndie.haul.feature.order.domain.OrderRepository
import io.github.youndie.haul.feature.order.screen.OrderCard
import io.github.youndie.haul.feature.order.screen.OrderScreen
import io.github.youndie.haul.feature.reviews.domain.ReviewCommands
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.ui.AccountBody
import io.github.youndie.haul.ui.AccountMenuItem
import io.github.youndie.haul.ui.AccountPickup
import io.github.youndie.haul.ui.AccountProfile
import io.github.youndie.haul.ui.AccountTile
import io.github.youndie.haul.ui.AccountTileKind
import io.github.youndie.haul.ui.ActiveOrder
import io.github.youndie.haul.ui.ActiveOrders
import io.github.youndie.haul.ui.EmptyState
import io.github.youndie.haul.ui.HistoryFilter
import io.github.youndie.haul.ui.HistoryRow
import io.github.youndie.haul.ui.HistoryStatusKind
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.OrderHistory
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.NavigateAction
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * `GET /ui/account` and `GET /ui/account/orders` (screen-account, feature-account): the frame and one
 * [AccountBody] — the overview, or the orders' history under a [HistoryFilterKey]. Both are drawn from the
 * customer's orders as tracking reads them (B-17), newest first, and from what the account cannot store
 * yet: the points and the membership ([Loyalty], B-23's) and the Saved list's counts ([SavedLists],
 * B-20's). The copy is the canvas's (`Account_*`).
 */
internal class AccountScreen(
    private val orders: OrderRepository,
    private val tracking: OrderTracking,
    private val orderScreen: OrderScreen,
    private val catalog: CatalogRepository,
    private val loyalty: Loyalty,
    private val saved: SavedLists,
) {
    suspend fun build(
        customer: Customer,
        page: AccountPage,
        viewer: Viewer,
    ): KompotComponent {
        val tracked = orders.orders(customer.id).map { tracking.of(it) }
        return page(page, view(customer, tracked), viewer)
    }

    /** The page of [view], under the frame [viewer] is looking at. */
    suspend fun page(
        page: AccountPage,
        view: AccountView,
        viewer: Viewer,
    ): KompotComponent = Frame.page("account", viewer, navigation(catalog.categories()), listOf(body(page, view)))

    /**
     * What the account of [customer] draws, given their orders as tracking reads them, newest first: each
     * order's state and the tiles of its lines, the cards of those still on their way, and the standing.
     */
    suspend fun view(
        customer: Customer,
        tracked: List<TrackedOrder>,
    ): AccountView {
        val skus =
            tracked
                .flatMap { order ->
                    order.order.placed.lines
                        .map { it.skuId }
                }.toSet()
        val tones =
            catalog
                .listedBySkus(skus)
                .flatMap { listed -> listed.skus.map { it.id to listed.product.tone } }
                .toMap()
        val orders =
            tracked.map { order ->
                val state = OrderState.of(order.progress)
                AccountOrder(
                    id = order.order.id,
                    state = state,
                    placedAt = order.order.placed.placedAt,
                    totalCents = order.order.placed.totalCents,
                    tones =
                        order.order.placed.lines
                            .map { tones[it.skuId].orEmpty() },
                    card =
                        if (state.active) {
                            OrderScreen.card(orderScreen.view(customer.id, order, customer.firstName))
                        } else {
                            null
                        },
                )
            }
        return AccountView(customer, loyalty.standing(customer), saved.summary(customer.id), orders)
    }

    companion object {
        /** The account's body as the server draws it from [view] on [page]: everything it needs is in the view. */
        fun body(
            page: AccountPage,
            view: AccountView,
        ): AccountBody = AccountPageBuilder(view).body(page)
    }
}

/** Which of the account's pages: the overview (`/account`) or the orders' history (`/account/orders`). */
internal sealed interface AccountPage {
    data object Overview : AccountPage

    data class Orders(
        val filter: HistoryFilterKey = HistoryFilterKey.All,
    ) : AccountPage
}

/**
 * The history's filters, as the chips name them and as the query string carries them
 * (`/account/orders?status=active`); [All] is the history without the parameter.
 */
internal enum class HistoryFilterKey(
    val parameter: String?,
    val label: String,
) {
    All(null, "All"),
    Active("active", "Active"),
    Delivered("delivered", "Delivered"),
    Returned("returned", "Returned"),
    Cancelled("cancelled", "Cancelled"),
    ;

    fun matches(state: OrderState): Boolean =
        when (this) {
            All -> true
            Active -> state.active
            Delivered -> state == OrderState.Delivered || state == OrderState.PickedUp
            Returned -> state == OrderState.Returned || state == OrderState.Returning
            Cancelled -> state == OrderState.Cancelled
        }

    companion object {
        /** The filter a `status` parameter names; none, or one the history does not have, is all of them. */
        fun of(parameter: String?): HistoryFilterKey = entries.firstOrNull { it.parameter == parameter } ?: All
    }
}

/**
 * Where an order is, as the history's chip says it. [active] is on its way or waiting to be collected —
 * the overview's «Active orders» and the menu's count. [Returned] and [Returning] are the returns' (B-21):
 * refunded, or asked for and not refunded yet — a return in flight has a label of its own, in Blush like a
 * returned one and under the same «Returned» filter, with «Details» rather than «Reorder», as its page has.
 */
internal enum class OrderState(
    val label: String,
    val kind: HistoryStatusKind,
    val active: Boolean,
) {
    Placed("Placed", HistoryStatusKind.Active, true),
    Packed("Packed", HistoryStatusKind.Active, true),
    InTransit("In transit", HistoryStatusKind.Active, true),
    ReadyForPickup("Ready for pickup", HistoryStatusKind.Active, true),
    Delivered("Delivered", HistoryStatusKind.Done, false),
    PickedUp("Picked up", HistoryStatusKind.Done, false),
    Returning("Returning", HistoryStatusKind.Returned, false),
    Returned("Returned", HistoryStatusKind.Returned, false),
    Cancelled("Cancelled", HistoryStatusKind.Done, false),
    ;

    companion object {
        fun of(progress: OrderProgress): OrderState =
            when (progress) {
                OrderProgress.Placing, OrderProgress.Placed -> Placed
                OrderProgress.Packed -> Packed
                OrderProgress.InTransit -> InTransit
                OrderProgress.ReadyForPickup -> ReadyForPickup
                OrderProgress.Delivered -> Delivered
                OrderProgress.PickedUp -> PickedUp
                OrderProgress.Cancelled -> Cancelled
                OrderProgress.Returning -> Returning
                OrderProgress.Returned -> Returned
            }
    }
}

/**
 * One of the customer's orders as the account draws it: its [state], when it was placed and for how much,
 * one tile per line ([tones]), and — while it is on its way — its [card] (`OrderScreen.card`).
 */
internal data class AccountOrder(
    val id: String,
    val state: OrderState,
    val placedAt: OffsetDateTime,
    val totalCents: Int,
    val tones: List<String>,
    val card: OrderCard?,
)

/** What the account is drawn from: who, their [standing], their Saved list's counts, and their [orders], newest first. */
internal data class AccountView(
    val customer: Customer,
    val standing: Standing,
    val saved: SavedSummary,
    val orders: List<AccountOrder>,
)

/** The account's body, built from its [view]; every date is the store's (New York's). */
private class AccountPageBuilder(
    private val view: AccountView,
) {
    private val customer = view.customer
    private val active = view.orders.filter { it.state.active }

    fun body(page: AccountPage): AccountBody =
        when (page) {
            AccountPage.Overview -> {
                val first = customer.firstName
                AccountBody(
                    id = "account",
                    profile = profile(),
                    menu = menu(page),
                    title = "Hi, $first",
                    accent = first,
                    tiles = tiles(),
                    active = activeOrders(),
                    history = recent(),
                )
            }

            is AccountPage.Orders -> {
                AccountBody(
                    id = "account",
                    profile = profile(),
                    menu = menu(page),
                    title = "Orders",
                    history = history(page.filter),
                )
            }
        }

    /** The initials on Acid for a member, else on the customer's own tile tone — the one their reviews are signed with. */
    private fun profile(): AccountProfile {
        val membership = view.standing.membership
        val subtitle =
            when {
                membership != null -> "Plus member since ${membership.since}"
                view.orders.isEmpty() -> "Joined " + MONTH_YEAR.format(customer.joined.atZoneSameInstant(STORE))
                else -> "No membership"
            }
        return AccountProfile(
            initials = initials(customer.name),
            tone = if (membership != null) ACID else ReviewCommands.avatarTone(customer.id),
            name = customer.name,
            subtitle = subtitle,
        )
    }

    /** Overview, Orders with the active orders' count, Saved with the saved products' — which has no page yet (B-20). */
    private fun menu(page: AccountPage): List<AccountMenuItem> =
        listOf(
            AccountMenuItem(
                "Overview",
                selected = page == AccountPage.Overview,
                action = NavigateAction(Frame.ACCOUNT),
            ),
            AccountMenuItem(
                "Orders",
                count = active.size.takeIf { it > 0 }?.let(::count),
                badge = true,
                selected = page is AccountPage.Orders,
                action = NavigateAction(Frame.ORDERS),
            ),
            AccountMenuItem(
                "Saved",
                count =
                    view.saved.saved
                        .takeIf { it > 0 }
                        ?.let(::count),
            ),
        )

    private fun tiles(): List<AccountTile> {
        val standing = view.standing
        val membership = standing.membership
        val points =
            AccountTile(
                AccountTileKind.Points,
                "Points",
                count(standing.points),
                when {
                    standing.points > 0 -> "Worth ${CartScreen.exact(standing.points)} on your next order"
                    membership != null -> "2 points for every dollar, on delivery"
                    else -> "1 point for every dollar, on delivery"
                },
            )
        val plus =
            if (membership != null) {
                AccountTile(
                    AccountTileKind.Plus,
                    "Haul Plus",
                    money(membership.savedCents),
                    listOfNotNull(
                        "Saved on delivery this year",
                        membership.renews?.let { "renews " + MONTH_DAY.format(it) },
                    ).joinToString(" · "),
                )
            } else {
                // The trial is feature-membership's (B-23); like the home page's offer, the button has no action yet.
                AccountTile(
                    AccountTileKind.PlusOffer,
                    "Haul Plus",
                    "30 days free",
                    "Free delivery and double points, then $4.99 / month",
                    accent = "free",
                    button = Link("Try 30 days free"),
                )
            }
        val drops = view.saved.priceDrops
        val dropped =
            AccountTile(
                AccountTileKind.PriceDrops,
                "Price drops",
                count(drops),
                when (drops) {
                    0 -> "Nothing in your Saved list got cheaper"
                    1 -> "An item in your Saved list got cheaper"
                    else -> "Items in your Saved list got cheaper"
                },
            )
        return listOf(points, plus, dropped)
    }

    /** Up to three orders on their way, newest first, or the sentence that there are none. */
    private fun activeOrders(): ActiveOrders =
        ActiveOrders(
            title = "Active orders",
            orders = active.take(ACTIVE_SHOWN).mapNotNull(::card),
            empty = "No active orders right now.".takeIf { active.isEmpty() },
        )

    private fun card(order: AccountOrder): ActiveOrder? {
        val card = order.card ?: return null
        return ActiveOrder(
            id = order.id,
            meta = card.meta,
            title = card.title,
            accent = card.accent,
            lead = card.lead,
            tones = if (card.pickupCode == null) order.tones else emptyList(),
            steps = card.steps,
            pickup = card.pickupCode?.let { AccountPickup("Pickup code", it) },
            details = Link("Details", NavigateAction(OrderPaths.page(order.id))),
        )
    }

    /**
     * The overview's history: the last four orders no longer on their way, and «All orders» whenever that
     * leaves one out — those above it among the active, or older ones. None while nothing has arrived.
     */
    private fun recent(): OrderHistory? {
        val past = view.orders.filterNot { it.state.active }
        if (past.isEmpty()) return null
        val shown = past.take(RECENT_SHOWN)
        return OrderHistory(
            title = "Order history",
            all = Link("All orders", NavigateAction(Frame.ORDERS)).takeIf { shown.size < view.orders.size },
            rows = shown.map(::row),
        )
    }

    /**
     * The orders' page: every order, those on their way first, then newest first (`Account_Orders`), under
     * the chips with their counts; a customer who never ordered sees the empty state and no chips.
     */
    private fun history(filter: HistoryFilterKey): OrderHistory {
        if (view.orders.isEmpty()) {
            return OrderHistory(
                empty =
                    EmptyState(
                        id = "no-orders",
                        title = "No orders yet",
                        text = "When you place an order, you can follow it here — from packing to your door.",
                        actionLabel = "See today’s deals",
                        action = NavigateAction(Frame.DEALS),
                        accent = "yet",
                        primary = true,
                    ),
            )
        }
        val rows = view.orders.sortedBy { !it.state.active }.filter { filter.matches(it.state) }
        return OrderHistory(
            filters =
                HistoryFilterKey.entries.map { key ->
                    HistoryFilter(
                        label = key.label,
                        count = count(view.orders.count { key.matches(it.state) }),
                        selected = key == filter,
                        action = NavigateAction(AccountPaths.orders(key)),
                    )
                },
            rows = rows.map(::row),
            none = "No ${filter.label.lowercase()} orders.".takeIf { rows.isEmpty() },
        )
    }

    /** «Sep 24 · #HL-46102 · tiles · $103.00 · Delivered · Reorder». */
    private fun row(order: AccountOrder): HistoryRow {
        val state = order.state
        val reorder = state == OrderState.Delivered || state == OrderState.PickedUp
        val label =
            when {
                reorder -> "Reorder"
                state.active && state != OrderState.ReadyForPickup -> "Track"
                else -> "Details"
            }
        return HistoryRow(
            id = order.id,
            date = MONTH_DAY.format(order.placedAt.atZoneSameInstant(STORE)),
            number = "#${order.id}",
            tones = order.tones,
            total = CartScreen.exact(order.totalCents),
            status = state.label,
            statusKind = state.kind,
            actionLabel = label,
            action = if (reorder) null else NavigateAction(OrderPaths.page(order.id)),
            reorderUrl = if (reorder) OrderPaths.reorder(order.id) else null,
        )
    }

    /** «MK» for «Maya Kowalski»: the first letters of the first two words. */
    private fun initials(name: String): String =
        name
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }

    private companion object {
        val STORE = DeliveryCalendar.STORE
        val MONTH_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
        val MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)

        /** Acid, a member's avatar (canvas.json's `secondaryContainer`). */
        const val ACID = "#DFFF3A"

        /** Feature-account: «the overview shows up to 3 active orders … and the last 4 of the history». */
        const val ACTIVE_SHOWN = 3
        const val RECENT_SHOWN = 4
    }
}
