package io.github.youndie.haul.feature.identity

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.cart.CartPaths
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.cart.data.CartsTable
import io.github.youndie.haul.feature.identity.data.CustomersTable
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.testing.SeededDatabase
import io.github.youndie.haul.testing.ShildikHarness
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.assertError
import io.github.youndie.haul.testing.assertRefresh
import io.github.youndie.haul.testing.cart
import io.github.youndie.haul.testing.guest
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.putLine
import io.github.youndie.haul.ui.AccountBody
import io.github.youndie.haul.ui.CartLine
import io.github.youndie.haul.ui.EmptyState
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * feature-identity over HTTP, against a running shildik ([ShildikHarness]) and the seeded catalog in
 * PostgreSQL. Every token is one the provider issued through the browser's flow — authorization code
 * with PKCE — and every test signs in a person of its own, so customers and carts never meet.
 */
class IdentityRoutesTest {
    private val mug = "${SampleCatalog.STONEWARE_MUG}-0"
    private val duvet = "${SampleCatalog.DUVET_COVER}-0"
    private val database = Databases.connect(SeededDatabase.dataSource)

    private fun signedIn(
        name: String,
        block: suspend HttpClient.(token: String, sub: String) -> Unit,
    ) {
        val sub = ShildikHarness.person(name)
        val token = ShildikHarness.accessToken(sub)
        haulTest(signIn = ShildikHarness.signIn) { block(token, sub) }
    }

    private suspend fun HttpClient.tree(
        path: String,
        request: HttpRequestBuilder.() -> Unit,
    ): KompotComponent {
        val response = get(path, request)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return haulWireJson.decodeKompotComponent(response.bodyAsText())
    }

    private suspend fun HttpClient.putLine(
        token: String,
        skuId: String,
        quantity: Int,
        guest: String? = null,
    ) = put(CartPaths.line(skuId)) {
        bearerAuth(token)
        guest?.let { header(GUEST_HEADER, it) }
        contentType(ContentType.Application.Json)
        setBody(haulWireJson.encodeToString(LineChange.serializer(), LineChange(quantity = quantity)))
    }

    private suspend fun HttpClient.merge(
        token: String?,
        guest: String?,
    ) = post(CartPaths.MERGE) {
        token?.let { bearerAuth(it) }
        guest?.let { header(GUEST_HEADER, it) }
    }

    private fun guestHasCart(guest: String): Boolean =
        transaction(database) { !CartsTable.selectAll().where { CartsTable.guestId eq guest }.empty() }

    /** Scenario «Guest cart survives sign-in». */
    @Test
    fun `a guest cart survives sign-in`() =
        signedIn("Maya Kowalski") { token, _ ->
            val guest = guest()
            putLine(guest, mug, LineChange(quantity = 1)).assertRefresh()
            putLine(token, mug, 1).assertRefresh()

            merge(token, guest).assertRefresh()

            val lines = tree(CartPaths.SCREEN) { bearerAuth(token) }.all().filterIsInstance<CartLine>()
            assertEquals(listOf(mug to 2), lines.map { it.skuId to it.quantity })
            assertTrue(!guestHasCart(guest), "the guest cart still exists")
            // The guest is still a guest — of an empty cart.
            cart(guest).only<EmptyState>()
        }

    /** A line only the guest had moves over as it was; the customer's own lines keep their place. */
    @Test
    fun `a guest's other lines join the customer's after its own`() =
        signedIn("Sam Ortiz") { token, _ ->
            val guest = guest()
            putLine(guest, duvet, LineChange(quantity = 2)).assertRefresh()
            putLine(token, mug, 1).assertRefresh()

            merge(token, guest).assertRefresh()
            // A second merge of the same guest finds nothing to move.
            merge(token, guest).assertRefresh()

            val lines = tree(CartPaths.SCREEN) { bearerAuth(token) }.all().filterIsInstance<CartLine>()
            assertEquals(listOf(mug to 1, duvet to 2), lines.map { it.skuId to it.quantity })
        }

    /** Scenario «Account needs a sign-in». */
    @Test
    fun `the account needs a sign-in`() =
        signedIn("Maya Kowalski") { token, _ ->
            get("/ui/account").assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
            get("/ui/account") { header(GUEST_HEADER, guest()) }
                .assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)

