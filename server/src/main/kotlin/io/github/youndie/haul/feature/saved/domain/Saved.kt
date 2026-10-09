package io.github.youndie.haul.feature.saved.domain

import io.github.youndie.haul.StoreClock
import io.github.youndie.haul.feature.account.domain.SavedLists
import io.github.youndie.haul.feature.account.domain.SavedSummary
import io.github.youndie.haul.feature.cart.domain.CartError
import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.cart.domain.CartRepository
import io.github.youndie.haul.feature.catalog.domain.CatalogError
import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.domain.Listed
import io.github.youndie.haul.feature.catalog.domain.PriceList
import java.time.OffsetDateTime

// The Saved list (B-20, feature-account): one list per customer of the products they hearted — on a card
// or on the product page — and of the cart lines they saved for later. A product is in it once, at the
// price and the day of its first save; «Price dropped» compares that price with the product's price now.

/** A product in a customer's Saved list: when it was saved ([savedAt]) and at what price ([savedPriceCents]). */
internal data class SavedItem(
    val productId: String,
    val savedPriceCents: Int,
    val savedAt: OffsetDateTime,
)

/** The Saved list's storage. Every method names the customer whose list it is. */
internal interface SavedRepository {
    /** The customer's list, newest first. */
    suspend fun items(customerId: String): List<SavedItem>

    /** The products in the customer's list: what draws a heart filled. */
    suspend fun productIds(customerId: String): Set<String>

    /**
     * Adds [productId] at [priceCents] on [at] unless the list holds it already, which keeps its first
     * price and day; whether it went in.
     */
    suspend fun save(
        customerId: String,
        productId: String,
        priceCents: Int,
        at: OffsetDateTime,
    ): Boolean

    /** Takes [productId] out of the customer's list; one that is not there is nothing to do. */
    suspend fun remove(
        customerId: String,
        productId: String,
    )
}

/**
 * A saved product as the list draws it: the product as the catalog lists it now, and what it cost when it
 * was saved. [dropCents] is how much cheaper it is — the cheapest SKU in stock now under the price at
 * saving (feature-account, «Price drops N») — and `null` when it is not, or when nothing of it is in stock.
 */
internal data class SavedEntry(
    val item: Listed,
    val savedPriceCents: Int,
    val savedAt: OffsetDateTime,
) {
    val dropCents: Int? = (savedPriceCents - item.shown.priceCents).takeIf { item.inStock && it > 0 }
}

/**
 * The commands of the Saved list (endpoint-saved). Each is the state it leaves, so a command sent twice
 * leaves the list as one does (feature-account, «Saved twice»).
 */
internal class SavedCommands(
    private val saved: SavedRepository,
    private val catalog: CatalogRepository,
    private val carts: CartRepository,
    private val clock: StoreClock,
) {
    /**
     * Keeps [productId] in the customer's list at its price now — the cheapest SKU in stock, as its card
     * shows it to them at [prices] — or, when it is there already, changes nothing. A product the catalog
     * does not have is `404 product_not_found`.
     */
    suspend fun save(
        customerId: String,
        productId: String,
        prices: PriceList,
    ) {
        val item = catalog.product(productId, prices) ?: throw CatalogError.ProductNotFound(productId)
        saved.save(customerId, productId, item.shown.priceCents, clock.now().toOffsetDateTime())
    }

    /** Lets [productId] go from the customer's list; one that is not in it is already gone. */
    suspend fun remove(
        customerId: String,
        productId: String,
    ) = saved.remove(customerId, productId)

    /**
     * «Save for later» on a cart line: the line's product goes into the Saved list and the line leaves
     * the cart — a move, as the phrase means in a shop. The save comes first, so a failure between the two
     * leaves the line in the cart and the product saved, and the same press again finishes the move. A
     * SKU the cart holds no line of is `404 line_not_found`, which a second press of a finished move gets.
     */
    suspend fun saveForLater(
        owner: CartOwner.Customer,
        skuId: String,
    ) {
        carts.cart(owner).line(skuId) ?: throw CartError.LineNotFound(skuId)
        val item =
            catalog.listedBySkus(setOf(skuId), owner.prices).singleOrNull() ?: throw CartError.LineNotFound(skuId)
        save(owner.id, item.product.id, owner.prices)
        carts.removeLines(owner, setOf(skuId))
    }
}

/**
 * The customer's Saved list read against the catalog now: [entries], newest first — a product the
 * catalog no longer lists is left out — and, for the account (B-19's port), how many there are and how
 * many got cheaper.
 */
internal class SavedListing(
    private val saved: SavedRepository,
    private val catalog: CatalogRepository,
) : SavedLists {
    /** The list against the catalog at [prices], the customer's own (B-53): a drop is against what they would pay. */
    suspend fun entries(
        customerId: String,
        prices: PriceList,
    ): List<SavedEntry> {
        val items = saved.items(customerId)
        val listed = catalog.listed(items.map { it.productId }, prices).associateBy { it.product.id }
        return items.mapNotNull { item ->
            listed[item.productId]?.let { SavedEntry(it, item.savedPriceCents, item.savedAt) }
        }
    }

    override suspend fun summary(
        customerId: String,
        prices: PriceList,
    ): SavedSummary {
        val entries = entries(customerId, prices)
        return SavedSummary(saved = entries.size, priceDrops = entries.count { it.dropCents != null })
    }
}
