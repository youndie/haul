package io.github.youndie.haul.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
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
import androidx.compose.ui.text.TextStyle
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

            override fun pasteText(done: (String?) -> Unit) = done(CLIPBOARD)
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
     * B-155: a field keeps a field's menu — Compose's own does nothing on the web, and the page takes the right button
     * before it — each entry only where it can do something: «Select all» and «Paste» in a field with nothing
     * selected, «Cut» and «Copy» once words are. Each acts on the field's own text, not the page's.
     */
    @Test
    fun `a field's right button offers cut copy paste and select all for its own text`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            var typed by mutableStateOf(WORDS)
            setContent {
                HaulTheme(FixtureFonts.fonts, compact = false) {
                    OverlayHost {
                        CompositionLocalProvider(LocalLinkCopier provides links) {
                            Selectable {
                                HaulTextField(
                                    typed,
                                    { typed = it },
                                    Modifier.size(320.dp, 40.dp).testTag(FIELD),
                                )
                            }
                        }
                    }
                }
            }
            rightClick(FIELD)
            onNodeWithText(CUT).assertDoesNotExist()
            onNodeWithText(COPY).assertDoesNotExist()
            pick(SELECT_ALL)
            rightClick(FIELD)
            pick(COPY)
            assertEquals(listOf(WORDS), copied)
            rightClick(FIELD)
            pick(CUT)
            assertEquals("", typed)
            assertEquals(listOf(WORDS, WORDS), copied)
            rightClick(FIELD)
            onNodeWithText(SELECT_ALL).assertDoesNotExist()
            pick(PASTE)
            assertEquals(CLIPBOARD, typed)
        }

    /** B-155: a link in a sentence — a crumb, a word of the category row — offers its address as any link does. */
    @Test
    fun `a link in a sentence offers its address`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            setContent {
                HaulTheme(FixtureFonts.fonts, compact = false) {
                    OverlayHost {
                        val handler = KompotActionHandler { followed += (it as NavigateAction).deeplink }
                        CompositionLocalProvider(LocalLinkCopier provides links, LocalHaulActions provides handler) {
                            Selectable {
                                val crumbs =
                                    linkedText {
                                        link("Electronics", NavigateAction(PATH), handler)
                                        text.append(" / Audio")
                                    }
                                LinkedBasicText(crumbs.text, crumbs.links, Modifier.testTag(TEXT), TextStyle.Default)
                            }
                        }
                    }
                }
            }
            onNodeWithContentDescription("Electronics").performMouseInput { rightClick() }
            waitForIdle()
            pick(OPEN_IN_NEW_TAB)
            assertEquals(listOf(PATH), opened)
            assertEquals(emptyList(), followed)
        }

    /** B-155: a sheet's words — a dialog's, the phone's menu's — select and open the store's menu over the sheet. */
    @Test
    fun `a sheet's words open the store's menu over the sheet`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            setContent {
                HaulTheme(FixtureFonts.fonts, compact = false) {
                    OverlayHost {
                        CompositionLocalProvider(LocalLinkCopier provides links) {
                            SheetOverPage(onDismiss = {}) { BasicText(WORDS, Modifier.testTag(TEXT)) }
                        }
                    }
                }
            }
            rightClick(TEXT)
            onNodeWithTag(LINK_MENU_TAG).assertExists()
            pick(SELECT_ALL)
            rightClick(TEXT)
            pick(COPY)
            assertEquals(listOf(WORDS), copied)
        }

    private companion object {
        const val WIDTH = 640
        const val HEIGHT = 480
        const val TEXT = "words"
        const val LINK = "link"
        const val FIELD = "field"
        const val CLIPBOARD = "AUTUMN10"
        const val WORDS = "Up to 40 hours, 3 min charge = 3 h"
        const val PATH = "/c/electronics/audio/headphones?brand=Sony"
    }
}
