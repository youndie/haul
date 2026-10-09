package io.github.youndie.haul.shell

import io.github.youndie.haul.haulWireJson
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.commands.LoadAction
import io.github.youndie.kompot.commands.UpdateHistory
import io.github.youndie.kompot.ktor.respondKompotAction
import io.github.youndie.kompot.ktor.respondKompotUpdate
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.uri

/**
 * A press that only filters, sorts or pages what a screen already shows (B-63) is kompot's `load` of the
 * screen's address under [PREFIX] — `/ui/parts/c/mugs?brand=Ostra` for `/c/mugs?brand=Ostra` — an endpoint
 * of kind `load` (kompot SPEC §16.1): a `GET` answered with an action, not a tree. The answer is an
 * `update` of the nodes of that screen a filter changes, with the address as its `deeplink` and `push`,
 * so back, reload and a shared link open the whole page the shopper is looking at.
 *
 * The parts are cut from the page the address opens, built the way `GET /ui<address>` builds it, so they
 * cannot drift from it: what is spared is the bytes and the redraw of everything else — the header, the
 * breadcrumbs, the footer — not the building.
 */
internal object Parts {
    /** Where the parts of a storefront address are: the address under it. */
    const val PREFIX = "/ui/parts"

    /** The press that loads the parts of [address] in place of opening it. */
    fun load(address: String): LoadAction = LoadAction(PREFIX + address)

    /** The storefront address this request asks the parts of: its path and query as the tree wrote them. */
    fun address(call: ApplicationCall): String = call.request.uri.removePrefix(PREFIX)
}

/**
 * Answers a `load` of the screen this request names with the nodes of [page] named [changing], in that
 * order, as an `update` whose `deeplink` is the address — or, when the parts cannot say what the address
 * shows, with `navigate` to it (the whole page): [page] is `null` (the address no longer opens this screen),
 * or lacks one of [changing] — `optional` excepted — so the page drawn would keep a node the address does
 * not have.
 */
internal suspend fun ApplicationCall.respondParts(
    page: KompotComponent?,
    changing: List<String>,
    optional: Set<String> = emptySet(),
) {
    val address = Parts.address(this)
    val nodes = page?.let { tree -> changing.map { id -> id to tree.node(id) } }
    if (nodes == null || nodes.any { (id, node) -> node == null && id !in optional }) {
        respondKompotAction(haulWireJson, NavigateAction(address))
        return
    }
    respondKompotUpdate(haulWireJson, deeplink = address, history = UpdateHistory.PUSH) {
        nodes.forEach { (_, node) -> node?.let { addComponent(it) } }
    }
}

/** The node under [id] in this tree — itself, or a node of its columns at any depth — or `null`. */
internal fun KompotComponent.node(id: String): KompotComponent? =
    if (this.id == id) this else (this as? ColumnComponent)?.children?.firstNotNullOfOrNull { it.node(id) }
