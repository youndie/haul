package io.github.youndie.haul.ui

import io.github.youndie.haul.feature.cart.LineCommand
import io.github.youndie.haul.feature.saved.SaveCommand
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.registry.KompotComponentMarker
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The Haul components every screen is built from (research D2). Values arrive formatted — «$349»,
// «Tomorrow», «2,341» — because the server owns money, dates and plurals and the client only draws.

/**
 * The header of every screen but checkout: the delivery strip, the logo, the catalog button, search,
 * the account shortcuts and the cart, and the category row.
 *
 * [customerName] is the signed-in first name; `null` is a guest, and the shortcut reads «Sign in». It
 * has a default because the server's wire leaves a `null` out (`explicitNulls = false`): without one a
 * guest's header did not decode in the client at all.
 * [query] is what the search field holds on the search screen; `null` shows the placeholder.
 * [account] is what a tap on the account shortcut does: a guest is sent to `/sign-in`, which the
 * client answers by opening the provider's page; a customer to `/account`.
 *
 * [catalog] is every top-level category with where it goes: «Catalog» opens it as a menu, and each
 * word of the category row ([categories], the first of them) follows the entry of the same name.
 * [deals] is where «Deals» goes, [cart] where the cart button goes, [orders] where «Orders» goes — a
 * customer's orders, a guest's sign-in — and [saved] where «Saved» goes: a customer's Saved list
 * (`/account/saved`, B-20), a guest's sign-in.
 */
@Serializable
@SerialName("haul_header")
@KompotComponentMarker
public data class HaulHeader(
    override val id: String,
    val deliverTo: String,
    val deliveryPromise: String,
    val customerName: String? = null,
    val cartCount: Int,
    val searchPlaceholder: String,
    val query: String? = null,
    val categories: List<String>,
    val account: @Polymorphic KompotAction? = null,
    val catalog: List<Link> = emptyList(),
    val deals: @Polymorphic KompotAction? = null,
    val cart: @Polymorphic KompotAction? = null,
    val orders: @Polymorphic KompotAction? = null,
    val saved: @Polymorphic KompotAction? = null,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/** Something drawn with a [label] that goes somewhere: a menu entry, a word of a row, a page number. */
@Serializable
public data class Link(
    val label: String,
    val action: @Polymorphic KompotAction? = null,
)

/**
 * A product in a grid or a row: the photo tile, the price, the title, the rating and the earliest
 * delivery day.
 *
 * [image] is where the product's stored photo is served (research D8, B-30), a path on the server the
 * tree came from; absent when there is no photo or no object storage. The placeholder tile — [tone],
 * one of the canvas's tile tones as `#RRGGBB`, and [label], what the tile says — is drawn whenever the
 * photo is absent, still loading or failed to load.
 *
 * [add] is what «+» sends: the line change that puts one more of the SKU whose price the card shows
 * into the cart (`PUT`, endpoint-cart). It is absent when the cart already holds as many as can be
 * bought, or the SKU is out of stock.
 *
 * The heart (B-20) is drawn filled when the product is in the viewer's Saved list ([saved]). For a
 * customer it is [heartCommand], the state a press leaves (`PUT` or `DELETE`, endpoint-saved); for a
 * guest it is [heartAction], the way to sign in. [drop] is the Saved list's mark on a product that got
 * cheaper since it was saved («Price dropped −$200»), on the Saved list's own cards only.
 */
@Serializable
@SerialName("haul_product_card")
@KompotComponentMarker
public data class ProductCard(
    override val id: String,
    val productId: String,
    val title: String,
    val price: String,
    val oldPrice: String? = null,
    val badge: String? = null,
    val rating: String,
    val reviews: String,
    val delivery: String,
    val tone: String,
    val label: String,
    val saved: Boolean = false,
    val image: String? = null,
    /** Where a tap on the card goes: the product page. */
    val action: @Polymorphic KompotAction? = null,
    val add: LineCommand? = null,
    val heartCommand: SaveCommand? = null,
    val heartAction: @Polymorphic KompotAction? = null,
    val drop: String? = null,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent
