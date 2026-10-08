package io.github.youndie.haul.ui

import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.registry.KompotComponentMarker
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The pieces of the Product screen (feature-product).

@Serializable
public data class VariantOption(
    val label: String,
    val skuId: String,
    val selected: Boolean,
    /** `false` when no SKU of this option is in stock with the other choices as they are. */
    val available: Boolean,
    val swatch: String? = null,
    val action: @Polymorphic KompotAction? = null,
)

@Serializable
public data class VariantGroup(
    val name: String,
    val selectedLabel: String,
    val options: List<VariantOption>,
)

@Serializable
public data class Highlight(
    val title: String,
    val text: String,
)

@Serializable
public data class DeliveryLine(
    val title: String,
    val detail: String,
    val price: String? = null,
)

@Serializable
public data class SellerSummary(
    val name: String,
    val initial: String,
    val meta: String,
)

/**
 * The top of the product page: the photo, the identity, the choices, and the buy box for the chosen
 * SKU. [inStock] `false` is `Product_OutOfStock`: the price is greyed and only «Save» stays live.
 */
@Serializable
@SerialName("haul_product_details")
@KompotComponentMarker
public data class ProductDetails(
    override val id: String,
    val productId: String,
    val skuId: String,
    val photoTone: String,
    val photoLabel: String,
    val photoCount: String,
    val badge: String? = null,
    val brand: String,
    val title: String,
    val rating: String,
    val reviews: String,
    val bought: String? = null,
    val variants: List<VariantGroup>,
    val highlights: List<Highlight>,
    val price: String,
    val oldPrice: String? = null,
    val discount: String? = null,
    val haulPay: String? = null,
    val inStock: Boolean,
    val stockNote: String? = null,
    val delivery: List<DeliveryLine>,
    val cutoff: String? = null,
    val seller: SellerSummary,
    val saved: Boolean = false,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

@Serializable
public data class TabLabel(
    val key: String,
    val title: String,
    val count: String? = null,
    val selected: Boolean,
    val action: @Polymorphic KompotAction? = null,
)

/** The tab row of the product page; the content below it is the selected tab's own component. */
@Serializable
@SerialName("haul_product_tabs")
@KompotComponentMarker
public data class ProductTabs(
    override val id: String,
    val tabs: List<TabLabel>,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

@Serializable
@SerialName("haul_product_description")
@KompotComponentMarker
public data class ProductDescription(
    override val id: String,
    val text: String,
    val facts: List<Highlight> = emptyList(),
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/** The full specifications, in order. */
@Serializable
@SerialName("haul_specification_list")
@KompotComponentMarker
public data class SpecificationList(
    override val id: String,
    val rows: List<Highlight>,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent
