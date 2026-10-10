package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.feature.cart.CartPaths
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.cart.LineCommand
import io.github.youndie.haul.feature.checkout.CheckoutPaths
import io.github.youndie.haul.feature.identity.GUEST_HEADER
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCatalog.SONY_HEADPHONES
import io.github.youndie.haul.seed.SampleCatalog.STONEWARE_MUG
import io.github.youndie.haul.testing.ShildikHarness
import io.github.youndie.haul.testing.action
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.assertRefresh
import io.github.youndie.haul.testing.cart
import io.github.youndie.haul.testing.guest
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.putLine
import io.github.youndie.haul.testing.update
import io.github.youndie.haul.ui.CartLine
import io.github.youndie.haul.ui.CheckoutSummary
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.ProductDetails
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B-48: the product page's «Add to cart» and «Buy now» carry their line changes for the SKU the page
 * shows, fixed in the tree as a card's «+» is (B-37), and following them — the command to the url it
 * names, then its `next` — puts that SKU in the cart and lands where the button promises. Each command
 * is sent as the client sends it, from the tree, never built by the test.
 */
class ProductButtonsTest {
    private val seed = CatalogSeed.generate(CatalogSeed.CANVAS_DAY)

    /**
     * Scenario «Variant changes the price», its last clause: «Add to cart» adds the SKU the page shows,
     * not the product's cheapest — and the page drawn again counts it in the header and offers one more.
     */
    @Test
    fun `add to cart puts one more of the SKU shown into the cart`() =
        haulTest {
            val guest = guest()
            val travel = "$SONY_HEADPHONES-1"
            val add =
                assertNotNull(details(guest, travel).add, "the travel-case bundle in stock offers no «Add to cart»")
            assertEquals(LineCommand(CartPaths.line(travel) + "?answer=details", LineChange(quantity = 1)), add)

            // Answered with the header and the buy box (B-63, `LineAnswersTest`), the page as it now is.
            assertNotNull(send(guest, add).action().update(), "«Add to cart» is not answered with an update")
            val after = page(guest, travel)
            assertEquals(1, after.only<HaulHeader>().cartCount, "«Add to cart» did not reach the header's count")
            assertEquals(listOf(travel to 1), lines(guest), "the cart holds another SKU than the page showed")
            val again = assertNotNull(after.only<ProductDetails>().add)
            assertEquals(LineChange(quantity = 2), again.change, "a second press would not add one more")
        }

    /**
     * A guest's «Buy now» adds as «Add to cart» does, selected, and goes to checkout through sign-in, by
     * the address the cart's «Sign in to check out» uses; a line the guest had unticked is ticked again,
     * or checkout would leave it behind.
     */
    @Test
    fun `a guest's buy now adds the SKU shown and goes to checkout through sign-in`() =
        haulTest {
            val guest = guest()
            val sku = "$SONY_HEADPHONES-0"
            val buy = assertNotNull(details(guest, sku).buy, "the headphones in stock offer no «Buy now»")
            assertEquals(CartPaths.line(sku), buy.url)
            assertEquals(LineChange(quantity = 1, selected = true), buy.change)
            assertEquals(NavigateAction("/sign-in?next=%2Fcheckout"), buy.next)

            send(guest, buy).assertRefresh()
            assertEquals(listOf(sku to 1), lines(guest))
            assertEquals(1, page(guest, sku).only<HaulHeader>().cartCount)

            putLine(guest, sku, LineChange(selected = false)).assertRefresh()
            val again = assertNotNull(details(guest, sku).buy)
            assertEquals(LineChange(quantity = 2, selected = true), again.change)
            send(guest, again).assertRefresh()
            val line = cart(guest).all().filterIsInstance<CartLine>().single()
            assertEquals(2, line.quantity)
            assertTrue(line.selected, "«Buy now» left the line unticked, so checkout would not take it")
        }

