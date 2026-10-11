package io.github.youndie.haul.shell

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.CANVAS_NOW
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.SEARCH_FIELD_TAG
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.TextComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import kotlinx.serialization.PolymorphicSerializer
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * B-94: a page the server refused says what happened and offers what helps. A Retry that asks for the same
 * refusal is not offered: an empty checkout is the cart, a search too short says so, a parameter an old or
 * edited link got wrong is dropped, a link refused for no parameter it has is said to be out of date. A page
 * that did not load tells «unreachable» from «our side» on every kind that has an error page of its own.
 */
@OptIn(ExperimentalTestApi::class)
class RefusedPagesTest {
    private val requests = CopyOnWriteArrayList<String>()
    private val answers = mutableMapOf<String, () -> HaulResponse>()
    private val transport =
        HaulTransport { path ->
            requests += path
            (answers[path] ?: error("nothing answers $path"))()
        }
    private lateinit var history: FakeHistory

    @Test
    fun `a checkout with nothing selected lands on the cart`() =
        at("/checkout") {
            answers["/ui/checkout"] = { refusal(409, "cart_empty") }
            answers["/ui/cart"] = { ok(page("The cart")) }
            storefront()
            onNodeWithText("The cart").assertExists()
            onNodeWithText("Retry").assertDoesNotExist()
            assertEquals(listOf("/cart"), history.entries, "the checkout gave its entry to the cart")
            assertEquals(listOf("/ui/checkout", "/ui/cart"), requests)
        }

    @Test
    fun `a search too short says so with the query in the field and no Retry`() =
        at("/search?q=a") {
            answers["/ui/search?q=a"] = { refusal(400, "query_too_short") }
            storefront()
            onNodeWithText("Search needs at least two characters", substring = true).assertExists()
            onNodeWithText("Search didn", substring = true).assertDoesNotExist()
            onNodeWithText("Retry").assertDoesNotExist()
            onNodeWithTag(SEARCH_FIELD_TAG).assertTextContains("a")
        }

    /** The results kept while the next search loads behind them (B-62): the notice says why, and offers no Retry. */
    @Test
    fun `a search too short over the results kept says so without Retry`() =
        at("/search?q=mugs") {
            answers["/ui/search?q=mugs"] = { ok(page("Results for mugs")) }
            answers["/ui/search?q=a"] = { refusal(400, "query_too_short") }
            answers["/ui/search/suggest?q=a"] = { refusal(400, "query_too_short") }
            storefront()
            val field = onNodeWithTag(SEARCH_FIELD_TAG)
            field.performClick()
            field.performTextInput("a")
            field.performKeyInput { pressKey(Key.Enter) }
            waitUntil(timeoutMillis = 5_000) { exists(NOT_UPDATED_TITLE) }
            onNodeWithText("Search needs at least two characters.").assertExists()
            onNodeWithText("Retry").assertDoesNotExist()
            onNodeWithText("Results for mugs").assertExists()
        }

    @Test
    fun `a parameter an old link got wrong is dropped and the page loads without it`() =
        at("/c/mugs?sort=bogus&brand=Ostra") {
            answers["/ui/c/mugs?sort=bogus&brand=Ostra"] = { refusal(400, "validation_failed", field = "sort") }
            answers["/ui/c/mugs?brand=Ostra"] = { ok(page("Ostra mugs")) }
            storefront()
            onNodeWithText("Ostra mugs").assertExists()
            assertEquals(listOf("/c/mugs?brand=Ostra"), history.entries)
            assertEquals(listOf("/ui/c/mugs?sort=bogus&brand=Ostra", "/ui/c/mugs?brand=Ostra"), requests)
        }

    @Test
    fun `a product link whose sku is gone opens the product`() =
        at("/p/p-1?sku=gone") {
            answers["/ui/p/p-1?sku=gone"] = { refusal(400, "validation_failed", field = "sku") }
            answers["/ui/p/p-1"] = { ok(page("The product")) }
            storefront()
            onNodeWithText("The product").assertExists()
            assertEquals(listOf("/p/p-1"), history.entries)
        }

    @Test
    fun `a link refused for no parameter it has is out of date and leads to its page`() =
        at("/c/mugs?page=2") {
            answers["/ui/c/mugs?page=2"] = { refusal(400, "validation_failed") }
            answers["/ui/c/mugs"] = { ok(page("All mugs")) }
            storefront()
            onNodeWithText("This link is out of date").assertExists()
            onNodeWithText("Something went wrong on our side", substring = true).assertDoesNotExist()
            onNodeWithText("Retry").assertDoesNotExist()
            onNodeWithText("Open the page").performClick()
            onNodeWithText("All mugs").assertExists()
            assertEquals(listOf("/c/mugs?page=2", "/c/mugs"), history.entries)
        }

