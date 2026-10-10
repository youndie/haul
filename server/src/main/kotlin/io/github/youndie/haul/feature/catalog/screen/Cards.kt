package io.github.youndie.haul.feature.catalog.screen

import io.github.youndie.haul.feature.cart.CartPaths
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.cart.LineCommand
import io.github.youndie.haul.feature.cart.domain.CartCommands
import io.github.youndie.haul.feature.catalog.domain.Browse
import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.domain.Category
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.Listed
import io.github.youndie.haul.feature.catalog.domain.Page
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.feature.catalog.domain.Sku
import io.github.youndie.haul.feature.catalog.domain.count
import io.github.youndie.haul.feature.catalog.domain.discount
import io.github.youndie.haul.feature.catalog.domain.money
import io.github.youndie.haul.feature.catalog.domain.pathOf
import io.github.youndie.haul.feature.saved.SaveCommand
import io.github.youndie.haul.feature.saved.SavedPaths
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.ui.HaulPagination
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.standard.NavigateAction

/**
 * A product as a card for [viewer]: the shown SKU's price, the earliest delivery day (feature-browse), its
 * photo if stored, «+», which puts one more of [sku] into the cart — given how many of each SKU the
 * viewer's cart already holds (`Viewer.inCart`) — and the heart, filled when the product is in the
 * viewer's Saved list (`Viewer.saved`, B-20).
 *
 * «+» is answered with the header and this card again (B-63, [LineAnswers]) when [inPlace]: the card is
 * built the same from the product and the SKU wherever it is drawn, and [query] is the search the page's
 * header shows, [scope] the category its picker reads (B-72). A card the page draws its own way (the Saved list's mark) is not, and «+» there redraws
 * the page.
 */
internal fun card(
    item: Listed,
    calendar: DeliveryCalendar,
    photos: ProductPhotos,
    viewer: Viewer,
    sku: Sku = item.shown,
    query: String? = null,
    inPlace: Boolean = true,
    scope: String? = null,
): ProductCard {
    val saved = item.product.id in viewer.saved
    val priceCents = sku.priceCents
    val oldCents = sku.oldPriceCents
    return ProductCard(
        id = "card-${item.product.id}",
        productId = item.product.id,
        title = item.product.listingName,
        price = money(priceCents),
        oldPrice = oldCents?.takeIf { it > priceCents }?.let(::money),
        badge = discount(priceCents, oldCents),
        rating = item.product.rating.toPlainString(),
        reviews = count(item.product.reviewsCount),
        delivery = calendar.label(calendar.courier(item)),
        tone = item.product.tone,
        label = item.product.label,
        image = photos.url(item.product),
        action = productLink(item.product.id),
        add = addToCart(sku, viewer.inCart, if (inPlace) LineAnswers.cardAnswer(query, scope) else ""),
        saved = saved,
        heartCommand = heart(item.product.id, saved, viewer),
        heartAction = heartAction(viewer),
    )
}

/**
 * The heart of [productId] for [viewer], a customer (B-20): the state a press leaves — out of the Saved
 * list when it is [saved], in it otherwise — fixed in the tree as «+» is. A guest has none.
 */
internal fun heart(
    productId: String,
    saved: Boolean,
    viewer: Viewer,
): SaveCommand? = viewer.customerId?.let { SaveCommand(SavedPaths.item(productId), save = !saved) }

/** A guest's heart: the way to sign in, which draws the same page again once it has gone through (B-41). */
internal fun heartAction(viewer: Viewer): NavigateAction? =
    if (viewer.customerId ==
        null
    ) {
        NavigateAction(Frame.SIGN_IN)
    } else {
        null
    }

/**
 * «+»: the line [sku] will have with one more in it (endpoint-cart, `PUT` `LineChange`) — or nothing to
 * send when the cart already holds as many as can be bought (ten, or the stock), or there is no stock.
 * The quantity is the line's next one rather than «add one», so a press sent twice puts in one, not two.
 * [answer] is the query that says which node the answer redraws ([LineAnswers]); empty, the page.
 */
internal fun addToCart(
    sku: Sku,
    inCart: Map<String, Int>,
    answer: String = "",
): LineCommand? {
    val next = (inCart[sku.id] ?: 0) + 1
    if (next > minOf(CartCommands.MAX_QUANTITY, sku.stock)) return null
    return LineCommand(CartPaths.line(sku.id) + answer, LineChange(quantity = next))
}

