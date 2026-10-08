package io.github.youndie.haul.feature.cart

import io.github.youndie.haul.feature.cart.domain.CartCommands
import io.github.youndie.haul.feature.cart.domain.CartError
import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.cart.screen.CartScreen
import io.github.youndie.haul.feature.identity.GUEST_HEADER
import io.github.youndie.haul.feature.identity.domain.Guests
import io.github.youndie.haul.feature.identity.domain.IdentityError
import io.github.youndie.haul.haulWireJson
import io.github.youndie.kompot.ktor.respondKompotAction
import io.github.youndie.kompot.ktor.respondKompotComponent
import io.github.youndie.kompot.standard.RefreshAction
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.serialization.DeserializationStrategy
import org.koin.ktor.ext.inject

/**
 * The cart (endpoint-cart), in the public tier: the caller is a guest named by `X-Haul-Guest`, and a
 * request naming no guest the server issued is `401 unauthenticated`. A customer's bearer token joins
 * the guest id here with sign-in (B-12). Every command answers `refresh`, which redraws the cart and
 * the header's count; every refusal is a [CartError] or an [IdentityError].
 */
internal fun Route.cartRouting() {
    val screen by inject<CartScreen>()
    val commands by inject<CartCommands>()
    val guests by inject<Guests>()

    suspend fun ApplicationCall.owner(): CartOwner {
        val id = request.headers[GUEST_HEADER]?.trim()?.takeIf { it.isNotEmpty() }
        if (id == null || !guests.exists(id)) throw IdentityError.Unauthenticated()
        return CartOwner.Guest(id)
    }

    suspend fun ApplicationCall.refresh() = respondKompotAction(haulWireJson, RefreshAction)

    get(CartPaths.SCREEN) {
        call.respondKompotComponent(haulWireJson, screen.build(call.owner()))
    }

    put(CartPaths.LINE) {
        val owner = call.owner()
        commands.changeLine(owner, call.parameters["skuId"]!!, call.body(LineChange.serializer()))
        call.refresh()
    }

    delete(CartPaths.LINES) {
        val owner = call.owner()
        commands.removeLines(owner, call.body(LinesRemoval.serializer()).skuIds)
        call.refresh()
    }

    post(CartPaths.ACKNOWLEDGE) {
        commands.acknowledge(call.owner(), call.parameters["skuId"]!!)
        call.refresh()
    }

    put(CartPaths.PROMO) {
        val owner = call.owner()
        commands.applyPromo(owner, call.body(PromoEntry.serializer()).code)
        call.refresh()
    }

    delete(CartPaths.PROMO) {
        commands.removePromo(call.owner())
        call.refresh()
    }
}

/**
 * The cart's paths: the server's strings (CLAUDE.md), handed to the client inside the tree
 * (`CartLine.url`, `CartSelection.linesUrl`, `PromoField.url`) rather than built by it.
 */
internal object CartPaths {
    const val SCREEN = "/ui/cart"
    const val LINES = "/api/v1/cart/lines"
    const val LINE = "$LINES/{skuId}"
    const val ACKNOWLEDGE = "$LINES/{skuId}/acknowledge"
    const val PROMO = "/api/v1/cart/promo"

    fun line(skuId: String): String = "$LINES/$skuId"

    fun acknowledge(skuId: String): String = "$LINES/$skuId/acknowledge"
}

/** A command's JSON body; one that does not parse is `400 validation_failed`, with no detail of why. */
private suspend fun <T> ApplicationCall.body(strategy: DeserializationStrategy<T>): T =
    try {
        haulWireJson.decodeFromString(strategy, receiveText())
    } catch (_: IllegalArgumentException) {
        // `SerializationException` is one; so is a JSON value of the wrong type.
        throw CartError.Invalid("body", "The request body is not the JSON this command takes")
    }
