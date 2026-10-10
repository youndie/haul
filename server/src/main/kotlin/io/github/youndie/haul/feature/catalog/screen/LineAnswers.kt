package io.github.youndie.haul.feature.catalog.screen

import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.commands.kompotUpdate
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.RefreshAction
import io.github.youndie.kompot.standard.SequenceAction
import io.github.youndie.kompot.standard.ShowMessageAction
import io.ktor.http.Parameters
import io.ktor.http.encodeURLParameter

/**
 * What a card's «+» and a product page's «Add to cart» are answered with (B-63). A line put into the cart
 * changes two things on the page it was pressed on: the header's count, and the control itself, whose next
 * quantity is one more. So the answer is an `update` of the header and of that node rather than `refresh`,
 * which sent the whole page again for them.
 *
 * Which node it is, the tree that drew the control wrote into the command's own address ([ANSWER]): a card
 * — built the same from the product and the SKU on every page that draws it, [card] — or the product page's
 * buy box ([DETAILS]); on the search page, also the search the header shows ([QUERY]) and the category it is
 * in ([SCOPE], B-72), which the header would otherwise lose. A command without it — the cart page's own lines — is answered `refresh`, as is one
 * whose SKU is no longer listed: there is no node to send.
 *
 * «Add to cart» also says so (B-75): its answer is `sequence[update, show_message]`, the message [ADDED] with
 * the way to the cart ([VIEW_CART]), which the client draws as a notice over the page. A card's «+» has no
 * message: the card stays where the shopper pressed it, and the header's count is the answer.
 */
internal class LineAnswers(
    private val catalog: CatalogRepository,
    private val calendar: DeliveryCalendar,
    private val photos: ProductPhotos,
    private val product: ProductScreen,
) {
    /** The answer to a change of [skuId]'s line asked with [query], for [viewer] as the change left them. */
    suspend fun after(
        query: Parameters,
        skuId: String,
        viewer: Viewer,
    ): KompotAction {
        val answer = query[ANSWER] ?: return RefreshAction
        val item = catalog.listedBySkus(setOf(skuId), viewer.prices).singleOrNull() ?: return RefreshAction
        val sku = item.skus.first { it.id == skuId }
        val node: KompotComponent =
            when (answer) {
                CARD -> card(item, calendar, photos, viewer, sku, query[QUERY], scope = query[SCOPE])
                DETAILS -> product.details(item, sku, viewer)
                else -> return RefreshAction
            }
        val header = Frame.header(viewer, navigation(catalog.categories()), query[QUERY], query[SCOPE])
        val update =
            kompotUpdate {
                addComponent(header)
                addComponent(node)
            }
        return if (answer == DETAILS) SequenceAction(listOf(update, ADDED_MESSAGE)) else update
    }

    companion object {
        /** What «Add to cart» says once the line is in (B-75). */
        const val ADDED = "Added to your cart"

        /** The words on the message's own button, which opens the cart. */
        const val VIEW_CART = "View cart"

        private val ADDED_MESSAGE =
            ShowMessageAction(ADDED, actionLabel = VIEW_CART, action = NavigateAction(Frame.CART))

        /** The query parameter that names the node a line change redraws. */
        const val ANSWER = "answer"
        const val CARD = "card"
        const val DETAILS = "details"

        /** The search the page's header shows, which the header redrawn keeps. */
        const val QUERY = "q"

        /** What the buy box's «Add to cart» adds to its line's address. */
        const val DETAILS_ANSWER = "?$ANSWER=$DETAILS"

        /** The category the page's search is in, which the header's picker redrawn keeps (B-72). */
        const val SCOPE = "category"

        /** What a card's «+» adds to its line's address, on a page whose header shows [query] in [scope]. */
        fun cardAnswer(
            query: String?,
            scope: String? = null,
        ): String =
            "?$ANSWER=$CARD" + query?.let { "&$QUERY=" + it.encodeURLParameter() }.orEmpty() +
                scope?.let { "&$SCOPE=" + it.encodeURLParameter() }.orEmpty()
    }
}
