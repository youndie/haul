package io.github.youndie.haul.feature.home

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.decode
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.shell.CommandRefused
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.MESSAGE_TAG
import io.github.youndie.haul.shell.NO_ANSWER
import io.github.youndie.haul.shell.RETRY
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.shell.TreeCommand
import io.github.youndie.haul.shell.TreeCommands
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.CloseAction
import io.github.youndie.kompot.standard.RefreshAction
import io.github.youndie.kompot.standard.SequenceAction
import kotlinx.serialization.PolymorphicSerializer
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Haul Plus trial in the storefront (B-23), from the home page's own body (`home_plus_trial.json`):
 * «Try 30 days free» presents the dialog its block carries, «Start trial» sends the `POST` the dialog names
 * and follows the answer — the dialog closed, the page fetched again — and «Not now» closes it sending
 * nothing. A refusal (`409 already_member`, a second tab first) closes it and draws the page again as well.
 * The command goes through the dialogs' seam (B-51); the `POST` it becomes on the wire is `TreeCommandsTest`'s.
 */
@OptIn(ExperimentalTestApi::class)
class PlusTrialWiringTest {
    private val requests = CopyOnWriteArrayList<String>()
    private val sent = CopyOnWriteArrayList<TreeCommand>()
    private var answer: (TreeCommand) -> KompotAction = { CLOSE_AND_REFRESH }

    private val home: KompotComponent = decode("home_plus_trial.json")

    private val transport =
        HaulTransport { path ->
            requests += path
            HaulResponse(200, haulWireJson.encodeToString(PolymorphicSerializer(KompotComponent::class), home))
        }

    private val commands =
        TreeCommands { command ->
            sent += command
            answer(command)
        }

    private fun ComposeUiTest.storefront() =
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                Storefront(transport, FakeHistory(), signIn = {}, treeCommands = commands)
            }
        }

    private fun ComposeUiTest.openDialog() {
        storefront()
        waitUntil(timeoutMillis = 5_000) { requests.isNotEmpty() }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Try 30 days free")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Try 30 days free").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag(PLUS_START_TAG)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.waitClosed() =
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag(PLUS_START_TAG)).fetchSemanticsNodes().isEmpty() }

    @Test
    fun `start trial sends the dialog's post and the page is drawn again`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            openDialog()
            onNodeWithTag(PLUS_START_TAG).performClick()
            waitClosed()
            waitUntil(timeoutMillis = 5_000) { requests.size == 2 }
            assertEquals(listOf<TreeCommand>(TreeCommand.StartTrial(TRIAL)), sent.toList())
            assertEquals(listOf("/ui/home", "/ui/home"), requests.toList())
        }

    @Test
    fun `not now closes the dialog and sends nothing`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            openDialog()
            onNodeWithTag(PLUS_DISMISS_TAG).performClick()
            waitClosed()
            assertEquals(emptyList(), sent.toList())
            assertEquals(listOf("/ui/home"), requests.toList())
        }

    /** B-151: Escape closes the dialog, as it closes the menus and the photo viewer, and sends nothing. */
    @Test
    fun `escape closes the dialog and sends nothing`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            openDialog()
            waitForIdle()
            onRoot().performKeyInput { pressKey(Key.Escape) }
            waitClosed()
            assertEquals(emptyList(), sent.toList())
            assertEquals(listOf("/ui/home"), requests.toList())
        }

    /** A refused trial — a member already — closes the dialog, draws the page again and says why (B-92). */
    @Test
    fun `a refused trial closes the dialog and says why over the page drawn again`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            answer = { throw CommandRefused(409, ErrorCode.AlreadyMember, "You are a Haul Plus member already") }
            openDialog()
            onNodeWithTag(PLUS_START_TAG).performClick()
            waitClosed()
            waitUntil(timeoutMillis = 5_000) { requests.size == 2 }
            assertEquals(listOf<TreeCommand>(TreeCommand.StartTrial(TRIAL)), sent.toList())
            waitUntil(timeoutMillis = 5_000) { message("You are a Haul Plus member already") }
        }

    /**
     * B-92: a trial with no answer keeps the dialog and says so over it, without «Retry» — the dialog's own «Start
     * trial» is the press again, and it is pressable.
     */
    @Test
    fun `a trial with no answer keeps the dialog and says so`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            answer = { throw IOException("connection refused") }
            openDialog()
            onNodeWithTag(PLUS_START_TAG).performClick()
            waitUntil(timeoutMillis = 5_000) { message(NO_ANSWER) }
            onNodeWithTag(PLUS_START_TAG).assertExists()
            onNode(hasText(RETRY) and hasAnyAncestor(hasTestTag(MESSAGE_TAG))).assertDoesNotExist()
            assertEquals(listOf("/ui/home"), requests.toList())

            answer = { CLOSE_AND_REFRESH }
            onNodeWithTag(PLUS_START_TAG).performClick()
            waitClosed()
            assertEquals(2, sent.size)
        }

    /** Whether the message over the page says [text]. */
    private fun ComposeUiTest.message(text: String): Boolean =
        onAllNodes(hasText(text) and hasAnyAncestor(hasTestTag(MESSAGE_TAG))).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val WIDTH = 1440
        const val HEIGHT = 3300
        const val TRIAL = "/api/v1/me/plus/trial"
        val CLOSE_AND_REFRESH = SequenceAction(listOf(CloseAction, RefreshAction))
    }
}
