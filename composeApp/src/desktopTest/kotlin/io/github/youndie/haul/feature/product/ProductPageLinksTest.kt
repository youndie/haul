package io.github.youndie.haul.feature.product

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.decode
import io.github.youndie.haul.feature.cart.CartCommand
import io.github.youndie.haul.feature.cart.CartCommands
import io.github.youndie.haul.feature.saved.SaveCommand
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.shell.TreeCommands
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.LinkCopier
import io.github.youndie.haul.ui.LocalLinkCopier
import io.github.youndie.haul.ui.ProductDetails
import io.github.youndie.haul.ui.ProductQuestions
import io.github.youndie.haul.ui.ProductReviews
import io.github.youndie.haul.ui.Question
import io.github.youndie.haul.ui.Review
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.commands.LoadAction
import io.github.youndie.kompot.commands.UpdateHistory
import io.github.youndie.kompot.commands.kompotUpdate
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.RefreshAction
import io.github.youndie.kompot.standard.TextComponent
import kotlinx.serialization.PolymorphicSerializer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-71 in the client: what looks pressable on the product page does what its tree says. The brand opens its
 * address; «2,341 reviews» and «All specifications» open their tabs and bring the tab row into view, where the
 * tab changes; the share button copies the product's link and says so, or, when the browser refuses, shows the
 * link to copy by hand (B-104); «Saved» out of stock takes the product off the Saved list; «Show more reviews»
 * and «Show more questions» load ten more in place. What is not wired is not pressable: the viewer's own review
 * reads «Your review», and there is no chevron or thumbnail to press. The pages are the server's bodies for the
 * Product artboards (`resources/bodies/`).
 */
@OptIn(ExperimentalTestApi::class)
class ProductPageLinksTest {
    private val requests = CopyOnWriteArrayList<String>()
    private val pages = mutableMapOf<String, String>()
    private val history = FakeHistory(PRODUCT)
    private val copied = CopyOnWriteArrayList<String>()
    private val sent = CopyOnWriteArrayList<CartCommand>()

    private fun serve(
        path: String,
        tree: KompotComponent,
    ) {
        pages[path] = haulWireJson.encodeToString(PolymorphicSerializer(KompotComponent::class), tree)
    }

    private fun serve(
        path: String,
        action: KompotAction,
    ) {
        pages[path] = haulWireJson.encodeToString(PolymorphicSerializer(KompotAction::class), action)
    }

