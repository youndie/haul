package io.github.youndie.haul

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.feature.order.ReturnFormView
import io.github.youndie.haul.feature.product.QuestionFormView
import io.github.youndie.haul.feature.product.ReviewFormView
import io.github.youndie.haul.feature.returns.ReturnEntry
import io.github.youndie.haul.feature.reviews.QuestionEntry
import io.github.youndie.haul.feature.reviews.ReviewEntry
import io.github.youndie.haul.feature.search.SearchSuggestOverlay
import io.github.youndie.haul.registry.haulJson
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.LocalHaulNow
import io.github.youndie.haul.ui.LocalPhotoLoader
import io.github.youndie.haul.ui.OrderBody
import io.github.youndie.haul.ui.PhotoLoader
import io.github.youndie.haul.ui.ProductQuestions
import io.github.youndie.haul.ui.ProductReviews
import io.github.youndie.haul.ui.QuestionForm
import io.github.youndie.haul.ui.ReturnForm
import io.github.youndie.haul.ui.ReviewForm
import io.github.youndie.haul.ui.SearchSuggestPanel
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.PresentAction
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-108: a cart line, checkout's «Your order», an order's line and the suggest panel's «Top products» drew a
 * blank tile where a card draws the product's stored photo. Each of them draws the photo the tree names now, over
 * the tile, through the same loader the cards use. The bodies are the fixtures' with a photo's address put on each
 * of those lines; a loader that marks every photo it is asked for counts them.
 */
@OptIn(ExperimentalTestApi::class)
class LinePhotosTest {
    @Test
    fun `cart lines draw their photos`() = photos("cart_content.json", kind = "haul_cart_line")

    @Test
    fun `checkout's lines draw their photos`() = photos("checkout_content.json", kind = "summary item")

    @Test
    fun `an order's lines draw their photos`() = photos("order_delivered.json", kind = "order item")

    @Test
    fun `the suggest panel's top products draw their photos`() = photos("search_suggest.json", kind = "suggestion")

    /** B-136: the return dialog's lines drew the bare tone tile where the same lines on the order draw the photo. */
    @Test
    fun `the return dialog's lines draw their photos`() =
        runDesktopComposeUiTest(1_440, 3_000) {
            val page = decode("order_delivered.json") as ColumnComponent
            val body = page.children.filterIsInstance<OrderBody>().single()
            val form = (body.summary.returnAction as PresentAction).content as ReturnForm
            val photographed = form.copy(lines = form.lines.map { it.copy(image = "/images/products/p/1.png") })
            assertTrue(form.lines.isNotEmpty(), "order_delivered.json offers no line to return")
            setContent {
                HaulTheme(FixtureFonts.fonts, compact = false) {
                    CompositionLocalProvider(LocalPhotoLoader provides marking, LocalHaulNow provides CANVAS_NOW) {
                        ReturnFormView(photographed, ReturnEntry())
                    }
                }
            }
            waitForIdle()
            assertEquals(
                form.lines.size,
                onAllNodes(hasTestTag(PHOTO), useUnmergedTree = true).fetchSemanticsNodes().size,
                "the return dialog's photos drawn",
            )
        }

    /**
     * B-149: the account's order cards and its history drew their lines as plain tones — the wire named no picture.
     * The bodies are the server's, which name one per line now: every address in them is drawn, the Saved list's
     * cards' with them.
     */
    @Test
    fun `the account's order cards and history rows draw their pictures`() {
        drawsEvery("account_content.json")
        drawsEvery("account_orders.json")
    }

    /** B-149: the review and question dialogs drew their product as a plain tone. */
    @Test
    fun `the review and question dialogs draw their product's picture`() =
        runDesktopComposeUiTest(1_440, 1_200) {
            val reviews = decode("product_reviews.json").children().filterIsInstance<ProductReviews>().single()
            val questions = decode("product_questions.json").children().filterIsInstance<ProductQuestions>().single()
            val review = (reviews.action as PresentAction).content as ReviewForm
            val question = (questions.action as PresentAction).content as QuestionForm
            assertTrue(review.product.image != null && question.product.image != null, "a dialog names no picture")
            setContent {
                HaulTheme(FixtureFonts.fonts, compact = false) {
                    CompositionLocalProvider(LocalPhotoLoader provides marking, LocalHaulNow provides CANVAS_NOW) {
                        Column {
                            ReviewFormView(review, ReviewEntry())
                            QuestionFormView(question, QuestionEntry())
                        }
                    }
                }
            }
            waitForIdle()
            assertEquals(2, onAllNodes(hasTestTag(PHOTO), useUnmergedTree = true).fetchSemanticsNodes().size)
        }

    /** Every address [body] names — an `image`, or an entry of `images` — is drawn through the loader. */
    private fun drawsEvery(body: String) =
        runDesktopComposeUiTest(1_440, 3_000) {
            val named = addresses(haulJson.parseToJsonElement(read(body)))
            assertTrue(named > 0, "$body names no picture")
            setContent {
                HaulTheme(FixtureFonts.fonts, compact = false) {
                    CompositionLocalProvider(LocalPhotoLoader provides marking, LocalHaulNow provides CANVAS_NOW) {
                        Page { Body(body) }
                    }
                }
            }
            waitForIdle()
            assertEquals(
                named,
                onAllNodes(hasTestTag(PHOTO), useUnmergedTree = true).fetchSemanticsNodes().size,
                "$body: the pictures drawn",
            )
        }

    /** How many pictures [element] names: each `image`, and each non-null entry of an `images`. */
    private fun addresses(element: JsonElement): Int =
        when (element) {
            is JsonArray -> {
                element.sumOf { addresses(it) }
            }

            is JsonObject -> {
                element.entries.sumOf { (key, value) ->
                    when {
                        key == "image" && value is JsonPrimitive && value.isString -> 1
                        key == "images" && value is JsonArray -> value.count { it is JsonPrimitive && it.isString }
                        else -> addresses(value)
                    }
                }
            }

            else -> {
                0
            }
        }

    private fun KompotComponent.children(): List<KompotComponent> = (this as ColumnComponent).children

    private fun photos(
        body: String,
        kind: String,
    ) = runDesktopComposeUiTest(1_440, 3_000) {
        var lines = 0
        val tree = withPhotos(haulJson.parseToJsonElement(read(body))) { lines += 1 }
        assertTrue(lines > 0, "$body has no $kind to put a photo on")
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                CompositionLocalProvider(LocalPhotoLoader provides marking, LocalHaulNow provides CANVAS_NOW) {
                    Page {
                        // The panel is drawn over the page it opens on, as the header draws it; anything else is a page.
                        when (
                            val component =
                                haulJson.decodeFromJsonElement(
                                    PolymorphicSerializer(KompotComponent::class),
                                    tree,
                                )
                        ) {
                            // The results under it name their cards' pictures (B-78), which are not this test's lines.
                            is SearchSuggestPanel -> {
                                SearchSuggestOverlay(component) {
                                    CompositionLocalProvider(
                                        LocalPhotoLoader provides unloaded,
                                    ) { Body("search_results.json") }
                                }
                            }

                            else -> {
                                Render(component)
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        assertEquals(
            lines,
            onAllNodes(hasTestTag(PHOTO), useUnmergedTree = true).fetchSemanticsNodes().size,
            "$body: the photos drawn",
        )
    }

    /**
     * [element] with a photo's address on every line a tile is drawn for — a cart line, a checkout or an order
     * item, a suggested product: an object with a `tone` that is neither a card nor a line of the return dialog;
     * [counted] once each.
     */
    private fun withPhotos(
        element: JsonElement,
        counted: () -> Unit,
    ): JsonElement =
        when (element) {
            is JsonArray -> {
                JsonArray(element.map { withPhotos(it, counted) })
            }

            is JsonObject -> {
                val inner = JsonObject(element.mapValues { (_, value) -> withPhotos(value, counted) })
                val line =
                    "tone" in inner &&
                        inner["type"] != JsonPrimitive("haul_product_card") &&
                        "refundCents" !in inner &&
                        ("skuId" in inner || "details" in inner || "options" in inner || "productId" in inner)
                if (line) {
                    counted()
                    JsonObject(inner + ("image" to JsonPrimitive("/images/products/p/1.png")))
                } else {
                    inner
                }
            }

            else -> {
                element
            }
        }

    private companion object {
        const val PHOTO = "line-photo"

        /** A loader that loads nothing: every tile stays its placeholder. */
        val unloaded = PhotoLoader { _, modifier, placeholder -> Box(modifier) { placeholder() } }

        /** A loader that draws a marked box for every photo it is asked for, so the test counts them. */
        val marking =
            PhotoLoader { _, modifier, _ ->
                Box(modifier) { Box(Modifier.matchParentSize().testTag(PHOTO)) }
            }
    }
}
