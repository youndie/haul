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
        assertEquals("/ui/nowhere", Address("/nowhere").screen)
        assertEquals(PageKind.Other, Address("/nowhere").kind)
    }

    /**
     * The kind is read off `StorefrontPage`, the list the server serves the page at (B-36): an address
     * the client draws as a page is one a reload opens, and one the server has no page for is not drawn
     * as one — `/p/p-1/reviews` has no screen route behind it (`/ui/p/{productId}`).
     */
    @Test
    fun `an address is the page the server serves at it`() {
        assertEquals(PageKind.Catalog, Address("/c/electronics/audio/headphones?brand=Sony").kind)
        assertEquals(PageKind.Other, Address("/p/p-1/reviews").kind)
        assertEquals(PageKind.Other, Address("/c/").kind)
        assertEquals(PageKind.Other, Address("/search/extra").kind)
        // The cart, the checkout and the account draw their own placeholders and failure (B-13, B-15, B-19).
        assertEquals(PageKind.Cart, Address("/cart").kind)
        assertEquals(PageKind.Other, Address("/cart/x").kind)
        assertEquals(PageKind.Checkout, Address("/checkout").kind)
        assertEquals(PageKind.Other, Address("/checkout/x").kind)
        assertEquals(PageKind.Account, Address("/account").kind)
        assertEquals(PageKind.Other, Address("/account/saved").kind)
        // The deals page (B-37) is a page a reload opens, drawn with the shell's own placeholders.
        assertEquals("/ui/deals?page=2", Address("/deals?page=2").screen)
        assertEquals(PageKind.Other, Address("/deals").kind)
        assertEquals(PageKind.Other, Address("/deals/today").kind)
        assertEquals("/ui/cart", Address("/cart").screen)
        assertEquals("/ui/checkout", Address("/checkout").screen)
        // An order (B-18): under the account's history (B-19), where placement lands.
        assertEquals(PageKind.Order, Address("/account/orders/HL-48302").kind)
        assertEquals("/ui/account/orders/HL-48302", Address("/account/orders/HL-48302").screen)
        assertEquals(PageKind.Account, Address("/account/orders").kind)
        assertEquals(PageKind.Account, Address("/account/orders?status=active").kind)
        assertEquals("/ui/account/orders?status=active", Address("/account/orders?status=active").screen)
        assertEquals(PageKind.Other, Address("/orders/HL-48302").kind)
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
