package io.github.youndie.haul.feature.cart.data

import io.github.youndie.haul.feature.catalog.data.SkusTable
import io.github.youndie.haul.feature.identity.data.CustomersTable
import io.github.youndie.haul.feature.identity.data.GuestsTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.between
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

// The Exposed side of V4__cart.sql (and of V8's key from a cart to its customer). `SchemaTest` holds the two together, CHECK constraints included:
// they are declared here under the names PostgreSQL gave them there.

internal object PromoCodesTable : Table("promo_codes") {
    val code = text("code")
    val percentOff = integer("percent_off").check("promo_codes_percent_off_check") { it.between(1, 100) }
    val capCents = integer("cap_cents").nullable()
    val startsAt = timestampWithTimeZone("starts_at")
    val endsAt = timestampWithTimeZone("ends_at")
    override val primaryKey = PrimaryKey(code)
}

internal object CartsTable : Table("carts") {
    val id = text("id")
    val guestId =
        text(
            "guest_id",
        ).references(GuestsTable.id, onDelete = ReferenceOption.CASCADE).nullable().uniqueIndex()
    val customerId =
        text(
            "customer_id",
        ).references(CustomersTable.id, onDelete = ReferenceOption.CASCADE).nullable().uniqueIndex()
    val promoCode = text("promo_code").references(PromoCodesTable.code).nullable()
    val promoAttempt = text("promo_attempt").nullable()
    val promoError = text("promo_error").nullable()
    override val primaryKey = PrimaryKey(id)

    init {
        check("carts_one_owner") {
            (guestId.isNull() and customerId.isNotNull()) or (guestId.isNotNull() and customerId.isNull())
        }
    }
}

internal object CartLinesTable : Table("cart_lines") {
    val cartId = text("cart_id").references(CartsTable.id, onDelete = ReferenceOption.CASCADE)
    val skuId = text("sku_id").references(SkusTable.id)
    val quantity = integer("quantity").check("cart_lines_quantity_check") { it.between(1, 10) }
    val selected = bool("selected")
    val seenPriceCents = integer("seen_price_cents")
    val seenInStock = bool("seen_in_stock")
    val addedAt = timestampWithTimeZone("added_at")
    val position = integer("position")
    override val primaryKey = PrimaryKey(cartId, skuId)
}

/** Every table the cart and its owners — guests and customers — need, parents before children. */
internal val cartTables: List<Table> = listOf(GuestsTable, CustomersTable, PromoCodesTable, CartsTable, CartLinesTable)
