package io.github.youndie.haul.shell

// Where a component of the outline is drawn and built (B-163), on GitHub: the view its renderer draws it with in
// `composeApp`, and the one file of `server` that builds it. A type built in several places — a section header, a
// grid every listing has — names no builder, and kompot's own types (`column`, `text`) name neither: a link the table
// would have to guess is left out. `HoodSourcesTest` holds every path to a file of the repository.

/** Where the component of a wire type is drawn ([view]) and built ([builder]), as paths in the repository. */
internal class HoodSource(
    val view: String?,
    val builder: String?,
)

internal object HoodSources {
    /** The repository's files on GitHub, at the default branch. */
    const val REPOSITORY: String = "https://github.com/youndie/haul/blob/main/"

    private const val CLIENT = "composeApp/src/commonMain/kotlin/io/github/youndie/haul/"
    private const val SERVER = "server/src/main/kotlin/io/github/youndie/haul/"

    /** The sources of [type], `null` when the table has none. */
    fun of(type: String): HoodSource? = BY_TYPE[type]

    /** The address of [path] on GitHub. */
    fun url(path: String): String = REPOSITORY + path

    /** Every type the table knows, for the test that holds it to the repository. */
    val types: Set<String> get() = BY_TYPE.keys

    private fun source(
        view: String,
        builder: String? = null,
    ) = HoodSource(CLIENT + view, builder?.let { SERVER + it })

    private const val ABOUT = "feature/about/screen/AboutScreen.kt"
    private const val HOME = "feature/catalog/screen/HomeScreen.kt"
    private const val CATALOG = "feature/catalog/screen/CatalogScreen.kt"
    private const val PRODUCT = "feature/catalog/screen/ProductScreen.kt"
    private const val CART = "feature/cart/screen/CartScreen.kt"
    private const val CHECKOUT = "feature/checkout/screen/CheckoutScreen.kt"
    private const val ORDER = "feature/order/screen/OrderScreen.kt"
    private const val REVIEWS = "feature/reviews/screen/ReviewTabs.kt"
    private const val SEARCH = "feature/search/screen/SearchScreen.kt"
    private const val PLUS = "feature/membership/screen/PlusOffer.kt"
    private const val FRAME = "shell/Frame.kt"

    private val BY_TYPE: Map<String, HoodSource> =
        mapOf(
            "haul_header" to source("ui/HaulHeaderView.kt", FRAME),
            "haul_footer" to source("ui/HaulFooterView.kt", FRAME),
            "haul_demo_note" to source("ui/AboutViews.kt", ABOUT),
            "haul_about" to source("ui/AboutViews.kt", ABOUT),
            "haul_product_card" to source("ui/ProductCardView.kt", "feature/catalog/screen/Cards.kt"),
            "haul_product_grid" to source("ui/ProductGridView.kt"),
            "haul_empty_state" to source("ui/EmptyStateView.kt"),
            "haul_campaign_row" to source("feature/home/HomeViews.kt", HOME),
            "haul_section_header" to source("feature/home/HomeViews.kt"),
            "haul_category_grid" to source("feature/home/HomeViews.kt"),
            "haul_plus_block" to source("feature/home/HomeViews.kt", PLUS),
            "haul_plus_trial_dialog" to source("feature/home/PlusTrialDialog.kt", PLUS),
            "haul_breadcrumbs" to source("feature/catalog/CatalogViews.kt"),
            "haul_page_title" to source("feature/catalog/CatalogViews.kt"),
            "haul_filter_chips" to source("feature/catalog/CatalogViews.kt"),
            "haul_pagination" to source("feature/catalog/CatalogViews.kt"),
            "haul_filtered_results" to source("feature/catalog/CatalogViews.kt", CATALOG),
            "haul_facet_panel" to source("feature/catalog/FacetPanelView.kt", CATALOG),
            "haul_product_details" to source("feature/product/ProductDetailsView.kt", PRODUCT),
            "haul_product_tabs" to source("feature/product/ProductTabsView.kt", PRODUCT),
            "haul_product_description" to source("feature/product/ProductTabsView.kt", PRODUCT),
            "haul_specification_list" to source("feature/product/ProductTabsView.kt", PRODUCT),
            "haul_product_reviews" to source("feature/product/ProductTabsView.kt", REVIEWS),
            "haul_product_questions" to source("feature/product/ProductTabsView.kt", REVIEWS),
            "haul_review_form" to source("feature/product/ReviewDialogs.kt", REVIEWS),
            "haul_question_form" to source("feature/product/ReviewDialogs.kt", REVIEWS),
            "haul_search_suggest_panel" to source("feature/search/SearchViews.kt", SEARCH),
            "haul_search_no_results" to source("feature/search/SearchViews.kt", SEARCH),
            "haul_cart_body" to source("feature/cart/CartViews.kt", CART),
            "haul_promo_field" to source("feature/cart/CartViews.kt", CART),
            "haul_checkout_header" to source("feature/checkout/CheckoutViews.kt", CHECKOUT),
            "haul_checkout_body" to source("feature/checkout/CheckoutViews.kt", CHECKOUT),
            "haul_account_body" to source("feature/account/AccountViews.kt", "feature/account/screen/AccountScreen.kt"),
            "haul_order_body" to source("feature/order/OrderViews.kt", ORDER),
            "haul_return_form" to source("feature/order/ReturnDialog.kt", ORDER),
        )
}
