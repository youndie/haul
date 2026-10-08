package io.github.youndie.haul.feature.catalog.screen

import io.github.youndie.haul.feature.account.domain.Loyalty
import io.github.youndie.haul.feature.catalog.domain.Campaign
import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.domain.Category
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.feature.membership.screen.PlusOffer
import io.github.youndie.haul.feature.recommendations.screen.PickedSection
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.ui.CampaignHero
import io.github.youndie.haul.ui.CampaignRow
import io.github.youndie.haul.ui.CategoryGrid
import io.github.youndie.haul.ui.CategoryTile
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.PromoBanner
import io.github.youndie.haul.ui.SectionHeader
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.NavigateAction
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * `/ui/home` (screen-home). Everybody sees the campaign, the banners, the categories, the deals of the
 * day and the Plus block: the offer to a guest and a non-member — whose «Try 30 days free» is sign-in or
 * the trial's dialog — and a member's savings and renewal (feature-membership, [PlusOffer]). A customer
 * sees «Picked for you» ([PickedSection], feature-recommendations) after it, where the canvas puts it.
 */
internal class HomeScreen(
    private val catalog: CatalogRepository,
    private val calendar: DeliveryCalendar,
    private val photos: ProductPhotos,
    private val loyalty: Loyalty,
    private val picked: PickedSection,
) {
    suspend fun build(viewer: Viewer): KompotComponent {
        val categories = catalog.categories()
        val topLevel = categories.filter { it.parentSlug == null }.sortedBy { it.position }
        val campaigns = catalog.campaigns()

        val sections = mutableListOf<KompotComponent>()
        campaigns.firstOrNull()?.let { first ->
            sections += CampaignRow("campaigns", hero(first), campaigns.drop(1).take(2).map(::banner))
        }
        sections +=
            SectionHeader(
                "categories-title",
                "Shop by category",
                linkLabel = "All ${topLevel.size} categories",
                accent = "category",
                compactLinkLabel = "All ${topLevel.size}",
            )
        sections +=
            CategoryGrid(
                id = "categories",
                tiles =
                    topLevel.take(CATEGORY_TILES).map {
                        CategoryTile("tile-${it.slug}", it.name, it.tone, it.label, categoryLink(it.slug))
                    },
            )
        sections +=
            SectionHeader(
                "deals-title",
                "Deals of the day",
                linkLabel = "View all deals",
                countdownEndsAt = calendar.midnight(),
                action = NavigateAction(Frame.DEALS),
                accent = "day",
            )
        sections +=
            ProductGrid(
                id = "deals",
                columns = DEAL_COLUMNS,
                scroll = true,
                cards = dealCards(catalog, calendar, photos, viewer),
            )
        sections += PlusOffer.block(viewer, viewer.customer?.let { loyalty.standing(it) })
        sections += picked.build(viewer)
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
            action = NavigateAction(Frame.DEALS),
            accent = campaign.title.substringBeforeLast(' ', "").ifEmpty { null },
        )

    private fun banner(campaign: Campaign) =
        PromoBanner(
            id = "banner-${campaign.slug}",
            eyebrow = campaign.subtitle,
            title = campaign.title,
            tone = campaign.tone,
            accent = campaign.title.substringAfterLast(' '),
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
    }
}

/** The header's categories ([Frame.page]): every top-level category, in order, with its page. */
internal fun navigation(categories: List<Category>): List<Link> =
    categories
        .filter { it.parentSlug == null }
        .sortedBy { it.position }
        .map { Link(it.name, categoryLink(it.slug)) }
