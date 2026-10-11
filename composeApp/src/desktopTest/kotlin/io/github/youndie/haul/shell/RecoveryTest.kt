package io.github.youndie.haul.shell

import io.github.youndie.haul.ErrorCode
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B-94: what a refused page leads to instead of a Retry that asks for the same refusal. The codes and fields
 * are the server's (`ErrorCode`, `CatalogError.Invalid`'s parameter names).
 */
class RecoveryTest {
    @Test
    fun `an empty checkout is the cart`() {
        assertEquals(
            Recovery.Redirect("/cart"),
            Address("/checkout").recovery(refused(409, ErrorCode.CartEmpty)),
        )
    }

    @Test
    fun `a search too short says so`() {
        assertEquals(Recovery.QueryTooShort, Address("/search?q=a").recovery(refused(400, ErrorCode.QueryTooShort)))
    }

    @Test
    fun `a parameter the server names is dropped and the rest of the query kept as written`() {
        val refused = refused(400, ErrorCode.ValidationFailed, field = "sort")
        assertEquals(
            Recovery.Redirect("/c/mugs?brand=Ostra%20Home&page=2"),
            Address("/c/mugs?brand=Ostra%20Home&sort=bogus&page=2").recovery(refused),
        )
        assertEquals(
            Recovery.Redirect("/p/p-1"),
            Address("/p/p-1?sku=gone").recovery(refused(400, ErrorCode.ValidationFailed, field = "sku")),
        )
    }

    @Test
    fun `every value of a repeated parameter goes`() {
        assertEquals("/c/mugs?sort=price", Address("/c/mugs?brand=a&sort=price&brand=b").without("brand"))
    }

    @Test
    fun `a refusal that names no parameter of the address is a stale link`() {
        assertEquals(Recovery.StaleLink, Address("/c/mugs?page=2").recovery(refused(400, ErrorCode.ValidationFailed)))
        assertEquals(
            Recovery.StaleLink,
            Address("/c/mugs").recovery(refused(400, ErrorCode.ValidationFailed, field = "sort")),
        )
    }

    @Test
    fun `no answer and the server's own errors are Retry's`() {
        assertNull(Address("/cart").recovery(ScreenFailed.Unreachable("/ui/cart", IOException("down"))))
        assertNull(Address("/cart").recovery(refused(500, ErrorCode.Internal)))
        assertNull(Address("/cart").recovery(refused(503, ErrorCode.Unavailable)))
        assertNull(Address("/cart").recovery(refused(409, ErrorCode.CartEmpty)), "only the checkout is the cart")
    }

    @Test
    fun `Retry helps unless the request itself was refused`() {
        assertTrue(ScreenFailed.Unreachable("/ui/home", IOException("down")).retryHelps)
        assertTrue(refused(500, ErrorCode.Internal).retryHelps)
        assertFalse(refused(400, ErrorCode.QueryTooShort).retryHelps)
        assertFalse(refused(400, ErrorCode.ValidationFailed, field = "page").retryHelps)
    }

    private fun refused(
        status: Int,
        code: ErrorCode,
        field: String? = null,
    ) = ScreenFailed.Refused("/ui/x", status, code, field = field)
}
