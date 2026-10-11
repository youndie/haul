package io.github.youndie.haul.feature.order

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.CANVAS_NOW
import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.decode
import io.github.youndie.haul.feature.cart.CartCommand
import io.github.youndie.haul.feature.cart.CartCommands
import io.github.youndie.haul.feature.cart.CartRefused
import io.github.youndie.haul.feature.cart.LocalCartCommands
import io.github.youndie.haul.read
import io.github.youndie.haul.registry.haulRegistry
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.shell.assertNoAnswer
import io.github.youndie.haul.shell.told
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
import io.github.youndie.kompot.standard.MessageLevel
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.PresentAction
import io.github.youndie.kompot.standard.SequenceAction
import io.github.youndie.kompot.standard.ShowMessageAction
import java.io.IOException
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

    /** What the server does with a reorder instead of answering it: refuses it, or is not there. */
    private var failure: (() -> Throwable)? = null

    private val commands =
        CartCommands { command ->
            sent += command
            failure?.let { throw it() }
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

    /**
     * A reorder the server refuses draws the order again — `refresh` — rather than leaving the page, and says
     * why in the server's words (B-92).
     */
    @Test
    fun `a refused reorder draws the order again and says why`() =
        runDesktopComposeUiTest(WIDTH, 1_200) {
            failure = { CartRefused(404, ErrorCode.OrderNotFound, "No order «HL-46102» among yours") }
            order(decode("order_delivered.json"))
            onNodeWithTag(REORDER_TAG).performClick()
            waitForIdle()
            assertEquals(listOf(told("No order «HL-46102» among yours")), followed.toList())
        }

    /** B-92: a reorder with no answer says so, without «Retry»: a second reorder that did arrive adds the lines twice. */
    @Test
    fun `a reorder with no answer says so without Retry`() =
        runDesktopComposeUiTest(WIDTH, 1_200) {
            failure = { IOException("connection refused") }
            order(decode("order_delivered.json"))
            onNodeWithTag(REORDER_TAG).performClick()
            waitForIdle()
            assertNoAnswer(followed.single(), retry = false)
        }

    /**
     * A cancelled order's «Back to cart» — once, in the banner: the summary drew a second one beside it (B-108) —
     * opens the cart and sends nothing.
     */
    @Test
    fun `a cancelled order leads back to the cart`() =
        runDesktopComposeUiTest(WIDTH, 1_200) {
            order(decode("order_cancelled.json"))
            onNodeWithText("Back to cart").performClick()
            waitForIdle()
            assertEquals(listOf<KompotAction>(NavigateAction("/cart")), followed.toList())
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

    /**
     * B-101: once a return of some of the lines is in, «Return items» is gone and the rule that took it is drawn where
     * it was; «Reorder» stays, and sends the order's reorder.
     */
    @Test
    fun `a partly returned order says the one return rule where its button was and still reorders`() =
        runDesktopComposeUiTest(WIDTH, 1_600) {
            val tree = decode("order_partly_returned.json")
            order(tree)
            val note = assertNotNull(body(tree).summary.returnNote, "the fixture says no rule")
            onNodeWithTag(RETURN_NOTE_TAG).assertTextEquals(note)
            onNodeWithTag(RETURN_TAG).assertDoesNotExist()
            onNodeWithTag(REORDER_TAG).performClick()
            waitForIdle()
            assertEquals(listOf(CartCommand.Reorder(assertNotNull(body(tree).summary.reorderUrl))), sent.toList())
        }

    /** A whole return leaves nothing to ask about: no rule, and «Reorder» stays (B-101). */
    @Test
    fun `a wholly returned order draws no rule and still reorders`() =
        runDesktopComposeUiTest(WIDTH, 1_400) {
            order(decode("order_returned.json"))
            onNodeWithTag(RETURN_NOTE_TAG).assertDoesNotExist()
            onNodeWithTag(REORDER_TAG).assertExists()
        }

    /**
     * B-101 in the storefront: a reorder some of whose lines could not go in lands on the cart with the server's
     * message naming them over it; one none of whose lines could stays on the order under its message.
     */
    @Test
    fun `in the storefront a reorder says what it could not put back`() =
        runDesktopComposeUiTest(WIDTH, 1_400) {
            var answer: KompotAction = SequenceAction(listOf(NavigateAction("/cart"), SHORT))
            val requests = CopyOnWriteArrayList<String>()
            val transport =
                HaulTransport { path ->
                    requests += path
                    when (path) {
                        "/ui$ORDER" -> HaulResponse(200, read("order_delivered.json"))
                        "/ui/cart" -> HaulResponse(200, read("cart_content.json"))
                        else -> error("nothing answers $path")
                    }
                }
            val reorders = CartCommands { answer }
            val history = FakeHistory(ORDER)
            setContent {
                HaulTheme(FixtureFonts.fonts, compact = false) {
                    CompositionLocalProvider(LocalHaulNow provides CANVAS_NOW) {
                        Storefront(transport, history, signIn = {}, cartCommands = reorders)
                    }
                }
            }
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(REORDER_TAG).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag(REORDER_TAG).performClick()
            waitUntil(timeoutMillis = 5_000) { "/ui/cart" in requests }
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(SHORT.text).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(listOf(ORDER, "/cart"), history.entries)

            answer = NONE
            history.back()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(REORDER_TAG).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag(REORDER_TAG).performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(NONE.text).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag(REORDER_TAG).assertExists()
            assertEquals(ORDER, history.location, "a reorder of nothing left the order")
        }

    private companion object {
        const val WIDTH = 1440
        const val ORDER = "/account/orders/HL-46102"
        const val SERUM = "Vitamin C Brightening Serum, 30 ml"
        val SHORT = ShowMessageAction("Added what was in stock — $SERUM isn't available now.", MessageLevel.ERROR)
        val NONE = ShowMessageAction("Nothing was added: $SERUM isn't available now.", MessageLevel.ERROR)
    }
}
