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
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.testing.untitled
import io.github.youndie.haul.testing.update
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.Link
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductDetails
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.SearchScope
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.commands.UpdateAction
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.RefreshAction
import io.github.youndie.kompot.standard.SequenceAction
import io.github.youndie.kompot.standard.ShowMessageAction
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
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * B-63: a card's «+» and the product page's «Add to cart» change two things on the page they are pressed on —
 * the header's count and the control itself, whose next quantity is one more — and are answered with an
 * `update` of those two nodes rather than `refresh`. The page the client draws after it, the page before
 * with both replaced by id, has to be the page the address opens now; on the search page that includes the
 * query the header shows. The cart's own lines change the whole cart and still answer `refresh`.
 *
 * Both presses then say so (B-75, B-81): the answer is `sequence[update, show_message]`, «Added to your cart»
 * with «View cart».
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

    /**
     * Presses [command] on the page at [path] and holds the answer's update to the page there now; returns the
     * answer, an `update` or a `sequence` around one.
     */
    private suspend fun HttpClient.answerOn(
        path: String,
        guest: String,
        command: LineCommand,
        node: String,
    ): KompotAction {
        val before = page(path, guest)
        val answer = press(guest, command).action()
        val update = assertNotNull(answer.update(), "${command.url} is not answered with an update")
        assertEquals(listOf("header", node), update.updates.map { it.componentId }, command.url)
        assertNull(update.deeplink, "a line change moved the page to ${update.deeplink}")
        assertEquals(
            page(path, guest).untitled().json(),
            before.after(update),
            "$path after «${command.url}» is not the page there now",
        )
        return answer
    }

    /** [answerOn] for a press that also says the line is in (B-81): the update, then the message. */
    private suspend fun HttpClient.pressedOn(
        path: String,
        guest: String,
        command: LineCommand,
        node: String,
    ): UpdateAction {
        val answer = assertIs<SequenceAction>(answerOn(path, guest, command, node), "${command.url} says nothing")
        assertEquals(listOf(answer.update(), ADDED), answer.actions, command.url)
        return assertNotNull(answer.update())
    }

    @Test
    fun `a card's plus answers the header and the card`() =
        haulTest {
            val guest = guest()
            val card = mug(page("/ui/c/home-kitchen/kitchen/mugs", guest))
            val update = pressedOn("/ui/c/home-kitchen/kitchen/mugs", guest, assertNotNull(card.add), card.id)
            assertEquals(1, (update.updates.first().component as HaulHeader).cartCount)
            val again = update.updates.last().component as ProductCard
            assertEquals(LineChange(quantity = 2), again.add?.change, "a second «+» would not add one more")
            // And again: the answer is the card as it is now, so the next press adds one more still.
            pressedOn("/ui/c/home-kitchen/kitchen/mugs", guest, assertNotNull(again.add), card.id)
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

    /** B-72: on a search in a top-level category the header answered keeps the picker's scope too. */
    @Test
    fun `a card's plus on a scoped search keeps the header's scope`() =
        haulTest {
            val guest = guest()
            val path = "/ui/search?q=running%20shoes&category=sports"
            val card = page(path, guest).only<ProductGrid>().cards.first { it.add != null }
            val update = pressedOn(path, guest, assertNotNull(card.add), card.id)
            val header = update.updates.first().component as HaulHeader
            assertEquals("running shoes", header.query)
            assertEquals("sports", header.scope)

            // B-119: a leaf's scope is a choice of the picker, named with its top level, and the answer keeps it.
            val leaf = "/ui/search?q=running%20shoes&category=running-shoes"
            val inLeaf = page(leaf, guest).only<ProductGrid>().cards.first { it.add != null }
            val after =
                pressedOn(
                    leaf,
                    guest,
                    assertNotNull(inLeaf.add),
                    inLeaf.id,
                ).updates.first().component as HaulHeader
            assertEquals("running-shoes", after.scope)
            assertEquals(
                SearchScope("Sports · Running shoes", "running-shoes"),
                after.scopes.single {
                    it.category ==
                        after.scope
                },
            )
        }

    @Test
    fun `a deal's plus on the home page answers the header and the deal`() =
        haulTest {
            val guest = guest()
            val deals = page("/ui/home", guest).all().filterIsInstance<ProductGrid>().single { it.id == "deals" }
            val card = deals.cards.first { it.add != null }
            pressedOn("/ui/home", guest, assertNotNull(card.add), card.id)
        }

    /**
     * B-75: «Add to cart» answers the header and the buy box, which now says the line is in the cart, then says
     * so in a message — `sequence[update, show_message]` — whose button opens the cart. Pressed on the reviews
     * tab, the buy box it answers with is the reviews tab's, its variants staying there (B-104).
     */
    @Test
    fun `add to cart answers the header and the buy box and says so`() =
        haulTest {
            val guest = guest()
            val path = "/ui/p/${SampleCatalog.SONORA_HEADPHONES}?sku=${SampleCatalog.SONORA_HEADPHONES}-1&tab=reviews"
            val details = page(path, guest).only<ProductDetails>()
            assertNull(details.inCart, "the buy box says the line is in a cart that holds none of it")
            val answer = assertIs<SequenceAction>(answerOn(path, guest, assertNotNull(details.add), "details"))
            assertEquals(ADDED, answer.actions.last())
            val after = assertNotNull(answer.update()).updates.last().component as ProductDetails
            assertEquals(LineChange(quantity = 2), after.add?.change)
            assertEquals(Link("1 in your cart", NavigateAction("/cart")), after.inCart)
            // B-104: the buy box redrawn on the reviews keeps its variants on the reviews.
            for (option in after.variants.flatMap { it.options }) {
                assertEquals(
                    NavigateAction("/p/${SampleCatalog.SONORA_HEADPHONES}?sku=${option.skuId}&tab=reviews"),
                    option.action,
                )
            }
        }

    /**
     * B-132: the buy box «Add to cart» answers with on `?tab=reviews` — an address naming no SKU — writes its rating
     * and «All specifications» without one, as the page did; at an address naming the SKU, with it.
     */
    @Test
    fun `the buy box redrawn names the sku as the page's address does`() =
        haulTest {
            val sonora = SampleCatalog.SONORA_HEADPHONES
            // Each page's address, and the SKU its links name: none, or the one named.
            val pages =
                listOf(
                    "/ui/p/$sonora?tab=reviews" to "",
                    "/ui/p/$sonora?sku=$sonora-1&tab=reviews" to "sku=$sonora-1&",
                )
            for ((path, sku) in pages) {
                val guest = guest()
                val details = page(path, guest).only<ProductDetails>()
                val answer = assertIs<SequenceAction>(answerOn(path, guest, assertNotNull(details.add), "details"))
                val after = assertNotNull(answer.update()).updates.last().component as ProductDetails
                assertEquals(details.ratingAction, after.ratingAction, path)
                assertEquals(NavigateAction("/p/$sonora?${sku}tab=reviews"), after.ratingAction, path)
                assertEquals(NavigateAction("/p/$sonora?${sku}tab=specifications"), after.specificationsAction, path)
            }
        }

    /**
     * B-81: on every page that draws cards — home, the deals, a category, a search — the first card of each grid
     * that can be added says so when its «+» is pressed, each by a guest of its own, so every press is a cart's
     * first line. «Picked for you» is a customer's, pressed in `RecommendationsRoutesTest`.
     */
    @Test
    fun `a card's plus says the item was added on every page that draws cards`() =
        haulTest {
            val pages = listOf("/ui/home", "/ui/deals", "/ui/c/home-kitchen/kitchen/mugs", "/ui/search?q=everyday")
            val pressed =
                pages.flatMap { path ->
                    page(path, guest()).grids().mapNotNull { grid ->
                        val buyer = guest()
                        val cards = page(path, buyer).grids().single { it.id == grid.id }.cards
                        val card = cards.firstOrNull { it.add != null } ?: return@mapNotNull null
                        pressedOn(path, buyer, assertNotNull(card.add), card.id)
                        "$path ${grid.id}"
                    }
                }
            assertEquals(pages, pressed.map { it.substringBefore(' ') }.distinct(), "pressed only $pressed")
        }

    /**
     * B-81: the press that takes the last one in stock still says so, and the card it answers with has nothing
     * left to add — no command, so a press on it is answered with nothing and says nothing.
     */
    @Test
    fun `a card's plus that takes the last one says so and leaves nothing to add`() =
        seededFreshDatabase().use { database ->
            haulTest(database) {
                val guest = guest()
                val path = "/ui/c/home-kitchen/kitchen/mugs"
                val skuId = assertNotNull(mug(page(path, guest)).add).url.substringBefore('?').substringAfterLast('/')
                database.sql("UPDATE skus SET stock = 1 WHERE id = '$skuId'")
                val card = mug(page(path, guest))
                val update = pressedOn(path, guest, assertNotNull(card.add), card.id)
                assertNull((update.updates.last().component as ProductCard).add, "«+» offers more than the stock")
            }
        }

    @Test
    fun `a line of the cart itself is answered with refresh`() =
        haulTest {
            val guest = guest()
            val card = mug(page("/ui/c/home-kitchen/kitchen/mugs", guest))
            pressedOn("/ui/c/home-kitchen/kitchen/mugs", guest, assertNotNull(card.add), card.id)
            val line =
                assertNotNull(
                    card.add,
                ).let { it.copy(url = it.url.substringBefore('?'), change = LineChange(quantity = 3)) }
            assertEquals(RefreshAction, press(guest, line).action())
        }

    private fun DataSource.sql(statement: String) {
        connection.use { c ->
            c.createStatement().use { it.executeUpdate(statement) }
            c.commit()
        }
    }

    private fun KompotComponent.grids(): List<ProductGrid> = all().filterIsInstance<ProductGrid>()

    private fun mug(page: KompotComponent): ProductCard =
        page.only<ProductGrid>().cards.single { it.productId == SampleCatalog.STONEWARE_MUG }

    private companion object {
        val ADDED = ShowMessageAction("Added to your cart", actionLabel = "View cart", action = NavigateAction("/cart"))
    }
}
