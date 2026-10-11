package io.github.youndie.haul

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.feature.product.HELPFUL_TAG
import io.github.youndie.haul.feature.product.TABS_TAG
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.shell.TreeCommands
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.ProductReviews
import io.github.youndie.haul.ui.ProductTabs
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.commands.LoadAction
import io.github.youndie.kompot.commands.UpdateHistory
import io.github.youndie.kompot.commands.kompotUpdate
import io.github.youndie.kompot.encodeKompotComponent
import io.github.youndie.kompot.standard.ButtonComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.RefreshAction
import io.github.youndie.kompot.standard.TextComponent
import kotlinx.serialization.PolymorphicSerializer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-93: a `refresh` draws the tree it brings whatever way the shopper reached the page shown. A new address of
 * the screen loaded behind the page (B-62) — a product tab, a facet or a page of results that `navigate`s, a
 * scoped search, back within the screen — used to leave the refresh writing into the tree of the address
 * before, which nothing drew any more: the server answered with the new page and the shopper kept the old one
 * until a reload. A `load` answered in place (B-63) is covered too, and so is the arrival a refresh is (B-64):
 * what an update wrote over the page goes with it. A fake transport serves each address's trees in turn.
 */
@OptIn(ExperimentalTestApi::class)
class RefreshInPlaceTest {
    private val requests = CopyOnWriteArrayList<String>()
    private val answers = mutableMapOf<String, ArrayDeque<HaulResponse>>()

    private fun answer(
        path: String,
        vararg responses: HaulResponse,
    ) {
        answers[path] = ArrayDeque(responses.toList())
    }

    private val transport =
        HaulTransport { path ->
            requests += path
            val queue = answers[path] ?: error("nothing answers $path")
            if (queue.size > 1) queue.removeFirst() else queue.first()
        }

    /** «Helpful»'s command, answered as the server answers it: `refresh`. */
    private val commands = TreeCommands { RefreshAction }

    private fun ComposeUiTest.storefront(history: FakeHistory) =
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                Storefront(transport, history, signIn = {}, treeCommands = commands)
            }
        }

    private fun ComposeUiTest.exists(text: String) =
        onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.waitForText(text: String) = waitUntil(timeoutMillis = 5_000) { exists(text) }

    /**
     * The QA pass's walk (v0.3.0, signed in): Description, then Reviews in place, then «Helpful». The vote went,
     * the page was fetched again with the new count, and the screen kept the count from before the vote.
     */
    @Test
    fun `helpful after switching to the reviews tab in place draws the count the server answers`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val description = decode("product_description.json") as ColumnComponent
            val tabbed =
                description.copy(
                    children =
                        description.children.map { child ->
                            if (child is ProductTabs) {
                                child.copy(tabs = child.tabs.map { it.copy(action = NavigateAction(PRODUCT + it.key)) })
                            } else {
                                child
                            }
                        },
                )
            val reviews = decode("product_reviews.json") as ColumnComponent
            val voted =
                reviews.copy(
                    children =
                        reviews.children.map { child ->
                            if (child is ProductReviews) {
                                child.copy(
                                    reviews =
                                        child.reviews.map {
                                            if (it.helpful ==
                                                HELPFUL
                                            ) {
                                                it.copy(helpful = VOTED)
                                            } else {
                                                it
                                            }
                                        },
                                )
                            } else {
                                child
                            }
                        },
                )
            answer("/ui${PRODUCT}description", ok(tabbed))
            answer("/ui${PRODUCT}reviews", ok(reviews), ok(voted))
            val history = FakeHistory(PRODUCT + "description")
            storefront(history)
            waitForText("Description")

            onNode(hasText("Reviews", substring = true) and hasAnyAncestor(hasTestTag(TABS_TAG))).performClick()
            waitForText(HELPFUL)

            onAllNodesWithTag(HELPFUL_TAG)[1].performClick()
            waitUntil(timeoutMillis = 5_000) { requests.size == 3 }
            waitForText(VOTED)
            onNodeWithText(HELPFUL).assertDoesNotExist()
            assertEquals(
                listOf("/ui${PRODUCT}description", "/ui${PRODUCT}reviews", "/ui${PRODUCT}reviews"),
                requests.toList(),
            )
        }

    /** A facet that `navigate`s within the catalog — the way B-62 left it — then a refresh. */
    @Test
    fun `a refresh after a facet opened in place draws the tree it brings`() =
        refreshesAfterVisit(MUGS, "$MUGS?brand=Ostra")

    /** The next page of the results, opened in place, then a refresh. */
    @Test
    fun `a refresh after a page of results opened in place draws the tree it brings`() =
        refreshesAfterVisit(MUGS, "$MUGS?page=2")

    /** A search scoped to a category is the search screen at another address, then a refresh. */
    @Test
    fun `a refresh after a scoped search opened in place draws the tree it brings`() =
        refreshesAfterVisit("/search?q=mug", "/search?q=mug&category=kitchen")

    /** Back within one screen is a visit of the address before, loaded behind the page; then a refresh. */
    @Test
    fun `a refresh after back within the screen draws the tree it brings`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val to = "$MUGS?brand=Ostra"
            answer("/ui$MUGS", ok(probe(MUGS, 1, to)), ok(probe(MUGS, 2, to)), ok(probe(MUGS, 3, to)))
            answer("/ui$to", ok(probe(to, 1, MUGS)))
            val history = FakeHistory(MUGS)
            storefront(history)
            waitForText(drawn(MUGS, 1))
            onNodeWithText(GO).performClick()
            waitForText(drawn(to, 1))

            history.back()
            waitForText(drawn(MUGS, 2))
            onNodeWithText(REFRESH).performClick()
            waitUntil(timeoutMillis = 5_000) { requests.size == 4 }
            waitForText(drawn(MUGS, 3))
            assertEquals(listOf("/ui$MUGS", "/ui$to", "/ui$MUGS", "/ui$MUGS"), requests.toList())
        }

    /**
     * A tick answered in place (B-63): the address the update made is the one a refresh asks for, and the
     * tree it brings is drawn over what the update wrote.
     */
    @Test
    fun `a refresh after a load answered in place asks for the address it made and draws the tree`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val to = "$MUGS?brand=Ostra"
            answer("/ui$MUGS", ok(probe(MUGS, 1, LoadAction(PARTS + to))))
            answer(PARTS + to, ok(kompotUpdate(to, UpdateHistory.PUSH) { addComponent(state(to, 1)) }))
            answer("/ui$to", ok(probe(to, 2, LoadAction(PARTS + to))))
            val history = FakeHistory(MUGS)
            storefront(history)
            waitForText(drawn(MUGS, 1))
            onNodeWithText(GO).performClick()
            waitForText(drawn(to, 1))
            assertEquals(listOf(MUGS, to), history.entries)

            onNodeWithText(REFRESH).performClick()
            waitForText(drawn(to, 2))
            assertEquals(listOf("/ui$MUGS", PARTS + to, "/ui$to"), requests.toList())
        }

    /**
     * A refresh is an arrival (B-64) after an in-place visit as well: an update written over the page that the
     * visit brought goes with the refresh, even when the refresh brings that same page again.
     */
    @Test
    fun `a refresh after an in-place visit drops what an update wrote over the page`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val to = "$MUGS?brand=Ostra"
            val page = probe(to, 1, MUGS)
            val written = "written over the page"
            answer("/ui$MUGS", ok(probe(MUGS, 1, to)))
            answer("/ui$to", ok(page.withButton("Write", LoadAction(PARTS + "write"))))
            answer(PARTS + "write", ok(kompotUpdate { addComponent(TextComponent(id = STATE, text = written)) }))
            val history = FakeHistory(MUGS)
            storefront(history)
            waitForText(drawn(MUGS, 1))
            onNodeWithText(GO).performClick()
            waitForText(drawn(to, 1))
            onNodeWithText("Write").performClick()
            waitForText(written)

            onNodeWithText(REFRESH).performClick()
            waitUntil(timeoutMillis = 5_000) { requests.size == 4 }
            waitForText(drawn(to, 1))
            onNodeWithText(written).assertDoesNotExist()
        }

    /** [from], then a `navigate` to [to] on the same screen, then a refresh: the tree it brings is drawn. */
    private fun refreshesAfterVisit(
        from: String,
        to: String,
    ) = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        answer("/ui$from", ok(probe(from, 1, to)))
        answer("/ui$to", ok(probe(to, 1, from)), ok(probe(to, 2, from)))
        storefront(FakeHistory(from))
        waitForText(drawn(from, 1))

        onNodeWithText(GO).performClick()
        waitForText(drawn(to, 1))
        onNodeWithText(REFRESH).performClick()
        waitUntil(timeoutMillis = 5_000) { requests.size == 3 }
        waitForText(drawn(to, 2))
        assertEquals(listOf("/ui$from", "/ui$to", "/ui$to"), requests.toList())
    }

    private companion object {
        const val WIDTH = 1440
        const val HEIGHT = 1800
        const val PRODUCT = "/p/p-sony-wh-1000xm6?sku=p-sony-wh-1000xm6-0&tab="
        const val HELPFUL = "48 people found this helpful"
        const val VOTED = "49 people found this helpful"
        const val MUGS = "/c/mugs"
        const val PARTS = "/ui/parts"
        const val STATE = "state"
        const val GO = "Go"
        const val REFRESH = "Refresh"

        fun drawn(
            address: String,
            version: Int,
        ) = "$address, tree $version"

        /** What the page says it is: the address and which of its trees the server sent. */
        fun state(
            address: String,
            version: Int,
        ) = TextComponent(id = STATE, text = drawn(address, version))

        /** A page at [address], the server's [version] of it, whose «Go» opens [to] and «Refresh» draws it again. */
        fun probe(
            address: String,
            version: Int,
            to: String,
        ) = probe(address, version, NavigateAction(to))

        fun probe(
            address: String,
            version: Int,
            go: KompotAction,
        ): ColumnComponent =
            ColumnComponent(
                id = "page",
                children =
                    listOf(
                        state(address, version),
                        ButtonComponent(id = "go", text = GO, action = go),
                        ButtonComponent(id = "refresh", text = REFRESH, action = RefreshAction),
                    ),
            )

        fun ColumnComponent.withButton(
            text: String,
            action: KompotAction,
        ) = copy(children = children + ButtonComponent(id = text.lowercase(), text = text, action = action))

        fun ok(tree: KompotComponent) = HaulResponse(200, haulWireJson.encodeKompotComponent(tree))

        fun ok(action: KompotAction) =
            HaulResponse(200, haulWireJson.encodeToString(PolymorphicSerializer(KompotAction::class), action))
    }
}
