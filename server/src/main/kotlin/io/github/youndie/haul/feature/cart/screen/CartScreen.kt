package io.github.youndie.haul.feature.cart.screen

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.feature.cart.CartPaths
import io.github.youndie.haul.feature.cart.domain.CartCommands
import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.cart.domain.CartRepository
import io.github.youndie.haul.feature.cart.domain.PricedLine
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
        val sections =
            listOf(
                PageTitle("title", "Cart", items(units)),
                CartSelection(
                    id = "selection",
                    allSelected = selectable.isNotEmpty() && selectable.all { it.counted },
                    selectedCount = totals.counted,
                    linesUrl = CartPaths.LINES,
                ),
            ) + groups(lines) +
                listOf(
                    promoField(cart),
                    summary(totals, promo?.code, owner, plus),
                )
        return Frame.page("cart", frame, navigation(categories), sections)
    }

    private suspend fun groups(lines: List<PricedLine>): List<CartGroup> =
        lines.groupBy { it.item.product.sellerId }.map { (sellerId, sellerLines) ->
            val seller = catalog.seller(sellerId)
            val day = sellerLines.maxOf { calendar.courier(it.item) }
            CartGroup(
                id = "group-$sellerId",
                seller = seller?.name ?: sellerId,
                delivery = "Delivery " + calendar.label(day).let { if (it == "Tomorrow") "tomorrow" else it },
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
            acknowledgeLabel = if (line.changed) "OK" else null,
            url = CartPaths.line(line.sku.id),
            acknowledgeUrl = if (line.changed) CartPaths.acknowledge(line.sku.id) else null,
            action = productLink(line.item.product.id),
        )
    }

    private fun selectable(line: PricedLine): Boolean = !line.changed && !line.outOfStock

    private fun promoField(cart: StoredCart): PromoField =
        when {
            cart.promoCode != null -> {
                PromoField("promo", CartPaths.PROMO, code = cart.promoCode, applied = true)
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
        promoCode: String?,
        owner: CartOwner,
        plus: Boolean,
    ): OrderSummary {
        val rows = summaryRows(totals, promoCode)
        val guest = owner is CartOwner.Guest
        return OrderSummary(
            id = "summary",
            rows = rows,
            totalLabel = "Total",
            total = exact(totals.totalCents),
            points = if (guest) null else "You'll earn ${count(totals.points(plus))} points",
            checkoutLabel = if (guest) "Sign in to check out" else "Checkout",
            checkoutEnabled = totals.counted > 0,
            checkoutAction = NavigateAction(if (guest) SIGN_IN else CHECKOUT),
        )
    }

    private suspend fun empty(): List<KompotComponent> {
        val deals = catalog.deals()
        val picks = catalog.listed(deals.map { it.skuId.substringBeforeLast('-') }.distinct())
        return listOf(
            EmptyState(
                id = "empty",
                title = "Your cart is empty",
                text = "Today's deals end at midnight.",
                actionLabel = "See today's deals",
                action = NavigateAction("/deals"),
            ),
            SectionHeader("picked-title", "Picked for you"),
            ProductGrid("picked", picks.take(PICKS).map { card(it, calendar, photos) }, columns = PICKS, scroll = true),
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
    }
}

/**
 * The summary's rows over [totals] — «Items $652.00», «Discount −$140.00», the promo code inside the
 * discount when it took something off, «Delivery Free» — the same on the cart and at checkout.
 */
internal fun summaryRows(
    totals: Totals,
    promoCode: String?,
): List<SummaryRow> =
    buildList {
        add(SummaryRow("Items", CartScreen.exact(totals.itemsCents)))
        add(SummaryRow("Discount", "−" + CartScreen.exact(totals.discountCents)))
        if (promoCode != null && totals.promoCents > 0) {
            add(SummaryRow("Promo $promoCode", "−" + CartScreen.exact(totals.promoCents), detail = true))
        }
        add(SummaryRow("Delivery", if (totals.deliveryCents == 0) "Free" else CartScreen.exact(totals.deliveryCents)))
    }
