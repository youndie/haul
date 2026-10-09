package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.cart.LineCommand
import io.github.youndie.haul.feature.identity.GUEST_HEADER
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.testing.action
import io.github.youndie.haul.testing.after
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.guest
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.json
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductDetails
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.commands.UpdateAction
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.standard.RefreshAction
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * B-63: a card's «+» and the product page's «Add to cart» change two things on the page they are pressed on —
 * the header's count and the control itself, whose next quantity is one more — and are answered with an
 * `update` of those two nodes rather than `refresh`. The page the client draws after it, the page before
 * with both replaced by id, has to be the page the address opens now; on the search page that includes the
 * query the header shows. The cart's own lines change the whole cart and still answer `refresh`.
 */
class LineAnswersTest {
    private suspend fun HttpClient.page(
        path: String,
        guest: String,
    ): KompotComponent {
        val response = get(path) { header(GUEST_HEADER, guest) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return haulWireJson.decodeKompotComponent(response.bodyAsText())
    }

    private suspend fun HttpClient.press(
        guest: String,
        command: LineCommand,
    ): HttpResponse =
        put(command.url) {
            header(GUEST_HEADER, guest)
            contentType(ContentType.Application.Json)
            setBody(haulWireJson.encodeToString(LineChange.serializer(), command.change))
        }

    /** Presses [command] on the page at [path] and holds the answer to the page there now; returns the update. */
    private suspend fun HttpClient.pressedOn(
        path: String,
        guest: String,
        command: LineCommand,
        node: String,
    ): UpdateAction {
        val before = page(path, guest)
        val update =
            assertNotNull(
                press(guest, command).action() as? UpdateAction,
                "${command.url} is not answered with an update",
            )
        assertEquals(listOf("header", node), update.updates.map { it.componentId }, command.url)
        assertNull(update.deeplink, "a line change moved the page to ${update.deeplink}")
        assertEquals(
            page(path, guest).json(),
            before.after(update),
            "$path after «${command.url}» is not the page there now",
        )
        return update
    }

    @Test
    fun `a card's plus answers the header and the card`() =
        haulTest {
            val guest = guest()
            val card = mug(page("/ui/c/mugs", guest))
            val update = pressedOn("/ui/c/mugs", guest, assertNotNull(card.add), card.id)
            assertEquals(1, (update.updates.first().component as HaulHeader).cartCount)
            val again = update.updates.last().component as ProductCard
            assertEquals(LineChange(quantity = 2), again.add?.change, "a second «+» would not add one more")
            // And again: the answer is the card as it is now, so the next press adds one more still.
            pressedOn("/ui/c/mugs", guest, assertNotNull(again.add), card.id)
        }

    @Test
    fun `a card's plus on the search page keeps the header's query`() =
        haulTest {
            val guest = guest()
            val path = "/ui/search?q=everyday"
            val card = page(path, guest).only<ProductGrid>().cards.first { it.add != null }
            val update = pressedOn(path, guest, assertNotNull(card.add), card.id)
            assertEquals("everyday", (update.updates.first().component as HaulHeader).query)
        }

    @Test
    fun `a deal's plus on the home page answers the header and the deal`() =
        haulTest {
            val guest = guest()
            val deals = page("/ui/home", guest).all().filterIsInstance<ProductGrid>().single { it.id == "deals" }
            val card = deals.cards.first { it.add != null }
            pressedOn("/ui/home", guest, assertNotNull(card.add), card.id)
        }

    @Test
    fun `add to cart answers the header and the buy box`() =
        haulTest {
            val guest = guest()
            val path = "/ui/p/${SampleCatalog.SONY_HEADPHONES}?sku=${SampleCatalog.SONY_HEADPHONES}-1&tab=reviews"
            val details = page(path, guest).only<ProductDetails>()
            val update = pressedOn(path, guest, assertNotNull(details.add), "details")
            assertEquals(LineChange(quantity = 2), (update.updates.last().component as ProductDetails).add?.change)
        }

    @Test
    fun `a line of the cart itself is answered with refresh`() =
        haulTest {
            val guest = guest()
            val card = mug(page("/ui/c/mugs", guest))
            pressedOn("/ui/c/mugs", guest, assertNotNull(card.add), card.id)
            val line =
                assertNotNull(
                    card.add,
                ).let { it.copy(url = it.url.substringBefore('?'), change = LineChange(quantity = 3)) }
            assertEquals(RefreshAction, press(guest, line).action())
        }

    private fun mug(page: KompotComponent): ProductCard =
        page.only<ProductGrid>().cards.single { it.productId == SampleCatalog.STONEWARE_MUG }
}
