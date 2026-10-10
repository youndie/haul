package io.github.youndie.haul.ui

import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.commands.LoadAction
import io.github.youndie.kompot.registry.KompotComponentMarker
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The pieces of the Home and Category screens (feature-browse). Copy arrives formatted; a link is a
// kompot action the client follows, never a URL it builds — the price range's two numbers excepted
// (`FacetRange`, B-69).
//
// An `accent` is the words of a title the canvas draws in Bodoni Moda's italic («Shop by *category*»);
// it must occur in the title, and a title without one is drawn upright throughout.

/**
 * The campaign that opens the home page. When [title] starts with [accent], the accent is the italic
 * lead line («Up to») and the rest is the figure («−70%»).
 */
@Serializable
@SerialName("haul_campaign_hero")
@KompotComponentMarker
public data class CampaignHero(
    override val id: String,
    val eyebrow: String,
    val title: String,
    val subtitle: String,
    val actionLabel: String,
    val tone: String,
    val label: String,
    val action: @Polymorphic KompotAction? = null,
    val accent: String? = null,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/** A smaller banner beside the campaign. */
@Serializable
@SerialName("haul_promo_banner")
@KompotComponentMarker
public data class PromoBanner(
    override val id: String,
    val eyebrow: String,
    val title: String,
    val subtitle: String? = null,
    val actionLabel: String? = null,
    val tone: String,
    val label: String? = null,
    val action: @Polymorphic KompotAction? = null,
    val accent: String? = null,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/**
 * The top of the home page: the campaign, and the banners beside it on a wide page or under it in a
 * row on a phone. One component because the two widths arrange the same three blocks differently.
 */
@Serializable
@SerialName("haul_campaign_row")
@KompotComponentMarker
public data class CampaignRow(
    override val id: String,
    val hero: CampaignHero,
    val banners: List<PromoBanner>,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/**
 * A section's heading: the title, an optional subtitle, an optional link, and — for deals of the day —
 * the instant the countdown runs to, ISO-8601; the client counts down, the server never sends a
 * remaining time.
 */
@Serializable
@SerialName("haul_section_header")
@KompotComponentMarker
public data class SectionHeader(
    override val id: String,
    val title: String,
    val subtitle: String? = null,
    val linkLabel: String? = null,
    val countdownEndsAt: String? = null,
    val action: @Polymorphic KompotAction? = null,
    val accent: String? = null,
    /** What the link reads on a phone («All 32»); `null` leaves the link out there. */
    val compactLinkLabel: String? = null,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

@Serializable
@SerialName("haul_category_tile")
@KompotComponentMarker
public data class CategoryTile(
    override val id: String,
    val name: String,
    val tone: String,
    val label: String,
    val action: @Polymorphic KompotAction? = null,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/** The category tiles of the home page: one row of eight on a wide page, rows of four on a phone. */
@Serializable
@SerialName("haul_category_grid")
@KompotComponentMarker
public data class CategoryGrid(
    override val id: String,
    val tiles: List<CategoryTile>,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/**
 * A row or a grid of product cards; [columns] is the desktop count, the phone draws two — or, when
 * [scroll] is set, one row that scrolls sideways.
 */
@Serializable
@SerialName("haul_product_grid")
@KompotComponentMarker
public data class ProductGrid(
    override val id: String,
    val cards: List<ProductCard>,
    val columns: Int,
    val scroll: Boolean = false,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/**
 * Haul Plus. [member] is the member form — [savings] and [renewal] — and otherwise the offer:
 * [benefits], [offer] and [price].
 */
@Serializable
@SerialName("haul_plus_block")
@KompotComponentMarker
public data class PlusBlock(
    override val id: String,
    val member: Boolean,
    val title: String,
    val benefits: List<String> = emptyList(),
    val offer: String? = null,
    val price: String? = null,
    val savings: String? = null,
    val renewal: String? = null,
    val action: @Polymorphic KompotAction? = null,
    val accent: String? = null,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

@Serializable
public data class FooterColumn(
    val title: String,
    val links: List<String>,
)

@Serializable
@SerialName("haul_footer")
@KompotComponentMarker
public data class HaulFooter(
    override val id: String,
    val columns: List<FooterColumn>,
    val appTitle: String,
    val appText: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

@Serializable
public data class Crumb(
    val label: String,
    val action: @Polymorphic KompotAction? = null,
)

/** The path above a page title; the last crumb is the current page. */
@Serializable
@SerialName("haul_breadcrumbs")
@KompotComponentMarker
public data class Breadcrumbs(
    override val id: String,
    val crumbs: List<Crumb>,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/**
 * A page's title with the count beside it («Headphones · 12,408 items»); or, [quoted], a search's:
 * the query in quotes with the count above it («14,870 results», «“Running shoes”»); or, [badge], the
 * cart's: the count in a pill at the title's top right («Cart 3 items»), where an empty cart has none.
 */
@Serializable
@SerialName("haul_page_title")
@KompotComponentMarker
public data class PageTitle(
    override val id: String,
    val title: String,
    val count: String? = null,
    val quoted: Boolean = false,
    val badge: Boolean = false,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

@Serializable
public data class Chip(
    val label: String,
    val selected: Boolean,
    val action: @Polymorphic KompotAction? = null,
    /** How many results the chip stands for («Running shoes 1,204»), on a search's category chips. */
    val count: String? = null,
)

/** The pill row under a title: the kinds of a category, or the categories of a search. */
@Serializable
@SerialName("haul_filter_chips")
@KompotComponentMarker
public data class FilterChips(
    override val id: String,
    val chips: List<Chip>,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

@Serializable
public data class FacetOption(
    val label: String,
    val count: Int? = null,
    val selected: Boolean,
    /** `#RRGGBB` for a colour swatch. */
    val swatch: String? = null,
    val action: @Polymorphic KompotAction? = null,
)

/**
 * One block of the facet column. [kind] is how it is drawn: `range` (price: [min], [max]),
 * `checkbox` (brand), `toggle` (delivery), `radio` (rating), `swatch` (colour), `pills` (features).
 *
 * [moreLabel] («Show 14 more») says how many options the block leaves out, and [moreAction] is where it
 * goes (B-49): the same page with the block expanded — every option listed, the filters, the sort and
 * the page kept — after which the block has neither.
 */
@Serializable
public data class Facet(
    val key: String,
    val title: String,
    val kind: String,
    val options: List<FacetOption> = emptyList(),
    val min: String? = null,
    val max: String? = null,
    val moreLabel: String? = null,
    val moreAction: @Polymorphic KompotAction? = null,
    /** Where the price range's selection starts and ends on the slider, as fractions of its track. */
    val rangeStart: Float? = null,
    val rangeEnd: Float? = null,
    /** What the `range` block's fields and thumbs edit, and where an edited range goes (B-69). */
    val range: FacetRange? = null,
)

/**
 * The price range as the shopper edits it (B-69): the applied bounds in whole dollars — `null` is no bound —
 * the dollars at the slider's right end ([top]; the left end is $0), and [template], the address of the
 * `load` that applies a range, with [LOW] and [HIGH] where the bounds go.
 *
 * The one address the client completes rather than follows: a bound is any whole number the shopper types,
 * so no list of `load`s could hold them. The server still writes the whole address — the category, every
 * other filter, the sort — and the client only puts two numbers into it ([applying]).
 */
@Serializable
public data class FacetRange(
    val low: Int? = null,
    val high: Int? = null,
    val top: Int,
    val template: String,
) {
    /**
     * The `load` that applies [low] to [high]: each placeholder replaced by its bound, and a query parameter
     * whose bound is `null` left out, so the address names only the bounds that are set. Bounds typed the
     * wrong way round are swapped.
     */
    public fun applying(
        low: Int?,
        high: Int?,
    ): LoadAction {
        val (from, to) = if (low != null && high != null && low > high) high to low else low to high
        val path = template.substringBefore('?')
        val query =
            template
                .substringAfter('?', "")
                .split('&')
                .filter { it.isNotEmpty() }
                .mapNotNull { pair ->
                    when {
                        LOW in pair -> from?.let { pair.replace(LOW, it.toString()) }
                        HIGH in pair -> to?.let { pair.replace(HIGH, it.toString()) }
                        else -> pair
                    }
                }
        return LoadAction(if (query.isEmpty()) path else path + "?" + query.joinToString("&"))
    }

    public companion object {
        /** Where the lower bound goes in [template]. */
        public const val LOW: String = "{min}"

        /** Where the upper bound goes in [template]. */
        public const val HIGH: String = "{max}"
    }
}

@Serializable
@SerialName("haul_facet_panel")
@KompotComponentMarker
public data class FacetPanel(
    override val id: String,
    val facets: List<Facet>,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/**
 * What is applied, as removable chips, «Clear all», and the sort control. [clearAction] is where «Clear
 * all» goes; [sorts] are the orders the sort control offers, each with where it goes — the one shown
 * is [sortLabel].
 */
@Serializable
@SerialName("haul_applied_filters")
@KompotComponentMarker
public data class AppliedFilters(
    override val id: String,
    val chips: List<Chip>,
    val clearLabel: String,
    val sortLabel: String,
    val filterCount: Int,
    val clearAction: @Polymorphic KompotAction? = null,
    val sorts: List<Link> = emptyList(),
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/**
 * A category's results with the filters that made them: on a wide page the facet column beside the
 * applied chips and the results; on a phone «Filters» and the sort above the chips, and the facets in
 * a sheet whose button reads [showLabel] («Show 48 items»). Either [grid] or [empty] is set.
 */
@Serializable
@SerialName("haul_filtered_results")
@KompotComponentMarker
public data class FilteredResults(
    override val id: String,
    val facets: FacetPanel,
    val applied: AppliedFilters,
    val showLabel: String,
    val grid: ProductGrid? = null,
    val pagination: HaulPagination? = null,
    val empty: EmptyState? = null,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/**
 * «Show 24 more» and the page numbers; [pages] lists what is drawn, `…` included. [moreAction] is where
 * «Show 24 more» goes (the next page); [links] are the page numbers that go somewhere, by their label —
 * the current page and `…` have none.
 */
@Serializable
@SerialName("haul_pagination")
@KompotComponentMarker
public data class HaulPagination(
    override val id: String,
    val current: Int,
    val pages: List<String>,
    val moreLabel: String? = null,
    val moreAction: @Polymorphic KompotAction? = null,
    val links: List<Link> = emptyList(),
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/**
 * An empty, a no-results or a not-found state: a title, a sentence and what to do instead — an
 * outlined button, or a filled one when it is the page's [primary] way on (the empty cart's deals).
 * The sentence may be absent: the empty cart's names today's deals and the sale, and is left out while
 * either is over (B-59).
 */
@Serializable
@SerialName("haul_empty_state")
@KompotComponentMarker
public data class EmptyState(
    override val id: String,
    val title: String,
    val text: String? = null,
    val actionLabel: String? = null,
    val action: @Polymorphic KompotAction? = null,
    val accent: String? = null,
    val primary: Boolean = false,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent
