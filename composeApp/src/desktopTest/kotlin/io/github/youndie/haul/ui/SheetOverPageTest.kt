package io.github.youndie.haul.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.theme.HaulTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-144 at the layer every sheet is drawn in: a sheet keeps the keys while it is open, whichever of its controls a
 * mouse press focused and whatever became of that control since — disabled (the photos' «›» on the last photo) or
 * gone (the filter sheet's «Clear all» once nothing is applied). A menu opened over a sheet has the keys until it
 * closes, and then the sheet has them again. Every key is sent to the window, wherever the focus is.
 */
@OptIn(ExperimentalTestApi::class)
class SheetOverPageTest {
    private val keys = mutableListOf<Key>()
    private var dismissed = 0

    private fun ComposeUiTest.sheet(content: @Composable () -> Unit) =
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                OverlayHost {
                    SheetOverPage(onDismiss = { dismissed += 1 }, onKey = { keys.add(it) }) { content() }
                }
            }
        }

    private fun ComposeUiTest.press(key: Key) {
        onRoot().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    @Test
    fun `a sheet keeps the keys after the control pressed is disabled`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            var enabled by mutableStateOf(true)
            sheet {
                Box(
                    Modifier
                        .size(44.dp)
                        .testTag(CONTROL)
                        .then(if (enabled) Modifier.pressable { enabled = false } else Modifier.inert()),
                )
            }
            onNodeWithTag(CONTROL).performMouseInput { click() }
            onNodeWithTag(CONTROL).assertHasNoClickAction()
            press(Key.DirectionLeft)
            assertEquals(listOf(Key.DirectionLeft), keys)
            press(Key.Escape)
            assertEquals(1, dismissed)
        }

    @Test
    fun `a sheet keeps the keys after the control pressed is gone`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            var shown by mutableStateOf(true)
            sheet {
                if (shown) Box(Modifier.size(44.dp).testTag(CONTROL).pressable { shown = false })
            }
            onNodeWithTag(CONTROL).performMouseInput { click() }
            onNodeWithTag(CONTROL).assertDoesNotExist()
            press(Key.Escape)
            assertEquals(1, dismissed)
        }

    @Test
    fun `a menu opened over a sheet has the keys until it closes`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            sheet {
                Column {
                    Menu(listOf(MenuEntry("One") {})) { press -> Box(press.size(44.dp).testTag(CONTROL)) }
                }
            }
            onNodeWithTag(CONTROL).performMouseInput { click() }
            onNodeWithTag(LINK_MENU_TAG).assertExists()
            press(Key.Escape)
            onNodeWithTag(LINK_MENU_TAG).assertDoesNotExist()
            assertEquals(0, dismissed, "Escape closed the sheet under the open menu")
            press(Key.Escape)
            assertEquals(1, dismissed)
        }

    private companion object {
        const val WIDTH = 1440
        const val HEIGHT = 900
        const val CONTROL = "control"
    }
}
