package io.github.youndie.haul.feature.account

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
import io.github.youndie.haul.ui.PlusTrialDialog
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotScreen
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.PresentAction
import io.github.youndie.kompot.standard.RefreshAction
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The account's presses, drawn from the server's own trees (`resources/bodies/account_*.json`) through the
 * app's registry (B-19): a delivered row's «Reorder» is the order page's cart command, whose answer —
 * `navigate` to the cart — goes to the screen's handler; everything else follows its tree's `navigate`:
 * «Details» and «Track» to the order's page, «All orders» and the menu to the account's pages, a chip to
 * its filter's address, «See today's deals» to the deals, «Saved» to the Saved list (B-20), «Try 30 days
 * free» to the trial's dialog (B-23).
 */
@OptIn(ExperimentalTestApi::class)
class AccountWiringTest {
    private val sent = CopyOnWriteArrayList<CartCommand>()
    private val followed = CopyOnWriteArrayList<KompotAction>()
    private var refuse = false

    private val commands =
        CartCommands { command ->
            sent += command
            if (refuse) throw CartRefused(404, null, null)
            NavigateAction("/cart")
        }

    private fun ComposeUiTest.account(
        tree: KompotComponent,
        compact: Boolean = false,
    ) = setContent {
        HaulTheme(FixtureFonts.fonts, compact = compact) {
            CompositionLocalProvider(LocalCartCommands provides commands, LocalHaulNow provides CANVAS_NOW) {
                val forms = remember { FormController(FormSchema(formId = "none", fields = emptyList())) }
                Box(Modifier.verticalScroll(rememberScrollState())) {
                    KompotScreen(tree, remember { haulRegistry() }, forms, KompotActionHandler { followed += it })
                }
            }
        }
    }

    /** A delivered row's «Reorder» sends that order's reorder, once per press, and follows the server to the cart. */
    @Test
    fun `reorder on a row sends its order's reorder and follows the server to the cart`() =
        runDesktopComposeUiTest(WIDTH, 1_700) {
            account(decode("account_content.json"))
            onNodeWithTag(REORDER_TAG + "HL-45277").performClick()
            waitForIdle()
            assertEquals(listOf(CartCommand.Reorder("/api/v1/me/orders/HL-45277/reorder")), sent.toList())
            assertEquals(listOf<KompotAction>(NavigateAction("/cart")), followed.toList())
        }

    /** A reorder the server refuses draws the account again — `refresh` — rather than leaving it. */
    @Test
    fun `a refused reorder draws the account again`() =
        runDesktopComposeUiTest(WIDTH, 1_700) {
            refuse = true
            account(decode("account_content.json"))
            onNodeWithTag(REORDER_TAG + "HL-46102").performClick()
            waitForIdle()
            assertEquals(listOf<KompotAction>(RefreshAction), followed.toList())
        }

    /**
     * The overview's ways on: each card's «Details» to its order's page, «All orders» and the menu's
     * «Orders» to the history; a returned row's «Details» to its page; the menu's «Saved» to the Saved list.
     */
    @Test
    fun `the overview's links open the order pages and the history`() =
        runDesktopComposeUiTest(WIDTH, 1_700) {
            account(decode("account_content.json"))
            onAllNodesWithText("Details")[0].performClick()
            onAllNodesWithText("Details")[1].performClick()
            onAllNodesWithText("Details")[2].performClick()
            onNodeWithText("All orders").performClick()
            // The header draws «Orders» and «Saved» too, first; the menu's are the second.
            onAllNodesWithText("Orders")[1].performClick()
            onAllNodesWithText("Saved")[1].performClick()
            waitForIdle()
            assertEquals(
                listOf<KompotAction>(
                    NavigateAction("/account/orders/HL-48211"),
                    NavigateAction("/account/orders/HL-47960"),
                    NavigateAction("/account/orders/HL-44019"),
                    NavigateAction("/account/orders"),
                    NavigateAction("/account/orders"),
                    NavigateAction("/account/saved"),
                ),
                followed.toList(),
            )
            assertEquals(emptyList(), sent.toList())
        }

    /** On the history a chip opens its filter's address, «Track» the order's page, and the menu's «Overview» the account. */
    @Test
    fun `the history's chips and rows open their addresses`() =
        runDesktopComposeUiTest(390, 1_400) {
            account(decode("account_orders.json"), compact = true)
            onNodeWithText("Active 2").performClick()
            onNodeWithText("Track").performClick()
            onNodeWithText("Overview").performClick()
            waitForIdle()
            assertEquals(
                listOf<KompotAction>(
                    NavigateAction("/account/orders?status=active"),
                    NavigateAction("/account/orders/HL-48211"),
                    NavigateAction("/account"),
                ),
                followed.toList(),
            )
        }

    /** Somebody with no orders is sent to the deals. */
    @Test
    fun `no orders leads to the deals`() =
        runDesktopComposeUiTest(WIDTH, 1_200) {
            account(decode("account_no_orders.json"))
            onNodeWithText("See today’s deals").performClick()
            waitForIdle()
            assertEquals(listOf<KompotAction>(NavigateAction("/deals")), followed.toList())
        }

    /** A non-member's «Try 30 days free» presents the trial's dialog its tile carries, and sends nothing itself. */
    @Test
    fun `the trial's button presents the trial dialog`() =
        runDesktopComposeUiTest(WIDTH, 1_200) {
            account(decode("account_not_member.json"))
            onNodeWithText("Try 30 days free").performClick()
            waitForIdle()
            val present = followed.single() as PresentAction
            assertEquals("plus-trial", (present.content as PlusTrialDialog).id)
            assertEquals(emptyList(), sent.toList())
        }

    private companion object {
        const val WIDTH = 1440
    }
}
