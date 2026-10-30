package io.github.youndie.haul.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.standard.NavigateAction
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-154: on a selectable page the right button opens the storefront's menu at the pointer — Compose's own text menu
 * drew its Copy and Select all in a look of its own, and on the web neither did anything. Over the words it offers
 * Copy (with words selected) and Select all; over a link, «Open in new tab» and «Copy link» for the link's address.
 * The copy shortcut copies what is selected, which on the web it did not.
 */
@OptIn(ExperimentalTestApi::class)
class ContextMenusTest {
    private val copied = mutableListOf<String>()
    private val opened = mutableListOf<String>()
    private val followed = mutableListOf<String>()

    private val links =
        object : LinkCopier {
            override fun copy(
                path: String,
                done: (Boolean) -> Unit,
            ) = done(copied.add("link $path"))

            override fun openApart(path: String) {
                opened += path
            }

            override fun copyText(
                text: String,
                done: (Boolean) -> Unit,
            ) = done(copied.add(text))
        }

    private fun ComposeUiTest.page() =
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                OverlayHost {
                    CompositionLocalProvider(
                        LocalLinkCopier provides links,
                        LocalHaulActions provides KompotActionHandler { followed += (it as NavigateAction).deeplink },
                    ) {
                        Selectable {
                            Column {
                                BasicText(WORDS, Modifier.testTag(TEXT))
                                Box(Modifier.size(80.dp, 40.dp).testTag(LINK).follows(NavigateAction(PATH)))
                            }
                        }
                    }
                }
            }
        }

    private fun ComposeUiTest.rightClick(tag: String) {
        onNodeWithTag(tag).performMouseInput { rightClick() }
        waitForIdle()
    }

    private fun ComposeUiTest.pick(entry: String) {
        onNodeWithText(entry).performClick()
        waitForIdle()
    }

    @Test
    fun `the right button over the words opens the storefront's menu with Select all`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            page()
            rightClick(TEXT)
            onNodeWithTag(LINK_MENU_TAG).assertExists()
            onNodeWithText(SELECT_ALL).assertExists()
            // Nothing is selected yet: there is nothing to copy.
            onNodeWithText(COPY).assertDoesNotExist()
        }

    @Test
    fun `Copy writes the selected words through the page's clipboard`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            page()
            rightClick(TEXT)
            pick(SELECT_ALL)
            onNodeWithTag(LINK_MENU_TAG).assertDoesNotExist()
            rightClick(TEXT)
            pick(COPY)
            assertEquals(listOf(WORDS), copied)
        }

    @Test
    fun `the copy shortcut copies the words selected`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            page()
            rightClick(TEXT)
            pick(SELECT_ALL)
            onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.C) } }
            waitForIdle()
            assertEquals(listOf(WORDS), copied)
        }

    @Test
    fun `the right button over a link offers to open it apart and to copy it`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            page()
            rightClick(LINK)
            onNodeWithText(SELECT_ALL).assertDoesNotExist()
            pick(OPEN_IN_NEW_TAB)
            assertEquals(listOf(PATH), opened)
            rightClick(LINK)
            pick(COPY_LINK)
            assertEquals(listOf("link $PATH"), copied)
            // The right button follows nothing: the page under the link stayed where it was.
            assertEquals(emptyList(), followed)
        }

    @Test
    fun `a press of the right button off the menu closes it`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            page()
            rightClick(LINK)
            onNodeWithTag(LINK_MENU_TAG).assertExists()
            onRoot().performMouseInput { rightClick(position = centerRight) }
            waitForIdle()
            onNodeWithTag(LINK_MENU_TAG).assertDoesNotExist()
        }

    @Test
    fun `Escape closes the menu`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            page()
            rightClick(TEXT)
            onRoot().performKeyInput { pressKey(Key.Escape) }
            waitForIdle()
            onNodeWithTag(LINK_MENU_TAG).assertDoesNotExist()
        }

    /**
     * The words in a control count presses: a second press soon after was a double press to them, which selected the
     * control's word and took the release, so the control was pressed once — «Write a review» pressed again just
     * after its dialog closed opened nothing. A control on a selectable page is pressed as often as it is pressed, and
     * a card under it not at all.
     */
    @Test
    fun `a control pressed twice at once on the page is pressed twice and the card under it not at all`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            var plus = 0
            var card = 0
            setContent {
                HaulTheme(FixtureFonts.fonts, compact = false) {
                    OverlayHost {
                        Selectable {
                            Box(Modifier.size(240.dp, 120.dp).pressable { card += 1 }) {
                                Box(
                                    Modifier.size(80.dp, 40.dp).testTag(PLUS).pressable { plus += 1 },
                                ) { BasicText(ADD) }
                            }
                        }
                    }
                }
            }
            onNodeWithText(ADD).performMouseInput { doubleClick() }
            waitForIdle()
            assertEquals(2, plus, "the second press of «Add» was taken by its word")
            assertEquals(0, card)
        }

    private companion object {
        const val WIDTH = 640
        const val HEIGHT = 480
        const val TEXT = "words"
        const val LINK = "link"
        const val PLUS = "plus"
        const val ADD = "Add to cart"
        const val WORDS = "Up to 40 hours, 3 min charge = 3 h"
        const val PATH = "/c/electronics/audio/headphones?brand=Sony"
    }
}
