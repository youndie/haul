package io.github.youndie.haul.feature.catalog.screen

import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.Listed
import io.github.youndie.haul.feature.catalog.domain.count
import io.github.youndie.haul.feature.catalog.domain.discount
import io.github.youndie.haul.feature.catalog.domain.money
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.kompot.standard.NavigateAction

/** A product as a card: the shown SKU's price, the earliest delivery day (feature-browse). */
internal fun card(
    item: Listed,
    calendar: DeliveryCalendar,
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
        action = productLink(item.product.id),
    )

internal fun productLink(productId: String): NavigateAction = NavigateAction("/p/$productId")

internal fun categoryLink(slug: String): NavigateAction = NavigateAction("/c/$slug")
