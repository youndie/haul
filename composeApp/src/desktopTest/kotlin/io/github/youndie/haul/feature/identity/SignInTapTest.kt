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
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The way into sign-in on the page: the header's «Sign in» hands its `account` action to the
 * storefront, and [SignInActions] answers `/sign-in` — from the header or from the cart's «Sign in to
 * check out» — by signing in and drawing the screen again.
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
    fun `sign-in's actions sign in and redraw, every other action is left to navigation`() =
        runBlocking {
            var signIns = 0
            var redraws = 0
            val actions = SignInActions(signIn = { signIns++ }, redraw = { redraws++ })

            assertTrue(actions.handle(NavigateAction("/sign-in")))
            assertTrue(actions.handle(NavigateAction("/sign-in?next=%2Fcheckout")))
            assertFalse(actions.handle(RefreshAction))
            assertFalse(actions.handle(NavigateAction("/p/p-sony-wh-1000xm6")))

            assertEquals(2, signIns)
            assertEquals(2, redraws)
        }

    @Test
    fun `a sign-in that fails leaves the shopper as they were and redraws`() =
        runBlocking {
            var redraws = 0
            val actions = SignInActions(signIn = { throw SignInUnavailable() }, redraw = { redraws++ })

            assertTrue(actions.handle(NavigateAction("/sign-in")))

            assertEquals(1, redraws)
        }
}
