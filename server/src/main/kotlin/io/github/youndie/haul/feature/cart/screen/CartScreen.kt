package io.github.youndie.haul.feature.cart.screen

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.feature.cart.CartPaths
import io.github.youndie.haul.feature.cart.domain.CartCommands
import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.cart.domain.CartRepository
import io.github.youndie.haul.feature.cart.domain.PricedLine
import io.github.youndie.haul.feature.cart.domain.PromoCode
import io.github.youndie.haul.feature.cart.domain.StoredCart
import io.github.youndie.haul.feature.cart.domain.Totals
import io.github.youndie.haul.feature.catalog.domain.Campaign
import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.domain.Category
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.feature.catalog.domain.count
import io.github.youndie.haul.feature.catalog.domain.liveSale
import io.github.youndie.haul.feature.catalog.domain.money
import io.github.youndie.haul.feature.catalog.screen.HomeScreen
import io.github.youndie.haul.feature.catalog.screen.card
import io.github.youndie.haul.feature.catalog.screen.navigation
import io.github.youndie.haul.feature.catalog.screen.popular
import io.github.youndie.haul.feature.catalog.screen.popularTitle
import io.github.youndie.haul.feature.catalog.screen.productLink
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.ui.CartBody
import io.github.youndie.haul.ui.CartGroup
import io.github.youndie.haul.ui.CartLine
import io.github.youndie.haul.ui.CartSelection
import io.github.youndie.haul.ui.EmptyState
import io.github.youndie.haul.ui.OrderSummary
import io.github.youndie.haul.ui.PageTitle
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.PromoField
import io.github.youndie.haul.ui.SectionHeader
import io.github.youndie.haul.ui.SummaryRow
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.NavigateAction
import java.text.NumberFormat
import java.util.Locale

/**
 * `GET /ui/cart` (screen-cart, feature-cart). One tree covers every server state: Content, Empty,
 * PromoApplied, PromoError, ItemChanged and Guest are what the stored cart is, not separate builders. The empty
 * cart's row is the day's deals as «Picked for you», or with no deal live home's «Popular on Haul» (B-87).
 * Lines group by seller in the order they were added, each group with its latest delivery day.
 */
internal class CartScreen(
    private val carts: CartRepository,
    private val commands: CartCommands,
    private val catalog: CatalogRepository,
    private val calendar: DeliveryCalendar,
    private val clock: StoreClock,
    private val photos: ProductPhotos,
) {
    suspend fun build(
        owner: CartOwner,
        viewer: Viewer = Viewer(customerId = (owner as? CartOwner.Customer)?.id),
    ): KompotComponent {
        val cart = carts.cart(owner)
        val lines = commands.priced(owner, cart)
        val categories = catalog.categories()
        val units = lines.sumOf { it.stored.quantity }
        val frame = viewer.copy(cartCount = units)
        if (lines.isEmpty()) {
            return Frame.page("cart-page", frame, navigation(categories), empty(frame, owner, categories))
        }

        val plus = (owner as? CartOwner.Customer)?.plus ?: false
        val promo = cart.promoCode?.let { carts.promo(it) }
        val totals = Totals.of(lines, promo, plus, clock.now().toOffsetDateTime())
        val selectable = lines.filter { selectable(it) }
        val body =
            CartBody(
                id = "cart",
                selection =
                    CartSelection(
                        id = "selection",
                        allSelected = selectable.isNotEmpty() && selectable.all { it.counted },
                        selectedCount = totals.counted,
                        linesUrl = CartPaths.LINES,
                    ),
                groups = groups(lines, owner),
                summary =
                    summary(
                        totals,
                        countedUnits = lines.filter { it.counted }.sumOf { it.stored.quantity },
                        promoCode = promo?.code,
                        promoField = promoField(cart, promo),
                        owner = owner,
                        plus = plus,
                    ),
            )
        return Frame.page("cart-page", frame, navigation(categories), listOf(title(items(units)), body))
    }

    private suspend fun groups(
        lines: List<PricedLine>,
        owner: CartOwner,
    ): List<CartGroup> =
        lines.groupBy { it.item.product.sellerId }.map { (sellerId, sellerLines) ->
            val seller = catalog.seller(sellerId)
            val day = sellerLines.maxOf { calendar.courier(it.item) }
            CartGroup(
                id = "group-$sellerId",
                seller = seller?.name ?: sellerId,
                delivery = "Courier · " + calendar.label(day),
                lines = sellerLines.map { line(it, owner) },
            )
        }

    /**
     * A line as the cart draws it. «Save for later» moves a customer's line to their Saved list (B-20); a
     * guest's is the way to sign in, which draws the cart again once it has gone through.
     */
    private fun line(
        line: PricedLine,
        owner: CartOwner,
    ): CartLine {
        val change = CartCommands.changeLabel(line)
        return CartLine(
            id = "line-${line.sku.id}",
            skuId = line.sku.id,
            productId = line.item.product.id,
            title = line.item.product.listingName,
            // `jsonb` does not keep the keys' order; the colour leads, as on the product page.
            options =
                line.sku.options.entries
                    .sortedBy { it.key != "colour" }
                    .joinToString(" · ") { it.value },
            tone = line.item.product.tone,
            label = line.item.product.label,
            image = photos.url(line.item.product),
            price = money(line.totalCents),
            oldPrice = line.listTotalCents.takeIf { it > line.totalCents }?.let(::money),
            each = if (line.stored.quantity > 1) "${money(line.sku.priceCents)} each" else null,
            quantity = line.stored.quantity,
            maxQuantity = minOf(CartCommands.MAX_QUANTITY, maxOf(line.sku.stock, line.stored.quantity)),
            selected = line.counted,
            selectable = selectable(line),
            change = change,
            changeDetail = CartCommands.changeDetail(line),
            acknowledgeLabel = if (line.changed) "OK" else null,
            url = CartPaths.existingLine(line.sku.id),
            acknowledgeUrl = if (line.changed) CartPaths.acknowledge(line.sku.id) else null,
            action = productLink(line.item.product.id, line.sku.id),
            saveUrl = if (owner is CartOwner.Customer) CartPaths.saveForLater(line.sku.id) else null,
            saveAction = if (owner is CartOwner.Guest) NavigateAction(Frame.SIGN_IN) else null,
        )
    }

    private fun selectable(line: PricedLine): Boolean = !line.changed && !line.outOfStock

    private fun promoField(
        cart: StoredCart,
        promo: PromoCode?,
    ): PromoField =
        when {
            cart.promoCode != null -> {
                PromoField("promo", CartPaths.PROMO, code = cart.promoCode, applied = true, terms = promo?.terms())
            }

            cart.promoAttempt != null && cart.promoError != null -> {
                PromoField(
                    "promo",
                    CartPaths.PROMO,
                    code = cart.promoAttempt,
                    error = CartCommands.promoMessage(cart.promoError),
                )
            }

            else -> {
                PromoField("promo", CartPaths.PROMO)
            }
        }

    private fun summary(
        totals: Totals,
        countedUnits: Int,
        promoCode: String?,
        promoField: PromoField,
        owner: CartOwner,
        plus: Boolean,
    ): OrderSummary {
        val rows = summaryRows(totals, promoCode, countedUnits)
        val guest = owner is CartOwner.Guest
        val earned = totals.points(plus)
        val points = "${count(earned)} points"
        // A guest earns nothing, and an order that earns nothing — nothing ticked — has no line to promise it (B-129).
        val promised = !guest && earned > 0
        return OrderSummary(
            id = "summary",
            rows = rows,
            totalLabel = "Total",
            // A price tag, as the canvas writes the total («$512»); the rows above keep their cents.
            total = money(totals.totalCents),
            promo = promoField,
            points = if (promised) "You’ll earn $points on this order" else null,
            pointsAccent = if (promised) points else null,
            checkoutLabel = if (guest) "Sign in to check out" else "Checkout",
            checkoutEnabled = totals.counted > 0,
            checkoutAction = NavigateAction(if (guest) SIGN_IN else CHECKOUT),
        )
    }

    private suspend fun empty(
        viewer: Viewer,
        owner: CartOwner,
        categories: List<Category>,
    ): List<KompotComponent> {
        val deals = catalog.deals()
        // The owner's prices rather than the viewer's: a cart drawn for a command's answer has a viewer
        // without the customer, and the picks must be at the prices the cart would charge (B-53).
        val picks = catalog.listed(deals.map { it.skuId.substringBeforeLast('-') }.distinct(), owner.prices)
        // The line names both today's deals and the sale, so it is drawn only while both are live (B-59):
        // before, it promised deals and −70 % on a stand that had neither left.
        val at = clock.now().toOffsetDateTime()
        val sale = liveSale(catalog.campaigns(), owner.prices, at)?.takeIf { deals.isNotEmpty() }
        val empty =
            listOf(
                title(count = null),
                EmptyState(
                    id = "empty",
                    title = "Your cart is empty",
                    accent = "empty",
                    text = sale?.let(::saleLine),
                    actionLabel = "See today’s deals",
                    action = NavigateAction(Frame.DEALS),
                    primary = true,
                ),
            )
        // With no live deal there is nothing to pick from (B-58), and the row is home's in the same case (B-87):
        // «Popular on Haul», the same six at the cart's prices, under its own title — it holds no deal, so it does
        // not say «Picked for you · From today’s deals».
        if (picks.isEmpty()) {
            val popular = popular(catalog, calendar, photos, viewer, categories, owner.prices, inPlace = false)
            if (popular.isEmpty()) return empty
            return empty + listOf(popularTitle(), ProductGrid(HomeScreen.POPULAR, popular, columns = PICKS))
        }
        return empty +
            listOf(
                // The picks are the day's deals, not recommendations (feature-recommendations), so the
                // subtitle says so rather than the canvas's «Based on your recent views».
                SectionHeader("picked-title", "Picked for you", subtitle = "From today’s deals", accent = "you"),
                // A pick's «+» changes the page, not only the card: the cart it is on gets its first line, so it
                // is answered with the page again rather than an update of the card (B-95).
                ProductGrid(
                    "picked",
                    picks.take(PICKS).map { card(it, calendar, photos, viewer, inPlace = false) },
                    columns = PICKS,
                ),
            )
    }

    companion object {
        private const val PICKS = 6

        /**
         * The empty cart's sale line (Cart_Empty), drawn from the sale itself (B-107): «Today’s deals end at
         * midnight — up to −70% in the Autumn mega sale.» — its title, the first letter lowered to run on, and its
         * name. The canvas wrote the sale's words into the line, which then named the autumn sale whatever the
         * sale was.
         */
        fun saleLine(sale: Campaign): String =
            "Today’s deals end at midnight — ${sale.title.replaceFirstChar { it.lowercase() }} in the ${sale.name}."

        /** Checkout asks a guest to sign in first (feature-cart); the client opens the provider's page. */
        const val SIGN_IN = "${Frame.SIGN_IN}?next=%2Fcheckout"
        const val CHECKOUT = "/checkout"

        private val GROUPED = NumberFormat.getIntegerInstance(Locale.US)

        /** «$652.00»: the summary always writes cents, unlike a price tag. */
        fun exact(cents: Int): String = "$" + GROUPED.format(cents / 100) + ".%02d".format(cents % 100)

        private fun items(units: Int): String = if (units == 1) "1 item" else "${count(units)} items"

        /** «Cart», with the count in its pill while the cart has lines. */
        private fun title(count: String?) = PageTitle("title", "Cart", count, badge = true)

        /** «10% off items, up to $50»: what an applied code takes off. */
        private fun PromoCode.terms(): String =
            "$percentOff% off items" + (capCents?.let { ", up to ${money(it)}" } ?: "")
    }
}

/**
 * The summary's rows over [totals] — «Items (3) $652.00» for the [units] counted, «Discount −$140.00», the
 * promo code's own row when it took something off («Promo · AUTUMN10 −$50.00»), «Delivery Free» — the same
 * on the cart and at checkout; the savings are marked to be drawn in the sale red. The rows add up to the
 * total (B-100): «Discount» is the sale alone, the code's row beside it rather than inside it, and a row
 * that takes nothing off is not drawn, as the order's page leaves it out. Checkout names the day in the
 * delivery row ([delivery] «Delivery · Wed, Oct 8»).
 */
internal fun summaryRows(
    totals: Totals,
    promoCode: String?,
    units: Int,
    delivery: String = "Delivery",
    pointsCents: Int = 0,
): List<SummaryRow> =
    buildList {
        add(SummaryRow("Items ($units)", CartScreen.exact(totals.itemsCents)))
        if (totals.productDiscountCents > 0) {
            add(SummaryRow("Discount", "−" + CartScreen.exact(totals.productDiscountCents), saving = true))
        }
        if (promoCode != null && totals.promoCents > 0) {
            add(SummaryRow("Promo · $promoCode", "−" + CartScreen.exact(totals.promoCents), saving = true))
        }
        // The points taken off at checkout (`Checkout_PointsApplied`, B-23); the cart has none.
        if (pointsCents > 0) add(SummaryRow("Points", "−" + CartScreen.exact(pointsCents), saving = true))
        add(SummaryRow(delivery, if (totals.deliveryCents == 0) "Free" else CartScreen.exact(totals.deliveryCents)))
    }
