package io.github.youndie.haul.feature.order

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.CANVAS_NOW
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.decode
import io.github.youndie.haul.feature.cart.CartCommand
import io.github.youndie.haul.feature.cart.CartCommands
import io.github.youndie.haul.feature.cart.CartRefused
import io.github.youndie.haul.feature.cart.LocalCartCommands
import io.github.youndie.haul.registry.haulRegistry
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.LocalHaulNow
import io.github.youndie.haul.ui.OrderBody
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotScreen
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.PresentAction
import io.github.youndie.kompot.standard.RefreshAction
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The order page's presses, drawn from the server's own trees (`resources/bodies/order_*.json`) through the
 * app's registry (B-18): «Reorder» is the cart command the tree's url names, and the server's answer —
 * `navigate` to the cart — goes to the screen's handler; «Write a review» follows its tree's `present` of the
 * review dialog (B-22); «Return items» is drawn and sends nothing (B-21).
 */
@OptIn(ExperimentalTestApi::class)
class OrderWiringTest {
    private val sent = CopyOnWriteArrayList<CartCommand>()
    private val followed = CopyOnWriteArrayList<KompotAction>()
    private var refuse = false

    private val commands =
        CartCommands { command ->
            sent += command
            if (refuse) throw CartRefused(404, null, null)
            NavigateAction("/cart")
        }

    private fun ComposeUiTest.order(tree: KompotComponent) =
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                CompositionLocalProvider(LocalCartCommands provides commands, LocalHaulNow provides CANVAS_NOW) {
                    val forms = remember { FormController(FormSchema(formId = "none", fields = emptyList())) }
                    Box(Modifier.verticalScroll(rememberScrollState())) {
                        KompotScreen(tree, remember { haulRegistry() }, forms, KompotActionHandler { followed += it })
                    }
                }
            }
        }

    private fun body(tree: KompotComponent): OrderBody =
        (tree as ColumnComponent).children.filterIsInstance<OrderBody>().single()

    /** A delivered order's «Reorder» sends the reorder its tree names, once per press, and follows the answer. */
    @Test
    fun `reorder sends the order's reorder and follows the server to the cart`() =
        runDesktopComposeUiTest(WIDTH, 1_200) {
            val tree = decode("order_delivered.json")
            order(tree)
            onNodeWithTag(REORDER_TAG).performClick()
            waitForIdle()
            assertEquals(listOf(CartCommand.Reorder(assertNotNull(body(tree).summary.reorderUrl))), sent.toList())
            assertEquals(listOf<KompotAction>(NavigateAction("/cart")), followed.toList())

            onNodeWithText("Return items").performClick()
            onAllNodesWithText("Write a review")[0].performClick()
            waitForIdle()
            assertEquals(1, sent.size, "«Return items» or a review link sent a command")
            assertTrue(
                followed.last() is PresentAction,
                "«Write a review» presents the review dialog: ${followed.last()}",
            )
        }

    /** A reorder the server refuses draws the order again — `refresh` — rather than leaving the page. */
    @Test
    fun `a refused reorder draws the order again`() =
        runDesktopComposeUiTest(WIDTH, 1_200) {
            refuse = true
            order(decode("order_delivered.json"))
            onNodeWithTag(REORDER_TAG).performClick()
            waitForIdle()
            assertEquals(listOf<KompotAction>(RefreshAction), followed.toList())
        }

    /** A cancelled order's «Back to cart», in the banner and in the summary, opens the cart and sends nothing. */
    @Test
    fun `a cancelled order leads back to the cart`() =
        runDesktopComposeUiTest(WIDTH, 1_200) {
            order(decode("order_cancelled.json"))
            onAllNodesWithText("Back to cart").fetchSemanticsNodes().indices.forEach {
                onAllNodesWithText("Back to cart")[it].performClick()
            }
            waitForIdle()
            assertEquals(listOf<KompotAction>(NavigateAction("/cart"), NavigateAction("/cart")), followed.toList())
            assertEquals(emptyList(), sent.toList())
        }

    private companion object {
        const val WIDTH = 1440
    }
}
