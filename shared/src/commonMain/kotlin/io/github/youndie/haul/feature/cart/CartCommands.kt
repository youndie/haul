package io.github.youndie.haul.feature.cart

import io.github.youndie.kompot.KompotAction
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

// The bodies of the cart's commands (endpoint-cart). Where each one is sent is the server's string,
// carried by the Cart tree's components (`CartLine.url`, `CartSelection.linesUrl`, `PromoField.url`);
// every command answers a kompot action — `refresh` — or an `ErrorBody`.

/**
 * A change to one line, `PUT` to the line's address. A SKU not yet in the cart is added with [quantity]
 * (1 when absent), selected unless [selected] says otherwise; a line already there changes only the
 * fields given. At least one field is required. `CartLine.url` — the address the cart's own stepper and
 * box send to — changes a line and never adds one: a line removed meanwhile (in another tab) is
 * `404 line_not_found` (B-125). A card's «+» and the product page's buttons send to an address that adds.
 *
 * [alone] is «Buy now» (B-100): the line is selected and every other line of the cart unticked, so
 * checkout takes this product alone; the line's price and stock become the ones the shopper saw, as the
 * product page showed them. It cannot be sent with [selected] `false`.
 */
@Serializable
public data class LineChange(
    val quantity: Int? = null,
    val selected: Boolean? = null,
    val alone: Boolean? = null,
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
 * A line change fixed in the tree, so the client sends it as it is: «+» on a product card (B-37) and
 * «Add to cart» on the product page (B-48) are [change] — the quantity the line will have — with `PUT`
 * to [url], answered `refresh`.
 *
 * [next] is what the client follows once the server has accepted the change, in place of its answer:
 * «Buy now» (B-48) navigates to checkout, or to sign-in on the way there. A refused change follows
 * the answer as any other command does — the page is drawn again — so nothing is bought that was not
 * put in the cart.
 */
@Serializable
public data class LineCommand(
    val url: String,
    val change: LineChange,
    val next: @Polymorphic KompotAction? = null,
)
