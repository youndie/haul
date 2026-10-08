package io.github.youndie.haul.feature.cart

import io.github.youndie.haul.feature.cart.domain.CartCommands
import io.github.youndie.haul.feature.cart.domain.CartError
import io.github.youndie.haul.feature.cart.domain.CartOwner
import io.github.youndie.haul.feature.cart.screen.CartScreen
import io.github.youndie.haul.feature.identity.Caller
import io.github.youndie.haul.feature.identity.Callers
import io.github.youndie.haul.feature.identity.domain.IdentityError
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.shell.Viewers
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
 * The cart (endpoint-cart), in the public tier: the caller is a customer by a verified bearer token,
 * or else a guest named by `X-Haul-Guest`; a request with neither — or naming a guest the server never
 * issued — is `401 unauthenticated`. A token and a guest id together are the customer (feature-identity).
 * Every command answers `refresh`, which redraws the cart and the header's count; every refusal is a
 * [CartError] or an [IdentityError].
 */
internal fun Route.cartRouting() {
    val screen by inject<CartScreen>()
    val commands by inject<CartCommands>()
    val callers by inject<Callers>()
    val viewers by inject<Viewers>()

    suspend fun ApplicationCall.caller(): Caller = callers.of(this)

    suspend fun ApplicationCall.owner(): CartOwner = caller().owner() ?: throw IdentityError.Unauthenticated()

    suspend fun ApplicationCall.refresh() = respondKompotAction(haulWireJson, RefreshAction)

    get(CartPaths.SCREEN) {
        val caller = call.caller()
        val owner = caller.owner() ?: throw IdentityError.Unauthenticated()
        call.respondKompotComponent(haulWireJson, screen.build(owner, viewers.of(caller)))
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

    /** Sign-in's merge of a guest cart into the customer's (endpoint-identity), in the customer tier. */
    const val MERGE = "/api/v1/me/cart/merge"

    fun line(skuId: String): String = "$LINES/$skuId"

    fun acknowledge(skuId: String): String = "$LINES/$skuId/acknowledge"
}

/** The cart a caller owns: a customer's, a guest's, or none. */
internal fun Caller.owner(): CartOwner? =
    when (this) {
        Caller.Nobody -> null
        is Caller.Guest -> CartOwner.Guest(id)
        is Caller.Customer -> CartOwner.Customer(customer.id, customer.plus)
    }

/** A command's JSON body; one that does not parse is `400 validation_failed`, with no detail of why. */
private suspend fun <T> ApplicationCall.body(strategy: DeserializationStrategy<T>): T =
    try {
        haulWireJson.decodeFromString(strategy, receiveText())
    } catch (_: IllegalArgumentException) {
        // `SerializationException` is one; so is a JSON value of the wrong type.
        throw CartError.Invalid("body", "The request body is not the JSON this command takes")
    }
