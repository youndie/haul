package io.github.youndie.haul.feature.cart

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.feature.identity.GUEST_HEADER
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.testing.ShildikHarness
import io.github.youndie.haul.testing.acknowledge
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.applyPromo
import io.github.youndie.haul.testing.assertError
import io.github.youndie.haul.testing.assertRefresh
import io.github.youndie.haul.testing.cart
import io.github.youndie.haul.testing.guest
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.putLine
import io.github.youndie.haul.testing.removeLines
import io.github.youndie.haul.testing.removePromo
import io.github.youndie.haul.ui.CartGroup
import io.github.youndie.haul.ui.CartLine
import io.github.youndie.haul.ui.CartSelection
import io.github.youndie.haul.ui.EmptyState
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.OrderSummary
import io.github.youndie.haul.ui.PageTitle
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.PromoField
import io.github.youndie.haul.ui.SummaryRow
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * feature-cart's scenarios and rules over HTTP, against the seeded catalog in PostgreSQL. Every test is
 * a new guest, so the shared database's carts never meet.
 */
class CartRoutesTest {
    private val headphones = "${SampleCatalog.SONY_HEADPHONES}-0"
    private val silverHeadphones = "${SampleCatalog.SONY_HEADPHONES}-3"
    private val duvet = "${SampleCatalog.DUVET_COVER}-0"
    private val mug = "${SampleCatalog.STONEWARE_MUG}-0"

    /** Maya's cart (research §6), filled the way the client fills it: one `PUT` per line. */
    private suspend fun HttpClient.mayasCart(): String {
        val guest = guest()
        listOf(headphones, duvet, mug).forEach { putLine(guest, it, LineChange(quantity = 1)).assertRefresh() }
        return guest
    }

    private fun KompotComponent.rows(): Map<String, String> =
        only<OrderSummary>().rows.associate { it.label to it.value }

    /** Scenario «Totals as drawn». */
    @Test
    fun `totals as drawn`() =
        haulTest {
            val tree = cart(mayasCart())
            val summary = tree.only<OrderSummary>()
            assertEquals(
                listOf(
                    SummaryRow("Items (3)", "$652.00"),
                    SummaryRow("Discount", "−$140.00", saving = true),
                    SummaryRow("Delivery", "Free"),
                ),
                summary.rows,
            )
            assertEquals("$512", summary.total)
            // Content: three items in two seller groups, in the order they were added, all selected.
            val groups = tree.all().filterIsInstance<CartGroup>()
            assertEquals(listOf("Sony Official Store", "Brooklyn Home Co."), groups.map { it.seller })
            assertEquals(listOf("Courier · Tomorrow", "Courier · Thu, Oct 9"), groups.map { it.delivery })
            assertEquals(listOf(listOf(headphones), listOf(duvet, mug)), groups.map { g -> g.lines.map { it.skuId } })
            assertTrue(groups.flatMap { it.lines }.all { it.selected && it.selectable && it.change == null })
            val line = groups.first().lines.single()
            assertEquals("$349" to "$449", line.price to line.oldPrice)
            assertEquals("Midnight Black · Headphones only", line.options)
            // The tree says where the line's commands go; the client builds no path.
            assertEquals("/api/v1/cart/lines/$headphones" to null, line.url to line.acknowledgeUrl)
            assertEquals(
                CartSelection("selection", allSelected = true, selectedCount = 3, linesUrl = "/api/v1/cart/lines"),
                tree.only<CartSelection>(),
            )
            assertEquals(PageTitle("title", "Cart", "3 items", badge = true), tree.only<PageTitle>())
            assertEquals(3, tree.only<HaulHeader>().cartCount, "the header counts the cart")
            assertEquals(PromoField("promo", "/api/v1/cart/promo"), tree.only<PromoField>())
        }

    /** `Cart_Guest`: the same cart without the points line, and checkout asks to sign in. */
    @Test
    fun `a guest sees no points and is asked to sign in at checkout`() =
        haulTest {
            val summary = cart(mayasCart()).only<OrderSummary>()
            assertNull(summary.points)
            assertNull(summary.pointsAccent)
            assertEquals("Sign in to check out", summary.checkoutLabel)
            assertTrue(summary.checkoutEnabled)
            assertEquals(NavigateAction("/sign-in?next=%2Fcheckout"), summary.checkoutAction)
        }

    /** Scenario «Promo applies once», and `Cart_PromoApplied`. */
    @Test
    fun `a promo applies once`() =
        haulTest {
            val guest = mayasCart()
            applyPromo(guest, "autumn10").assertRefresh()
            applyPromo(guest, "SUMMER5").assertError(HttpStatusCode.Conflict, ErrorCode.PromoAlreadyApplied)
            // The same code again changes nothing and is not a refusal.
            applyPromo(guest, "AUTUMN10").assertRefresh()

            val tree = cart(guest)
            assertEquals(
                PromoField(
                    "promo",
                    "/api/v1/cart/promo",
                    code = "AUTUMN10",
                    applied = true,
                    terms = "10% off items, up to $50",
                ),
                tree.only<PromoField>(),
            )
            // 10 % of $512 is $51.20, and AUTUMN10 stops at $50; the discount includes the promo.
            assertEquals(
                listOf(
                    SummaryRow("Items (3)", "$652.00"),
                    SummaryRow("Discount", "−$190.00", saving = true),
                    SummaryRow("Promo · AUTUMN10", "−$50.00", saving = true),
                    SummaryRow("Delivery", "Free"),
                ),
                tree.only<OrderSummary>().rows,
            )
            assertEquals("$462", tree.only<OrderSummary>().total)

            removePromo(guest).assertRefresh()
            assertEquals("$512", cart(guest).only<OrderSummary>().total)
            applyPromo(guest, "SUMMER5").assertError(HttpStatusCode.UnprocessableEntity, ErrorCode.PromoExpired)
        }

    /** Scenario «Expired promo», and `Cart_PromoError`: the field keeps the code and says why. */
    @Test
    fun `an expired promo is 422 promo_expired and the field says so`() =
        haulTest {
            val guest = mayasCart()
            val error =
                applyPromo(
                    guest,
                    "SUMMER5",
                ).assertError(HttpStatusCode.UnprocessableEntity, ErrorCode.PromoExpired)
            assertEquals("This code has expired", error.message)

            val tree = cart(guest)
            assertEquals(
                PromoField("promo", "/api/v1/cart/promo", code = "SUMMER5", error = "This code has expired"),
                tree.only<PromoField>(),
            )
            assertEquals("$512", tree.only<OrderSummary>().total, "a refused code took money off")
            // The refusal is shown once: the next change to the cart clears it.
            putLine(guest, mug, LineChange(quantity = 2)).assertRefresh()
            assertEquals(PromoField("promo", "/api/v1/cart/promo"), cart(guest).only<PromoField>())
        }

    @Test
    fun `an unknown code is 404 and a code with nothing selected is 422`() =
        haulTest {
            val guest = guest()
            applyPromo(guest, "NOPE").assertError(HttpStatusCode.NotFound, ErrorCode.PromoNotFound)
            applyPromo(guest, "AUTUMN10").assertError(HttpStatusCode.UnprocessableEntity, ErrorCode.PromoNotApplicable)
            applyPromo(guest, " ").assertError(HttpStatusCode.BadRequest, ErrorCode.ValidationFailed)
        }

    /** The public tier: a guest id the server issued, or nothing works (endpoint-cart). */
    @Test
    fun `no guest or an unknown one is 401 unauthenticated`() =
        haulTest {
            get(CartPaths.SCREEN).assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
            get(CartPaths.SCREEN) { header(GUEST_HEADER, "g-not-issued") }
                .assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
            putLine(
                null,
                mug,
                LineChange(quantity = 1),
            ).assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
            // Positive control: the same request with an issued id goes through.
            putLine(guest(), mug, LineChange(quantity = 1)).assertRefresh()
        }

    /** A guest's cart is the guest's: another guest sees an empty one. */
    @Test
    fun `one guest's cart is not another's`() =
        haulTest {
            mayasCart()
            val tree = cart(guest())
            assertEquals("Your cart is empty", tree.only<EmptyState>().title)
            assertTrue(tree.only<ProductGrid>().cards.isNotEmpty(), "the empty cart offers no picks")
            assertEquals(0, tree.only<HaulHeader>().cartCount)
            assertTrue(tree.all().none { it is CartLine })
        }

    /** Quantity 1…10 and never above stock; a SKU with stock 0 cannot be added (feature-product «Out of stock»). */
    @Test
    fun `quantities stay within one to ten and the stock`() =
        haulTest {
            val guest = guest()
            putLine(
                guest,
                mug,
                LineChange(quantity = 11),
            ).assertError(HttpStatusCode.BadRequest, ErrorCode.ValidationFailed)
            putLine(
                guest,
                mug,
                LineChange(quantity = 0),
            ).assertError(HttpStatusCode.BadRequest, ErrorCode.ValidationFailed)
            putLine(guest, mug, LineChange()).assertError(HttpStatusCode.BadRequest, ErrorCode.ValidationFailed)
            putLine(
                guest,
                silverHeadphones,
                LineChange(quantity = 1),
            ).assertError(HttpStatusCode.Conflict, ErrorCode.OutOfStock)
            putLine(
                guest,
                "no-such-sku",
                LineChange(quantity = 1),
            ).assertError(HttpStatusCode.NotFound, ErrorCode.SkuNotFound)
            // The headphones have 25 in stock; ten is the cap that bites first, so the cap is checked at 10.
            putLine(guest, headphones, LineChange(quantity = 10)).assertRefresh()
            val line = cart(guest).all().filterIsInstance<CartLine>().single()
            assertEquals(10 to 10, line.quantity to line.maxQuantity)
            assertEquals("$349 each", line.each)
            assertEquals("$3,490" to "$4,490", line.price to line.oldPrice)
        }

