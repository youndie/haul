package io.github.youndie.haul

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The storefront's addresses, as the server reads them to decide where the page answers (B-36). The
 * shapes are those of the screen routes behind them: a category's path may have several slugs
 * (`/ui/c/{path...}`), a product has one id (`/ui/p/{productId}`), the rest are exact.
 */
class StorefrontPageTest {
    @Test
    fun `each storefront address is its page`() {
        assertEquals(StorefrontPage.Home, StorefrontPage.of("/"))
        assertEquals(StorefrontPage.Catalog, StorefrontPage.of("/c/headphones"))
        assertEquals(StorefrontPage.Catalog, StorefrontPage.of("/c/electronics/audio/headphones"))
        assertEquals(StorefrontPage.Product, StorefrontPage.of("/p/p-sony-wh-1000xm6"))
        assertEquals(StorefrontPage.Search, StorefrontPage.of("/search"))
        assertEquals(StorefrontPage.Cart, StorefrontPage.of("/cart"))
        assertEquals(StorefrontPage.Checkout, StorefrontPage.of("/checkout"))
        assertEquals(StorefrontPage.Account, StorefrontPage.of("/account"))
        assertEquals(StorefrontPage.SignIn, StorefrontPage.of("/sign-in"))
    }

    @Test
    fun `anything else is no page`() {
        listOf(
            "",
            "c/headphones",
            "/nowhere",
            "/deals",
            "/ui/home",
            "/ui/p/p-1",
            "/api/v1/guests",
            "/images/a.webp",
            "/index.html",
            "/c",
            "/c/",
            "/c/headphones/",
            "/c//headphones",
            "/p",
            "/p/",
            "/p/p-1/reviews",
            "/search/",
            "/search/extra",
            "/cart/1",
            "/checkout/pay",
            "/account/orders",
            "/sign-in/",
        ).forEach { assertNull(StorefrontPage.of(it), "«$it»") }
    }
}
