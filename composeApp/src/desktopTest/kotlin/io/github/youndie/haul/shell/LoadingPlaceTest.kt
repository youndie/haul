package io.github.youndie.haul.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.Body
import io.github.youndie.haul.Fixture
import io.github.youndie.haul.Page
import io.github.youndie.haul.Render
import io.github.youndie.haul.decode
import io.github.youndie.haul.feature.cart.CART_LINES_TAG
import io.github.youndie.haul.ui.FilteredResults
import io.github.youndie.haul.ui.PHOTO_TAG
import io.github.youndie.haul.ui.PageTitle
import io.github.youndie.kompot.standard.ColumnComponent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-143: a page's first content stands where its placeholder stood. Each row of a placeholder above it — the crumbs,
 * the title, a category's kinds and filters — is as tall as the page's own row, so nothing moves when the page
 * arrives: a category's first card, the cart's lines. At 1440, 1280 and on a phone. The product's photo is
 * `ProductPageLayoutTest`'s; an order's steps are not here, since how many lines its title and lead take is the
 * order's, which no placeholder knows.
 */
@OptIn(ExperimentalTestApi::class)
class LoadingPlaceTest {
    /** The top of the highest node tagged [tag]. */
    private fun ComposeUiTest.topOf(tag: String): Float =
        onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().minOf { it.boundsInRoot.top }

    private fun standsWhereItArrives(
        width: Int,
        loading: @Composable () -> Unit,
        page: @Composable () -> Unit,
        placeholderTag: String,
        arrivedTag: String,
    ) = runDesktopComposeUiTest(width, HEIGHT) {
        var loaded by mutableStateOf(false)
        setContent { Fixture(compact = width < WIDE) { Page { if (loaded) page() else loading() } } }
        waitForIdle()
        val before = topOf(placeholderTag)
        loaded = true
        waitForIdle()
        assertEquals(topOf(arrivedTag), before, "the first content moves when the page arrives at $width")
    }

    /**
     * A category opened from its card — no filter applied, as the shopper arrives from home or the menu — whose count
     * fits beside its title. On a phone a longer title («Headphones») puts its count on a line of its own, 28 px more
     * than the placeholder's one line: how long a category's name is, no placeholder knows (B-143's findings).
     */
    private val unfiltered: ColumnComponent =
        (decode("catalog_content.json") as ColumnComponent).let { page ->
            page.copy(
                children =
                    page.children.map {
                        when (it) {
                            is FilteredResults -> {
                                it.copy(
                                    applied = it.applied.copy(chips = emptyList(), filterCount = 0),
                                )
                            }

                            is PageTitle -> {
                                it.copy(title = "Audio")
                            }

                            else -> {
                                it
                            }
                        }
                    },
            )
        }

    private fun category(width: Int) =
        standsWhereItArrives(width, { CatalogLoading() }, { Render(unfiltered) }, CARD_SKELETON_TAG, PHOTO_TAG)

    @Test
    fun `a category's first card stands where its placeholder stood`() = category(DESKTOP)

    @Test
    fun `a category's first card stands where its placeholder stood at 1280`() = category(NARROW_DESKTOP)

    @Test
    fun `a category's first card stands where its placeholder stood on a phone`() = category(PHONE)

    private fun cart(width: Int) =
        standsWhereItArrives(width, { CartLoading() }, { Body("cart_content.json") }, CART_LINES_TAG, CART_LINES_TAG)

    @Test
    fun `the cart's lines stand where their placeholders stood`() = cart(DESKTOP)

    @Test
    fun `the cart's lines stand where their placeholders stood at 1280`() = cart(NARROW_DESKTOP)

    @Test
    fun `the cart's lines stand where their placeholders stood on a phone`() = cart(PHONE)

    private companion object {
        const val DESKTOP = 1440
        const val NARROW_DESKTOP = 1280
        const val PHONE = 390
        const val HEIGHT = 3000

        /** Below it the phone's arrangement is drawn (`App.kt`'s `COMPACT_BELOW`). */
        const val WIDE = 1024
    }
}
