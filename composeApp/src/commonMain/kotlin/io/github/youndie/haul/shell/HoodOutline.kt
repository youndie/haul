package io.github.youndie.haul.shell

import io.github.youndie.haul.registry.haulJson
import io.github.youndie.kompot.KompotComponent
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

// The outline «Under the hood» draws (B-163): a tree as the wire has it, read from its JSON rather than from its
// classes, so every component — kompot's own, Haul's, one added tomorrow — is outlined without a line about it here.
// A node is an object with a `type` and an `id`; everything else on it is a field, its value said short.

/**
 * A node of the outline: its wire [type] — or, for a component the wire sends without one (a card in a grid's list),
 * the field it is in, «cards[]» — and [id], the field it hangs from in its parent — [via], `null` for a child of
 * `children`, the root and a node named by its field — its [fields], said short, and its [children]. [key] is its place in the
 * outline («0/2/1»), which keeps what is open while the page's updates replace nodes around it.
 */
internal class HoodNode(
    val type: String,
    val id: String,
    val via: String?,
    val fields: List<HoodField>,
    val children: List<HoodNode>,
    val key: String,
) {
    /** How many nodes this is, itself included. */
    val count: Int = 1 + children.sumOf { it.count }
}

/** A field of a node: its [name] and its [value], cut to [VALUE_CHARS]. */
internal class HoodField(
    val name: String,
    val value: String,
)

internal object HoodOutline {
    /** [tree] as it is drawn: each node [replaced] by an update written after whatever drew the node around it. */
    fun of(
        tree: KompotComponent,
        replaced: Map<String, Replaced> = emptyMap(),
    ): HoodNode = node(encode(tree), via = null, key = "0", floor = 0L, replaced, unnamed = "")

    private fun encode(component: KompotComponent): JsonObject =
        haulJson.encodeToJsonElement(PolymorphicSerializer(KompotComponent::class), component).jsonObject

    private fun node(
        sent: JsonObject,
        via: String?,
        key: String,
        floor: Long,
        replaced: Map<String, Replaced>,
        unnamed: String,
    ): HoodNode {
        val id = sent.text(ID).orEmpty()
        // As kompot's store does: an update applies under a node only when it is newer than what drew that node.
        val update = replaced[id]?.takeIf { it.order > floor }
        val drawn = update?.let { encode(it.component) } ?: sent
        val below = update?.order ?: floor
        val fields = mutableListOf<HoodField>()
        val children = mutableListOf<HoodNode>()
        drawn.forEach { (name, value) ->
            if (name == TYPE || name == ID) return@forEach
            val nodes = nodesIn(value, inList = false)
            nodes.forEach { (found, inList) ->
                // A component in a list of one type — a grid's cards, a grid's tiles — goes without its `type` on
                // the wire; it is named by the field it is in.
                val named = if (inList) "$name[]" else name
                children +=
                    node(found, via = name.takeIf { it != CHILDREN }, "$key/${children.size}", below, replaced, named)
            }
            val onlyNodes = nodes.isNotEmpty() && (value.isNode() || (value is JsonArray && value.all { it.isNode() }))
            if (!onlyNodes) fields += HoodField(name, said(value))
        }
        val type = drawn.text(TYPE)
        return HoodNode(type ?: unnamed, id, via.takeIf { type != null }, fields, children, key)
    }

    /** The nodes inside [value], the outermost ones — a node's own nodes are its children — and whether in a list. */
    private fun nodesIn(
        value: JsonElement,
        inList: Boolean,
    ): List<Pair<JsonObject, Boolean>> =
        when {
            value.isNode() -> listOf(value as JsonObject to inList)
            value is JsonObject -> value.values.flatMap { nodesIn(it, inList = false) }
            value is JsonArray -> value.flatMap { nodesIn(it, inList = true) }
            else -> emptyList()
        }

    /**
     * Whether [this] is a node: an object with an `id`, every component's. A component's `type` is on it wherever the
     * field holds any component; in a list of one type it is left out, and the object is still a node.
     */
    private fun JsonElement.isNode(): Boolean = this is JsonObject && text(ID) != null

    private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** A field's value in a few words: a string quoted, an action by its kind and where it goes, a list by its size. */
    fun said(value: JsonElement): String =
        cut(
            when (value) {
                is JsonNull -> {
                    "null"
                }

                is JsonPrimitive -> {
                    if (value.isString) "\"${value.content}\"" else value.content
                }

                is JsonObject -> {
                    val kind = value.text(TYPE)
                    val first =
                        value.entries
                            .firstOrNull { (name, it) -> name != TYPE && it is JsonPrimitive }
                            ?.value
                            ?.let { (it as JsonPrimitive).content }
                    when {
                        kind != null -> listOfNotNull(kind, first).joinToString(" ")
                        value.isEmpty() -> "{}"
                        else -> "{" + value.keys.take(KEYS).joinToString(", ") + if (value.size > KEYS) ", …}" else "}"
                    }
                }

                is JsonArray -> {
                    when {
                        value.isEmpty() -> {
                            "[]"
                        }

                        value.all { it is JsonPrimitive } -> {
                            "[" + value.joinToString(", ") { said(it) } + "]"
                        }

                        else -> {
                            "[${value.size} ${if (value.size == 1) "item" else "items"}]"
                        }
                    }
                }
            },
        )

    private fun cut(text: String): String = if (text.length <= VALUE_CHARS) text else text.take(VALUE_CHARS - 1) + "…"

    private const val TYPE = "type"
    private const val ID = "id"
    private const val CHILDREN = "children"
    private const val KEYS = 3
}

/** How long a field's value is drawn at most: the outline is a map of the tree, not a dump of it. */
internal const val VALUE_CHARS: Int = 48
