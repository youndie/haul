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
import io.github.youndie.haul.ui.ReturnForm
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
 * review dialog (B-22); «Return items» follows its tree's `present` of the return dialog (B-21).
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

            onNodeWithTag(RETURN_TAG).performClick()
            waitForIdle()
            assertEquals(
                ReturnForm::class,
                ((followed.last() as? PresentAction)?.content ?: followed.last())::class,
                "«Return items» presents the return dialog",
            )
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

    /**
     * A Haul Pay order draws its schedule (B-24) under the summary's payment fact — every payment's day, where it
     * stands and what it charges, as the tree says — and an order paid by card draws none.
     */
    @Test
    fun `a Haul Pay order draws its schedule under the payment fact`() =
        runDesktopComposeUiTest(WIDTH, 1_600) {
            val tree = decode("order_haul_pay.json")
            order(tree)
            onNodeWithTag(PLAN_TAG).assertExists()
            val plan = assertNotNull(body(tree).summary.plan, "the fixture is not a Haul Pay order")
            assertEquals(4, plan.payments.size)
            plan.payments.forEach { payment ->
                onNodeWithText(payment.label).assertExists()
                onAllNodesWithText(payment.detail).fetchSemanticsNodes().let {
                    assertTrue(it.isNotEmpty(), "«${payment.detail}» is not drawn")
                }
            }
            onNodeWithText(plan.title).assertExists()
        }

    /** The schedule is Haul Pay's alone: an order paid by card has none to draw. */
    @Test
    fun `an order paid by card draws no schedule`() =
        runDesktopComposeUiTest(WIDTH, 1_400) {
            order(decode("order_in_transit.json"))
            onNodeWithTag(PLAN_TAG).assertDoesNotExist()
        }

    private companion object {
        const val WIDTH = 1440
    }
}
