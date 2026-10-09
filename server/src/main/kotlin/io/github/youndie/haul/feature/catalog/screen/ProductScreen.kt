package io.github.youndie.haul.feature.catalog.screen

import io.github.youndie.haul.feature.cart.CartPaths
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.cart.LineCommand
import io.github.youndie.haul.feature.cart.screen.CartScreen
import io.github.youndie.haul.feature.catalog.domain.BoughtThisMonth
import io.github.youndie.haul.feature.catalog.domain.CatalogError
import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.domain.Category
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.HaulPay
import io.github.youndie.haul.feature.catalog.domain.Listed
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.feature.catalog.domain.Seller
import io.github.youndie.haul.feature.catalog.domain.Sku
import io.github.youndie.haul.feature.catalog.domain.count
import io.github.youndie.haul.feature.catalog.domain.discount
import io.github.youndie.haul.feature.catalog.domain.money
import io.github.youndie.haul.feature.reviews.screen.ReviewTabs
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.ui.Breadcrumbs
import io.github.youndie.haul.ui.Crumb
import io.github.youndie.haul.ui.DeliveryLine
import io.github.youndie.haul.ui.Highlight
import io.github.youndie.haul.ui.ProductDescription
import io.github.youndie.haul.ui.ProductDetails
import io.github.youndie.haul.ui.ProductTabs
import io.github.youndie.haul.ui.SellerSummary
import io.github.youndie.haul.ui.SpecificationList
import io.github.youndie.haul.ui.TabLabel
import io.github.youndie.haul.ui.VariantGroup
import io.github.youndie.haul.ui.VariantOption
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.NavigateAction

/** The tabs the product page has; reviews and questions are feature-reviews' (B-22). */
internal enum class ProductTab(
    val key: String,
    val title: String,
    val compactTitle: String? = null,
) {
    Description("description", "Description"),
    Specifications("specifications", "Specifications", "Specs"),
    Reviews("reviews", "Reviews"),
    Questions("questions", "Questions"),
    ;

    companion object {
        fun of(key: String): ProductTab? = entries.firstOrNull { it.key == key }
    }
}

/**
 * `/ui/p/{productId}` (screen-product). The chosen SKU is the `sku` parameter, or the cheapest in
 * stock; everything in the buy box follows it — feature-product, «Variant changes the price». A SKU
 * with no stock is `Product_OutOfStock`: the details say so and the client keeps only «Save».
 * «Add to cart» and «Buy now» carry their line changes for that SKU (B-48), drawn for the [Viewer]'s
 * cart as a card's «+» is. «12K bought this month» under the rating is [BoughtThisMonth]'s (B-52).
 */
