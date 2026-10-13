package io.github.youndie.haul

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.feature.home.PLUS_CLOSE_TAG
import io.github.youndie.haul.feature.product.CLOSE_TAG
import io.github.youndie.haul.feature.product.PHOTO_CLOSE_TAG
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.CARD_ADD_TAG
import io.github.youndie.haul.ui.CART_BUTTON_TAG
import io.github.youndie.haul.ui.HEADER_MENU_TAG
import io.github.youndie.haul.ui.HaulIcons
import io.github.youndie.haul.ui.Icon
import io.github.youndie.haul.ui.OPEN_MENU
import io.github.youndie.haul.ui.OVER_PAGE_TAG
import io.github.youndie.kompot.standard.RefreshAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every press and every field on the main screens and in the dialogs has a name (B-126, B-131). The browser's
 * accessible mirror of the page is Compose's merged semantics tree: a node that can be pressed is a `role="button"`
 * there, named by its content description, its text, or the text under it — the name a screen reader reads. A press
 * with none of them reads as «button». A field is named by its content description alone: what is typed in it is
 * its value, and a placeholder drawn in it is gone once something is.
 *
 * Each screen is drawn from its wire body at both widths by the storefront itself, at the address it is served at —
 * the shell's own presses (the search field's, the header's menu) are there only then — and every node with a
 * click action or a text to set in the merged tree is read the way the mirror's is. A dialog is opened as a shopper
 * opens it, by pressing what presents it. The header's cart button is read on its own as well: on a phone it draws
 * the count alone, which names it «3» and not as the cart.
 *
 * B-151: a box to tick or a switch says what it does and whether it is on in its name — the 1.12 mirror carries no
 * state — and has the role and the state for whatever reads them; a card's «+» is named by its product; an open
 * dialog or sheet leaves only itself in the tree, and no press holds a dialog's body.
 */
@OptIn(ExperimentalTestApi::class)
class NamedPressesTest {
    @Test
    fun `every press on the main screens has a name`() {
        val unnamed =
            SCREENS.flatMap { (address, body) ->
                listOf(false, true).flatMap { compact -> unnamedPresses(address, body, compact) }
            }
        assertEquals(emptyList(), unnamed, "presses and fields with no name, by screen and width")
    }

    @Test
    fun `every press and field in the dialogs has a name`() {
        val unnamed =
            DIALOGS.flatMap { dialog ->
                listOf(false, true).flatMap { compact -> unnamedPresses(dialog.address, dialog.body, compact, dialog) }
            }
        assertEquals(emptyList(), unnamed, "presses and fields with no name in a dialog, by dialog and width")
    }

    /**
     * The cart's boxes — one line unticked, so both states are read — «Select all» partly ticked, and checkout's
     * points switch: each named for what it does and whether it is on, with its state, and a button's role while the
     * mirror writes no state (a checkbox's would read as not checked, `MIRRORED_ROLE`).
     */
    @Test
    fun `boxes and switches say what they do and whether they are on`() {
        val said =
            listOf(false, true).flatMap { compact ->
                onScreen("/cart", oneUnticked(read("cart_content.json")), compact) { toggles() } +
                    onScreen("/checkout", read("checkout_content.json"), compact) { toggles() }
            }
        val expected =
            listOf("wide", "phone").flatMap { width ->
                listOf(
                    "/cart $width: Button On «Select Sony WH-1000XM6 Wireless Noise Cancelling Headphones, ticked»",
                    "/cart $width: Button Off «Select Linen Duvet Cover Set, Queen, not ticked»",
                    "/cart $width: Button On «Select Stoneware Mug, 12 oz, ticked»",
                    "/cart $width: Button Indeterminate «Select all, partly ticked»",
                    "/checkout $width: Button Off «Use 2,480 points (−\$24.80), off. 100 points = \$1 · all or nothing»",
                )
            }
        assertEquals(expected.sorted(), said.sorted())
    }

