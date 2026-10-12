package io.github.youndie.haul

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.HaulIcons
import io.github.youndie.haul.ui.Icon
import io.github.youndie.kompot.standard.RefreshAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every press on the main screens has a name (B-126). The browser's accessible mirror of the page is Compose's
 * merged semantics tree: a node that can be pressed is a `role="button"` there, named by its content description,
 * its text, or the text under it — the name a screen reader reads. A press with none of them reads as «button».
 *
 * Each screen is drawn from its wire body at both widths by the storefront itself, at the address it is served at —
 * the shell's own presses (the search field's, the header's menu) are there only then — and every node with a
 * click action in the merged tree is read the way the mirror's is.
 */
@OptIn(ExperimentalTestApi::class)
class NamedPressesTest {
    @Test
    fun `every press on the main screens has a name`() {
        val unnamed =
            SCREENS.flatMap { (address, body) ->
                listOf(false, true).flatMap { compact -> unnamedPresses(address, body, compact) }
            }
        assertEquals(emptyList(), unnamed, "presses with no name, by screen and width")
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

    private fun unnamedPresses(
        address: String,
        body: String,
        compact: Boolean,
    ): List<String> {
        val tree = withFacetActions(read(body))
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
            found = unnamed().map { "$address ${if (compact) "phone" else "wide"}: $it" }
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

    private fun ComposeUiTest.unnamed(): List<String> =
        onAllNodes(hasClickAction())
            .fetchSemanticsNodes()
            .filter { name(it).isBlank() }
            .map { node ->
                val tag = node.config.getOrNull(SemanticsProperties.TestTag)
                val bounds = node.boundsInRoot
                "#${node.id} tag=$tag at (${bounds.left.toInt()}, ${bounds.top.toInt()}) " +
                    "${bounds.width.toInt()}x${bounds.height.toInt()}"
            }

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

    private companion object {
        const val WIDE = 1_440
        const val PHONE = 390

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
                "/account/orders/HL-48302" to "order_in_transit.json",
                "/account/orders/HL-48302" to "order_delivered.json",
            )
    }
}
