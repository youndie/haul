package io.github.youndie.haul.feature.saved.screen

import io.github.youndie.haul.feature.catalog.domain.Browse
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.feature.catalog.domain.count
import io.github.youndie.haul.feature.catalog.domain.money
import io.github.youndie.haul.feature.catalog.screen.card
import io.github.youndie.haul.feature.catalog.screen.pageNumbers
import io.github.youndie.haul.feature.saved.SavedPaths
import io.github.youndie.haul.feature.saved.domain.SavedEntry
import io.github.youndie.haul.feature.saved.domain.SavedListing
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.ui.EmptyState
import io.github.youndie.haul.ui.HaulPagination
import io.github.youndie.haul.ui.HistoryFilter
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.SavedList
import io.github.youndie.haul.ui.SavedStep
import io.github.youndie.kompot.standard.NavigateAction

/**
 * The Saved list under the account's title (screen-saved, feature-account, B-20): the customer's saved
 * products newest first, [Browse.PAGE_SIZE] to a page, under the filter «All» / «Price dropped»; each card
 * with its heart filled and, on a product cheaper than on the day it was saved, «Price dropped −$200»
 * (that price minus the cheapest in stock now). Nothing saved is the empty state and how the heart works.
 * The copy is the canvas's (`Saved_*`).
 */
internal class SavedScreen(
    private val listing: SavedListing,
    private val calendar: DeliveryCalendar,
    private val photos: ProductPhotos,
) {
    /** The list of [customerId] under [filter], at [page] — or its last page, when there are fewer. */
    suspend fun list(
        customerId: String,
        filter: SavedFilter,
        page: Int,
        viewer: Viewer,
    ): SavedList = list(listing.entries(customerId), filter, page, viewer)

    /** The list drawn from [entries], newest first; everything it shows is in them. */
    fun list(
        entries: List<SavedEntry>,
        filter: SavedFilter,
        page: Int,
        viewer: Viewer,
    ): SavedList {
        if (entries.isEmpty()) return SavedList(empty = EMPTY, steps = STEPS)
        val shown = entries.filter(filter::matches)
        val pages = maxOf(1, (shown.size + Browse.PAGE_SIZE - 1) / Browse.PAGE_SIZE)
        val current = page.coerceIn(1, pages)
        return SavedList(
            filters =
                SavedFilter.entries.map { key ->
                    HistoryFilter(
                        label = key.label,
                        count = count(entries.count(key::matches)),
                        selected = key == filter,
                        action = NavigateAction(SavedPaths.page(key)),
                    )
                },
            cards =
                shown
                    .drop((current - 1) * Browse.PAGE_SIZE)
                    .take(Browse.PAGE_SIZE)
                    .map { card(it, viewer) },
            pagination = if (pages > 1) pagination(filter, current, pages) else null,
            none = "Nothing in your Saved list got cheaper yet.".takeIf { shown.isEmpty() },
        )
    }

    /** A saved product's card: in the list, so its heart is filled, and marked when it got cheaper. */
    private fun card(
        entry: SavedEntry,
        viewer: Viewer,
    ): ProductCard =
        card(entry.item, calendar, photos, viewer.copy(saved = viewer.saved + entry.item.product.id))
            .copy(drop = entry.dropCents?.let { "Price dropped −" + money(it) })

    /** The page numbers, each to its own address under the same filter; no «Show more», as the canvas draws it. */
    private fun pagination(
        filter: SavedFilter,
        current: Int,
        pages: Int,
    ): HaulPagination {
        val numbers = pageNumbers(pages)
        return HaulPagination(
            id = "pagination",
            current = current,
            pages = numbers,
            links =
                numbers.mapNotNull { label ->
                    label.toIntOrNull()?.takeIf { it != current }?.let {
                        Link(label, NavigateAction(SavedPaths.page(filter, it)))
                    }
                },
        )
    }

    companion object {
        /** `Saved_Empty`: nothing saved yet, and the way to the deals. */
        val EMPTY =
            EmptyState(
                id = "saved-empty",
                title = "Nothing saved yet",
                accent = "yet",
                text = "Keep products here to come back to them, and to see when they get cheaper.",
                actionLabel = "Browse deals",
                action = NavigateAction(Frame.DEALS),
                primary = true,
            )

        /** How the heart works, under the empty state: the canvas's three cards. */
        val STEPS =
            listOf(
                SavedStep("01", "Tap the heart on any product", "On a card or on the product page."),
                SavedStep("02", "It lands here, newest first", "Up to 24 per page, filter by price drops."),
                SavedStep(
                    "03",
                    "We mark what got cheaper",
                    "«Price dropped» compares with the price on the day you saved it.",
                ),
            )
    }
}

/**
 * The list's filters, as the chips name them and as the query string carries them
 * (`/account/saved?filter=price-dropped`); [All] is the list without the parameter.
 */
internal enum class SavedFilter(
    val parameter: String?,
    val label: String,
) {
    All(null, "All"),
    PriceDropped("price-dropped", "Price dropped"),
    ;

    fun matches(entry: SavedEntry): Boolean =
        when (this) {
            All -> true
            PriceDropped -> entry.dropCents != null
        }

    companion object {
        /** The filter a `filter` parameter names; none, or one the list does not have, is all of them. */
        fun of(parameter: String?): SavedFilter = entries.firstOrNull { it.parameter == parameter } ?: All
    }
}
