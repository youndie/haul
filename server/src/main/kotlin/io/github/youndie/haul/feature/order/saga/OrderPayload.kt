package io.github.youndie.haul.feature.order.saga

import io.github.youndie.haul.feature.checkout.AddressEntry
import io.github.youndie.haul.feature.checkout.DeliveryMethod
import io.github.youndie.haul.feature.order.domain.NewOrder
import io.github.youndie.haul.feature.order.domain.OrderLine
import io.github.youndie.petich.PetichPayload
import io.github.youndie.petich.PetichStepRecord
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

/**
 * What the order saga carries in its row: the order as the quote placed it. Written once, by the
 * insert, and read by every member on every pass — the first one and the one a restart carries on — so
 * it holds everything a member needs and nothing a member could find changed (research D4).
 *
 * `@SerialName` pins the storage format: without it the discriminator is the class's full name, and
 * moving the class would make every saga in flight unreadable.
 */
@Serializable
@SerialName("haul_order")
internal data class OrderPayload(
    val orderId: String,
    val customerId: String,
    val plus: Boolean,
    val method: DeliveryMethod,
    val addressId: String? = null,
    /** The address as placed (B-40); absent in a saga stored before V14, whose order keeps only the id. */
    val address: AddressEntry? = null,
    val pointId: String? = null,
    val slotId: String? = null,
    val payment: String,
    val promoCode: String? = null,
    val itemsCents: Int,
    val discountCents: Int,
    val deliveryCents: Int,
    val totalCents: Int,
    val points: Int,
    val placedAt: String,
    val lines: List<Line>,
    /** The points it is paid with, taken by the saga (B-23); none in a saga stored before V19. */
    val pointsRedeemed: Int = 0,
    /** The delivery fee Plus took off (B-23). */
    val deliveryWaivedCents: Int = 0,
) : PetichPayload() {
    @Serializable
    data class Line(
        val skuId: String,
        val sellerId: String,
        val title: String,
        val quantity: Int,
        val priceCents: Int,
        val listCents: Int,
    )

    val placedAtTime: OffsetDateTime get() = OffsetDateTime.parse(placedAt)

    fun order(sagaId: String): NewOrder =
        NewOrder(
            id = orderId,
            sagaId = sagaId,
            customerId = customerId,
            method = method,
            addressId = addressId,
            address = address,
            pointId = pointId,
            slotId = slotId,
            payment = payment,
            promoCode = promoCode,
            itemsCents = itemsCents,
            discountCents = discountCents,
            deliveryCents = deliveryCents,
            totalCents = totalCents,
            points = points,
            placedAt = placedAtTime,
            lines = lines.map { OrderLine(it.skuId, it.sellerId, it.title, it.quantity, it.priceCents, it.listCents) },
            pointsRedeemed = pointsRedeemed,
            deliveryWaivedCents = deliveryWaivedCents,
        )
}

/**
 * Why a member refused the saga, kept in the saga's row under that member's key: what placement answers
 * the shopper with — on the pass that ran it and on every replay of the same key, after the request that
 * ran it is long gone.
 */
@Serializable
@SerialName("haul_refused")
internal data class Refused(
    val reason: String,
) : PetichStepRecord() {
    companion object {
        const val OUT_OF_STOCK = "out_of_stock"
        const val SLOT_UNAVAILABLE = "slot_unavailable"
        const val PAYMENT_DECLINED = "payment_declined"

        /** The points the order was to be paid with are no longer there: spent by another order meanwhile. */
        const val POINTS_SHORT = "points_short"
    }
}
