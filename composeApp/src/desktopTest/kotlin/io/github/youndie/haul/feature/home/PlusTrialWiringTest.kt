package io.github.youndie.haul.feature.home

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.decode
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.shell.HaulCommands
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.kompot.KompotComponent
import kotlinx.serialization.PolymorphicSerializer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Haul Plus trial in the storefront (B-23), from the home page's own body (`home_plus_trial.json`):
 * «Try 30 days free» presents the dialog its block carries, «Start trial» sends the `POST` the dialog names
 * and follows the answer — the dialog closed, the page fetched again — and «Not now» closes it sending
 * nothing. A refusal (`409 already_member`, a second tab first) closes it and draws the page again as well.
 */
@OptIn(ExperimentalTestApi::class)
class PlusTrialWiringTest {
    private val requests = CopyOnWriteArrayList<String>()
    private val sent = CopyOnWriteArrayList<String>()
    private var answer = HaulResponse(201, CLOSE_AND_REFRESH)

    private val home: KompotComponent = decode("home_plus_trial.json")

    private val transport =
        HaulTransport { path ->
            requests += path
            HaulResponse(200, haulWireJson.encodeToString(PolymorphicSerializer(KompotComponent::class), home))
        }

    private val commands =
        HaulCommands { method, path, body ->
            sent += "$method $path ${body.orEmpty()}".trim()
            answer
        }

    private fun ComposeUiTest.storefront() =
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                Storefront(transport, FakeHistory(), signIn = {}, commands = commands)
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
            assertEquals(listOf("POST $TRIAL"), sent.toList())
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

    @Test
    fun `a refused trial closes the dialog and draws the page again`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            answer = HaulResponse(409, """{"code":"already_member","message":"You are a Haul Plus member already"}""")
            openDialog()
            onNodeWithTag(PLUS_START_TAG).performClick()
            waitClosed()
            waitUntil(timeoutMillis = 5_000) { requests.size == 2 }
            assertEquals(listOf("POST $TRIAL"), sent.toList())
        }

    private companion object {
        const val WIDTH = 1440
        const val HEIGHT = 3300
        const val TRIAL = "/api/v1/me/plus/trial"
        const val CLOSE_AND_REFRESH = """{"type":"sequence","actions":[{"type":"close"},{"type":"refresh"}]}"""
    }
}
