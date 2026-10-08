package io.github.youndie.haul.testing

import io.github.youndie.haul.ErrorBody
import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.feature.cart.CartRoutes
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.cart.LinesRemoval
import io.github.youndie.haul.feature.cart.PromoEntry
import io.github.youndie.haul.feature.identity.GuestDto
import io.github.youndie.haul.feature.identity.GuestRoutes
import io.github.youndie.haul.haulWireJson
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotAction
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.standard.RefreshAction
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.assertEquals

// The cart's routes as a client calls them, by the contract's paths and bodies.

/** A new guest, through `POST /api/v1/guests`. */
internal suspend fun HttpClient.guest(): String {
    val response = post(GuestRoutes.GUESTS)
    assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
    return haulWireJson.decodeFromString(GuestDto.serializer(), response.bodyAsText()).id
}

internal suspend fun HttpClient.putLine(
    guest: String?,
    skuId: String,
    change: LineChange,
): HttpResponse =
    put(CartRoutes.line(skuId)) {
        guest?.let { header(GuestRoutes.HEADER, it) }
        contentType(ContentType.Application.Json)
        setBody(haulWireJson.encodeToString(LineChange.serializer(), change))
    }

internal suspend fun HttpClient.removeLines(
    guest: String,
    vararg skuIds: String,
): HttpResponse =
    delete(CartRoutes.LINES) {
        header(GuestRoutes.HEADER, guest)
        contentType(ContentType.Application.Json)
        setBody(haulWireJson.encodeToString(LinesRemoval.serializer(), LinesRemoval(skuIds.toList())))
    }

internal suspend fun HttpClient.acknowledge(
    guest: String,
    skuId: String,
): HttpResponse = post(CartRoutes.acknowledge(skuId)) { header(GuestRoutes.HEADER, guest) }

internal suspend fun HttpClient.applyPromo(
    guest: String,
    code: String,
): HttpResponse =
    put(CartRoutes.PROMO) {
        header(GuestRoutes.HEADER, guest)
        contentType(ContentType.Application.Json)
        setBody(haulWireJson.encodeToString(PromoEntry.serializer(), PromoEntry(code)))
    }

internal suspend fun HttpClient.removePromo(guest: String): HttpResponse =
    delete(CartRoutes.PROMO) { header(GuestRoutes.HEADER, guest) }

/** The Cart tree for [guest]. */
internal suspend fun HttpClient.cart(guest: String): KompotComponent {
    val response = get(CartRoutes.SCREEN) { header(GuestRoutes.HEADER, guest) }
    assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
    return haulWireJson.decodeKompotComponent(response.bodyAsText())
}

/** A command answered `refresh`. */
internal suspend fun HttpResponse.assertRefresh() {
    assertEquals(HttpStatusCode.OK, status, bodyAsText())
    assertEquals(RefreshAction, haulWireJson.decodeKompotAction(bodyAsText()))
}

/** A command or a screen refused with [status] and [code]. */
internal suspend fun HttpResponse.assertError(
    status: HttpStatusCode,
    code: ErrorCode,
): ErrorBody {
    val body = bodyAsText()
    assertEquals(status, this.status, body)
    return haulWireJson.decodeFromString(ErrorBody.serializer(), body).also { assertEquals(code, it.code, body) }
}
