package io.github.youndie.haul.feature.catalog.screen

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.feature.account.domain.Loyalty
import io.github.youndie.haul.feature.catalog.domain.Campaign
import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.domain.Category
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.Listed
import io.github.youndie.haul.feature.catalog.domain.PriceList
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.feature.catalog.domain.SaleFigures
import io.github.youndie.haul.feature.catalog.domain.liveSale
import io.github.youndie.haul.feature.membership.screen.PlusOffer
import io.github.youndie.haul.feature.recommendations.screen.PickedSection
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.ui.CampaignHero
import io.github.youndie.haul.ui.CampaignRow
import io.github.youndie.haul.ui.CategoryGrid
import io.github.youndie.haul.ui.CategoryTile
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.PromoBanner
import io.github.youndie.haul.ui.SectionHeader
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.NavigateAction
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * `/ui/home` (screen-home). Everybody sees the campaign and its banners — the hero while the sale is live for
 * the viewer, with the sale's figures as the catalogue holds them (B-172), each campaign's banner until its campaign
 * ends (B-59), then Haul Plus's — the categories, the deals of the day — while a
 * deal is live: with none, the section is not drawn (B-58), and «Popular on Haul» takes its place, so a guest's
 * home always has a row of products (B-70) — and the Plus block: the offer to a guest and a
 * non-member — whose «Try 30 days free» is sign-in or the trial's dialog — and a member's savings and renewal
 * (feature-membership, [PlusOffer]). A customer sees «Picked for you» ([PickedSection],
 * feature-recommendations) after it, where the canvas puts it, without the products a row above already draws:
 * a product is drawn once on a page, whichever row draws it first (B-96).
 */
internal class HomeScreen(
    private val catalog: CatalogRepository,
    private val calendar: DeliveryCalendar,
    private val photos: ProductPhotos,
    private val loyalty: Loyalty,
    private val picked: PickedSection,
    private val clock: StoreClock,
) {
    suspend fun build(viewer: Viewer): KompotComponent {
        val categories = catalog.categories()
        val topLevel = categories.filter { it.parentSlug == null }.sortedBy { it.position }
        val campaigns = catalog.campaigns()
        val at = clock.now().toOffsetDateTime()

        val sections = mutableListOf<KompotComponent>()
        // The row is the hero's: with the sale over (or not yet open to this viewer) nothing announces it,
        // and its banners go with it — in the seed the sale outlives both banners, so none is lost (B-59).
        liveSale(campaigns, viewer.prices, at)?.let { sale ->
            val figures = saleFigures(catalog, categories, viewer.prices)
            val banners =
                campaigns.filter { it.slug != sale.slug && !it.endedAt(at) }.map(::banner) + PlusOffer.banner(viewer)
            sections += CampaignRow("campaigns", hero(sale, figures), banners.take(BANNERS))
        }
        sections +=
            SectionHeader(
                "categories-title",
                "Shop by category",
                linkLabel = "All ${topLevel.size} categories",
                action = NavigateAction(Frame.CATALOG),
                accent = "category",
                compactLinkLabel = "All ${topLevel.size}",
            )
        sections +=
            CategoryGrid(
                id = "categories",
                tiles =
                    topLevel.take(CATEGORY_TILES).map {
                        CategoryTile(
                            "tile-${it.slug}",
                            it.name,
                            it.tone,
                            photos.category(it),
                            categoryLink(it, categories),
                        )
                    },
            )
        val deals = dealsOfTheDay(catalog, calendar, photos, viewer)
        if (deals != null) {
            sections +=
                SectionHeader(
                    "deals-title",
                    "Deals of the day",
                    linkLabel = "View all deals",
                    countdownEndsAt = deals.endsAt,
                    action = NavigateAction(Frame.DEALS),
                    accent = "day",
                )
            sections += ProductGrid(id = "deals", columns = DEAL_COLUMNS, scroll = true, cards = deals.cards)
        } else {
            val popular = popular(catalog, calendar, photos, viewer, categories, viewer.prices)
            if (popular.isNotEmpty()) {
                sections += popularTitle()
                sections += ProductGrid(id = POPULAR, columns = DEAL_COLUMNS, scroll = true, cards = popular)
            }
        }
        sections += PlusOffer.block(viewer, viewer.customer?.let { loyalty.standing(it) })
        sections += picked.build(viewer, onPage = productsIn(sections))
        return Frame.page("home", viewer, navigation(categories), sections, Frame.HOME_TITLE, footer = true)
    }

    /** The products the rows in [sections] draw, whichever rows those are. */
    private fun productsIn(sections: List<KompotComponent>): Set<String> =
        sections
            .filterIsInstance<ProductGrid>()
            .flatMap { grid -> grid.cards.map { it.productId } }
            .toSet()

    /**
     * The sale's hero, its figures — «Up to {deepest}», «{items} marked down across {categories}» — written in from
     * what is on sale for the viewer ([SaleFigures], B-172), the deals page it opens counting the same list.
     */
    private fun hero(
        campaign: Campaign,
        figures: SaleFigures,
    ): CampaignHero {
        val title = figures.fill(campaign.title)
        return CampaignHero(
            id = "campaign-${campaign.slug}",
            eyebrow = "${campaign.name} · ${dates(campaign)}",
            title = title,
            subtitle = figures.fill(campaign.subtitle),
            actionLabel = "Shop the sale",
            tone = campaign.tone,
            image = photos.hero(campaign),
            action = NavigateAction(Frame.DEALS),
            accent = title.substringBeforeLast(' ', "").ifEmpty { null },
        )
    }

    /**
     * A campaign's banner opens what its title names (B-172): the campaign's [Campaign.link] — Tech week's «Computers
     * from $399» the computers from $399, cheapest first — or, with none, the deals page (B-59: a campaign has no page
     * of its own, `skus.campaign_slug` being the sale's alone). It opened `/deals` whatever it said: «Laptops from
     * $399» led to headphones and duvet covers, in a store that sells no laptops.
     */
    private fun banner(campaign: Campaign) =
        PromoBanner(
            id = "banner-${campaign.slug}",
            eyebrow = campaign.subtitle,
            title = campaign.title,
            tone = campaign.tone,
            accent = campaign.title.substringAfterLast(' '),
            image = photos.banner(campaign),
            action = NavigateAction(campaign.link ?: Frame.DEALS),
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

        /** Two banners beside the hero: the campaigns' first, then Haul Plus's ([PlusOffer.banner]). */
        private const val BANNERS = 2
        private const val DEAL_COLUMNS = 6

        /** The ids of the row drawn while no deal is live (B-70), on home and in the empty cart (B-87). */
        const val POPULAR_TITLE = "popular-title"
        const val POPULAR = "popular"

        /** How many products the popular row holds: a row of six, as the deals it stands in for. */
        const val POPULAR_SIZE = DEAL_COLUMNS
        private val MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    }
}

