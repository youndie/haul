package io.github.youndie.haul.feature.product

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.decode
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.ProductReviews
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.commands.LoadAction
import io.github.youndie.kompot.commands.UpdateHistory
import io.github.youndie.kompot.commands.kompotUpdate
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.RefreshAction
import kotlinx.serialization.PolymorphicSerializer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-86 in the client: the two controls of the reviews tab that pressed nothing before. A bar of the histogram lists
 * the reviews of its stars in place — the tab's `load`, its node updated and the address pushed — and pressed again,
 * or «All», lists every review; a bar the server gives no action presses nothing. A review's photo opens a viewer over
 * the page in the page's own layer, one photo at a time, named for a reader of the page, closed by «×», Escape or a
 * press outside it. The page is the server's body for Product_Reviews (`resources/bodies/product_reviews.json`), whose
 * first review, Daniel's, has two photos.
 */
@OptIn(ExperimentalTestApi::class)
class ReviewControlsTest {
    private val requests = CopyOnWriteArrayList<String>()
    private val pages = mutableMapOf<String, String>()
    private val history = FakeHistory(REVIEWS)

    private fun serve(
        path: String,
        action: KompotAction,
    ) {
        pages[path] = haulWireJson.encodeToString(PolymorphicSerializer(KompotAction::class), action)
    }

