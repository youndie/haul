package io.github.youndie.haul.feature.catalog.screen

import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.Listed
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.feature.catalog.domain.count
import io.github.youndie.haul.feature.catalog.domain.discount
import io.github.youndie.haul.feature.catalog.domain.money
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.kompot.standard.NavigateAction

/** A product as a card: the shown SKU's price, the earliest delivery day (feature-browse), its photo if stored. */
internal fun card(
    item: Listed,
    calendar: DeliveryCalendar,
    photos: ProductPhotos,
    priceCents: Int = item.shown.priceCents,
    oldCents: Int? = item.shown.oldPriceCents,
): ProductCard =
    ProductCard(
        id = "card-${item.product.id}",
        productId = item.product.id,
        title = item.product.title,
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
    )

internal fun productLink(productId: String): NavigateAction = NavigateAction("/p/$productId")

internal fun categoryLink(slug: String): NavigateAction = NavigateAction("/c/$slug")

/** «1 2 3 … 517»: the first three pages, and the last after an ellipsis when there are more. */
internal fun pageNumbers(pages: Int): List<String> =
    if (pages <= PAGES_SHOWN + 1) {
        (1..pages).map { "$it" }
    } else {
        (1..PAGES_SHOWN).map { "$it" } + "…" + "$pages"
    }

private const val PAGES_SHOWN = 3
