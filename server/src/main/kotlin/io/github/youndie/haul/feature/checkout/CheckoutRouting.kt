package io.github.youndie.haul.feature.checkout

import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.checkout.domain.CheckoutCommands
import io.github.youndie.haul.feature.checkout.domain.CheckoutError
import io.github.youndie.haul.feature.checkout.screen.CheckoutScreen
import io.github.youndie.haul.feature.identity.Caller
import io.github.youndie.haul.feature.identity.Callers
import io.github.youndie.haul.feature.identity.domain.IdentityError
import io.github.youndie.haul.feature.order.domain.OrderError
import io.github.youndie.haul.feature.order.domain.Placement
import io.github.youndie.haul.haulWireJson
import io.github.youndie.kompot.encodeKompotAction
import io.github.youndie.kompot.ktor.respondKompotAction
import io.github.youndie.kompot.ktor.respondKompotComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.RefreshAction
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.serialization.DeserializationStrategy
import org.koin.ktor.ext.inject

/**
 * Checkout (endpoint-checkout) and the address form (endpoint-identity), in the customer tier: the
 * caller is always a customer by a verified token, and a request without one never reaches here (`401
 * unauthenticated`). The tree is `GET /ui/checkout`; each choice and the address form answer `refresh`,
 * which redraws it with a new quote; every refusal is a [CheckoutError].
 *
 * Placement (B-16) is `POST /api/v1/orders` with the quote's fingerprint and an `Idempotency-Key`: the
 * order saga runs ([Placement]) and the answer is `202` with kompot's `navigate` to the order's page —
 * placed, or cancelled because the card was declined. A window that filled or stock that ran out is a
 * `409` the checkout is drawn again for; the rest of its refusals are [OrderError]s.
 */
internal fun Route.checkoutRouting() {
    val screen by inject<CheckoutScreen>()
    val commands by inject<CheckoutCommands>()
    val callers by inject<Callers>()
    val placement by inject<Placement>()

    suspend fun ApplicationCall.customer(): CartOwner.Customer {
        val customer = (callers.of(this) as? Caller.Customer)?.customer ?: throw IdentityError.Unauthenticated()
        return CartOwner.Customer(customer.id, customer.plus)
    }

    get(CheckoutPaths.SCREEN) {
        call.respondKompotComponent(haulWireJson, screen.build(call.customer()))
    }

    put(CheckoutPaths.CHOICE) {
        val customer = call.customer()
        commands.choose(customer, call.body(CheckoutChoice.serializer()))
        call.respondKompotAction(haulWireJson, RefreshAction)
    }

    post(CheckoutPaths.PLACE) {
        val customer = call.customer()
        val request = call.body(PlaceOrderRequest.serializer())
        val orderId = placement.place(customer, call.request.headers[IDEMPOTENCY_KEY_HEADER], request)
        call.respondText(
            haulWireJson.encodeKompotAction(NavigateAction(CheckoutPaths.order(orderId))),
            ContentType.Application.Json,
            HttpStatusCode.Accepted,
        )
    }

    post(CheckoutPaths.ADDRESSES) {
        val customer = call.customer()
        commands.saveAddress(customer, call.body(AddressEntry.serializer()))
        call.respondKompotAction(haulWireJson, RefreshAction)
    }
}

/**
 * Checkout's paths: the server's strings (CLAUDE.md), handed to the client inside the tree
 * (`DeliveryMethods.url`, `CheckoutAddress.url`, …) rather than built by it.
 */
internal object CheckoutPaths {
    const val SCREEN = "/ui/checkout"
    const val CHOICE = "/api/v1/me/checkout"
    const val ADDRESSES = "/api/v1/me/addresses"

    /** Placement (endpoint-checkout): `CheckoutSummary.placeUrl`. */
    const val PLACE = "/api/v1/orders"

    /**
     * Where placement sends the shopper: the order's page, whose tree is the same address under `/ui`
     * (`GET /ui/orders/{id}`, endpoint-orders; built by B-18).
     */
    fun order(orderId: String): String = "/orders/$orderId"
}

/** A command's JSON body; one that does not parse is `400 validation_failed`, with no detail of why. */
private suspend fun <T> ApplicationCall.body(strategy: DeserializationStrategy<T>): T =
    try {
        haulWireJson.decodeFromString(strategy, receiveText())
    } catch (_: IllegalArgumentException) {
        // `SerializationException` is one; so is a JSON value of the wrong type or an unknown method.
        throw CheckoutError.Invalid("body", "The request body is not the JSON this command takes")
    }
