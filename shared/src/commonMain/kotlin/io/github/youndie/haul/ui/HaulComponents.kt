package io.github.youndie.haul.ui

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
 * [customerName] is the signed-in first name; `null` is a guest, and the shortcut reads «Sign in».
 * [query] is what the search field holds on the search screen; `null` shows the placeholder.
 * [account] is what a tap on the account shortcut does: a guest is sent to `/sign-in`, which the
 * client answers by opening the provider's page; a customer to `/account`.
 */
@Serializable
@SerialName("haul_header")
@KompotComponentMarker
public data class HaulHeader(
    override val id: String,
    val deliverTo: String,
    val deliveryPromise: String,
    val customerName: String?,
    val cartCount: Int,
    val searchPlaceholder: String,
    val query: String? = null,
    val categories: List<String>,
    val account: @Polymorphic KompotAction? = null,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/**
 * A product in a grid or a row: the photo tile, the price, the title, the rating and the earliest
 * delivery day.
 *
 * [image] is where the product's stored photo is served (research D8, B-30), a path on the server the
 * tree came from; absent when there is no photo or no object storage. The placeholder tile — [tone],
 * one of the canvas's tile tones as `#RRGGBB`, and [label], what the tile says — is drawn whenever the
 * photo is absent, still loading or failed to load.
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
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent
