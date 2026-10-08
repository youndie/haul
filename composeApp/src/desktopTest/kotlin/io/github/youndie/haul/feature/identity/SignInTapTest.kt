package io.github.youndie.haul.feature.identity

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.youndie.haul.Fixture
import io.github.youndie.haul.decode
import io.github.youndie.haul.registry.haulRegistry
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotScreen
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.RefreshAction
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The way into sign-in on the page: the header's «Sign in» hands its `account` action to the
 * storefront, and [SignInActions] answers `/sign-in` — from the header or from the cart's «Sign in to
 * check out» — by signing in, then opening the storefront address its `next` names (B-41) or drawing
 * the screen again.
 */
class SignInTapTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a tap on Sign in hands the header's account action to the storefront`() =
        runComposeUiTest {
            val handled = mutableListOf<KompotAction>()
            val header = (decode("haul_header_guest.json") as HaulHeader).copy(account = NavigateAction("/sign-in"))
            setContent {
                Fixture(compact = false) {
                    KompotScreen(
                        header,
                        haulRegistry(),
                        FormController(FormSchema(formId = "none", fields = emptyList())),
                        KompotActionHandler { handled += it },
                    )
                }
            }

            onNodeWithText("Sign in").performClick()

            assertEquals(listOf<KompotAction>(NavigateAction("/sign-in")), handled)
        }

    /**
     * The server leaves a `null` out of the wire (`explicitNulls = false`), so a guest's header arrives
     * with no `customerName` at all — and until B-12 the client refused it, every guest's every screen.
     */
    @Test
    fun `a guest's header decodes as the server sends it, without a name`() {
        val header = decode("haul_header_guest.json") as HaulHeader
        assertNull(header.customerName)
    }

    @Test
    fun `sign-in's actions sign in and redraw and every other action is left to navigation`() =
        runBlocking {
            var signIns = 0
            var redraws = 0
            val opened = mutableListOf<String>()
            val actions = SignInActions(signIn = { signIns++ }, redraw = { redraws++ }, open = { opened += it })

            assertTrue(actions.handle(NavigateAction("/sign-in")))
            assertFalse(actions.handle(RefreshAction))
            assertFalse(actions.handle(NavigateAction("/p/p-sony-wh-1000xm6")))

            assertEquals(1, signIns)
            assertEquals(1, redraws)
            assertEquals(emptyList(), opened, "a sign-in without a next stays where it is")
        }

    /**
     * B-41: the cart's «Sign in to check out» carries `next=%2Fcheckout`, and the app used to draw the
     * cart again after the popup — checkout then had to be found by hand.
     */
    @Test
    fun `a sign-in asked with a next opens it once the shopper is signed in`() =
        runBlocking {
            var redraws = 0
            val opened = mutableListOf<String>()
            val actions = SignInActions(signIn = {}, redraw = { redraws++ }, open = { opened += it })

            assertTrue(actions.handle(NavigateAction("/sign-in?next=%2Fcheckout")))

            assertEquals(listOf("/checkout"), opened)
            assertEquals(0, redraws, "the next page is drawn by opening it")
        }

    /** A popup closed or a server without sign-in: the shopper is still a guest, and goes nowhere. */
    @Test
    fun `a sign-in that fails opens nothing and redraws`() =
        runBlocking {
            var redraws = 0
            val opened = mutableListOf<String>()
            val actions =
                SignInActions(signIn = { throw SignInUnavailable() }, redraw = { redraws++ }, open = { opened += it })

            assertTrue(actions.handle(NavigateAction("/sign-in?next=%2Fcheckout")))

            assertEquals(emptyList(), opened)
            assertEquals(1, redraws)
        }

    @Test
    fun `a next is a storefront address with its query kept`() {
        assertEquals("/checkout", SignInActions.next("/sign-in?next=%2Fcheckout"))
        assertEquals("/", SignInActions.next("/sign-in?next=%2F"))
        assertEquals("/account", SignInActions.next("/sign-in?from=header&next=%2Faccount"))
        assertEquals(
            "/search?q=running%20shoes",
            SignInActions.next("/sign-in?next=" + "/search?q=running%20shoes".encodeURLParameter()),
        )
        assertEquals("/c/electronics/headphones", SignInActions.next("/sign-in?next=/c/electronics/headphones"))
    }

    /**
     * The open-redirect guard. `next` is in an address anybody can write and hand a shopper, so only
     * what `StorefrontPage` has a page for is followed; «starts with a slash» would let `//host` and
     * `/\host` through, which a browser reads as another site.
     */
    @Test
    fun `a next that is not a storefront address is refused`() {
        listOf(
            "https://evil.example/checkout",
            "http://evil.example",
            "//evil.example",
            "//evil.example/checkout",
            "/\\evil.example",
            "\\\\evil.example",
            "/\t/evil.example",
            "javascript:alert(1)",
            "data:text/html,hi",
            "%2F%2Fevil.example",
            "checkout",
            "",
            " /checkout",
            "/nowhere",
            "/checkout/pay",
            "/ui/checkout",
            "/api/v1/guests",
            "/sign-in",
            "/sign-in?next=%2Fcheckout",
        ).forEach { next ->
            val deeplink = "/sign-in?next=" + next.encodeURLParameter()
            assertNull(SignInActions.next(deeplink), "«$next» was followed")
        }
        assertNull(SignInActions.next("/sign-in"), "no query")
        assertNull(SignInActions.next("/sign-in?"), "an empty query")
        assertNull(SignInActions.next("/sign-in?from=header"), "no next")
        assertNull(SignInActions.next("/sign-in?next=%zz"), "a query that does not decode")
        // Positive control: the same shape with a storefront address is followed.
        assertEquals("/checkout", SignInActions.next("/sign-in?next=" + "/checkout".encodeURLParameter()))
    }
}
