package io.github.youndie.haul

import io.github.youndie.haul.shell.Address
import io.github.youndie.haul.shell.PageKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The browser's address and the screen route behind it: the addresses are what the server's
 * `NavigateAction`s carry, and each tree is the same address under `/ui` (endpoint-catalog,
 * endpoint-search), the home page's being `/ui/home`.
 */
class AddressTest {
    @Test
    fun `the root is the home screen`() {
        assertEquals("/ui/home", Address("/").screen)
        assertEquals(PageKind.Home, Address("/").kind)
    }

    @Test
    fun `category product and search addresses keep their path and query under ui`() {
        assertEquals(
            "/ui/c/electronics/headphones?brand=Sony&page=2",
            Address("/c/electronics/headphones?brand=Sony&page=2").screen,
        )
        assertEquals(PageKind.Catalog, Address("/c/headphones").kind)
        assertEquals("/ui/p/p-1?sku=s-1&tab=specifications", Address("/p/p-1?sku=s-1&tab=specifications").screen)
        assertEquals(PageKind.Product, Address("/p/p-1").kind)
        assertEquals(
            "/ui/search?q=running%20shoes&category=running-shoes",
            Address("/search?q=running%20shoes&category=running-shoes").screen,
        )
        assertEquals(PageKind.Search, Address("/search?q=x").kind)
    }

    @Test
    fun `an address the storefront has no page for is still asked of the server`() {
        assertEquals("/ui/deals", Address("/deals").screen)
        assertEquals(PageKind.Other, Address("/deals").kind)
    }

    @Test
    fun `the search's query is read decoded and written the way the server writes it`() {
        assertEquals("running shoes", Address("/search?q=running%20shoes").query)
        assertEquals("running shoes", Address("/search?q=running+shoes").query)
        assertNull(Address("/c/headphones?q=x").query)
        // The server encodes a query with `%20` for a space (SearchScreen.searchLink); so does the field.
        assertEquals("/search?q=running%20shoes", Address.search("running shoes"))
        assertEquals("/ui/search/suggest?q=caf%C3%A9%20%26%20bar", Address.suggest("café & bar"))
    }
}
