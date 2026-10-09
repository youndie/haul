package io.github.youndie.haul.feature.catalog.screen

import io.github.youndie.haul.feature.cart.CartPaths
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.cart.LineCommand
import io.github.youndie.haul.feature.cart.domain.CartCommands
import io.github.youndie.haul.feature.catalog.domain.Browse
import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.Listed
import io.github.youndie.haul.feature.catalog.domain.Page
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.feature.catalog.domain.Sku
import io.github.youndie.haul.feature.catalog.domain.count
import io.github.youndie.haul.feature.catalog.domain.discount
import io.github.youndie.haul.feature.catalog.domain.money
import io.github.youndie.haul.feature.saved.SaveCommand
import io.github.youndie.haul.feature.saved.SavedPaths
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.ui.HaulPagination
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.kompot.standard.NavigateAction

/**
 * A product as a card for [viewer]: the shown SKU's price, the earliest delivery day (feature-browse), its
 * photo if stored, «+», which puts one more of [sku] into the cart — given how many of each SKU the
 * viewer's cart already holds (`Viewer.inCart`) — and the heart, filled when the product is in the
 * viewer's Saved list (`Viewer.saved`, B-20).
 */
internal fun card(
    item: Listed,
    calendar: DeliveryCalendar,
    photos: ProductPhotos,
    viewer: Viewer,
    sku: Sku = item.shown,
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
        add = addToCart(sku, viewer.inCart),
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
 */
internal fun addToCart(
    sku: Sku,
    inCart: Map<String, Int>,
): LineCommand? {
    val next = (inCart[sku.id] ?: 0) + 1
    if (next > minOf(CartCommands.MAX_QUANTITY, sku.stock)) return null
    return LineCommand(CartPaths.line(sku.id), LineChange(quantity = next))
}

/**
 * The cards of the deals live on the store's clock (feature-browse): each the deal's SKU at the price the
 * catalog reads for it — the deal's, over the campaign or regular price it beats, or the campaign's when
 * that is as low (B-57, `CampaignPricing`) — which is what «+» puts into the cart and the cart charges. A
 * deal whose SKU is gone is left out, and an ended one is not read at all.
 */
internal suspend fun dealCards(
    catalog: CatalogRepository,
    calendar: DeliveryCalendar,
    photos: ProductPhotos,
    viewer: Viewer,
): List<ProductCard> {
    val deals = catalog.deals()
    val items = catalog.listed(deals.map { deal -> deal.skuId.substringBeforeLast('-') }, viewer.prices)
    return deals.mapNotNull { deal ->
        val item = items.firstOrNull { item -> item.skus.any { it.id == deal.skuId } } ?: return@mapNotNull null
        card(item, calendar, photos, viewer, sku = item.skus.first { it.id == deal.skuId })
    }
}

internal fun productLink(productId: String): NavigateAction = NavigateAction("/p/$productId")

internal fun categoryLink(slug: String): NavigateAction = NavigateAction("/c/$slug")

/**
 * The pages under a grid: «Show 24 more» goes to the next page, and every page number but the current
 * one to its own; [address] is the page's address at page n (`?page=` is left out for the first).
 */
internal fun pagination(
    page: Page,
    address: (page: Int) -> String,
): HaulPagination {
    val pages = pageNumbers(page.pages)
    val more = page.page < page.pages
    return HaulPagination(
        id = "pagination",
        current = page.page,
        pages = pages,
        moreLabel = if (more) "Show ${Browse.PAGE_SIZE} more" else null,
        moreAction = if (more) NavigateAction(address(page.page + 1)) else null,
        links =
            pages.mapNotNull { label ->
                label.toIntOrNull()?.takeIf { it != page.page }?.let { Link(label, NavigateAction(address(it))) }
            },
    )
}

/** «1 2 3 … 517»: the first three pages, and the last after an ellipsis when there are more. */
internal fun pageNumbers(pages: Int): List<String> =
    if (pages <= PAGES_SHOWN + 1) {
        (1..pages).map { "$it" }
    } else {
        (1..PAGES_SHOWN).map { "$it" } + "…" + "$pages"
    }

private const val PAGES_SHOWN = 3