/**
 * «Deals of the day» as home and the deals page draw it: the [cards] of the deals live on the store's clock
 * and the instant the section's countdown runs to, [endsAt] — the soonest end among the drawn deals (B-58),
 * ISO-8601 in the store's zone. A seeded deal of the day ends at the store's next midnight (research D7),
 * which is what the countdown read before; a deal written with another end is counted down to that end
 * rather than to a midnight it outlives or misses.
 */
internal class DealsOfTheDay(
    val cards: List<ProductCard>,
    val endsAt: String,
)

/**
 * The deals live on the store's clock (feature-browse), or `null` when there are none — no deal is live, or
 * no live deal's SKU is left — so a screen draws no «Deals of the day» at all rather than its header over an
 * empty grid (B-58). Each card is the deal's SKU at the price the catalog reads for it — the deal's, over the
 * campaign or regular price it beats, or the campaign's when that is as low (B-57, `CampaignPricing`) — which
 * is what «+» puts into the cart and the cart charges. An ended deal is not read at all.
 */
internal suspend fun dealsOfTheDay(
    catalog: CatalogRepository,
    calendar: DeliveryCalendar,
    photos: ProductPhotos,
    viewer: Viewer,
): DealsOfTheDay? {
    val deals = catalog.deals()
    val items = catalog.listed(deals.map { deal -> deal.skuId.substringBeforeLast('-') }, viewer.prices)
    val drawn =
        deals.mapNotNull { deal ->
            val item = items.firstOrNull { item -> item.skus.any { it.id == deal.skuId } } ?: return@mapNotNull null
            deal to card(item, calendar, photos, viewer, sku = item.skus.first { it.id == deal.skuId })
        }
    if (drawn.isEmpty()) return null
    val endsAt = drawn.minOf { (deal, _) -> deal.endsAt.toInstant() }
    return DealsOfTheDay(
        cards = drawn.map { (_, card) -> card },
        endsAt = endsAt.atZone(DeliveryCalendar.STORE).toOffsetDateTime().toString(),
    )
}

internal fun productLink(productId: String): NavigateAction = NavigateAction("/p/$productId")

/** A link to [category]'s page at its one address (B-68), whatever page it is drawn on. */
internal fun categoryLink(
    category: Category,
    categories: List<Category>,
): NavigateAction = NavigateAction("${Frame.CATALOG}/${categories.pathOf(category)}")

/**
 * The pages under a grid. «Show N more» appends the next page to the grid (B-77): it goes to the pages
 * the grid holds and the next one, `?page=<next>&from=<first>`, an address that draws every one of them, so
 * the press keeps the cards above where the shopper is, and a reload or a shared link shows the same grid.
 * Every page number but the current one goes to that page alone. [address] is the page's address showing
 * the pages in the range (`?page=` is left out for the first alone), and [press] what a press on one does —
 * opens it, or loads its parts (B-63) — given the pages it shows and its address.
 */
internal fun pagination(
    page: Page,
    address: (pages: IntRange) -> String,
    press: (pages: IntRange, address: String) -> KompotAction = { _, to -> NavigateAction(to) },
): HaulPagination {
    val pages = pageNumbers(page.page, page.pages)
    val more = page.page < page.pages
    val next = page.from..page.page + 1
    return HaulPagination(
        id = "pagination",
        current = page.page,
        pages = pages,
        moreLabel = if (more) "Show ${page.next} more" else null,
        moreAction = if (more) press(next, address(next)) else null,
        links =
            pages.mapNotNull { label ->
                label.toIntOrNull()?.takeIf { it != page.page }?.let { Link(label, press(it..it, address(it..it))) }
            },
    )
}

/**
 * The page numbers drawn for page [current] of [pages] (B-77): the first and the last, the current page with
 * its neighbours, and «…» for each run of pages left out — «1 … 6 7 8 … 517». A run of one page is drawn as
 * its number, since «…» would take as much room. At either end the window is the three pages there, so the
 * first page reads «1 2 3 … 517», as the canvas draws it.
 */
internal fun pageNumbers(
    current: Int,
    pages: Int,
): List<String> {
    val start = (current - 1).coerceAtMost(pages - WINDOW + 1).coerceAtLeast(1)
    val end = (start + WINDOW - 1).coerceAtMost(pages)
    val shown = (setOf(1, pages) + (start..end)).sorted()
    return buildList {
        shown.forEachIndexed { index, n ->
            val gap = if (index == 0) 0 else n - shown[index - 1] - 1
            when {
                gap == 1 -> add("${n - 1}")
                gap > 1 -> add("…")
            }
            add("$n")
        }
    }
}

/** How many page numbers stand around the current one: it and a neighbour on either side. */
private const val WINDOW = 3
