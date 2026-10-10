package io.github.youndie.haul.feature.catalog.screen

import io.github.youndie.haul.feature.catalog.domain.Browse
import io.github.youndie.haul.feature.catalog.domain.Page
import io.github.youndie.kompot.standard.NavigateAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B-77: every page of a listing is one press away — the current page is drawn with its neighbours, not only
 * the first three and the last — and «Show N more» appends the next page to the pages the grid holds. The
 * canvas's listing is 517 pages; the seed has none that long, so the long ones are held here.
 */
class PaginationTest {
    private fun page(
        current: Int,
        pages: Int,
        from: Int = current,
        total: Int = pages * Browse.PAGE_SIZE,
    ) = Page(emptyList(), total, current, pages, from)

    private fun address(pages: IntRange) =
        "/c/mugs?page=${pages.last}" + if (pages.first < pages.last) "&from=${pages.first}" else ""

    @Test
    fun `page seven of a long listing shows its neighbours and both ends`() {
        val seventh = pagination(page(7, 517), ::address)
        assertEquals(listOf("1", "…", "6", "7", "8", "…", "517"), seventh.pages)
        assertEquals(7, seventh.current)
        assertEquals(
            listOf(
                "1" to "/c/mugs?page=1",
                "6" to "/c/mugs?page=6",
                "8" to "/c/mugs?page=8",
                "517" to "/c/mugs?page=517",
            ),
            seventh.links.map { it.label to (it.action as NavigateAction).deeplink },
        )
        assertEquals(NavigateAction("/c/mugs?page=8&from=7"), seventh.moreAction)
        assertEquals("Show 24 more", seventh.moreLabel)
    }

    @Test
    fun `the ends keep three pages and a gap of one page is its number`() {
        assertEquals(listOf("1", "2", "3", "…", "517"), pageNumbers(1, 517))
        assertEquals(listOf("1", "2", "3", "…", "517"), pageNumbers(2, 517))
        assertEquals(listOf("1", "2", "3", "4", "…", "517"), pageNumbers(3, 517))
        assertEquals(listOf("1", "2", "3", "4", "5", "…", "517"), pageNumbers(4, 517))
        assertEquals(listOf("1", "…", "4", "5", "6", "…", "517"), pageNumbers(5, 517))
        assertEquals(listOf("1", "…", "515", "516", "517"), pageNumbers(517, 517))
        assertEquals(listOf("1", "…", "514", "515", "516", "517"), pageNumbers(515, 517))
        assertEquals(listOf("1"), pageNumbers(1, 1))
        assertEquals(listOf("1", "2", "3", "4"), pageNumbers(1, 4))
        assertEquals(listOf("1", "2", "3", "4", "5"), pageNumbers(1, 5))
    }

    @Test
    fun `every page of a long listing is reachable from the one before it`() {
        val pages = 517
        (1 until pages).forEach { n ->
            val drawn = pagination(page(n, pages), ::address)
            val labels = drawn.links.map { it.label }
            assertTrue("${n + 1}" in labels, "page ${n + 1} is not drawn on page $n: ${drawn.pages}")
            if (n > 1) assertTrue("${n - 1}" in labels, "page ${n - 1} is not drawn on page $n: ${drawn.pages}")
            assertTrue(drawn.pages.size <= 7, "page $n draws ${drawn.pages.size} numbers: ${drawn.pages}")
        }
    }

    @Test
    fun `show more goes on from the pages the grid holds and says how many are left`() {
        val held = pagination(page(3, 4, from = 1, total = 3 * Browse.PAGE_SIZE + 5), ::address)
        assertEquals(NavigateAction("/c/mugs?page=4&from=1"), held.moreAction)
        assertEquals("Show 5 more", held.moreLabel)
        // A page number opens its page alone, whatever the grid holds.
        assertEquals(NavigateAction("/c/mugs?page=2"), held.links.single { it.label == "2" }.action)
        val last = pagination(page(4, 4, from = 1, total = 3 * Browse.PAGE_SIZE + 5), ::address)
        assertNull(last.moreAction)
        assertNull(last.moreLabel)
    }
}