            assertEquals("Hi, Maya", tree("/ui/account") { bearerAuth(token) }.only<AccountBody>().title)
        }

    /** «The first authenticated request of an unknown `sub` creates the Customer from the token's name claim.» */
    @Test
    fun `the first request of a new person creates the customer from the token's name`() =
        signedIn("Ada Lovelace") { token, sub ->
            fun stored() =
                transaction(database) {
                    CustomersTable
                        .selectAll()
                        .where { CustomersTable.id eq sub }
                        .map { it[CustomersTable.name] to it[CustomersTable.plus] }
                }
            assertEquals(emptyList(), stored())

            val header = tree("/ui/home") { bearerAuth(token) }.only<HaulHeader>()

            assertEquals("Ada", header.customerName)
            assertEquals(NavigateAction("/account"), header.account)
            assertEquals(listOf("Ada Lovelace" to false), stored())
        }

    /** «A request with both a valid bearer and a guest id acts as the customer; the guest id is used only by the merge.» */
    @Test
    fun `a token and a guest id together are the customer`() =
        signedIn("Sam Ortiz") { token, _ ->
            val guest = guest()
            putLine(token, mug, 3, guest = guest).assertRefresh()

            assertEquals(3, tree("/ui/home") { bearerAuth(token) }.only<HaulHeader>().cartCount)
            assertEquals(0, tree("/ui/home") { header(GUEST_HEADER, guest) }.only<HaulHeader>().cartCount)
        }

    /** The header's three states on screens other than the cart: signed out, signed in by name, the cart's count. */
    @Test
    fun `every screen's header greets whoever is looking and counts their cart`() =
        signedIn("Maya Kowalski") { token, _ ->
            val guest = guest()
            putLine(guest, mug, LineChange(quantity = 2)).assertRefresh()
            putLine(token, duvet, 1).assertRefresh()
            val screens =
                listOf(
                    "/ui/home",
                    "/ui/c/electronics/audio/headphones",
                    "/ui/p/${SampleCatalog.SONY_HEADPHONES}",
                    "/ui/search?q=mug",
                )

            for (screen in screens) {
                val nobody = tree(screen) {}.only<HaulHeader>()
                assertEquals(
                    Triple(null, 0, NavigateAction("/sign-in")),
                    Triple(nobody.customerName, nobody.cartCount, nobody.account),
                    screen,
                )

                val asGuest = tree(screen) { header(GUEST_HEADER, guest) }.only<HaulHeader>()
                assertEquals(
                    Triple(null, 2, NavigateAction("/sign-in")),
                    Triple(asGuest.customerName, asGuest.cartCount, asGuest.account),
                    screen,
                )

                val asCustomer = tree(screen) { bearerAuth(token) }.only<HaulHeader>()
                assertEquals(
                    Triple("Maya", 1, NavigateAction("/account")),
                    Triple(asCustomer.customerName, asCustomer.cartCount, asCustomer.account),
                    screen,
                )
            }
        }

    /** A lapsed or foreign token is refused, not quietly read as a guest: the client has to know to sign in again. */
    @Test
    fun `a token that does not verify is 401 unauthenticated`() =
        signedIn("Sam Ortiz") { _, sub ->
            get(
                "/ui/home",
            ) { bearerAuth("not-a-token") }.assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)

            // Issued by the same realm, to another of its clients: its `azp` is not the storefront.
            val foreign = ShildikHarness.accessToken(sub, client = ShildikHarness.OTHER_CLIENT)
            get("/ui/home") { bearerAuth(foreign) }.assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
            merge(foreign, guest()).assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
        }

    @Test
    fun `the merge needs a customer and a guest the server issued`() =
        signedIn("Sam Ortiz") { token, _ ->
            merge(token = null, guest = guest()).assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
            merge(token, guest = "g-never-issued").assertError(HttpStatusCode.NotFound, ErrorCode.GuestNotFound)
            merge(token, guest = null).assertError(HttpStatusCode.NotFound, ErrorCode.GuestNotFound)
        }

    @Test
    fun `the browser is told where to sign in, and a server without sign-in says so`() {
        signedIn("Sam Ortiz") { _, _ ->
            val response = get(SIGN_IN_SETTINGS)
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                SignInSettings(
                    issuer = ShildikHarness.issuer,
                    clientId = ShildikHarness.CLIENT,
                    scope = "openid profile email offline_access",
                    mergeUrl = CartPaths.MERGE,
                ),
                haulWireJson.decodeFromString(SignInSettings.serializer(), response.bodyAsText()),
            )
        }
        haulTest {
            get(SIGN_IN_SETTINGS).assertError(HttpStatusCode.ServiceUnavailable, ErrorCode.Unavailable)
            // Off, every token is refused rather than ignored.
            get(
                "/ui/home",
            ) { bearerAuth("anything") }.assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
            assertNull(tree("/ui/home") {}.only<HaulHeader>().customerName)
        }
    }
}