/**
 * The row drawn in the deals' place while no deal is live (B-70): a guest's home has no other row of products,
 * and with none it is a hero, banners and tiles over nothing; the empty cart draws the same six in its picks'
 * place (B-87). The most reviewed products in stock among [categories] — a measure that needs no window, so it is
 * there whatever the sale's calendar — drawn as every card is for [viewer], at [prices]: the empty cart passes the
 * cart owner's, which a command's answer has and its viewer may not (B-53). [inPlace] is [card]'s: the empty cart's
 * «+» redraws the page (B-95).
 */
internal suspend fun popular(
    catalog: CatalogRepository,
    calendar: DeliveryCalendar,
    photos: ProductPhotos,
    viewer: Viewer,
    categories: List<Category>,
    prices: PriceList,
    inPlace: Boolean = true,
): List<ProductCard> =
    catalog
        .listedIn(categories.map { it.slug }.toSet(), prices)
        .filter { it.inStock }
        .sortedWith(compareByDescending<Listed> { it.product.reviewsCount }.thenBy { it.product.id })
        .take(HomeScreen.POPULAR_SIZE)
        .map { card(it, calendar, photos, viewer, inPlace = inPlace) }

/**
 * The sale's figures for a viewer at [prices] (B-172): [SaleFigures] of every product's prices, by the rule the deals
 * page lists what is on sale by ([onSale]), so the hero and that page cannot count apart.
 */
internal suspend fun saleFigures(
    catalog: CatalogRepository,
    categories: List<Category>,
    prices: PriceList,
): SaleFigures = SaleFigures.of(catalog.priced(prices), categories)

/**
 * The popular row's header, «Popular on Haul», on home and in the empty cart alike (B-87): the title names what the
 * row holds, and in the cart that is not a pick from today's deals.
 */
internal fun popularTitle(): SectionHeader = SectionHeader(HomeScreen.POPULAR_TITLE, "Popular on Haul", accent = "Haul")

/** The header's categories ([Frame.page]): every top-level category, in order, with its page. */
internal fun navigation(categories: List<Category>): List<Link> =
    categories
        .filter { it.parentSlug == null }
        .sortedBy { it.position }
        .map { Link(it.name, categoryLink(it, categories)) }