    /** Each card's «+» names the product of its card: «Add Sony WH-1000XM6 to cart», not six «Add to cart» in a row. */
    @Test
    fun `each card's plus names its product`() {
        val unnamed =
            listOf("/" to "home_content.json", "/c/electronics" to "catalog_content.json").flatMap { (address, body) ->
                // The products of the cards the body sends: a card is what has a title, a price and an `add`.
                val named = cardTitles(Json.parseToJsonElement(read(body))).map { "Add $it to cart" }.toSet()
                listOf(false, true).flatMap { compact ->
                    onScreen(address, read(body), compact) {
                        val pluses = onAllNodesWithTag(CARD_ADD_TAG).fetchSemanticsNodes().map { described(it) }
                        val few =
                            if (pluses.toSet().size <
                                2
                            ) {
                                listOf("${pluses.toSet()} on ${pluses.size} cards")
                            } else {
                                emptyList()
                            }
                        few + pluses.filterNot { it in named }.map { "«+» named «$it», not by a card's product" }
                    }
                }
            }
        assertEquals(emptyList(), unnamed, "cards' «+» not named by their product")
    }

    /**
     * With a dialog or a sheet open, nothing of the page under it is in the tree — no press, field or words — and no
     * press holds a dialog's body: the scrim was a press with the whole dialog in it, read as one button.
     */
    @Test
    fun `an open dialog or sheet leaves only itself in the tree`() {
        val exposed =
            (DIALOGS.flatMap { dialog -> listOf(false, true).map { dialog to it } } + SHEETS.map { it to true })
                .flatMap { (dialog, compact) ->
                    onScreen(dialog.address, read(dialog.body), compact, dialog) {
                        val under =
                            onAllNodes(
                                (hasClickAction() or hasSetTextAction() or hasWords()) and
                                    !hasTestTag(OVER_PAGE_TAG) and !hasAnyAncestor(hasTestTag(OVER_PAGE_TAG)),
                            ).fetchSemanticsNodes()
                        val holding =
                            onAllNodes(hasClickAction() and hasAnyDescendant(hasTestTag(dialog.close)))
                                .fetchSemanticsNodes()
                        under.map { "under it: «${name(it)}» ${describe(it)}" } +
                            holding.map { "a press holding the dialog: «${name(it).take(60)}…» ${describe(it)}" }
                    }
                }
        assertEquals(emptyList(), exposed, "the page under an open dialog or sheet, or a press holding a dialog")
    }

    /** The control: a press with nothing to read is reported, so the check is seen to be able to fail. */
    @Test
    fun `a press with no name is reported`() =
        runDesktopComposeUiTest(PHONE, 400) {
            setContent {
                Fixture(compact = true) {
                    Box(Modifier.clickable(onClick = {})) { Icon(HaulIcons.close, 18.dp, HaulColors.onSurface) }
                }
            }
            assertEquals(1, unnamed().size)
        }

    /** The control for fields: one with nothing to name it is reported, though something is typed in it. */
    @Test
    fun `a field with no name is reported`() =
        runDesktopComposeUiTest(PHONE, 400) {
            setContent { Fixture(compact = true) { BasicTextField("typed", {}) } }
            assertEquals(1, unnamed().size)
        }

    private fun unnamedPresses(
        address: String,
        body: String,
        compact: Boolean,
        dialog: Dialog? = null,
    ): List<String> = onScreen(address, read(body), compact, dialog) { unnamed() + unnamedCart() }

