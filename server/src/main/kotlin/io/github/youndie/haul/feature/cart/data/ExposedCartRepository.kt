package io.github.youndie.haul.feature.cart.data

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.cart.domain.CartRepository
import io.github.youndie.haul.feature.cart.domain.PromoCode
import io.github.youndie.haul.feature.cart.domain.StoredCart
import io.github.youndie.haul.feature.cart.domain.StoredLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import java.util.UUID

/**
 * Carts over Exposed. The owner is in every filter: a cart is found by its guest or customer id, never
 * by a cart id a request carries, so «not yours» has nothing to match.
 */
internal class ExposedCartRepository(
    private val database: Database,
) : CartRepository {
    private suspend fun <T> tx(block: () -> T): T = withContext(Dispatchers.IO) { transaction(database) { block() } }

    override suspend fun cart(owner: CartOwner): StoredCart =
        tx {
            val row = CartsTable.selectAll().where { owns(owner) }.singleOrNull() ?: return@tx StoredCart.EMPTY
            StoredCart(
                lines =
                    CartLinesTable
                        .selectAll()
                        .where { CartLinesTable.cartId eq row[CartsTable.id] }
                        .orderBy(CartLinesTable.position)
                        .map(::line),
                promoCode = row[CartsTable.promoCode],
                promoAttempt = row[CartsTable.promoAttempt],
                promoError =
                    row[CartsTable.promoError]?.let { name ->
                        ErrorCode.entries.firstOrNull { it.name == name }
                    },
            )
        }

    override suspend fun putLine(
        owner: CartOwner,
        line: StoredLine,
    ) {
        tx {
            val cartId = cartId(owner)
            forgetAttempt(cartId)
            val next =
                (
                    CartLinesTable
                        .select(CartLinesTable.position.max())
                        .where { CartLinesTable.cartId eq cartId }
                        .single()[CartLinesTable.position.max()] ?: 0
                ) + 1
            // A line already there keeps its place: `position` is left out of the update.
            CartLinesTable.upsert(onUpdateExclude = listOf(CartLinesTable.position, CartLinesTable.addedAt)) {
                it[CartLinesTable.cartId] = cartId
                it[skuId] = line.skuId
                it[quantity] = line.quantity
                it[selected] = line.selected
                it[seenPriceCents] = line.seenPriceCents
                it[seenInStock] = line.seenInStock
                it[addedAt] = line.addedAt
                it[position] = next
            }
        }
    }

    override suspend fun removeLines(
        owner: CartOwner,
        skuIds: Set<String>,
    ) {
        tx {
            val cartId = existingCartId(owner) ?: return@tx
            forgetAttempt(cartId)
            CartLinesTable.deleteWhere { (CartLinesTable.cartId eq cartId) and (CartLinesTable.skuId inList skuIds) }
        }
    }

    override suspend fun setPromo(
        owner: CartOwner,
        code: String?,
        attempt: String?,
        error: ErrorCode?,
    ) {
        tx {
            val cartId = cartId(owner)
            CartsTable.update({ CartsTable.id eq cartId }) {
                it[promoCode] = code
                it[promoAttempt] = attempt
                it[promoError] = error?.name
            }
        }
    }

    override suspend fun promo(code: String): PromoCode? =
        tx {
            PromoCodesTable.selectAll().where { PromoCodesTable.code eq code }.singleOrNull()?.let {
                PromoCode(
                    it[PromoCodesTable.code],
                    it[PromoCodesTable.percentOff],
                    it[PromoCodesTable.capCents],
                    it[PromoCodesTable.startsAt],
                    it[PromoCodesTable.endsAt],
                )
            }
        }

    private fun owns(owner: CartOwner): Op<Boolean> =
        when (owner) {
            is CartOwner.Guest -> CartsTable.guestId eq owner.id
            is CartOwner.Customer -> CartsTable.customerId eq owner.id
        }

    private fun existingCartId(owner: CartOwner): String? =
        CartsTable
            .select(CartsTable.id)
            .where { owns(owner) }
            .singleOrNull()
            ?.get(CartsTable.id)

    /**
     * The owner's cart, created on the first write. Two first writes at once both insert; the unique
     * owner index lets one through and the other reads the winner's id.
     */
    private fun cartId(owner: CartOwner): String {
        existingCartId(owner)?.let { return it }
        CartsTable.insertIgnore {
            it[id] = "cart-${UUID.randomUUID()}"
            it[guestId] = (owner as? CartOwner.Guest)?.id
            it[customerId] = (owner as? CartOwner.Customer)?.id
        }
        return checkNotNull(existingCartId(owner)) { "the cart of $owner was neither found nor created" }
    }

    private fun forgetAttempt(cartId: String) {
        CartsTable.update({ CartsTable.id eq cartId }) {
            it[promoAttempt] = null
            it[promoError] = null
        }
    }

    private fun line(row: ResultRow): StoredLine =
        StoredLine(
            skuId = row[CartLinesTable.skuId],
            quantity = row[CartLinesTable.quantity],
            selected = row[CartLinesTable.selected],
            seenPriceCents = row[CartLinesTable.seenPriceCents],
            seenInStock = row[CartLinesTable.seenInStock],
            addedAt = row[CartLinesTable.addedAt],
        )
}
