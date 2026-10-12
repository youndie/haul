package io.github.youndie.haul.feature.catalog

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.CompletableDeferred
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

    /** While set, a part's answer waits for it, as one over the network takes frames to arrive. */
    private var answers: CompletableDeferred<Unit>? = null

    private val transport =
        HaulTransport { path ->
            requests += path
            if (path.startsWith(PARTS)) answers?.await()
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

    /**
     * B-118: at 1440 a bound typed into «to» and left with a click on blank page space stayed in the field, applied by
     * nothing; a browser's input lets go of the focus there, and so does the field now — and applies the bound once.
     */
    @Test
    fun `a click on blank page space leaves a price field and applies it`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            ranged(TO_60, null, 60)
            storefront()
            waitForText(OSTRA_MUG)
            val to = onNodeWithTag(PRICE_TO_TAG)
            to.performClick()
            to.performTextInput("60")
            onRoot().performMouseInput { click(Offset(BLANK_X, BLANK_Y)) }
            waitForText("Up to $60")
            to.assertIsNotFocused()
            loaded(TO_60)
        }

    /** The box around a bound's digits is the field's too: a press on it is no press outside the field. */
    @Test
    fun `a press on the box around a price field keeps it`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            storefront()
            waitForText(OSTRA_MUG)
            val to = onNodeWithTag(PRICE_TO_TAG)
            to.performClick()
            to.performTextInput("60")
            to.performMouseInput { click(Offset(-GAP, centerY)) }
            waitForIdle()
            to.assertIsFocused()
            assertEquals(listOf("/ui$BY_OSTRA"), requests.toList())
        }

    /**
     * B-118: a press from «from» into «to», a bound typed and Tab, all before «from»'s confirm had waited out its
     * frames (B-76), sent the same load twice, the first aborted by the second: both confirms read both fields. Here
     * the frames step one at a time, as quick keys run inside them in the browser: «from» is left, a frame later
     * «to» is, so the two loads would start a frame apart, as they did there.
     */
    @Test
    fun `tab out of a price field sends one load`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            ranged(TO_60, null, 60)
            storefront()
            waitForText(OSTRA_MUG)
            onNodeWithTag(PRICE_FROM_TAG).performClick()
            waitForIdle()
            val to = onNodeWithTag(PRICE_TO_TAG)
            mainClock.autoAdvance = false
            to.performClick()
            mainClock.advanceTimeByFrame()
            to.performTextInput("60")
            to.performKeyInput { keyDown(Key.Tab) }
            mainClock.advanceTimeByFrame()
            mainClock.autoAdvance = true
            onRoot().performKeyInput { keyUp(Key.Tab) }
            waitForText("Up to $60")
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            loaded(TO_60)
        }

    /**
     * B-133: a press on the word «from» or «to», left of a box's digits, focused nothing, and the keys typed after it
     * were lost. The word is the field's now: «from» pressed takes the bound typed next, and «to» pressed takes the
     * focus from it and applies it.
     */
    @Test
    fun `a press on a price box's word focuses its field`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            ranged(FROM_20, 20, null)
            storefront()
            waitForText(OSTRA_MUG)
            onNodeWithText("from").performClick()
            waitForIdle()
            val from = onNodeWithTag(PRICE_FROM_TAG)
            from.assertIsFocused()
            from.performTextInput("20")
            onNodeWithText("to").performClick()
            waitForIdle()
            onNodeWithTag(PRICE_TO_TAG).assertIsFocused()
            waitForText("From $20")
            loaded(FROM_20)
        }

    /**
     * B-133, after B-118's one confirm: «from» left for «to» and a press on blank page space within the frames
     * «from»'s confirm waits still sent the same load twice, the first aborted. The blank press waits frames of its
     * own before it takes the focus off «to», so «to»'s confirm came after «from»'s had sent, read the same two
     * bounds and sent them again.
     */
    @Test
    fun `leaving both price fields at once sends one load`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            ranged(FROM_20, 20, null)
            storefront()
            waitForText(OSTRA_MUG)
            val from = onNodeWithTag(PRICE_FROM_TAG)
            from.performClick()
            from.performTextInput("20")
            waitForIdle()
            answers = CompletableDeferred()
            mainClock.autoAdvance = false
            onNodeWithTag(PRICE_TO_TAG).performClick()
            onRoot().performMouseInput { click(Offset(BLANK_X, BLANK_Y)) }
            mainClock.advanceTimeBy(ANSWER_MILLIS)
            answers?.complete(Unit)
            mainClock.autoAdvance = true
            waitForText("From $20")
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            loaded(FROM_20)
        }

    /** The phone's sheet is the page its fields are on: a tap on its blank space leaves them as a click does at 1440. */
    @Test
    fun `a tap on the filter sheet's blank space applies a typed bound`() =
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
            onAllNodes(isRoot()).onLast().performTouchInput { click(Offset(PHONE / 2f, SHEET_BLANK_Y)) }
            waitForText("2 applied")
            onNodeWithContentDescription(CLOSE_FILTERS).assertExists()
            loaded(FROM_20)
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

    /**
     * B-90 (from B-69): a thumb at its end of the track is drawn half past the track, and only its inner half could
     * be grabbed. Each thumb taken by its outer half — the upper past the track's right end, then the lower past
     * its left — follows the pointer and applies where it is released.
     */
    @Test
    fun `a thumb at the end of the track is grabbed by its outer half`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            ranged(TO_50, null, 50)
            ranged(FROM_25_TO_50, 25, 50)
            storefront()
            waitForText(OSTRA_MUG)
            onNodeWithTag(PRICE_SLIDER_TAG).performTouchInput {
                down(Offset(width + OUTER_HALF.dp.toPx(), centerY))
                moveTo(Offset(width * 0.75f, centerY))
                moveTo(center)
                up()
            }
            waitForText("Up to $50")
            onNodeWithTag(PRICE_SLIDER_TAG).performTouchInput {
                down(Offset(-OUTER_HALF.dp.toPx(), centerY))
                moveTo(Offset(width * 0.1f, centerY))
                moveTo(Offset(width * 0.25f, centerY))
                up()
            }
            waitForText("$25 – $50")
            loaded(TO_50, FROM_25_TO_50)
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
        const val FROM_25_TO_50 = "$BY_OSTRA&price_min=25&price_max=50"

        /** How long a part's answer is held back: a few frames, as long as a load over the network takes. */
        const val ANSWER_MILLIS = 200L

        /** Blank page space at 1440: the window's bottom-right corner, below the page's one row of cards. */
        const val BLANK_X = WIDTH - 24f
        const val BLANK_Y = HEIGHT - 24f

        /** The filter sheet's blank space: under its one facet, above the button pinned at its bottom. */
        const val SHEET_BLANK_Y = 1_000f

        /** Into the gap between a price field's «to» and its digits: the box's, not the text's. */
        const val GAP = 3f

        /** Where the outer half of an end thumb is pressed: 7 of its 10 dp past the end of the track. */
        const val OUTER_HALF = 7
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
                searchPlaceholder = "Search products",
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
