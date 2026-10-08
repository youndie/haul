package io.github.youndie.haul.feature.catalog.screen

import io.github.youndie.haul.feature.catalog.domain.Campaign
import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.ui.CampaignHero
import io.github.youndie.haul.ui.CategoryTile
import io.github.youndie.haul.ui.PlusBlock
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.PromoBanner
import io.github.youndie.haul.ui.SectionHeader
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.RowComponent
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * `/ui/home` (screen-home). A guest sees the campaign, the banners, the categories, the deals of the
 * day and the Plus offer; «Picked for you» and the member's Plus block arrive with feature-recommendations
 * and feature-membership.
 */
internal class HomeScreen(
    private val catalog: CatalogRepository,
    private val calendar: DeliveryCalendar,
) {
    suspend fun build(viewer: Viewer): KompotComponent {
        val categories = catalog.categories()
        val topLevel = categories.filter { it.parentSlug == null }.sortedBy { it.position }
        val campaigns = catalog.campaigns()
        val deals = catalog.deals()
        val dealItems = catalog.listed(deals.map { deal -> deal.skuId.substringBeforeLast('-') })

        val sections = mutableListOf<KompotComponent>()
        campaigns.firstOrNull()?.let { sections += hero(it) }
        campaigns.drop(1).take(2).forEach { sections += banner(it) }
        sections += SectionHeader("categories-title", "Shop by category", linkLabel = "All ${topLevel.size} categories")
        sections +=
            RowComponent(
                id = "categories",
                children =
                    topLevel.take(CATEGORY_TILES).map {
                        CategoryTile("tile-${it.slug}", it.name, it.tone, it.label, categoryLink(it.slug))
                    },
                spacing = 16,
            )
        sections +=
            SectionHeader(
                "deals-title",
                "Deals of the day",
                linkLabel = "View all deals",
                countdownEndsAt = calendar.midnight(),
            )
        sections +=
            ProductGrid(
                id = "deals",
                columns = DEAL_COLUMNS,
                cards =
                    deals.mapNotNull { deal ->
                        val item =
                            dealItems.firstOrNull { item -> item.skus.any { it.id == deal.skuId } }
                                ?: return@mapNotNull null
                        val sku = item.skus.first { it.id == deal.skuId }
                        val old = if (deal.priceCents < sku.priceCents) sku.priceCents else sku.oldPriceCents
                        card(item, calendar, priceCents = deal.priceCents, oldCents = old)
                    },
            )
        if (viewer.firstName == null) sections += PLUS_OFFER
        return Frame.page("home", viewer, navigation(categories), sections, footer = true)
    }

    private fun hero(campaign: Campaign) =
        CampaignHero(
            id = "campaign-${campaign.slug}",
            eyebrow = "Autumn mega sale · ${dates(campaign)}",
            title = campaign.title,
            subtitle = campaign.subtitle,
            actionLabel = "Shop the sale",
            tone = campaign.tone,
            label = "campaign image",
            action = NavigateAction("/deals"),
        )

    private fun banner(campaign: Campaign) =
        PromoBanner(
            id = "banner-${campaign.slug}",
            eyebrow = campaign.subtitle,
            title = campaign.title,
            tone = campaign.tone,
        )

    /** «Oct 7 — 14»: the canvas's way of writing a window inside one month. */
    private fun dates(campaign: Campaign): String {
        val start = campaign.startsAt.atZoneSameInstant(DeliveryCalendar.STORE)
        val last = campaign.endsAt.atZoneSameInstant(DeliveryCalendar.STORE).minusDays(1)
        return MONTH_DAY.format(start) + " — " +
            if (start.month == last.month) last.dayOfMonth else MONTH_DAY.format(last)
    }

    companion object {
        private const val CATEGORY_TILES = 8
        private const val DEAL_COLUMNS = 6
        private val MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US)

        val PLUS_OFFER =
            PlusBlock(
                id = "plus",
                member = false,
                title = "Free delivery. Every order.",
                benefits =
                    listOf(
                        "Next-day delivery with no minimum",
                        "Early access to sales",
                        "Double points on every purchase",
                    ),
                offer = "Try 30 days free",
                price = "then $4.99 / month",
            )
    }
}

/** The category row of the header: the first ten top-level categories. */
internal fun navigation(categories: List<io.github.youndie.haul.feature.catalog.domain.Category>): List<String> =
    categories
        .filter { it.parentSlug == null }
        .sortedBy { it.position }
        .take(10)
        .map { it.name }
