package io.github.youndie.haul.shell

import io.github.youndie.haul.ui.FooterColumn
import io.github.youndie.haul.ui.HaulFooter
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.Link
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction

/**
 * Who is looking: what the header shows and which blocks a screen offers. [customerId] is a signed-in
 * customer's id, `null` for a guest; [inCart] is how many of each SKU their cart holds, by SKU id;
 * [Viewers] tells both from a request.
 */
internal data class Viewer(
    val firstName: String? = null,
    val cartCount: Int = 0,
    val customerId: String? = null,
    val inCart: Map<String, Int> = emptyMap(),
)

/**
 * The frame every screen but checkout sits in: the header above, the screen's sections, the footer.
 * [navigation] is every top-level category with its link: the header's «Catalog» menu, and the first
 * [ROW] of them its category row.
 */
internal object Frame {
    fun page(
        id: String,
        viewer: Viewer,
        navigation: List<Link>,
        sections: List<KompotComponent>,
        query: String? = null,
        footer: Boolean = false,
    ): KompotComponent =
        ColumnComponent(
            id = id,
            children =
                listOf(header(viewer, navigation, query)) + sections + (if (footer) listOf(FOOTER) else emptyList()),
        )

    private fun header(
        viewer: Viewer,
        navigation: List<Link>,
        query: String?,
    ) = HaulHeader(
        id = "header",
        deliverTo = DEFAULT_PLACE,
        deliveryPromise = "Free delivery over $35",
        customerName = viewer.firstName,
        cartCount = viewer.cartCount,
        searchPlaceholder = "Search 2.4 million products",
        query = query,
        categories = navigation.take(ROW).map { it.label },
        account = NavigateAction(if (viewer.customerId == null) SIGN_IN else ACCOUNT),
        catalog = navigation,
        deals = NavigateAction(DEALS),
        cart = NavigateAction(CART),
        orders = NavigateAction(if (viewer.customerId == null) SIGN_IN else ORDERS),
    )

    /** How many categories the header's row names. */
    private const val ROW = 10

    /** Where the account shortcut sends a guest: the client opens the provider's page (feature-identity). */
    const val SIGN_IN = "/sign-in"

    /** Where it sends a customer. */
    const val ACCOUNT = "/account"

    /** Where «Deals» goes: every product on sale, today's deals first (`/ui/deals`). */
    const val DEALS = "/deals"

    /** Where the cart button goes (`/ui/cart`). */
    const val CART = "/cart"

    /**
     * Where «Orders» takes a customer — and an order page that is not there, «Go to your orders», and the
     * order page's «Orders» crumb: the orders' history (B-19), which the order pages sit under. A guest's
     * «Orders» is sign-in, as the account shortcut is.
     */
    const val ORDERS = "$ACCOUNT/orders"

    /** The store's default place, until a customer's address says otherwise (feature-browse). */
    private const val DEFAULT_PLACE = "Brooklyn, NY 11211"

    private val FOOTER =
        HaulFooter(
            id = "footer",
            columns =
                listOf(
                    FooterColumn("Shop", listOf("Deals", "New arrivals", "Gift cards", "Haul Plus")),
                    FooterColumn("Sell", listOf("Open a store", "Seller center", "Fulfillment", "Ads")),
                    FooterColumn("Help", listOf("Track an order", "Returns", "Delivery", "Contact us")),
                    FooterColumn("Company", listOf("About", "Careers", "Press", "Privacy")),
                ),
            appTitle = "Get the app",
            appText = "Order tracking, price drop alerts and app-only deals.",
        )
}