    private fun ComposeUiTest.storefront(
        page: KompotComponent,
        links: LinkCopier = LinkCopier { path, done -> done(copied.add(path)) },
    ) {
        serve("/ui$PRODUCT", page)
        val transport =
            HaulTransport { path ->
                requests += path
                HaulResponse(200, pages[path] ?: error("nothing answers $path"))
            }
        val votes = TreeCommands { RefreshAction }
        val cart =
            CartCommands { command ->
                sent += command
                RefreshAction
            }
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                CompositionLocalProvider(LocalLinkCopier provides links) {
                    Storefront(transport, history, signIn = {}, cartCommands = cart, treeCommands = votes)
                }
            }
        }
        waitUntil(timeoutMillis = 5_000) { requests.isNotEmpty() }
        onNodeWithText("Add to cart").assertExists()
    }

    private fun ComposeUiTest.waitForRequest(path: String) = waitUntil(timeoutMillis = 5_000) { path in requests }

    /**
     * B-152: a press on the option already chosen opened the page shown again; checkout's chosen way to pay sends
     * nothing for the same press. The chosen swatch and the chosen pill press nothing; another option opens its SKU.
     */
    @Test
    fun `the chosen variant presses nothing and another opens its sku`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val page = decode("product_description.json") as ColumnComponent
            val linked =
                page.copy(
                    children =
                        page.children.map { child ->
                            if (child !is ProductDetails) return@map child
                            child.copy(
                                variants =
                                    child.variants.map { group ->
                                        group.copy(
                                            options =
                                                group.options.map {
                                                    it.copy(action = NavigateAction("$PRODUCT?sku=${it.skuId}"))
                                                },
                                        )
                                    },
                            )
                        },
                )
            serve("/ui$PRODUCT?sku=p-sonora-sh-718n-1", NEXT_PAGE)
            storefront(linked)
            onNodeWithContentDescription("Midnight Black").performClick()
            onNodeWithText("Headphones only").performClick()
            waitForIdle()
            assertEquals(listOf("/ui$PRODUCT"), requests.filter { it.startsWith("/ui/p/") })
            assertEquals(listOf(PRODUCT), history.entries)

            onNodeWithText("+ Travel case").performClick()
            onNodeWithText(NEXT).assertExists()
            assertEquals(listOf(PRODUCT, "$PRODUCT?sku=p-sonora-sh-718n-1"), history.entries)
        }

    @Test
    fun `the brand opens its products in the category`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            serve("/ui$BRAND", NEXT_PAGE)
            storefront(decode("product_description.json"))
            onNodeWithText("SONORA").performClick()
            onNodeWithText(NEXT).assertExists()
            assertEquals(listOf(PRODUCT, BRAND), history.entries)
        }

    /**
     * «2,341 reviews» opens the reviews tab on the page drawn (the same path, B-62): the tab row, below the fold
     * before, is brought into view with the tab under it.
     */
    @Test
    fun `the reviews count opens the reviews tab and brings the tabs into view`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            serve("/ui$REVIEWS", decode("product_reviews.json"))
            storefront(decode("product_description.json"))
            onNodeWithTag(TABS_TAG).assertIsNotDisplayed()
            onNodeWithText("2,341 reviews").performClick()
            waitForRequest("/ui$REVIEWS")
            waitUntil(
                timeoutMillis = 5_000,
            ) { onAllNodes(hasText("Write a review")).fetchSemanticsNodes().isNotEmpty() }
            waitForIdle()
            onNodeWithTag(TABS_TAG).assertIsDisplayed()
            assertEquals(listOf(PRODUCT, REVIEWS), history.entries)
        }

    /**
     * B-121: on the reviews tab «2,341 reviews» opens the address shown — loaded again (B-95), the same tab arriving —
     * and the tab row stayed out of sight. The press asks for the row itself.
     */
    @Test
    fun `the reviews count on the reviews tab brings the tabs into view`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            history.entries[0] = REVIEWS
            serve("/ui$REVIEWS", decode("product_reviews.json"))
            storefront(decode("product_reviews.json"))
            onNodeWithTag(TABS_TAG).assertIsNotDisplayed()
            onNodeWithText("2,341 reviews").performClick()
            waitUntil(timeoutMillis = 5_000) { requests.count { it == "/ui$REVIEWS" } == 2 }
            waitForIdle()
            onNodeWithTag(TABS_TAG).assertIsDisplayed()
            assertEquals(listOf(REVIEWS), history.entries)
        }

    @Test
    fun `all specifications opens the specifications tab and brings the tabs into view`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            serve("/ui$SPECIFICATIONS", decode("product_specifications.json"))
            storefront(decode("product_description.json"))
            onNodeWithText("All specifications").performScrollTo()
            onNodeWithTag(TABS_TAG).assertIsNotDisplayed()
            onNodeWithText("All specifications").performClick()
            waitForRequest("/ui$SPECIFICATIONS")
            waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Codecs")).fetchSemanticsNodes().isNotEmpty() }
            waitForIdle()
            onNodeWithTag(TABS_TAG).assertIsDisplayed()
            assertEquals(listOf(PRODUCT, SPECIFICATIONS), history.entries)
        }

    @Test
    fun `share copies the product's link and says it did`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            storefront(decode("product_description.json"))
            onNodeWithContentDescription(COPY_LINK).performClick()
            onNodeWithContentDescription(LINK_COPIED).assertExists()
            assertEquals(listOf("/p/p-sonora-sh-718n?sku=p-sonora-sh-718n-0"), copied.toList())
            assertEquals(listOf("/ui$PRODUCT"), requests.toList(), "copying a link loaded a page")
        }

    /**
     * B-104: a browser that keeps the link off the clipboard leaves the button as it was and says so, over the
     * whole link to copy by hand; closing it takes it away, and a press that copies after all says «copied».
     */
    @Test
    fun `share the browser refuses shows the link to copy by hand`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            var allowed = false
            val refusing =
                object : LinkCopier {
                    override fun copy(
                        path: String,
                        done: (Boolean) -> Unit,
                    ) = done(allowed && copied.add(path))

                    override fun address(path: String): String = ORIGIN + path
                }
            storefront(decode("product_description.json"), refusing)
            onNodeWithTag(SHARE_FALLBACK_TAG).assertDoesNotExist()
            onNodeWithContentDescription(COPY_LINK).performClick()
            onNodeWithContentDescription(COPY_LINK).assertExists()
            onNodeWithTag(SHARE_FALLBACK_TAG).assertIsDisplayed()
            onNodeWithText(COPY_REFUSED).assertIsDisplayed()
            onNodeWithText("$ORIGIN/p/p-sonora-sh-718n?sku=p-sonora-sh-718n-0").assertIsDisplayed()
            onNodeWithContentDescription(CLOSE_LINK).performClick()
            onNodeWithTag(SHARE_FALLBACK_TAG).assertDoesNotExist()

            onNodeWithContentDescription(COPY_LINK).performClick()
            onNodeWithTag(SHARE_FALLBACK_TAG).assertIsDisplayed()
            allowed = true
            onNodeWithContentDescription(COPY_LINK).performClick()
            onNodeWithContentDescription(LINK_COPIED).assertExists()
            onNodeWithTag(SHARE_FALLBACK_TAG).assertDoesNotExist()
            assertEquals(listOf("/p/p-sonora-sh-718n?sku=p-sonora-sh-718n-0"), copied.toList())
            assertEquals(listOf("/ui$PRODUCT"), requests.toList(), "a refused copy loaded a page")
        }

    /** Out of stock, a saved product's «Save» reads «Saved» and takes it off the list, as the heart does. */
    @Test
    fun `saved out of stock reads saved and takes the product off the list`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val unsave = SaveCommand("/api/v1/saved/p-sonora-sh-718n", save = false)
            storefront(details(decode("product_out_of_stock.json")) { it.copy(saved = true, heartCommand = unsave) })
            onNode(hasText("Saved") and hasAnyAncestor(hasTestTag(SAVE_TAG)), useUnmergedTree = true).assertExists()
            onNodeWithTag(SAVE_TAG).performScrollTo().performClick()
            waitUntil(timeoutMillis = 5_000) { sent.isNotEmpty() }
            assertEquals(listOf<CartCommand>(CartCommand.Heart(unsave.url, save = false)), sent.toList())
        }

    @Test
    fun `show more reviews loads ten more in place`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val page = moreReviews(decode("product_reviews.json"))
            serve(
                MORE_REVIEWS,
                kompotUpdate(MORE_REVIEWS.removePrefix(PARTS), UpdateHistory.PUSH) {
                    addComponent(
                        page.only<ProductReviews>().let {
                            it.copy(
                                reviews = it.reviews + LATER,
                                more = null,
                                moreLabel = null,
                            )
                        },
                    )
                },
            )
            storefront(page)
            onNodeWithTag(MORE_TAG).performScrollTo().performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText(LATER.title)).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag(MORE_TAG).assertDoesNotExist()
            assertEquals(listOf("/ui$PRODUCT", MORE_REVIEWS), requests.toList(), "the page was loaded for more reviews")
            assertEquals(listOf(PRODUCT, MORE_REVIEWS.removePrefix(PARTS)), history.entries)
        }

    @Test
    fun `show more questions loads ten more in place`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val page = moreQuestions(decode("product_questions.json"))
            serve(
                MORE_QUESTIONS,
                kompotUpdate(MORE_QUESTIONS.removePrefix(PARTS), UpdateHistory.PUSH) {
                    addComponent(
                        page.only<ProductQuestions>().let {
                            it.copy(
                                questions = it.questions + ASKED,
                                more = null,
                                moreLabel = null,
                            )
                        },
                    )
                },
            )
            storefront(page)
            onNodeWithTag(MORE_TAG).performScrollTo().performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText(ASKED.question)).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag(MORE_TAG).assertDoesNotExist()
            assertEquals(listOf("/ui$PRODUCT", MORE_QUESTIONS), requests.toList())
        }

    /** The viewer's own review carries no vote: «Your review» is words, the others' «Helpful» a control. */
    @Test
    fun `the viewer's own review reads your review and presses nothing`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val page = decode("product_reviews.json") as ColumnComponent
            storefront(
                page.copy(
                    children =
                        page.children.map { node ->
                            if (node is ProductReviews) {
                                node.copy(
                                    reviews =
                                        listOf(
                                            node.reviews[0].copy(
                                                helpfulLabel = "Your review",
                                                helpfulAction = null,
                                                helpfulCommand = null,
                                            ),
                                        ) + node.reviews.drop(1),
                                )
                            } else {
                                node
                            }
                        },
                ),
            )
            waitForIdle()
            val helpful = onAllNodesWithTag(HELPFUL_TAG)
            helpful[0].assertHasNoClickAction()
            onNodeWithText("Your review").assertExists()
            helpful[1].assertHasClickAction()
        }

    private inline fun <reified T : KompotComponent> KompotComponent.only(): T =
        (this as ColumnComponent).children.filterIsInstance<T>().single()

    private fun details(
        page: KompotComponent,
        change: (ProductDetails) -> ProductDetails,
    ): KompotComponent {
        page as ColumnComponent
        return page.copy(children = page.children.map { if (it is ProductDetails) change(it) else it })
    }

    private fun moreReviews(page: KompotComponent): KompotComponent {
        page as ColumnComponent
        return page.copy(
            children =
                page.children.map {
                    if (it is ProductReviews) {
                        it.copy(
                            moreLabel = "Show more reviews",
                            more = LoadAction(MORE_REVIEWS),
                        )
                    } else {
                        it
                    }
                },
        )
    }

    private fun moreQuestions(page: KompotComponent): KompotComponent {
        page as ColumnComponent
        return page.copy(
            children =
                page.children.map {
                    if (it is ProductQuestions) {
                        it.copy(
                            moreLabel = "Show more questions",
                            more = LoadAction(MORE_QUESTIONS),
                        )
                    } else {
                        it
                    }
                },
        )
    }

    private companion object {
        const val WIDTH = 1440
        const val HEIGHT = 820
        const val PARTS = "/ui/parts"
        const val PRODUCT = "/p/p-sonora-sh-718n"
        const val REVIEWS = "/p/p-sonora-sh-718n?tab=reviews"
        const val SPECIFICATIONS = "/p/p-sonora-sh-718n?tab=specifications"
        const val BRAND = "/c/electronics/audio/headphones?brand=Sonora"
        const val MORE_REVIEWS = "$PARTS/p/p-sonora-sh-718n?tab=reviews&shown=20"
        const val MORE_QUESTIONS = "$PARTS/p/p-sonora-sh-718n?tab=questions&shown=20"
        const val NEXT = "The next page"
        const val ORIGIN = "https://haul.test"

        val NEXT_PAGE: KompotComponent =
            ColumnComponent(id = "page", children = listOf(TextComponent(id = "next", text = NEXT)))

        val LATER =
            Review(
                author = "Lee P.",
                initial = "L",
                tone = "#E6E4FF",
                meta = "Sep 2",
                score = "4.0",
                title = "The eleventh review",
                text = "Drawn once «Show more reviews» has loaded it.",
            )

        val ASKED = Question(question = "Is this the eleventh question?", asked = "Asked Sep 2")
    }
}
