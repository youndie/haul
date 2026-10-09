package io.github.youndie.haul.feature.cart.domain

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.domain.Listed
import io.github.youndie.haul.feature.catalog.domain.Sku
import io.github.youndie.haul.feature.catalog.domain.money

/**
 * The cart's commands (endpoint-cart) and the rules they hold (feature-cart): quantity 1…10 and never
 * above stock, one promo code per cart, a changed line accepted before it counts again. Each refusal
 * is a [CartError]; a refused promo code is also remembered, so the next tree draws the field with
 * the code and the reason (`Cart_PromoError`).
 */
internal class CartCommands(
    private val carts: CartRepository,
    private val catalog: CatalogRepository,
    private val clock: StoreClock,
) {
    /** Adds the SKU, or changes its line's quantity or selection. */
    suspend fun changeLine(
        owner: CartOwner,
        skuId: String,
        change: LineChange,
    ) {
        if (change.quantity == null && change.selected == null) {
            throw CartError.Invalid("quantity", "Send a quantity, a selection or both")
        }
        change.quantity?.let {
            if (it !in 1..MAX_QUANTITY) throw CartError.Invalid("quantity", "A quantity is 1 to $MAX_QUANTITY, not $it")
        }
        val (_, sku) = sku(skuId, owner)
        val existing = carts.cart(owner).line(skuId)
        val quantity = change.quantity ?: existing?.quantity ?: 1
        if (sku.stock <= 0 && (existing == null || change.quantity != null)) {
            throw CartError.OutOfStock("This item is out of stock")
        }
        if (change.quantity != null && quantity > sku.stock) {
            throw CartError.OutOfStock("Only ${sku.stock} left in stock")
        }
        carts.putLine(
            owner,
            existing?.copy(quantity = quantity, selected = change.selected ?: existing.selected)
                ?: StoredLine(
                    skuId = skuId,
                    quantity = quantity,
                    selected = change.selected ?: true,
                    seenPriceCents = sku.priceCents,
                    seenInStock = true,
                    addedAt = clock.now().toOffsetDateTime(),
                ),
        )
    }

    /** «Remove» and «Delete selected». */
    suspend fun removeLines(
        owner: CartOwner,
        skuIds: List<String>,
    ) {
        if (skuIds.isEmpty()) throw CartError.Invalid("skuIds", "Name at least one line to delete")
        carts.removeLines(owner, skuIds.toSet())
    }

    /**
     * «OK» on a changed line: the price and the stock it shows now become the ones the shopper saw,
     * and the line is selected again if it can be bought.
     */
    suspend fun acknowledge(
        owner: CartOwner,
        skuId: String,
    ) {
        val line = carts.cart(owner).line(skuId) ?: throw CartError.LineNotFound(skuId)
        val (_, sku) = sku(skuId, owner)
        val inStock = sku.stock > 0
        carts.putLine(owner, line.copy(seenPriceCents = sku.priceCents, seenInStock = inStock, selected = inStock))
    }

    /** Applies a code: one per cart, and only one that is valid now for something selected. */
    suspend fun applyPromo(
        owner: CartOwner,
        raw: String,
    ) {
        val code = raw.trim().uppercase()
        if (code.isEmpty()) throw CartError.Invalid("code", "Type a promo code")
        val cart = carts.cart(owner)
        when (cart.promoCode) {
            code -> return

            null -> Unit

            else -> throw CartError.Promo(
                ErrorCode.PromoAlreadyApplied,
                "Only one code per order: remove ${cart.promoCode} first",
            )
        }
        val promo = carts.promo(code) ?: refuse(owner, code, ErrorCode.PromoNotFound)
        val now = clock.now().toOffsetDateTime()
        when {
            !now.isBefore(promo.endsAt) -> refuse(owner, code, ErrorCode.PromoExpired)
            now.isBefore(promo.startsAt) -> refuse(owner, code, ErrorCode.PromoNotApplicable)
        }
        if (priced(owner, cart).none { it.counted }) {
            refuse(owner, code, ErrorCode.PromoNotApplicable)
        }
        carts.setPromo(owner, code)
    }

    suspend fun removePromo(owner: CartOwner) {
        carts.setPromo(owner, null)
    }

    /**
     * Sign-in keeps what the guest put in the cart (feature-identity): the guest's lines move into the
     * customer's cart — the same SKU's quantities summed and capped at ten and at the stock, a new SKU
     * appended as the guest had it — the customer's code stays, or the guest's comes along when the
     * customer had none, and the guest's cart is deleted. Merging a guest with no cart deletes nothing
     * and changes nothing: a second merge of the same guest is harmless.
     */
    suspend fun merge(
        guest: CartOwner.Guest,
        customer: CartOwner.Customer,
    ) {
        val from = carts.cart(guest)
        val into = carts.cart(customer)
        val stock =
            catalog
                .listedBySkus(from.lines.map { it.skuId }.toSet(), customer.prices)
                .flatMap { it.skus }
                .associate { it.id to it.stock }
        val lines =
            from.lines.map { line ->
                val existing = into.line(line.skuId) ?: return@map line
                existing.copy(quantity = mergedQuantity(existing.quantity + line.quantity, stock[line.skuId] ?: 0))
            }
        carts.merge(guest, customer, lines, into.promoCode ?: from.promoCode)
    }

    /**
     * The stored lines of [owner]'s [cart] with their SKUs as the catalog has them now, at the owner's
     * prices (B-53), in the cart's order.
     */
    suspend fun priced(
        owner: CartOwner,
        cart: StoredCart,
    ): List<PricedLine> {
        val items = catalog.listedBySkus(cart.lines.map { it.skuId }.toSet(), owner.prices)
        return cart.lines
            .mapNotNull { line ->
                val item =
                    items.firstOrNull { listed -> listed.skus.any { it.id == line.skuId } } ?: return@mapNotNull null
                PricedLine(line, item, item.skus.first { it.id == line.skuId })
            }
    }

    private suspend fun sku(
        skuId: String,
        owner: CartOwner,
    ): Pair<Listed, Sku> {
        val item = catalog.listedBySkus(setOf(skuId), owner.prices).singleOrNull() ?: throw CartError.SkuNotFound(skuId)
        return item to item.skus.first { it.id == skuId }
    }

    private suspend fun refuse(
        owner: CartOwner,
        code: String,
        error: ErrorCode,
    ): Nothing {
        carts.setPromo(owner, null, attempt = code, error = error)
        throw CartError.Promo(error, promoMessage(error))
    }

    companion object {
        const val MAX_QUANTITY = 10

        /**
         * A merged line's quantity: the [sum], at most ten and at most the [stock]; never below one,
         * because a line is one item or more — one out of stock shows as changed and cannot be bought.
         */
        fun mergedQuantity(
            sum: Int,
            stock: Int,
        ): Int = minOf(sum, MAX_QUANTITY, stock).coerceAtLeast(1)

        /** What a refused code is told: the command's error message, and the field's text in the next tree. */
        fun promoMessage(error: ErrorCode): String =
            when (error) {
                ErrorCode.PromoNotFound -> "There is no such code"
                ErrorCode.PromoExpired -> "This code has expired"
                else -> "This code does not apply to your cart"
            }

        /** «Price changed: now $26» or «Out of stock». */
        fun changeLabel(line: PricedLine): String? =
            when {
                line.outOfStock -> "Out of stock"
                line.changed -> "Price changed: now ${money(line.sku.priceCents)}"
                else -> null
            }

        /** «It was $24 when you added it»: the price the shopper saw, under a changed price; `null` otherwise. */
        fun changeDetail(line: PricedLine): String? =
            if (!line.outOfStock && line.priceChanged) {
                "It was ${money(line.stored.seenPriceCents)} when you added it"
            } else {
                null
            }
    }
}
