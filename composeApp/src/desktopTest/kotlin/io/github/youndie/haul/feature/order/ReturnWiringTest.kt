package io.github.youndie.haul.feature.order

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.CANVAS_NOW
import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.feature.product.CANCEL_TAG
import io.github.youndie.haul.feature.product.ReviewCommand
import io.github.youndie.haul.feature.product.ReviewCommands
import io.github.youndie.haul.feature.product.ReviewRefused
import io.github.youndie.haul.feature.product.SUBMIT_TAG
import io.github.youndie.haul.feature.returns.ReturnEntry
import io.github.youndie.haul.read
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.standard.CloseAction
import io.github.youndie.kompot.standard.RefreshAction
import io.github.youndie.kompot.standard.SequenceAction
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * «Return items» in the storefront, over the server's own tree of a delivered order (`resources/bodies/
 * order_delivered.json`, #HL-46102: the $80.00 sweater and the $23.00 serum, B-21): the button presents the
 * dialog the tree carries; what is ticked is added up into the refund as the shopper ticks; a form at fault by
 * the server's rules sends nothing; a filled one is sent where the form says, and its answer — close, then
 * refresh — takes the dialog away and fetches the order again. A refusal is drawn in the dialog.
 */
@OptIn(ExperimentalTestApi::class)
class ReturnWiringTest {
    private val sent = CopyOnWriteArrayList<ReviewCommand>()
    private val requests = CopyOnWriteArrayList<String>()
    private var answer: (ReviewCommand) -> KompotAction = { CLOSED }

    private fun ComposeUiTest.delivered() {
        val transport =
            HaulTransport { path ->
                requests += path
                HaulResponse(200, read("order_delivered.json"))
            }
        val commands =
            ReviewCommands { command ->
                sent += command
                answer(command)
            }
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                Storefront(transport, FakeHistory(ADDRESS), signIn = {}, clock = FixedClock, reviewCommands = commands)
            }
        }
        waitUntil(timeoutMillis = 5_000) { requests.isNotEmpty() }
        onNodeWithTag(RETURN_TAG).performClick()
        onNodeWithTag(SUBMIT_TAG).assertExists()
    }

    /** The refund is the ticked lines' sum, said for one line or for several, and gone when nothing is ticked. */
    @Test
    fun `the refund adds up the lines as they are ticked`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            delivered()
            onNodeWithTag(REFUND_TAG).assertDoesNotExist()
            onNodeWithTag(lineTag(0)).performClick()
            onNodeWithText("Refund $80.00 to card ···· 4821").assertExists()
            onNodeWithText("A courier picks it up for free. Points earned on this line are reversed.").assertExists()
            onNodeWithTag(lineTag(1)).performClick()
            onNodeWithText("Refund $103.00 to card ···· 4821").assertExists()
            onNodeWithText("A courier picks it up for free. Points earned on these lines are reversed.").assertExists()
            onNodeWithTag(lineTag(0)).performClick()
            onNodeWithTag(lineTag(1)).performClick()
            onNodeWithTag(REFUND_TAG).assertDoesNotExist()
        }

    /** The lines ticked and the reason chosen are the command, sent where the form says; the answer closes and redraws. */
    @Test
    fun `a return is sent where the form says and the answer closes the dialog and redraws the order`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            delivered()
            onNodeWithTag(lineTag(0)).performClick()
            onNodeWithTag(REASON_TAG).performClick()
            onNodeWithTag(reasonTag("doesnt_fit")).performClick()
            onNodeWithTag(SUBMIT_TAG).performClick()
            waitUntil(timeoutMillis = 5_000) { requests.size == 2 }
            assertEquals(
                listOf<ReviewCommand>(ReviewCommand.Return(RETURNS_URL, ReturnEntry(listOf(0), "doesnt_fit"))),
                sent.toList(),
            )
            onNodeWithTag(SUBMIT_TAG).assertDoesNotExist()
        }

    /** The server's rules, checked before sending: nothing ticked and no reason send nothing, and each says why. */
    @Test
    fun `a return at fault by the server's rules sends nothing and says why`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            delivered()
            onNodeWithTag(SUBMIT_TAG).performClick()
            onNodeWithText("Choose what to return").assertExists()
            onNodeWithText("Choose a reason for the return").assertExists()
            onNodeWithTag(lineTag(1)).performClick()
            onNodeWithText("Choose what to return").assertDoesNotExist()
            assertEquals(emptyList(), sent.toList())
            assertEquals(1, requests.size)
        }

    /** «Late return» seen from the dialog: the window shut since the page was drawn, and the server's sentence says so. */
    @Test
    fun `a late return is refused in the dialog`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            answer =
                { throw ReviewRefused(422, ErrorCode.ReturnWindowClosed, "Returns for this order closed on Oct 26") }
            delivered()
            onNodeWithTag(lineTag(0)).performClick()
            onNodeWithTag(REASON_TAG).performClick()
            onNodeWithTag(reasonTag("damaged")).performClick()
            onNodeWithTag(SUBMIT_TAG).performClick()
            waitUntil(timeoutMillis = 5_000) { sent.isNotEmpty() }
            onNodeWithText("Returns for this order closed on Oct 26").assertExists()
            onNodeWithTag(SUBMIT_TAG).assertExists()
            assertEquals(1, requests.size)
        }

    @Test
    fun `cancel takes the dialog away and sends nothing`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            delivered()
            onNodeWithTag(lineTag(0)).performClick()
            onNodeWithTag(CANCEL_TAG).performClick()
            onNodeWithTag(SUBMIT_TAG).assertDoesNotExist()
            assertEquals(emptyList(), sent.toList())
        }

    private object FixedClock : Clock {
        override fun now(): Instant = CANVAS_NOW
    }

    private companion object {
        const val WIDTH = 1440
        const val HEIGHT = 1800
        const val ADDRESS = "/account/orders/HL-46102"
        const val RETURNS_URL = "/api/v1/me/orders/HL-46102/returns"
        val CLOSED = SequenceAction(listOf(CloseAction, RefreshAction))
    }
}
