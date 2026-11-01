package io.github.youndie.haul

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.shell.BrowserTitle
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.ButtonComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.PolymorphicSerializer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-161: the browser's tab is titled by the page drawn — the title its tree's header names, the server's copy —
 * and follows every visit, back and forward included. A fake transport serves the trees and a [BrowserTitle] records
 * what the tab was told, in order.
 */
@OptIn(ExperimentalTestApi::class)
class TabTitleTest {
    private val titles = CopyOnWriteArrayList<String>()
    private val history = FakeHistory()
    private val answers = mutableMapOf<String, suspend () -> HaulResponse>()

    private val transport =
        HaulTransport { path -> (answers[path] ?: error("nothing answers $path"))() }

    private fun ComposeUiTest.storefront() =
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                Storefront(transport, history, signIn = {}, title = BrowserTitle { titles += it })
            }
        }

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.waitForTitle(title: String) =
        waitUntil(timeoutMillis = 5_000) { titles.lastOrNull() == title }

    @Test
    fun `the tab follows two pages and back again`() =
        runDesktopComposeUiTest(1_440, 1_000) {
            answers["/ui/home"] = ok(page(HOME, "to-product", "Open the product", PRODUCT_PATH))
            answers["/ui$PRODUCT_PATH"] = ok(page(PRODUCT, "to-category", "Open the category", CATEGORY_PATH))
            answers["/ui$CATEGORY_PATH"] = ok(page(CATEGORY, "to-home", "Go home", "/"))
            storefront()
            waitForTitle(HOME)

            onNodeWithText("Open the product").performClick()
            waitForText("Open the category")
            waitForTitle(PRODUCT)
            onNodeWithText("Open the category").performClick()
            waitForText("Go home")
            waitForTitle(CATEGORY)

            history.back()
            waitForText("Open the category")
            waitForTitle(PRODUCT)
            history.back()
            waitForText("Open the product")
            waitForTitle(HOME)
            history.forward()
            waitForText("Open the category")
            waitForTitle(PRODUCT)

            // Each page's own, with «Haul» between them while the next page's placeholder was drawn; never the
            // title of a page that was not on the screen.
            assertEquals(
                listOf(HOME, STORE, PRODUCT, STORE, CATEGORY, STORE, PRODUCT, STORE, HOME, STORE, PRODUCT),
                titles,
            )
        }

    /** A cold load keeps the page's own `<title>` until a tree names the page: no «Haul» for the moment between. */
    @Test
    fun `a cold load leaves the page's title until the tree names it`() =
        runDesktopComposeUiTest(1_440, 1_000) {
            val arrived = CompletableDeferred<HaulResponse>()
            answers["/ui/home"] = { arrived.await() }
            storefront()
            waitForIdle()
            assertEquals(emptyList(), titles)
            arrived.complete(ok(page(HOME, "to-product", "Open the product", PRODUCT_PATH))())
            waitForTitle(HOME)
            assertEquals(listOf(HOME), titles)
        }

    /** A page the shell draws itself with no tree names no page; the sign-in page is the client's own. */
    @Test
    fun `a page with no tree is the store's and the sign-in page is sign-in's`() =
        runDesktopComposeUiTest(1_440, 1_000) {
            answers["/ui/home"] = ok(page(HOME, "to-product", "Open the product", PRODUCT_PATH))
            answers["/ui$PRODUCT_PATH"] = { HaulResponse(404, """{"code":"product_not_found","message":"gone"}""") }
            storefront()
            waitForTitle(HOME)
            onNodeWithText("Open the product").performClick()
            waitForText("This product is no longer available")
            waitForTitle(STORE)

            history.entries[0] = "/sign-in"
            titles.clear()
            history.back()
            waitForTitle("Sign in · Haul")
        }

    private companion object {
        const val HOME = "Haul — a demo store built end to end in Kotlin"
        const val PRODUCT = "Sony WH-1000XM6 Wireless Noise Cancelling Headphones · Haul"
        const val CATEGORY = "Headphones · Haul"
        const val STORE = "Haul"
        const val PRODUCT_PATH = "/p/p-1"
        const val CATEGORY_PATH = "/c/headphones"

        /** A page under a header naming the tab [title], with one button to [to]. */
        fun page(
            title: String,
            id: String,
            text: String,
            to: String,
        ): KompotComponent =
            ColumnComponent(
                id = "page",
                children =
                    listOf(
                        HaulHeader(
                            id = "header",
                            deliverTo = "Brooklyn, NY 11211",
                            deliveryPromise = "Free delivery over $35",
                            cartCount = 0,
                            searchPlaceholder = "Search products",
                            categories = listOf("Electronics"),
                            tabTitle = title,
                        ),
                        ButtonComponent(id = id, text = text, action = NavigateAction(to)),
                    ),
            )

        fun ok(tree: KompotComponent): suspend () -> HaulResponse =
            { HaulResponse(200, haulWireJson.encodeToString(PolymorphicSerializer(KompotComponent::class), tree)) }
    }
}