    /**
     * A link cut off mid-escape or with an escape that is none (B-117), opened through the browser's own transport:
     * Ktor's client cannot send a query it cannot decode, so the tree request is refused before it leaves, as the
     * server refuses one (`400 validation_failed`, no parameter named) — not «We couldn't reach Haul» with a Retry
     * that fails the same way — and the search's query is read without throwing from the page's placeholders.
     */
    @Test
    fun `a link whose query does not decode is out of date and leads to its page`() =
        MALFORMED.forEach { location ->
            val path = location.substringBefore('?')
            val sent = CopyOnWriteArrayList<String>()
            val http =
                HttpClient(
                    MockEngine { request ->
                        sent += request.url.fullPath
                        val tree =
                            haulWireJson.encodeToString(
                                PolymorphicSerializer(KompotComponent::class),
                                page("The page at $path"),
                            )
                        respond(tree, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                )
            at(location) {
                storefront(ktorTransport(http, "http://haul.test"))
                onNodeWithText("This link is out of date").assertExists()
                onNodeWithText("Retry").assertDoesNotExist()
                onNodeWithText("Open the page").performClick()
                onNodeWithText("The page at $path").assertExists()
                assertEquals(listOf(location, path), history.entries, location)
                // The header the out-of-date page is drawn under aside, only the page at its path was asked for.
                assertEquals(listOf("/ui$path"), sent.filter { it != HEADER_TREE }, location)
            }
        }

    @Test
    fun `an account that could not be reached says so, not our side`() =
        unreachable("/account", ShellFailure.Unreachable.message)

    @Test
    fun `a saved list that could not be reached says so, not our side`() =
        unreachable("/account/saved", ShellFailure.Unreachable.message)

    @Test
    fun `a saved list the server failed says it was our side`() =
        at("/account/saved") {
            answers["/ui/account/saved"] = { refusal(500, "internal") }
            storefront()
            onNodeWithText(ShellFailure.Server.message).assertExists()
        }

    @Test
    fun `a cart that could not be reached says so`() =
        unreachable("/cart", "We couldn’t reach Haul. Nothing in it was lost. Check your connection and try again.")

    @Test
    fun `a checkout that could not be reached says so`() =
        unreachable("/checkout", "We couldn’t reach Haul. Your cart is unchanged. Check your connection and try again.")

    @Test
    fun `a search that could not be reached says so`() =
        unreachable(
            "/search?q=mugs",
            "We couldn’t reach Haul. Your query is still in the field. Check your connection and try again.",
        )

    /** [location] answered with no answer at all: its error page says [message] and offers Retry. */
    private fun unreachable(
        location: String,
        message: String,
    ) = at(location) {
        answers[Address(location).screen] = { throw IOException("connection refused") }
        storefront()
        onNodeWithText(message).assertExists()
        onNodeWithText("Retry").assertExists()
    }

    private fun at(
        location: String,
        test: ComposeUiTest.() -> Unit,
    ) = runDesktopComposeUiTest(1440, 1_000) {
        history = FakeHistory(location)
        test()
    }

    private fun ComposeUiTest.storefront(transport: HaulTransport = this@RefusedPagesTest.transport) =
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                Storefront(transport, history, signIn = {}, clock = FIXED)
            }
        }

    private fun ComposeUiTest.exists(text: String): Boolean =
        onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        /** The four the v0.4.0 run opened (TC-ERR-04 step 4). */
        val MALFORMED =
            listOf(
                "/c/electronics?brand=%E0%A4%A",
                "/search?q=%zz",
                "/c/electronics?%zz",
                "/p/p-sony-wh-1000xm6?sku=%zz",
            )

        val FIXED =
            object : Clock {
                override fun now(): Instant = CANVAS_NOW
            }

        val header =
            HaulHeader(
                id = "header",
                deliverTo = "Brooklyn, NY 11211",
                deliveryPromise = "Free delivery over $35",
                customerName = "Maya",
                cartCount = 0,
                searchPlaceholder = "Search products",
                categories = listOf("Electronics"),
            )

        fun page(text: String): KompotComponent =
            ColumnComponent(id = "page", children = listOf(header, TextComponent(id = "text", text = text)))

        fun ok(tree: KompotComponent) =
            HaulResponse(200, haulWireJson.encodeToString(PolymorphicSerializer(KompotComponent::class), tree))

        fun refusal(
            status: Int,
            code: String,
            field: String? = null,
        ) = HaulResponse(
            status,
            """{"code":"$code","message":"refused"${field?.let { ""","field":"$it"""" }.orEmpty()}}""",
        )
    }
}
