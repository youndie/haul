package io.github.youndie.haul.ui

import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.registry.KompotComponentMarker
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The pieces of the Product screen (feature-product). An `accent` is, as on the browse screens, the
// words of a title drawn in Bodoni Moda's italic.

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
 * SKU. [inStock] `false` is `Product_OutOfStock`: the price is greyed, only «Save» stays live, and
 * [stockAdvice] («Silver is out of stock.» and where to turn) takes the delivery lines' place.
 *
 * [gallery] is the thumbnails' tile tones, the first being the photo shown; [morePhotos] is the last
 * thumbnail's «+3»; [photoTotal] is how many photos there are (the phone's dots). [haulPayStrong] is
 * the part of [haulPay] drawn bold.
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
    val accent: String? = null,
    val gallery: List<String> = emptyList(),
    val morePhotos: String? = null,
    val photoTotal: Int = 1,
    val haulPayStrong: String? = null,
    val stockAdvice: Highlight? = null,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/** A tab's label; [compactTitle] is what a phone writes («Specs»), [title] when absent. */
@Serializable
public data class TabLabel(
    val key: String,
    val title: String,
    val count: String? = null,
    val selected: Boolean,
    val action: @Polymorphic KompotAction? = null,
    val compactTitle: String? = null,
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

/** The description tab: a headline, the copy (paragraphs split by a blank line) and the fact tiles. */
@Serializable
@SerialName("haul_product_description")
@KompotComponentMarker
public data class ProductDescription(
    override val id: String,
    val text: String,
    val facts: List<Highlight> = emptyList(),
    val title: String? = null,
    val accent: String? = null,
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

/** One bar of a rating histogram: how many of the reviews gave [stars], in whole percent. */
@Serializable
public data class HistogramBar(
    val stars: Int,
    val percent: Int,
)

/**
 * A review. [tone] is the avatar's tile tone, [photos] the tones of its photo placeholders (research
 * D8); [helpful] is «48 people found this helpful», absent while nobody has.
 */
@Serializable
public data class Review(
    val author: String,
    val initial: String,
    val tone: String,
    val meta: String,
    val score: String,
    val title: String,
    val text: String,
    val photos: List<String> = emptyList(),
    val helpful: String? = null,
    val helpfulLabel: String = "Helpful",
    val helpfulAction: @Polymorphic KompotAction? = null,
)

/**
 * The reviews tab: the rating, its histogram and «Write a review» beside the reviews themselves
 * (feature-reviews, B-22).
 */
@Serializable
@SerialName("haul_product_reviews")
@KompotComponentMarker
public data class ProductReviews(
    override val id: String,
    val rating: String,
    val caption: String,
    val histogram: List<HistogramBar>,
    val actionLabel: String,
    val reviews: List<Review>,
    val action: @Polymorphic KompotAction? = null,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/** A question, with the seller's answer or, without one, [pendingLabel] («Not answered yet»). */
@Serializable
public data class Question(
    val question: String,
    val asked: String,
    val answer: String? = null,
    val answeredBy: String? = null,
    val pendingLabel: String? = null,
)

/** The questions tab: how many there are, who answers, «Ask a question», and the questions. */
@Serializable
@SerialName("haul_product_questions")
@KompotComponentMarker
public data class ProductQuestions(
    override val id: String,
    val count: String,
    val caption: String,
    val text: String,
    val actionLabel: String,
    val questions: List<Question>,
    val action: @Polymorphic KompotAction? = null,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent
