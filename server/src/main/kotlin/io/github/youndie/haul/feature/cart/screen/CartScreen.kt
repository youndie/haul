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
import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.feature.catalog.domain.count
import io.github.youndie.haul.feature.catalog.domain.money
import io.github.youndie.haul.feature.catalog.screen.card
import io.github.youndie.haul.feature.catalog.screen.navigation
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
 * PromoApplied, PromoError, ItemChanged and Guest are what the stored cart is, not separate builders.
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
        val lines = commands.priced(cart)
        val categories = catalog.categories()
        val units = lines.sumOf { it.stored.quantity }
        val frame = viewer.copy(cartCount = units)
        if (lines.isEmpty()) return Frame.page("cart", frame, navigation(categories), empty())

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
                groups = groups(lines),
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
        return Frame.page("cart", frame, navigation(categories), listOf(title(items(units)), body))
    }

    private suspend fun groups(lines: List<PricedLine>): List<CartGroup> =
        lines.groupBy { it.item.product.sellerId }.map { (sellerId, sellerLines) ->
            val seller = catalog.seller(sellerId)
            val day = sellerLines.maxOf { calendar.courier(it.item) }
            CartGroup(
                id = "group-$sellerId",
                seller = seller?.name ?: sellerId,
                delivery = "Courier · " + calendar.label(day),
                lines = sellerLines.map(::line),
            )
        }

    private fun line(line: PricedLine): CartLine {
        val change = CartCommands.changeLabel(line)
        return CartLine(
            id = "line-${line.sku.id}",
            skuId = line.sku.id,
            productId = line.item.product.id,
            title = line.item.product.title,
            // `jsonb` does not keep the keys' order; the colour leads, as on the product page.
            options =
                line.sku.options.entries
                    .sortedBy { it.key != "colour" }
                    .joinToString(" · ") { it.value },
            tone = line.item.product.tone,
            label = line.item.product.label,
            price = money(line.priceCents),
            oldPrice = line.listCents.takeIf { it > line.priceCents }?.let(::money),
            each = if (line.stored.quantity > 1) "${money(line.sku.priceCents)} each" else null,
            quantity = line.stored.quantity,
            maxQuantity = minOf(CartCommands.MAX_QUANTITY, maxOf(line.sku.stock, line.stored.quantity)),
            selected = line.counted,
            selectable = selectable(line),
            change = change,
            changeDetail = CartCommands.changeDetail(line),
            acknowledgeLabel = if (line.changed) "OK" else null,
            url = CartPaths.line(line.sku.id),
            acknowledgeUrl = if (line.changed) CartPaths.acknowledge(line.sku.id) else null,
            action = productLink(line.item.product.id),
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
        val points = "${count(totals.points(plus))} points"
        return OrderSummary(
            id = "summary",
            rows = rows,
            totalLabel = "Total",
            // A price tag, as the canvas writes the total («$512»); the rows above keep their cents.
            total = money(totals.totalCents),
            promo = promoField,
            points = if (guest) null else "You'll earn $points on this order",
            pointsAccent = if (guest) null else points,
            checkoutLabel = if (guest) "Sign in to check out" else "Checkout",
            checkoutEnabled = totals.counted > 0,
            checkoutAction = NavigateAction(if (guest) SIGN_IN else CHECKOUT),
        )
    }

    private suspend fun empty(): List<KompotComponent> {
        val deals = catalog.deals()
        val picks = catalog.listed(deals.map { it.skuId.substringBeforeLast('-') }.distinct())
        return listOf(
            title(count = null),
            EmptyState(
                id = "empty",
                title = "Your cart is empty",
                accent = "empty",
                text = "Today’s deals end at midnight — up to −70 % in the Autumn mega sale.",
                actionLabel = "See today’s deals",
                action = NavigateAction("/deals"),
                primary = true,
            ),
            // The picks are the day's deals, not recommendations (feature-recommendations), so the
            // subtitle says so rather than the canvas's «Based on your recent views».
            SectionHeader("picked-title", "Picked for you", subtitle = "From today’s deals", accent = "you"),
            ProductGrid("picked", picks.take(PICKS).map { card(it, calendar, photos) }, columns = PICKS),
        )
    }

    companion object {
        private const val PICKS = 6

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
 * The summary's rows over [totals] — «Items (3) $652.00» for the [units] counted, «Discount −$140.00»,
 * the promo code inside the discount when it took something off («Promo · AUTUMN10 −$50.00»), «Delivery
 * Free» — the same on the cart and at checkout; the savings are marked to be drawn in the sale red.
 */
internal fun summaryRows(
    totals: Totals,
    promoCode: String?,
    units: Int,
): List<SummaryRow> =
    buildList {
        add(SummaryRow("Items ($units)", CartScreen.exact(totals.itemsCents)))
        add(SummaryRow("Discount", "−" + CartScreen.exact(totals.discountCents), saving = true))
        if (promoCode != null && totals.promoCents > 0) {
            add(SummaryRow("Promo · $promoCode", "−" + CartScreen.exact(totals.promoCents), saving = true))
        }
        add(SummaryRow("Delivery", if (totals.deliveryCents == 0) "Free" else CartScreen.exact(totals.deliveryCents)))
    }
