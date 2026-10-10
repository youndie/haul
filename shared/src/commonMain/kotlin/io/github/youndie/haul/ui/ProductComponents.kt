package io.github.youndie.haul.ui

import io.github.youndie.haul.feature.cart.LineCommand
import io.github.youndie.haul.feature.reviews.HelpfulCommand
import io.github.youndie.haul.feature.saved.SaveCommand
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
 * The product has one photo, [photo] over the tile of [photoTone] captioned [photoLabel]: the canvas's
 * thumbnails, «+3» and «1 / 8» drew photos no product has, and are not sent (B-71). [haulPayStrong] is
 * the part of [haulPay] drawn bold.
 *
 * What looks like a link is one (B-71): [brandAction] opens the brand in the product's category,
 * [ratingAction] — the stars and «2,341 reviews» — the reviews tab, [specificationsAction] — «All
 * specifications» — the specifications tab. [share] is the product's address at its SKU, which the share
 * button over the photo copies as a link on the page's own origin; absent, no share button is drawn.
 *
 * [bought] is «12K bought this month» under the rating, already abbreviated by the server (B-52); absent
 * for a product bought fewer than 50 times in the last 30 days.
 *
 * [photo] is where the product's stored photo is served (research D8, B-30): drawn as the shown photo
 * and the first thumbnail, over the placeholder tile of [photoTone], which stays whenever the photo is
 * absent, loading or failed.
 *
 * The heart over the photo — and «Save» beside an out-of-stock product's buttons — is the Saved list's
 * (B-20), as on a card: drawn filled when [saved]; for a customer [heartCommand], for a guest
 * [heartAction], the way to sign in.
 *
 * [add] is what «Add to cart» sends (B-48): one more of [skuId] into the cart, as a card's «+» does —
 * absent when the line already holds as many as can be bought. [buy] is «Buy now»: the same line,
 * selected, then [LineCommand.next] to checkout (or to sign-in on the way there, for a guest); at the
 * line's limit it only selects the line. Both are absent out of stock.
 *
 * [inCart] says the SKU shown is in the viewer's cart (B-75): its words («2 in your cart») and where they
 * lead, the cart; absent while the cart holds none of it. It is what «Add to cart» changes on the page
 * besides the header's count, so a press shows it worked.
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
    val haulPayStrong: String? = null,
    val stockAdvice: Highlight? = null,
    val photo: String? = null,
    val heartCommand: SaveCommand? = null,
    val heartAction: @Polymorphic KompotAction? = null,
    val add: LineCommand? = null,
    val buy: LineCommand? = null,
    val inCart: Link? = null,
    val brandAction: @Polymorphic KompotAction? = null,
    val ratingAction: @Polymorphic KompotAction? = null,
    val specificationsAction: @Polymorphic KompotAction? = null,
    val share: String? = null,
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
 * D8); [helpful] is «48 people found this helpful», the stored count, absent while nobody has.
 *
 * «Helpful» ([helpfulLabel]) carries, for a customer, [helpfulCommand] — their vote, or taking it back
 * (B-43) — and for a guest [helpfulAction], the way to sign in. On the customer's own review it carries
 * neither: the author cannot vote on it, and [helpfulLabel] is «Your review», drawn as words, not a
 * control (B-71).
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
    val helpfulCommand: HelpfulCommand? = null,
)

/**
 * The reviews tab: the rating, its histogram and «Write a review» beside the reviews themselves
 * (feature-reviews, B-22). [action] is what «Write a review» does: kompot's `present` of a
 * [ReviewForm] for a customer, the way to sign in for a guest. [more] is «[moreLabel]» under the reviews
 * while the product has more than are listed: kompot's `load` of the tab listing ten more (B-71).
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
    val moreLabel: String? = null,
    val more: @Polymorphic KompotAction? = null,
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

/**
 * The questions tab: how many there are, who answers, «Ask a question», and the questions. [action] is
 * what «Ask a question» does: kompot's `present` of a [QuestionForm] for a customer, sign-in for a guest.
 * [more] is «[moreLabel]» under the questions, as on the reviews tab.
 */
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
    val moreLabel: String? = null,
    val more: @Polymorphic KompotAction? = null,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/** The product a dialog is about: its name, the chosen SKU's options («Midnight Black · Headphones only»), its tile. */
@Serializable
public data class FormProduct(
    val name: String,
    val detail: String,
    val tone: String,
)

/**
 * «Write a review» (Product_ReviewDialog), presented over the reviews tab: the stars, a title and the
 * review, posted as a `ReviewEntry` to [url]. The limits are `ReviewRules`, which the client checks
 * before it sends; [bodyHint] is what is written under the review («At least 20 characters»). [close]
 * is what «×» and [cancelLabel] do.
 */
@Serializable
@SerialName("haul_review_form")
@KompotComponentMarker
public data class ReviewForm(
    override val id: String,
    val title: String,
    val product: FormProduct,
    val ratingLabel: String,
    val titleLabel: String,
    val bodyLabel: String,
    val bodyHint: String,
    val submitLabel: String,
    val cancelLabel: String,
    val url: String,
    val close: @Polymorphic KompotAction? = null,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/**
 * «Ask a question» (Product_QuestionDialog), presented over the questions tab: the question, sent as a
 * `QuestionEntry` to [url]; [hint] is its limits in words, [note] who answers and where.
 */
@Serializable
@SerialName("haul_question_form")
@KompotComponentMarker
public data class QuestionForm(
    override val id: String,
    val title: String,
    val product: FormProduct,
    val label: String,
    val hint: String,
    val note: String,
    val submitLabel: String,
    val cancelLabel: String,
    val url: String,
    val close: @Polymorphic KompotAction? = null,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent
