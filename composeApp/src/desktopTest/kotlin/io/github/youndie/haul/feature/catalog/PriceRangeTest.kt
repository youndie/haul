package io.github.youndie.haul.feature.catalog

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.AppliedFilters
import io.github.youndie.haul.ui.Chip
import io.github.youndie.haul.ui.Facet
import io.github.youndie.haul.ui.FacetPanel
import io.github.youndie.haul.ui.FacetRange
import io.github.youndie.haul.ui.FilteredResults
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.commands.LoadAction
import io.github.youndie.kompot.commands.UpdateHistory
import io.github.youndie.kompot.commands.kompotUpdate
import io.github.youndie.kompot.standard.ColumnComponent
import kotlinx.serialization.PolymorphicSerializer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-69: the price facet's fields could not be edited and its thumbs could not be dragged. A bound typed and
 * confirmed — Enter, «Done», leaving the field — or a thumb released loads the range in place: the `load` its
 * [FacetRange] makes of the two bounds, answered with an `update` of the results, on a wide page and in the
 * phone's filter sheet alike. A fake transport serves the page and the parts each range loads.
 */
@OptIn(ExperimentalTestApi::class)
class PriceRangeTest {
    private val requests = CopyOnWriteArrayList<String>()
    private val history = FakeHistory(BY_OSTRA)
    private val parts = mutableMapOf<String, KompotAction>()

    private val transport =
        HaulTransport { path ->
            requests += path
            val action =
                parts[path]?.let { haulWireJson.encodeToString(PolymorphicSerializer(KompotAction::class), it) }
            val body =
                action ?: haulWireJson.encodeToString(
                    PolymorphicSerializer(KompotComponent::class),
                    if (path == "/ui$BY_OSTRA") priced() else error("nothing answers $path"),
                )
            HaulResponse(200, body)
        }

    /** [address] — the mugs by Ostra with a range — answered as its `load` endpoint does: the results it shows. */
    private fun ranged(
        address: String,
        low: Int?,
        high: Int?,
    ) {
        val results = (priced(low, high) as ColumnComponent).children.single { it.id == "results" }
        parts[PARTS + address] = kompotUpdate(address, UpdateHistory.PUSH) { addComponent(results) }
    }

    private fun ComposeUiTest.storefront(compact: Boolean = false) =
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = compact) {
                Storefront(transport, history, signIn = {})
            }
        }

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.loaded(vararg addresses: String) {
        waitUntil(timeoutMillis = 5_000) { requests.size > addresses.size }
        assertEquals(listOf("/ui$BY_OSTRA") + addresses.map { PARTS + it }, requests.toList())
        assertEquals(listOf(BY_OSTRA) + addresses, history.entries)
    }

    @Test
    fun `a lower bound typed and entered loads the range in place`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            ranged(FROM_20, 20, null)
            storefront()
            waitForText(OSTRA_MUG)
            val from = onNodeWithTag(PRICE_FROM_TAG)
            from.performClick()
            from.performTextInput("20")
            from.performKeyInput { pressKey(Key.Enter) }
            waitForText("From $20")
            loaded(FROM_20)
        }

    /**
     * B-76: in the browser a keystroke reaches the field at the next frame while Enter is handled at once, so
     * a bound typed just before it would be read without its last digits. Here the digits land after Enter is
     * down and before the next frame, as they do there (a whole key press would advance the test's clock).
     */
    @Test
    fun `a bound typed just before enter is the one applied`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            ranged(FROM_20, 20, null)
            storefront()
            waitForText(OSTRA_MUG)
            val from = onNodeWithTag(PRICE_FROM_TAG)
            from.performClick()
            mainClock.autoAdvance = false
            from.performKeyInput { keyDown(Key.Enter) }
            from.performTextInput("20")
            mainClock.autoAdvance = true
            from.performKeyInput { keyUp(Key.Enter) }
            waitForText("From $20")
            loaded(FROM_20)
        }

    @Test
    fun `leaving a price field applies what it holds`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            ranged(TO_60, null, 60)
            storefront()
            waitForText(OSTRA_MUG)
            val to = onNodeWithTag(PRICE_TO_TAG)
            to.performClick()
            to.performTextInput("60")
            onNodeWithTag(PRICE_FROM_TAG).performClick()
            waitForText("Up to $60")
            loaded(TO_60)
        }

    @Test
    fun `a field left as it was loads nothing`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            storefront()
            waitForText(OSTRA_MUG)
            onNodeWithTag(PRICE_FROM_TAG).performClick()
            onNodeWithTag(PRICE_TO_TAG).performClick()
            onNodeWithTag(PRICE_TO_TAG).performKeyInput { pressKey(Key.Enter) }
            waitForIdle()
            assertEquals(listOf("/ui$BY_OSTRA"), requests.toList())
        }

    /** The upper thumb, taken from the end of the track to its middle and released: $50 of a $100 track. */
    @Test
    fun `a thumb released loads the range it was dragged to`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            ranged(TO_50, null, 50)
            storefront()
            waitForText(OSTRA_MUG)
            onNodeWithTag(PRICE_SLIDER_TAG).performTouchInput {
                down(Offset(width - 1f, centerY))
                moveTo(Offset(width * 0.75f, centerY))
                moveTo(center)
                up()
            }
            waitForText("Up to $50")
            loaded(TO_50)
        }

    @Test
    fun `the filter sheet applies a typed range and stays open over its results`() =
        runDesktopComposeUiTest(PHONE, HEIGHT) {
            ranged(FROM_20, 20, null)
            storefront(compact = true)
            waitForText(OSTRA_MUG)
            onNodeWithText("Filters").performClick()
            waitUntil(timeoutMillis = 5_000) {
                onAllNodes(hasContentDescription(CLOSE_FILTERS)).fetchSemanticsNodes().isNotEmpty()
            }
            val from = onNodeWithTag(PRICE_FROM_TAG)
            from.performClick()
            from.performTextInput("20")
            from.performImeAction()
            waitForText("2 applied")
            onNodeWithContentDescription(CLOSE_FILTERS).assertExists()
            loaded(FROM_20)
        }

    @Test
    fun `the applied range is a chip that removes it`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            ranged(FROM_20, 20, null)
            ranged(BY_OSTRA, null, null)
            storefront()
            waitForText(OSTRA_MUG)
            val from = onNodeWithTag(PRICE_FROM_TAG)
            from.performClick()
            from.performTextInput("20")
            from.performKeyInput { pressKey(Key.Enter) }
            waitForText("From $20")
            onNodeWithText("From $20").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("From $20")).fetchSemanticsNodes().isEmpty() }
            loaded(FROM_20, BY_OSTRA)
        }

    /** Completing the template: a bound left empty is left out, and bounds the wrong way round are swapped. */
    @Test
    fun `a range makes its address from the bounds that are set`() {
        val range = FacetRange(top = 100, template = TEMPLATE)
        assertEquals(LoadAction("$PARTS$BY_OSTRA&price_min=20&price_max=60"), range.applying(20, 60))
        assertEquals(LoadAction("$PARTS$BY_OSTRA&price_min=20&price_max=60"), range.applying(60, 20))
        assertEquals(LoadAction("$PARTS$BY_OSTRA&price_max=60"), range.applying(null, 60))
        assertEquals(LoadAction("$PARTS$BY_OSTRA"), range.applying(null, null))
        assertEquals(
            LoadAction("$PARTS/c/mugs?price_min=5&sort=rating"),
            FacetRange(top = 100, template = "$PARTS/c/mugs?price_min={min}&price_max={max}&sort=rating")
                .applying(5, null),
        )
        assertEquals(
            LoadAction("$PARTS/c/mugs"),
            FacetRange(top = 100, template = "$PARTS/c/mugs?price_min={min}&price_max={max}").applying(null, null),
        )
    }

    private companion object {
        const val WIDTH = 1440
        const val PHONE = 390
        const val HEIGHT = 1_600
        const val PARTS = "/ui/parts"
        const val BY_OSTRA = "/c/mugs?brand=Ostra"
        const val FROM_20 = "$BY_OSTRA&price_min=20"
        const val TO_60 = "$BY_OSTRA&price_max=60"
        const val TO_50 = "$BY_OSTRA&price_max=50"
        const val TEMPLATE = "$PARTS$BY_OSTRA&price_min={min}&price_max={max}"
        const val OSTRA_MUG = "Ostra Mug"

        /**
         * The mugs by Ostra with [low] to [high] of a $0 – $100 track applied: the price facet, its chip
         * removing the range when one is set, and one card.
         */
        fun priced(
            low: Int? = null,
            high: Int? = null,
        ): KompotComponent {
            val label =
                when {
                    low != null && high != null -> "$$low – $$high"
                    low != null -> "From $$low"
                    high != null -> "Up to $$high"
                    else -> null
                }
            val price =
                Facet(
                    "price",
                    "Price",
                    "range",
                    min = low?.let { "$$it" },
                    max = high?.let { "$$it" },
                    rangeStart = (low ?: 0) / 100f,
                    rangeEnd = (high ?: 100) / 100f,
                    range = FacetRange(low, high, top = 100, template = TEMPLATE),
                )
            return ColumnComponent(
                id = "page",
                children =
                    listOf(
                        HEADER,
                        FilteredResults(
                            id = "results",
                            facets = FacetPanel("facets", listOf(price)),
                            applied =
                                AppliedFilters(
                                    id = "applied",
                                    chips =
                                        listOfNotNull(
                                            Chip("Ostra", true),
                                            label?.let { Chip(it, true, LoadAction(PARTS + BY_OSTRA)) },
                                        ),
                                    clearLabel = "Clear all",
                                    sortLabel = "Popular",
                                    filterCount = if (label == null) 1 else 2,
                                ),
                            showLabel = "Show 12 items",
                            grid = ProductGrid("grid", listOf(MUG), columns = 4),
                        ),
                    ),
            )
        }

        val HEADER =
            HaulHeader(
                id = "header",
                deliverTo = "Brooklyn, NY 11211",
                deliveryPromise = "Free delivery over $35",
                customerName = "Maya",
                cartCount = 0,
                searchPlaceholder = "Search 2.4 million products",
                categories = emptyList(),
            )

        val MUG =
            ProductCard(
                id = "card-mug",
                productId = "p-ostra-mug",
                title = OSTRA_MUG,
                price = "$24",
                rating = "4.8",
                reviews = "420",
                delivery = "Tomorrow",
                tone = "#E3F5D8",
                label = "mug",
            )
    }
}
