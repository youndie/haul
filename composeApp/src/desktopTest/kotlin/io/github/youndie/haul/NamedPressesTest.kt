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
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.ui.HaulIcons
import io.github.youndie.haul.ui.Icon
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every press on the main screens has a name (B-126). The browser's accessible mirror of the page is Compose's
 * merged semantics tree: a node that can be pressed is a `role="button"` there, named by its content description,
 * its text, or the text under it — the name a screen reader reads. A press with none of them reads as «button».
 *
 * Each screen is drawn from its wire body at both widths, the way the storefront draws it (a press needs somebody
 * to follow it, which the registry provides), and every node with a click action in the merged tree is read the
 * way the mirror's is.
 */
@OptIn(ExperimentalTestApi::class)
class NamedPressesTest {
    @Test
    fun `every press on the main screens has a name`() {
        val unnamed =
            SCREENS.flatMap { body ->
                listOf(false, true).flatMap { compact -> unnamedPresses(body, compact) }
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
        body: String,
        compact: Boolean,
    ): List<String> {
        var found = emptyList<String>()
        runDesktopComposeUiTest(if (compact) PHONE else WIDE, 1_200) {
            setContent { Fixture(compact) { Page { Body(body) } } }
            waitForIdle()
            found = unnamed().map { "$body ${if (compact) "phone" else "wide"}: $it" }
        }
        return found
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

        /** Characters that draw nothing and read as nothing. */
        const val INVISIBLE = "​⁠ "

        /** The main screens, as the server sends them: the header and footer come with each. */
        val SCREENS =
            listOf(
                "home_content.json",
                "home_guest.json",
                "catalog_content.json",
                "search_results.json",
                "product_description.json",
                "product_reviews.json",
                "product_questions.json",
                "cart_content.json",
                "cart_guest.json",
                "checkout_content.json",
                "account_content.json",
                "account_orders.json",
                "saved_content.json",
                "saved_price_drops.json",
                "order_in_transit.json",
                "order_delivered.json",
            )
    }
}