    private fun ComposeUiTest.storefront(
        page: KompotComponent = decode("product_reviews.json"),
        compact: Boolean = false,
    ) {
        pages["/ui${history.entries.single()}"] =
            haulWireJson.encodeToString(PolymorphicSerializer(KompotComponent::class), page)
        val transport =
            HaulTransport { path ->
                requests += path
                HaulResponse(200, pages[path] ?: error("nothing answers $path"))
            }
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = compact) {
                Storefront(transport, history, signIn = {}, treeCommands = { RefreshAction })
            }
        }
        waitUntil(timeoutMillis = 5_000) { requests.isNotEmpty() }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Write a review")).fetchSemanticsNodes().isNotEmpty() }
    }

    /** The bar of four stars lists the four-star reviews in place, says so, and is drawn pressed. */
    @Test
    fun `a bar lists the reviews of its stars in place`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val page = decode("product_reviews.json")
            serve(FOURS, kompotUpdate(FOURS.removePrefix(PARTS), UpdateHistory.PUSH) { addComponent(fours(page)) })
            storefront(page)
            onNodeWithTag(REVIEW_FILTER_TAG).assertDoesNotExist()
            onNodeWithTag(histogramTag(4))
                .performScrollTo()
                .assertContentDescriptionEquals("4 stars, 12% of reviews")
                .assertIsNotSelected()
                .performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(REVIEW_FILTER_TAG).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag(REVIEW_FILTER_TAG).assertTextEquals("Showing 4-star reviews")
            onNodeWithTag(histogramTag(4)).assertIsSelected()
            onNodeWithTag(histogramTag(5)).assertIsNotSelected()
            onAllNodes(hasText("The quietest flight I've had")).assertCountEquals(0)
            assertEquals(listOf("/ui$REVIEWS", FOURS), requests.toList(), "the page was loaded for a bar")
            assertEquals(listOf(REVIEWS, FOURS.removePrefix(PARTS)), history.entries)
        }

    /** On the filtered tab «All» and the pressed bar each list every review again, in place. */
    @Test
    fun `all and the pressed bar list every review again`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val every = decode("product_reviews.json")
            history.entries[0] = FOURS.removePrefix(PARTS)
            serve(ALL, kompotUpdate(REVIEWS, UpdateHistory.PUSH) { addComponent(every.only<ProductReviews>()) })
            storefront(filtered(every))
            onNodeWithTag(ALL_REVIEWS_TAG).performScrollTo().assertHasClickAction().performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(REVIEW_FILTER_TAG).fetchSemanticsNodes().isEmpty() }
            onNodeWithTag(histogramTag(4)).assertIsNotSelected()
            assertEquals(listOf(FOURS.removePrefix(PARTS), REVIEWS), history.entries)
            assertEquals(ALL, requests.last())
        }

    @Test
    fun `the pressed bar lists every review again`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val every = decode("product_reviews.json")
            history.entries[0] = FOURS.removePrefix(PARTS)
            serve(ALL, kompotUpdate(REVIEWS, UpdateHistory.PUSH) { addComponent(every.only<ProductReviews>()) })
            storefront(filtered(every))
            onNodeWithTag(histogramTag(4)).performScrollTo().assertIsSelected().performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(REVIEW_FILTER_TAG).fetchSemanticsNodes().isEmpty() }
            assertEquals(ALL, requests.last())
            assertEquals(listOf(FOURS.removePrefix(PARTS), REVIEWS), history.entries)
        }

    /** A bar the server sends no action for — one nobody gave — is not a press. */
    @Test
    fun `a bar without an action presses nothing`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val page = decode("product_reviews.json")
            storefront(
                page.replacing(
                    page.only<ProductReviews>().let { reviews ->
                        reviews.copy(
                            histogram = reviews.histogram.map { if (it.stars == 2) it.copy(action = null) else it },
                        )
                    },
                ),
            )
            onNodeWithTag(histogramTag(2)).assertHasNoClickAction()
            onNodeWithTag(histogramTag(3)).assertHasClickAction()
        }

    /**
     * A photo of Daniel's review opens the viewer at that photo, named, in the page's own layer (one root: a `Dialog`
     * is a second, which Compose 1.12's browser mirror keeps after it closes, B-126); «›» and «‹» step through the
     * review's two photos, the arrows stopping at each end; «×» closes it, and nothing was loaded.
     */
    @Test
    fun `a review's photo opens its viewer which steps through its photos and closes by the cross`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            storefront()
            onNodeWithContentDescription("Open photo 2 of 2 by Daniel R.").performScrollTo().performClick()
            onNodeWithTag(PHOTO_VIEWER_TAG).assertExists()
            onAllNodes(isRoot()).assertCountEquals(1)
            onNodeWithTag(PHOTO_COUNTER_TAG).assertTextEquals("Photo 2 of 2")
            onNodeWithContentDescription("Photo 2 of 2 by Daniel R.").assertExists()
            onNodeWithTag(PHOTO_NEXT_TAG).assertContentDescriptionEquals("Next photo").assertHasNoClickAction()
            onNodeWithTag(PHOTO_PREVIOUS_TAG).assertContentDescriptionEquals("Previous photo").performClick()
            onNodeWithTag(PHOTO_COUNTER_TAG).assertTextEquals("Photo 1 of 2")
            onNodeWithTag(PHOTO_PREVIOUS_TAG).assertHasNoClickAction()
            onNodeWithTag(PHOTO_NEXT_TAG).performClick()
            onNodeWithTag(PHOTO_COUNTER_TAG).assertTextEquals("Photo 2 of 2")
            onNodeWithTag(PHOTO_CLOSE_TAG).assertContentDescriptionEquals("Close photos").performClick()
            onNodeWithTag(PHOTO_VIEWER_TAG).assertDoesNotExist()
            assertEquals(listOf("/ui$REVIEWS"), requests.toList(), "a photo loaded a page")
            assertEquals(listOf(REVIEWS), history.entries)
        }

    /** Escape closes the viewer, and the arrow keys step through the photos while it is open. */
    @Test
    fun `escape closes the photo viewer and the arrow keys step through it`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            storefront()
            onNodeWithContentDescription("Open photo 1 of 2 by Daniel R.").performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag(PHOTO_VIEWER_TAG).performKeyInput { pressKey(Key.DirectionRight) }
            onNodeWithTag(PHOTO_COUNTER_TAG).assertTextEquals("Photo 2 of 2")
            onNodeWithTag(PHOTO_VIEWER_TAG).performKeyInput { pressKey(Key.DirectionLeft) }
            onNodeWithTag(PHOTO_COUNTER_TAG).assertTextEquals("Photo 1 of 2")
            onNodeWithTag(PHOTO_VIEWER_TAG).performKeyInput { pressKey(Key.Escape) }
            waitForIdle()
            onNodeWithTag(PHOTO_VIEWER_TAG).assertDoesNotExist()
        }

    /**
     * B-144, in the order TC-PDP-19 walks it: a mouse press on «›» takes the key focus to the arrow, and with two
     * photos the arrow it lands on is disabled at once. The viewer keeps the keys all the same: ArrowLeft steps back
     * and Escape closes it, both sent to the window — wherever the focus is — rather than to a node of the viewer.
     */
    @Test
    fun `after a press on an arrow the arrow keys and escape still work`() = keysAfterAPress(WIDTH, compact = false)

    @Test
    fun `on a phone after a press on an arrow the arrow keys and escape still work`() =
        keysAfterAPress(PHONE, compact = true)

    private fun keysAfterAPress(
        width: Int,
        compact: Boolean,
    ) = runDesktopComposeUiTest(width, HEIGHT) {
        storefront(compact = compact)
        onNodeWithContentDescription("Open photo 1 of 2 by Daniel R.").performScrollTo().performMouseInput { click() }
        onNodeWithTag(PHOTO_COUNTER_TAG).assertTextEquals("Photo 1 of 2")
        onNodeWithTag(PHOTO_NEXT_TAG).performMouseInput { click() }
        onNodeWithTag(PHOTO_COUNTER_TAG).assertTextEquals("Photo 2 of 2")
        onNodeWithTag(PHOTO_NEXT_TAG).assertHasNoClickAction()
        onAllNodes(isRoot()).onFirst().performKeyInput { pressKey(Key.DirectionLeft) }
        onNodeWithTag(PHOTO_COUNTER_TAG).assertTextEquals("Photo 1 of 2")
        onAllNodes(isRoot()).onFirst().performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        onNodeWithTag(PHOTO_VIEWER_TAG).assertDoesNotExist()
    }

    /**
     * A press outside the photo's card closes the viewer and reaches nothing under it — Aisha's «Helpful», under the
     * press, is not voted (a vote would load the page again) — and a press on the card keeps it open.
     */
    @Test
    fun `a press outside the photo closes the viewer and one on it does not`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            storefront()
            onNodeWithContentDescription("Open photo 1 of 2 by Daniel R.").performScrollTo()
            val helpful = onAllNodesWithTag(HELPFUL_TAG)[1].fetchSemanticsNode().boundsInRoot.center
            assertTrue(helpful.y in 0f..HEIGHT.toFloat(), "Aisha's «Helpful» is in the window")
            onNodeWithContentDescription("Open photo 1 of 2 by Daniel R.").performClick()
            onNodeWithTag(PHOTO_VIEWER_TAG).performTouchInput { click(center) }
            onNodeWithTag(PHOTO_VIEWER_TAG).assertExists()
            onAllNodes(isRoot()).onFirst().performTouchInput { click(helpful) }
            waitForIdle()
            onNodeWithTag(PHOTO_VIEWER_TAG).assertDoesNotExist()
            assertEquals(listOf("/ui$REVIEWS"), requests.toList(), "a press outside the viewer reached the page")
        }

    /** On a phone the viewer takes the page's width, and a press above it closes it. */
    @Test
    fun `on a phone a press outside the photo closes the viewer`() =
        runDesktopComposeUiTest(PHONE, HEIGHT) {
            storefront(compact = true)
            onNodeWithContentDescription("Open photo 1 of 2 by Daniel R.").performScrollTo().performClick()
            onNodeWithTag(PHOTO_VIEWER_TAG).assertExists()
            onAllNodes(isRoot()).onFirst().performTouchInput { click(Offset(centerX, 8f)) }
            waitForIdle()
            onNodeWithTag(PHOTO_VIEWER_TAG).assertDoesNotExist()
            assertEquals(listOf(REVIEWS), history.entries)
        }

    private inline fun <reified T : KompotComponent> KompotComponent.only(): T =
        (this as ColumnComponent).children.filterIsInstance<T>().single()

    private fun KompotComponent.replacing(reviews: ProductReviews): KompotComponent {
        this as ColumnComponent
        return copy(children = children.map { if (it is ProductReviews) reviews else it })
    }

    /** The tab listing the four-star reviews, as the server answers a press on that bar. */
    private fun fours(page: KompotComponent): ProductReviews =
        page.only<ProductReviews>().let { every ->
            every.copy(
                reviews = every.reviews.filter { it.score == "4.0" },
                histogram =
                    every.histogram.map {
                        if (it.stars == 4) it.copy(selected = true, action = LoadAction(ALL)) else it
                    },
                filter = "Showing 4-star reviews",
                allLabel = "All",
                all = LoadAction(ALL),
            )
        }

    private fun filtered(every: KompotComponent): KompotComponent = every.replacing(fours(every))

    private companion object {
        const val WIDTH = 1440
        const val PHONE = 390
        const val HEIGHT = 900
        const val PARTS = "/ui/parts"
        const val REVIEWS = "/p/p-sony-wh-1000xm6?tab=reviews"
        const val ALL = "$PARTS$REVIEWS"
        const val FOURS = "$PARTS$REVIEWS&stars=4"
    }
}
