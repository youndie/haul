package io.github.youndie.haul.feature.cart

import kotlinx.serialization.Serializable

// The bodies of the cart's commands (endpoint-cart). Where each one is sent is the server's string,
// carried by the Cart tree's components (`CartLine.url`, `CartSelection.linesUrl`, `PromoField.url`);
// every command answers a kompot action — `refresh` — or an `ErrorBody`.

/**
 * A change to one line, `PUT` to `CartLine.url`. A SKU not yet in the cart is added with [quantity]
 * (1 when absent), selected unless [selected] says otherwise; a line already there changes only the
 * fields given. At least one field is required.
 */
@Serializable
public data class LineChange(
    val quantity: Int? = null,
    val selected: Boolean? = null,
)

/** «Remove» and «Delete selected», `DELETE` to `CartSelection.linesUrl`: the SKUs whose lines go. */
@Serializable
public data class LinesRemoval(
    val skuIds: List<String>,
)

/** A promo code as typed, `PUT` to `PromoField.url`; the server trims it and ignores its case. */
@Serializable
public data class PromoEntry(
    val code: String,
)

/**
 * A line change fixed in the tree, so the client sends it as it is: «+» on a product card (B-37) is
 * [change] — the quantity the line will have — with `PUT` to [url], answered `refresh`.
 */
@Serializable
public data class LineCommand(
    val url: String,
    val change: LineChange,
)
