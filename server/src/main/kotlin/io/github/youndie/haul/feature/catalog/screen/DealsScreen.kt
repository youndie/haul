package io.github.youndie.haul.feature.catalog.screen

import io.github.youndie.haul.feature.catalog.domain.Browse
import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.Listed
import io.github.youndie.haul.feature.catalog.domain.Page
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.feature.catalog.domain.count
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.ui.PageTitle
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.SectionHeader
import io.github.youndie.kompot.KompotComponent

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
    suspend fun build(
        page: Int,
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
        val shown = paged(onSale, page)
        val sections = mutableListOf<KompotComponent>()
        sections += PageTitle("title", "Deals", "${count(onSale.size)} items on sale")
        val deals = if (shown.page == 1) dealsOfTheDay(catalog, calendar, photos, viewer) else null
        if (deals != null) {
            sections += SectionHeader("deals-title", "Deals of the day", countdownEndsAt = deals.endsAt, accent = "day")
            sections += ProductGrid("deals", deals.cards, columns = DEAL_COLUMNS)
        }
        sections += SectionHeader("sale-title", "On sale", accent = "sale")
        sections +=
            ProductGrid("grid", shown.items.map { card(it, calendar, photos, viewer) }, columns = GRID_COLUMNS)
        sections += pagination(shown) { if (it > 1) "${Frame.DEALS}?page=$it" else Frame.DEALS }
        return Frame.page("deals", viewer, navigation(categories), sections, footer = true)
    }

    /** How far the shown price is under the old one, as a fraction of the old; 0 when it is not. */
    private fun markdown(item: Listed): Double {
        val old = item.shown.oldPriceCents ?: return 0.0
        return if (old > item.shown.priceCents) (old - item.shown.priceCents).toDouble() / old else 0.0
    }

    private fun paged(
        all: List<Listed>,
        page: Int,
    ): Page {
        val pages = maxOf(1, (all.size + Browse.PAGE_SIZE - 1) / Browse.PAGE_SIZE)
        return Page(all.drop((page - 1) * Browse.PAGE_SIZE).take(Browse.PAGE_SIZE), all.size, page, pages)
    }

    companion object {
        private const val DEAL_COLUMNS = 6
        private const val GRID_COLUMNS = 5
    }
}
