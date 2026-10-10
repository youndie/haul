package io.github.youndie.haul.feature.catalog.screen

import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.Listed
import io.github.youndie.haul.feature.catalog.domain.Page
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.feature.catalog.domain.count
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Parts
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.ui.PageTitle
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.SectionHeader
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.NavigateAction

/**
 * `/ui/deals` (B-37): where «Deals», «View all deals», «Shop the sale» and the empty cart's «See today's
 * deals» go. On the first page today's deals with their countdown, as on the home page, while a deal is
 * live (B-58: with none, no section); then everything on sale — a shown price under its old one — the
 * deepest discount first, paged like a category.
 *
 * Built from the components the other screens draw; no artboard draws this page (B-37's findings).
 */
internal class DealsScreen(
    private val catalog: CatalogRepository,
    private val calendar: DeliveryCalendar,
    private val photos: ProductPhotos,
) {
    /** The deals page whose grid holds [pages]: one page, or those «Show N more» appended (B-77). */
    suspend fun build(
        pages: IntRange,
        viewer: Viewer,
    ): KompotComponent {
        val categories = catalog.categories()
        val onSale =
            catalog
                .listedIn(categories.map { it.slug }.toSet(), viewer.prices)
                .filter { markdown(it) > 0.0 }
                .sortedWith(
                    compareByDescending<Listed> { markdown(it) }
                        .thenByDescending { it.product.reviewsCount }
                        .thenBy { it.product.id },
                )
        val shown = Page.of(onSale, pages.last, pages.first)
        val sections = mutableListOf<KompotComponent>()
        sections += PageTitle("title", "Deals", "${count(onSale.size)} items on sale")
        val deals = if (shown.from == 1) dealsOfTheDay(catalog, calendar, photos, viewer) else null
        if (deals != null) {
            sections += SectionHeader("deals-title", "Deals of the day", countdownEndsAt = deals.endsAt, accent = "day")
            sections += ProductGrid(TODAY, deals.cards, columns = DEAL_COLUMNS)
        }
        sections += SectionHeader("sale-title", "On sale", accent = "sale")
        sections +=
            ProductGrid("grid", shown.items.map { card(it, calendar, photos, viewer) }, columns = GRID_COLUMNS)
        // A page loads in place (B-63) unless either side of the press has the deals of the day — a grid from
        // the first page: an `update` replaces nodes, it cannot take them away or add a section the page drawn
        // does not have. «Show N more» from the first page opens its address, the same screen (B-62): the page
        // is kept while it loads, and the cards above stay where they were.
        sections +=
            pagination(shown, ::address) { to, address ->
                if (deals == null && to.first > 1) Parts.load(address) else NavigateAction(address)
            }
        return Frame.page("deals-page", viewer, navigation(categories), sections, footer = true)
    }

    /** How far the shown price is under the old one, as a fraction of the old; 0 when it is not. */
    private fun markdown(item: Listed): Double {
        val old = item.shown.oldPriceCents ?: return 0.0
        return if (old > item.shown.priceCents) (old - item.shown.priceCents).toDouble() / old else 0.0
    }

    /** The deals' address with the grid holding [pages]. */
    private fun address(pages: IntRange): String {
        val query =
            listOfNotNull(
                if (pages.last > 1) "page=${pages.last}" else null,
                if (pages.first < pages.last) "${CatalogUrl.FROM}=${pages.first}" else null,
            )
        return Frame.DEALS + if (query.isEmpty()) "" else "?" + query.joinToString("&")
    }

    companion object {
        /** Today's deals, the grid the first page draws while a deal is live and no other page draws. */
        const val TODAY = "deals"

        /** What a page of the deals changes when it loads in place (B-63): the grid and the pages. */
        val PARTS = listOf("grid", "pagination")

        private const val DEAL_COLUMNS = 6
        private const val GRID_COLUMNS = 5
    }
}
