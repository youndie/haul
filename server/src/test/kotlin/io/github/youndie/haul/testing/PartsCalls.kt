package io.github.youndie.haul.testing

import io.github.youndie.haul.haulWireJson
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.commands.UpdateAction
import io.github.youndie.kompot.decodeKompotAction
import io.github.youndie.kompot.encodeKompotComponent
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.test.assertEquals

// B-63's checks of an `update`: what the client draws after one is the tree it had with each node the
// update names replaced by id (kompot SPEC §16.4), and that has to be the page the update's address opens.

/** The tree as it travels: JSON, where a node is found by its `id` at any depth. */
internal fun KompotComponent.json(): JsonElement = Json.parseToJsonElement(haulWireJson.encodeKompotComponent(this))

/**
 * The tree the client draws after [update]: every node whose `id` the update names replaced by the update's
 * node, wherever it is drawn — a card inside a grid inside the results as much as a node of the page's column.
 * A node inside a list of one concrete type carries no `type` on the wire, and the replacement drops it too.
 */
internal fun KompotComponent.after(update: UpdateAction): JsonElement {
    val nodes =
        update.updates.associate { frame ->
            frame.componentId to
                haulWireJson.encodeToJsonElement(PolymorphicSerializer(KompotComponent::class), frame.component)
        }
    return json().replacing(nodes)
}

private fun JsonElement.replacing(nodes: Map<String, JsonElement>): JsonElement =
    when (this) {
        is JsonObject -> {
            val replacement = (this["id"] as? JsonPrimitive)?.contentOrNull?.let(nodes::get) as? JsonObject
            when {
                replacement == null -> JsonObject(mapValues { (_, value) -> value.replacing(nodes) })
                TYPE in this -> replacement
                else -> JsonObject(replacement - TYPE)
            }
        }

        is JsonArray -> {
            JsonArray(map { it.replacing(nodes) })
        }

        else -> {
            this
        }
    }

/** The url of every `load` the tree carries, in the order the tree has them, once each. */
internal fun KompotComponent.loads(): List<String> {
    val found = linkedSetOf<String>()

    fun walk(element: JsonElement) {
        when (element) {
            is JsonObject -> {
                if ((element[TYPE] as? JsonPrimitive)?.contentOrNull == "load") {
                    (element["url"] as? JsonPrimitive)?.contentOrNull?.let(found::add)
                }
                element.values.forEach(::walk)
            }

            is JsonArray -> {
                element.forEach(::walk)
            }

            else -> {}
        }
    }
    walk(json())
    return found.toList()
}

/** The action a `GET` of [url] answers — a `load` endpoint's — with `200`. */
internal suspend fun HttpClient.answer(
    url: String,
    request: HttpRequestBuilder.() -> Unit = {},
): KompotAction {
    val response = get(url, request)
    assertEquals(HttpStatusCode.OK, response.status, "$url: ${response.bodyAsText()}")
    return haulWireJson.decodeKompotAction(response.bodyAsText())
}

private const val TYPE = "type"