internal class ProductScreen(
    private val catalog: CatalogRepository,
    private val calendar: DeliveryCalendar,
    private val photos: ProductPhotos,
    private val reviewTabs: ReviewTabs,
    private val boughtThisMonth: BoughtThisMonth,
) {
    suspend fun build(
        productId: String,
        skuId: String?,
        tab: ProductTab,
        viewer: Viewer,
    ): KompotComponent {
        val item = catalog.product(productId, viewer.prices) ?: throw CatalogError.ProductNotFound(productId)
        val sku =
            skuId?.let { id ->
                item.skus.firstOrNull { it.id == id }
                    ?: throw CatalogError.Invalid("sku", "No SKU «$id» on this product")
            }
                ?: item.shown
        val seller = catalog.seller(item.product.sellerId) ?: error("product ${item.product.id} names no seller")
        val categories = catalog.categories()

        val sections =
            listOf(
                Breadcrumbs("breadcrumbs", crumbs(item, categories)),
                details(item, sku, seller, viewer, boughtThisMonth.label(item.product)),
                ProductTabs(
                    id = "tabs",
                    tabs =
                        ProductTab.entries.map {
                            TabLabel(
                                it.key,
                                it.title,
                                count =
                                    when (it) {
                                        ProductTab.Reviews -> count(item.product.reviewsCount)
                                        ProductTab.Questions -> count(item.product.questionsCount)
                                        else -> null
                                    },
                                selected = it == tab,
                                action = NavigateAction("/p/${item.product.id}?sku=${sku.id}&tab=${it.key}"),
                                compactTitle = it.compactTitle,
                            )
                        },
                ),
                when (tab) {
                    ProductTab.Description -> {
                        ProductDescription(
                            id = "description",
                            text = item.product.description,
                            facts = highlights(item),
                            title = item.product.headline,
                            accent = item.product.headlineAccent,
                        )
                    }

                    ProductTab.Specifications -> {
                        SpecificationList(
                            "specifications",
                            item.product.specifications.map { (k, v) ->
                                Highlight(k, v)
                            },
                        )
                    }

                    ProductTab.Reviews -> {
                        reviewTabs.reviews(item, sku, viewer)
                    }

                    ProductTab.Questions -> {
                        reviewTabs.questions(item, sku, seller, viewer)
                    }
                },
            )
        return Frame.page("product", viewer, navigation(categories), sections)
    }

    /**
     * The buy box of [item] at [sku] for [viewer], as the page at that SKU draws it: what «Add to cart»
     * answers with besides the header (B-63, [LineAnswers]).
     */
    suspend fun details(
        item: Listed,
        sku: Sku,
        viewer: Viewer,
    ): ProductDetails {
        val seller = catalog.seller(item.product.sellerId) ?: error("product ${item.product.id} names no seller")
        return details(item, sku, seller, viewer, boughtThisMonth.label(item.product))
    }

    private fun details(
        item: Listed,
        sku: Sku,
        seller: Seller,
        viewer: Viewer,
        bought: String?,
    ): ProductDetails {
        val inStock = sku.stock > 0
        val saved = item.product.id in viewer.saved
        val courier = calendar.courier(item)
        val freeDelivery = sku.priceCents >= FREE_DELIVERY_CENTS
        val cutoff = if (inStock) calendar.cutoffLabel() else null
        return ProductDetails(
            id = "details",
            productId = item.product.id,
            skuId = sku.id,
            photoTone = item.product.tone,
            photo = photos.url(item.product),
            photoLabel = "product photo",
            photoCount = "1 / $PHOTOS",
            photoTotal = PHOTOS,
            gallery = listOf(item.product.tone) + GALLERY_TONES,
            morePhotos = "+${PHOTOS - GALLERY_TONES.size - 2}",
            accent = item.product.title.substringAfterLast(' '),
            badge = if (item.product.reviewsCount >= BESTSELLER_REVIEWS) "Bestseller" else null,
            brand = item.product.brand,
            title = item.product.title,
            rating = item.product.rating.toPlainString(),
            reviews = "${count(item.product.reviewsCount)} reviews",
            bought = bought,
            variants = variants(item, sku),
            highlights = highlights(item),
            price = money(sku.priceCents),
            oldPrice = sku.oldPriceCents?.takeIf { it > sku.priceCents }?.let(::money),
            discount = discount(sku.priceCents, sku.oldPriceCents),
            haulPay = haulPay(sku.priceCents),
            haulPayStrong =
                haulPay(
                    sku.priceCents,
                )?.let { "4 payments of ${money(HaulPay.paymentCents(sku.priceCents))}" },
            stockAdvice = if (inStock) null else stockAdvice(item, sku, courier),
            inStock = inStock,
            stockNote = if (inStock) null else "Out of stock",
            delivery =
                listOf(
                    // The courier line reads the cut-off when there is one to race (Product_Description).
                    DeliveryLine(
                        "Courier · ${calendar.label(courier)}",
                        cutoff ?: "To Brooklyn, NY 11211",
                        if (freeDelivery) "Free" else money(DELIVERY_FEE_CENTS),
                    ),
                    DeliveryLine(
                        "Pickup point · ${calendar.label(calendar.pickup(item))}",
                        "Pickup points near you",
                        "Free",
                    ),
                    DeliveryLine("Returns", "30 days, free pickup"),
                ),
            cutoff = cutoff,
            seller =
                SellerSummary(
                    seller.name,
                    seller.name.take(1),
                    "${seller.rating.toPlainString()} · ${seller.positivePercent}% positive · ${seller.yearsOnHaul} yrs on Haul",
                ),
            saved = saved,
            heartCommand = heart(item.product.id, saved, viewer),
            heartAction = heartAction(viewer),
            add = addToCart(sku, viewer.inCart, LineAnswers.DETAILS_ANSWER),
            buy = buyNow(sku, viewer),
        )
    }

    /**
     * «Buy now» (B-48): what «Add to cart» puts in, with the line selected — checkout takes the selected
     * lines only, and a line the shopper had unticked would otherwise be left behind — then checkout. A
     * guest goes there through sign-in, by the address the cart's «Sign in to check out» uses, so both
     * ways to checkout run the same sign-in and the same merge, and none goes through a page refused
     * first. At the line's limit there is nothing to add, and «Buy now» still buys what is there: the
     * line is only selected. Out of stock there is nothing to buy.
     */
    private fun buyNow(
        sku: Sku,
        viewer: Viewer,
    ): LineCommand? {
        if (sku.stock <= 0) return null
        val add = addToCart(sku, viewer.inCart)?.change
        return LineCommand(
            url = CartPaths.line(sku.id),
            change = LineChange(quantity = add?.quantity, selected = true),
            next = NavigateAction(if (viewer.customerId == null) CartScreen.SIGN_IN else CartScreen.CHECKOUT),
        )
    }

    /**
     * One group per option the SKUs vary in (colour, then bundle), in the order the SKUs list them. An
     * option points at the SKU that has it with the other choices kept, and is unavailable when that
     * SKU is out of stock or does not exist.
     */
    private fun variants(
        item: Listed,
        selected: Sku,
    ): List<VariantGroup> {
        val keys = item.skus.flatMap { it.options.keys }.distinct()
        return keys.mapNotNull { key ->
            val values = item.skus.mapNotNull { it.options[key] }.distinct()
            if (values.size < 2 && key != "colour") return@mapNotNull null
            VariantGroup(
                name = key.replaceFirstChar { it.uppercase() },
                selectedLabel = selected.options[key].orEmpty(),
                options =
                    values.map { value ->
                        val wanted = selected.options + (key to value)
                        val target =
                            item.skus.firstOrNull { it.options == wanted }
                                ?: item.skus.first { it.options[key] == value }
                        VariantOption(
                            label = value,
                            skuId = target.id,
                            selected = selected.options[key] == value,
                            available = target.stock > 0,
                            action = NavigateAction("/p/${item.product.id}?sku=${target.id}"),
                        )
                    },
            )
        }
    }

    /**
     * «Silver is out of stock.» and, when another colour of the same bundle is in stock, where to turn
     * (Product_OutOfStock).
     */
    private fun stockAdvice(
        item: Listed,
        sku: Sku,
        courier: java.time.LocalDate,
    ): Highlight {
        val name = sku.options["colour"] ?: item.product.title
        val other =
            item.skus.firstOrNull {
                it.stock > 0 && it.options["bundle"] == sku.options["bundle"] &&
                    it.options["colour"] != sku.options["colour"]
            }
        val text =
            other?.options?.get("colour")?.let {
                "$it is in stock and arrives ${calendar.label(courier).replaceFirstChar { c -> c.lowercase() }}."
            } ?: ""
        return Highlight("$name is out of stock.", text)
    }

    private fun highlights(item: Listed): List<Highlight> =
        item.product.specifications
            .take(HIGHLIGHTS)
            .map { (k, v) -> Highlight(k, v) }

    /** «or 4 payments of $87.25 with Haul Pay», for prices from $50 to $2,000 (research D6). */
    private fun haulPay(cents: Int): String? =
        if (HaulPay.offered(cents)) "or 4 payments of ${money(HaulPay.paymentCents(cents))} with Haul Pay" else null

    private fun crumbs(
        item: Listed,
        categories: List<Category>,
    ): List<Crumb> {
        val leaf = categories.first { it.slug == item.product.categorySlug }
        val path =
            generateSequence(
                leaf,
            ) { c -> categories.firstOrNull { it.slug == c.parentSlug } }.toList().reversed()
        return listOf(Crumb("Home", NavigateAction("/"))) + path.map { Crumb(it.name, categoryLink(it.slug)) } +
            Crumb(item.product.title)
    }

    companion object {
        private const val PHOTOS = 8

        /** The placeholder tones of the thumbnails after the first (research D8). */
        private val GALLERY_TONES = listOf("#DEDCF7", "#E9E6E0", "#E0EEF7")
        private const val HIGHLIGHTS = 4
        private const val BESTSELLER_REVIEWS = 2_000
        private const val FREE_DELIVERY_CENTS = 3_500
        private const val DELIVERY_FEE_CENTS = 599
    }
}
