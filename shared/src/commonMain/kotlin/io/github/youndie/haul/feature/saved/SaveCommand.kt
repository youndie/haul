package io.github.youndie.haul.feature.saved

import kotlinx.serialization.Serializable

// The Saved list's commands (endpoint-saved, B-20). Where each goes is the server's string, carried by
// the tree: the heart on a product card or on the product page (`ProductCard.heartCommand`,
// `ProductDetails.heartCommand`) and «Save for later» on a cart line (`CartLine.saveUrl`). None has a
// body; every one answers kompot's `refresh`, or an `ErrorBody`.

/**
 * The heart fixed in the tree for the customer looking at it: [save] is the state the press leaves —
 * `true` is `PUT` to [url], which keeps the product in the Saved list at today's price, `false` is
 * `DELETE` to it, which lets it go. The state, not a toggle, as «Helpful» is (B-43): a press sent twice
 * leaves the list as one press does (feature-account, «Saved twice»). A guest's heart has none: it is the
 * way to sign in (`heartAction`).
 */
@Serializable
public data class SaveCommand(
    val url: String,
    val save: Boolean,
)