    /** A customer's «Buy now» goes straight to checkout, and checkout opens on the line it put in. */
    @Test
    fun `a customer's buy now adds the SKU shown and opens checkout`() {
        val token = ShildikHarness.accessToken(ShildikHarness.person("Buy Now"))
        haulTest(signIn = ShildikHarness.signIn) {
            val sku = "$STONEWARE_MUG-0"
            val buy =
                assertNotNull(tree("/ui/p/$STONEWARE_MUG?sku=$sku") { bearerAuth(token) }.only<ProductDetails>().buy)
            assertEquals(LineChange(quantity = 1, selected = true), buy.change)
            assertEquals(NavigateAction("/checkout"), buy.next)

            send(buy) { bearerAuth(token) }.assertRefresh()
            val next = assertNotNull(buy.next as? NavigateAction).deeplink
            assertEquals(CheckoutPaths.SCREEN, "/ui$next", "«Buy now» goes elsewhere than the checkout's tree")
            val items = tree("/ui$next") { bearerAuth(token) }.only<CheckoutSummary>().items
            assertEquals(listOf("Stoneware Mug, 12 oz" to 1), items.map { it.title to it.quantity })
        }
    }

    /**
     * A line holds ten, and never more than the stock: there «Add to cart» is gone, as a card's «+» is,
     * rather than a request the server refuses; «Buy now» still buys what is there, selecting the line
     * without changing its quantity.
     */
    @Test
    fun `at the line's limit add to cart is gone and buy now only selects the line`() =
        haulTest {
            val guest = guest()
            val mug = "$STONEWARE_MUG-0"
            putLine(guest, mug, LineChange(quantity = 10)).assertRefresh()
            val atTen = details(guest, mug)
            assertNull(atTen.add, "ten mugs in the cart and «Add to cart» still offered")
            val buy = assertNotNull(atTen.buy, "at the limit «Buy now» has nothing to buy")
            assertEquals(LineChange(selected = true), buy.change)
            send(guest, buy).assertRefresh()
            assertEquals(listOf(mug to 10), lines(guest), "«Buy now» at the limit changed the quantity")

            val scarce = seed.skus.first { it.stock in 1..9 }
            putLine(guest, scarce.id, LineChange(quantity = scarce.stock)).assertRefresh()
            val all = details(guest, scarce.id)
            assertNull(all.add, "${scarce.id}: the whole stock in the cart and «Add to cart» still offered")
            assertEquals(LineChange(selected = true), assertNotNull(all.buy).change)
        }

    /** The page's out-of-stock state (`Product_OutOfStock`): neither button is offered. */
    @Test
    fun `out of stock neither add to cart nor buy now is offered`() =
        haulTest {
            val silver = details(guest(), "$SONY_HEADPHONES-3")
            assertFalse(silver.inStock, "Silver is in stock in the seed now; the test needs another SKU")
            assertNull(silver.add, "Silver is out of stock and offers «Add to cart»")
            assertNull(silver.buy, "Silver is out of stock and offers «Buy now»")
        }

    private suspend fun HttpClient.page(
        guest: String,
        skuId: String,
    ): KompotComponent = tree("/ui/p/${skuId.substringBeforeLast('-')}?sku=$skuId") { header(GUEST_HEADER, guest) }

    private suspend fun HttpClient.details(
        guest: String,
        skuId: String,
    ): ProductDetails = page(guest, skuId).only()

    private suspend fun HttpClient.tree(
        path: String,
        request: HttpRequestBuilder.() -> Unit,
    ): KompotComponent {
        val response = get(path, request)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return haulWireJson.decodeKompotComponent(response.bodyAsText())
    }

    /** A command from the tree as the client sends it: `PUT` to its url with its change. */
    private suspend fun HttpClient.send(
        command: LineCommand,
        request: HttpRequestBuilder.() -> Unit,
    ): HttpResponse =
        put(command.url) {
            request()
            contentType(ContentType.Application.Json)
            setBody(haulWireJson.encodeToString(LineChange.serializer(), command.change))
        }

    private suspend fun HttpClient.send(
        guest: String,
        command: LineCommand,
    ): HttpResponse = send(command) { header(GUEST_HEADER, guest) }

    private suspend fun HttpClient.lines(guest: String): List<Pair<String, Int>> =
        cart(guest).all().filterIsInstance<CartLine>().map { it.skuId to it.quantity }
}
