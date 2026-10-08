package io.github.youndie.haul.shell

import io.github.youndie.haul.ui.FooterColumn
import io.github.youndie.haul.ui.HaulFooter
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.ColumnComponent

/**
 * Who is looking: what the header shows and which blocks a screen offers. [customerId] is a signed-in
 * customer's id, `null` for a guest; nothing sets it until sign-in arrives (B-12).
 */
internal data class Viewer(
    val firstName: String? = null,
    val cartCount: Int = 0,
    val customerId: String? = null,
)

/** The frame every screen but checkout sits in: the header above, the screen's sections, the footer. */
internal object Frame {
    fun page(
        id: String,
        viewer: Viewer,
        navigation: List<String>,
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
        navigation: List<String>,
        query: String?,
    ) = HaulHeader(
        id = "header",
        deliverTo = DEFAULT_PLACE,
        deliveryPromise = "Free delivery over $35",
        customerName = viewer.firstName,
        cartCount = viewer.cartCount,
        searchPlaceholder = "Search 2.4 million products",
        query = query,
        categories = navigation,
    )

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