    /**
     * The storefront at [address] drawn from [body] at a width, [dialog] opened over it when there is one, and what
     * [check] finds there, each by where it was found.
     */
    private fun onScreen(
        address: String,
        body: String,
        compact: Boolean,
        dialog: Dialog? = null,
        check: ComposeUiTest.() -> List<String>,
    ): List<String> {
        val tree = withFacetActions(body)
        val transport = HaulTransport { HaulResponse(200, tree) }
        val history = FakeHistory(address)
        var found = emptyList<String>()
        runDesktopComposeUiTest(if (compact) PHONE else WIDE, 1_200) {
            setContent {
                HaulTheme(FixtureFonts.fonts, compact = compact) {
                    Storefront(
                        transport,
                        history,
                        signIn = {},
                        cartCommands = { RefreshAction },
                        commands = { _, _, _ -> HaulResponse(200, "{}") },
                        checkoutCommands = { RefreshAction },
                        treeCommands = { RefreshAction },
                    )
                }
            }
            waitUntil(timeoutMillis = 5_000) { onAllNodes(hasClickAction()).fetchSemanticsNodes().size > 5 }
            waitForIdle()
            if (dialog != null) {
                // The press itself, wherever the control is: below the window's fold a click would miss it.
                onAllNodes(hasText(dialog.opener) or hasContentDescription(dialog.opener))
                    .onFirst()
                    .performSemanticsAction(SemanticsActions.OnClick)
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(dialog.close).fetchSemanticsNodes().isNotEmpty() }
                waitForIdle()
            }
            val where = "${dialog?.name ?: address} ${if (compact) "phone" else "wide"}"
            found = check().map { "$where: $it" }
        }
        return found
    }

    /**
     * The body with an action on every facet option that has none: the fixtures were written before the options
     * carried their `load`s, and an option with nothing to follow is not a press at all.
     */
    private fun withFacetActions(body: String): String {
        fun walk(element: JsonElement): JsonElement =
            when (element) {
                is JsonObject -> {
                    val walked = JsonObject(element.mapValues { (_, value) -> walk(value) })
                    if ("selected" in walked && "label" in walked && "action" !in walked) {
                        JsonObject(walked + ("action" to Json.parseToJsonElement(NAVIGATE)))
                    } else {
                        walked
                    }
                }

                is JsonArray -> {
                    JsonArray(element.map(::walk))
                }

                else -> {
                    element
                }
            }
        return walk(Json.parseToJsonElement(body)).toString()
    }

    private fun ComposeUiTest.unnamed(): List<String> {
        val presses = onAllNodes(hasClickAction() and !hasSetTextAction()).fetchSemanticsNodes()
        val fields = onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        return presses.filter { name(it).isBlank() }.map { "press ${describe(it)}" } +
            fields.filter { described(it).isBlank() }.map { "field ${describe(it)}" }
    }

    /** Every node with a state to toggle: its role, its state and its name — its label, which the mirror reads. */
    private fun ComposeUiTest.toggles(): List<String> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).fetchSemanticsNodes().map {
            val role = it.config.getOrNull(SemanticsProperties.Role)
            "$role ${it.config[SemanticsProperties.ToggleableState]} «${described(it)}»"
        }

    /** The titles of the product cards in a body: what has a title, a price and an `add`. */
    private fun cardTitles(element: JsonElement): List<String> =
        when (element) {
            is JsonObject -> {
                val own =
                    (element["title"] as? JsonPrimitive)
                        ?.takeIf { "price" in element && "add" in element }
                        ?.content
                listOfNotNull(own) + element.values.flatMap(::cardTitles)
            }

            is JsonArray -> {
                element.flatMap(::cardTitles)
            }

            else -> {
                emptyList()
            }
        }

    /** Words drawn or a name: what the mirror would hold as a node. */
    private fun hasWords(): SemanticsMatcher =
        SemanticsMatcher("has words") { node ->
            val config = node.config
            config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.isNotBlank() } ||
                config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.isNotBlank() }
        }

    /** The cart's body with its second line unticked: «Select all» partly ticked. */
    private fun oneUnticked(body: String): String {
        var line = 0

        fun walk(element: JsonElement): JsonElement =
            when (element) {
                is JsonObject -> {
                    val walked = JsonObject(element.mapValues { (_, value) -> walk(value) })
                    when {
                        "skuId" in walked && "selected" in walked && line++ == 1 -> {
                            JsonObject(walked + ("selected" to JsonPrimitive(false)))
                        }

                        "allSelected" in walked -> {
                            val partly =
                                mapOf(
                                    "allSelected" to JsonPrimitive(false),
                                    "selectedCount" to JsonPrimitive(2),
                                )
                            JsonObject(walked + partly)
                        }

                        else -> {
                            walked
                        }
                    }
                }

                is JsonArray -> {
                    JsonArray(element.map(::walk))
                }

                else -> {
                    element
                }
            }
        return walk(Json.parseToJsonElement(body)).toString()
    }

    /** The header's cart button, when its name does not say it is the cart. */
    private fun ComposeUiTest.unnamedCart(): List<String> =
        onAllNodesWithTag(CART_BUTTON_TAG)
            .fetchSemanticsNodes()
            .filterNot { CART in name(it) }
            .map { "the cart button, named «${name(it)}», ${describe(it)}" }

    private fun describe(node: SemanticsNode): String {
        val tag = node.config.getOrNull(SemanticsProperties.TestTag)
        val bounds = node.boundsInRoot
        return "#${node.id} tag=$tag at (${bounds.left.toInt()}, ${bounds.top.toInt()}) " +
            "${bounds.width.toInt()}x${bounds.height.toInt()}"
    }

    /** A field's name: its content description, the label a reader hears before its value. */
    private fun described(node: SemanticsNode): String =
        node.config
            .getOrNull(SemanticsProperties.ContentDescription)
            ?.joinToString(" ")
            .orEmpty()
            .filterNot { it in INVISIBLE }
            .trim()

    /** The name the browser computes for the mirrored node: its label, else the text in it and under it. */
    private fun name(node: SemanticsNode): String {
        val own =
            listOfNotNull(
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
                node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text },
                node.config.getOrNull(SemanticsProperties.EditableText)?.text,
            ).joinToString(" ")
        return (own + " " + node.children.joinToString(" ") { name(it) }).filterNot { it in INVISIBLE }.trim()
    }

    /**
     * A dialog the storefront presents over [body] at [address] when [opener] is pressed; [close] is its «×» — for a
     * sheet, a node that is there while it is open.
     */
    private class Dialog(
        val name: String,
        val address: String,
        val body: String,
        val opener: String,
        val close: String,
    )

    private companion object {
        const val WIDE = 1_440
        const val PHONE = 390

        /** The word the header's cart button is named by, with its count when there is one. */
        const val CART = "Cart"

        const val NAVIGATE = """{"type":"navigate","deeplink":"/c/electronics"}"""

        /** Characters that draw nothing and read as nothing. */
        const val INVISIBLE = "​⁠ "

        /** The main screens at their addresses, as the server sends them: the header and footer come with each. */
        val SCREENS =
            listOf(
                "/" to "home_content.json",
                "/" to "home_guest.json",
                "/c/electronics" to "catalog_content.json",
                "/search?q=headphones" to "search_results.json",
                "/p/p-sony-wh-1000xm6" to "product_description.json",
                "/p/p-sony-wh-1000xm6?tab=reviews" to "product_reviews.json",
                "/p/p-sony-wh-1000xm6?tab=questions" to "product_questions.json",
                "/cart" to "cart_content.json",
                "/cart" to "cart_guest.json",
                "/checkout" to "checkout_content.json",
                "/account" to "account_content.json",
                "/account/orders" to "account_orders.json",
                "/account/saved" to "saved_content.json",
                "/account/saved?view=drops" to "saved_price_drops.json",
                "/account/saved" to "saved_empty.json",
                "/account/orders/HL-48302" to "order_in_transit.json",
                "/account/orders/HL-48302" to "order_delivered.json",
            )

        /**
         * Every dialog the storefront presents — the review's, the question's, the return's, the Plus trial's — and
         * the viewer of a review's photos (B-86).
         */
        val DIALOGS =
            listOf(
                Dialog(
                    "review",
                    "/p/p-sony-wh-1000xm6?tab=reviews",
                    "product_reviews.json",
                    "Write a review",
                    CLOSE_TAG,
                ),
                Dialog(
                    "question",
                    "/p/p-sony-wh-1000xm6?tab=questions",
                    "product_questions.json",
                    "Ask a question",
                    CLOSE_TAG,
                ),
                Dialog("return", "/account/orders/HL-46102", "order_delivered.json", "Return items", CLOSE_TAG),
                Dialog("plus trial", "/", "home_plus_trial.json", "Try 30 days free", PLUS_CLOSE_TAG),
                // B-86: a review's photos, opened by a photo — named by its content description, it has no words.
                Dialog(
                    "review photos",
                    "/p/p-sony-wh-1000xm6?tab=reviews",
                    "product_reviews.json",
                    "Open photo 1 of 2 by Daniel R.",
                    PHOTO_CLOSE_TAG,
                ),
            )

        /** The phone's sheets: the header's menu and the catalog's filters (B-151 reads what is under them). */
        val SHEETS =
            listOf(
                Dialog("phone menu", "/", "home_content.json", OPEN_MENU, HEADER_MENU_TAG),
                Dialog("filter sheet", "/c/electronics", "catalog_content.json", "Filters", OVER_PAGE_TAG),
            )
    }
}