    @Test
    fun `a body that is not the command's JSON is 400`() =
        haulTest {
            val guest = guest()
            put(CartPaths.line(mug)) {
                header(GUEST_HEADER, guest)
                setBody("""{"quantity":"three"}""")
            }.assertError(HttpStatusCode.BadRequest, ErrorCode.ValidationFailed)
        }

    /** Checkout and the totals take the selected lines only; «Delete selected» removes lines. */
    @Test
    fun `totals count the selected lines and deleted lines go`() =
        haulTest {
            val guest = mayasCart()
            putLine(guest, headphones, LineChange(selected = false)).assertRefresh()
            val tree = cart(guest)
            assertEquals(mapOf("Items (2)" to "$203.00", "Discount" to "−$40.00", "Delivery" to "Free"), tree.rows())
            assertEquals("$163", tree.only<OrderSummary>().total)
            assertEquals(
                CartSelection("selection", allSelected = false, selectedCount = 2, linesUrl = "/api/v1/cart/lines"),
                tree.only<CartSelection>(),
            )
            assertEquals(3, tree.only<HaulHeader>().cartCount, "an unselected line is still in the cart")

            removeLines(guest, duvet).assertRefresh()
            // The mug alone is under $35: delivery is $5.99 for a guest (research D7).
            val mugOnly = cart(guest)
            assertEquals(mapOf("Items (1)" to "$24.00", "Discount" to "−$0.00", "Delivery" to "$5.99"), mugOnly.rows())
            assertEquals("$29.99", mugOnly.only<OrderSummary>().total)

            removeLines(guest, headphones, mug).assertRefresh()
            assertEquals("Your cart is empty", cart(guest).only<EmptyState>().title)
            removeLines(guest).assertError(HttpStatusCode.BadRequest, ErrorCode.ValidationFailed)
        }

    /**
     * B-76: a customer's code goes through the same route as a guest's, with the bearer token in place of
     * the guest id — the promo row under the discount, then why a refused code is refused.
     */
    @Test
    fun `a customer applies a code and sees why another is refused`() =
        haulTest(signIn = ShildikHarness.signIn) {
            val customer = ShildikHarness.accessToken(ShildikHarness.person("Test Customer"))

            suspend fun send(
                path: String,
                body: String,
            ) = put(path) {
                bearerAuth(customer)
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            suspend fun tree(): KompotComponent {
                val response = get(CartPaths.SCREEN) { bearerAuth(customer) }
                assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
                return haulWireJson.decodeKompotComponent(response.bodyAsText())
            }
            send(CartPaths.line(mug), haulWireJson.encodeToString(LineChange.serializer(), LineChange(quantity = 1)))
                .assertRefresh()

            send(CartPaths.PROMO, haulWireJson.encodeToString(PromoEntry.serializer(), PromoEntry("AUTUMN10")))
                .assertRefresh()
            val applied = tree()
            assertTrue(applied.only<PromoField>().applied, "the field: ${applied.only<PromoField>()}")
            assertEquals("−$2.40", applied.rows()["Promo · AUTUMN10"], "the rows: ${applied.rows()}")

            delete(CartPaths.PROMO) { bearerAuth(customer) }.assertRefresh()
            send(CartPaths.PROMO, haulWireJson.encodeToString(PromoEntry.serializer(), PromoEntry("SUMMER5")))
                .assertError(HttpStatusCode.UnprocessableEntity, ErrorCode.PromoExpired)
            assertEquals(
                PromoField("promo", CartPaths.PROMO, code = "SUMMER5", error = "This code has expired"),
                tree().only<PromoField>(),
            )
        }

    @Test
    fun `nothing selected disables checkout`() =
        haulTest {
            val guest = guest()
            putLine(guest, mug, LineChange(quantity = 1, selected = false)).assertRefresh()
            val summary = cart(guest).only<OrderSummary>()
            assertFalse(summary.checkoutEnabled)
            assertEquals("$0", summary.total)
        }

    @Test
    fun `acknowledging a line that is not in the cart is 404`() =
        haulTest {
            acknowledge(guest(), mug).assertError(HttpStatusCode.NotFound, ErrorCode.LineNotFound)
        }
}
