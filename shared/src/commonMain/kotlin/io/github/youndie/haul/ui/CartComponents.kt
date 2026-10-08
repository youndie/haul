package io.github.youndie.haul.ui

import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.registry.KompotComponentMarker
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The pieces of the Cart screen (feature-cart). The title is the catalog's `PageTitle`, the empty cart
// the shared `EmptyState` with a `ProductGrid` of picks. The commands a line or the promo field send
// are the routes in `feature/cart/CartCommands.kt`; the components carry what those commands need
// (the SKU id, the quantity bounds) rather than the commands themselves.

/**
 * One line of the cart: the product tile, the title and the chosen options, the price of the line
 * ([price], with [oldPrice] crossed out), the quantity stepper and the selection box.
 *
 * [price] and [oldPrice] are the line's total; [each] is the unit price when [quantity] is above one.
 * [maxQuantity] is where «+» stops: ten, or the stock when it is lower. A line is [selectable] only
 * while it is in stock and unchanged; [change] is what changed since it was added («Price changed:
 * now $26», «Out of stock»), and [acknowledgeLabel] the button that accepts it («OK») — `null` on
 * both when nothing changed.
 */
@Serializable
@SerialName("haul_cart_line")
@KompotComponentMarker
public data class CartLine(
    override val id: String,
    val skuId: String,
    val productId: String,
    val title: String,
    val options: String,
    val tone: String,
    val label: String,
    val price: String,
    val oldPrice: String? = null,
    val each: String? = null,
    val quantity: Int,
    val maxQuantity: Int,
    val selected: Boolean,
    val selectable: Boolean,
    val change: String? = null,
    val acknowledgeLabel: String? = null,
    val saveLabel: String = "Save for later",
    val removeLabel: String = "Remove",
    /** Where a tap on the tile or the title goes: the product page. */
    val action: @Polymorphic KompotAction? = null,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/** One seller's lines and the day they arrive («Delivery tomorrow»). */
@Serializable
@SerialName("haul_cart_group")
@KompotComponentMarker
public data class CartGroup(
    override val id: String,
    val seller: String,
    val delivery: String,
    val lines: List<CartLine>,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/**
 * The row above the groups: «Select all» with its state, and «Delete selected», which deletes the
 * [selectedCount] lines whose box is ticked.
 */
@Serializable
@SerialName("haul_cart_selection")
@KompotComponentMarker
public data class CartSelection(
    override val id: String,
    val allSelected: Boolean,
    val selectedCount: Int,
    val selectAllLabel: String = "Select all",
    val deleteLabel: String = "Delete selected",
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/**
 * The promo code field. Empty: [code] `null`. Applied (`Cart_PromoApplied`): [applied] with the
 * [code] and [removeLabel]. Refused (`Cart_PromoError`): the [code] as typed and the [error]
 * («This code has expired»).
 */
@Serializable
@SerialName("haul_promo_field")
@KompotComponentMarker
public data class PromoField(
    override val id: String,
    val code: String? = null,
    val applied: Boolean = false,
    val error: String? = null,
    val placeholder: String = "Promo code",
    val applyLabel: String = "Apply",
    val removeLabel: String = "Remove",
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/**
 * A row of the summary: «Items $652.00», «Discount −$140.00», «Delivery Free». A [detail] row is a
 * part of the row above it (the promo code inside the discount) and is drawn smaller.
 */
@Serializable
public data class SummaryRow(
    val label: String,
    val value: String,
    val detail: Boolean = false,
)

/**
 * The totals and the way on: the rows, the total, the points the order earns ([points] `null` for a
 * guest, `Cart_Guest`), and the checkout button — «Sign in to check out» for a guest. [checkoutEnabled]
 * is `false` while no line is selected.
 */
@Serializable
@SerialName("haul_order_summary")
@KompotComponentMarker
public data class OrderSummary(
    override val id: String,
    val rows: List<SummaryRow>,
    val totalLabel: String,
    val total: String,
    val points: String? = null,
    val checkoutLabel: String,
    val checkoutEnabled: Boolean,
    val checkoutAction: @Polymorphic KompotAction? = null,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent
