package io.github.youndie.haul.feature.cart

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.CANVAS_NOW
import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.decode
import io.github.youndie.haul.read
import io.github.youndie.haul.registry.haulRegistry
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.LocalHaulNow
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotScreen
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.RefreshAction
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The cart's presses, drawn from the server's own trees (`resources/bodies/cart_*.json`) through the
 * app's registry: each press is the command endpoint-cart names, sent to the URL the tree carries,
 * and the server's answer goes to the screen's handler, which draws the cart again. A press the tree
 * does not allow sends nothing.
 */
@OptIn(ExperimentalTestApi::class)
class CartWiringTest {
    private val sent = CopyOnWriteArrayList<CartCommand>()
    private val followed = CopyOnWriteArrayList<KompotAction>()
    private var refusing = false

    private val commands =
        CartCommands { command ->
            sent += command
            if (refusing) throw CartRefused(422, ErrorCode.PromoExpired, "This code has expired")
            RefreshAction
        }

    private fun ComposeUiTest.cart(body: String) =
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                CompositionLocalProvider(LocalCartCommands provides commands, LocalHaulNow provides CANVAS_NOW) {
                    val forms = remember { FormController(FormSchema(formId = "none", fields = emptyList())) }
                    Box(Modifier.verticalScroll(rememberScrollState())) {
                        KompotScreen(
                            decode(body),
                            remember { haulRegistry() },
                            forms,
                            KompotActionHandler {
                                followed +=
                                    it
                            },
                        )
                    }
                }
            }
        }

    /** Presses [tag] and waits for the screen to be handed what the press led to. */
    private fun ComposeUiTest.press(tag: String) {
        onNodeWithTag(tag).performClick()
        waitUntil(timeoutMillis = 5_000) { followed.isNotEmpty() }
    }

    /** Presses [tag] and checks that nothing was sent and nothing followed. */
    private fun ComposeUiTest.pressInert(tag: String) {
        onNodeWithTag(tag).performClick()
        waitForIdle()
        assertEquals(emptyList(), sent.toList(), "$tag sent a command")
        assertEquals(emptyList(), followed.toList())
    }

    @Test
    fun `plus asks for one more and the answer redraws the cart`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            cart(CONTENT)
            press("cart-line-more:$MUG")
            assertEquals(listOf<CartCommand>(CartCommand.ChangeLine(mugUrl, LineChange(quantity = 2))), sent.toList())
            assertEquals(listOf<KompotAction>(RefreshAction), followed.toList())
        }

    @Test
    fun `minus at one sends nothing`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            cart(CONTENT)
            pressInert("cart-line-less:$MUG")
        }

    @Test
    fun `a line's box unticks it`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            cart(CONTENT)
            press("cart-line-box:$MUG")
            assertEquals(
                listOf<CartCommand>(CartCommand.ChangeLine(mugUrl, LineChange(selected = false))),
                sent.toList(),
            )
        }

    @Test
    fun `remove deletes the line where the selection says`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            cart(CONTENT)
            press("cart-line-remove:$MUG")
            assertEquals(listOf<CartCommand>(CartCommand.RemoveLines(LINES, LinesRemoval(listOf(MUG)))), sent.toList())
        }

    /** Every line follows «Select all» through its own URL; the screen is drawn again once, after the last. */
    @Test
    fun `select all unticks every line in one change`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            cart(CONTENT)
            press(SELECT_ALL_TAG)
            assertEquals(
                listOf(
                    HEADPHONES,
                    DUVET,
                    MUG,
                ).map { CartCommand.ChangeLine("$LINES/$it", LineChange(selected = false)) },
                sent.toList(),
            )
            assertEquals(listOf<KompotAction>(RefreshAction), followed.toList())
        }

    @Test
    fun `delete selected deletes the ticked lines`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            cart(CONTENT)
            press(DELETE_SELECTED_TAG)
            assertEquals(
                listOf<CartCommand>(CartCommand.RemoveLines(LINES, LinesRemoval(listOf(HEADPHONES, DUVET, MUG)))),
                sent.toList(),
            )
        }

    /** Cart_ItemChanged: the changed mug cannot be ticked until «OK» accepts the change. */
    @Test
    fun `OK accepts a changed line whose box does nothing`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            cart(ITEM_CHANGED)
            pressInert("cart-line-box:$MUG")
            press("cart-line-ok:$MUG")
            assertEquals(listOf<CartCommand>(CartCommand.Acknowledge("$mugUrl/acknowledge")), sent.toList())
        }

    @Test
    fun `apply sends the code as typed`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            cart(CONTENT)
            onNodeWithTag(PROMO_INPUT_TAG).performTextInput("autumn10")
            press(PROMO_APPLY_TAG)
            assertEquals(listOf<CartCommand>(CartCommand.ApplyPromo(PROMO, PromoEntry("autumn10"))), sent.toList())
        }

    @Test
    fun `apply with nothing typed sends nothing`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            cart(CONTENT)
            pressInert(PROMO_APPLY_TAG)
        }

    @Test
    fun `remove takes the applied code off`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            cart(PROMO_APPLIED)
            press(PROMO_REMOVE_TAG)
            assertEquals(listOf<CartCommand>(CartCommand.RemovePromo(PROMO)), sent.toList())
        }

    /** The server keeps a refused code and why (Cart_PromoError), so a refusal redraws the cart too. */
    @Test
    fun `a refused code still draws the cart again`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            refusing = true
            cart(PROMO_ERROR)
            press(PROMO_APPLY_TAG)
            assertEquals(listOf<CartCommand>(CartCommand.ApplyPromo(PROMO, PromoEntry("SUMMER5"))), sent.toList())
            assertEquals(listOf<KompotAction>(RefreshAction), followed.toList())
        }

    @Test
    fun `checkout follows the tree's action`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            cart(CONTENT)
            press(CHECKOUT_TAG)
            assertEquals(listOf<KompotAction>(NavigateAction("/checkout")), followed.toList())
            assertEquals(emptyList(), sent.toList())
        }

    /** Cart_Guest: the button asks to sign in, which the shell's sign-in seam answers (B-12). */
    @Test
    fun `a guest's checkout asks to sign in`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            cart(GUEST)
            press(CHECKOUT_TAG)
            assertEquals(listOf<KompotAction>(NavigateAction("/sign-in?next=%2Fcheckout")), followed.toList())
        }

    @Test
    fun `a line's title opens the product`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            cart(CONTENT)
            onNodeWithText("Stoneware Mug, 12 oz").performClick()
            waitUntil(timeoutMillis = 5_000) { followed.isNotEmpty() }
            assertEquals(listOf<KompotAction>(NavigateAction("/p/p-stoneware-mug")), followed.toList())
        }

    /**
     * In the storefront: `/cart` loads the cart's tree, and a command's `refresh` fetches it again —
     * the cart redrawn in place by the shell (B-35), not by anything the cart builds.
     */
    @Test
    fun `in the storefront a command's answer fetches the cart again`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val requests = CopyOnWriteArrayList<String>()
            val transport =
                HaulTransport { path ->
                    requests += path
                    HaulResponse(200, read(CONTENT))
                }
            setContent {
                HaulTheme(FixtureFonts.fonts, compact = false) {
                    Storefront(
                        transport,
                        FakeHistory("/cart"),
                        signIn = {},
                        clock = FixedClock,
                        cartCommands = commands,
                    )
                }
            }
            onNodeWithTag("cart-line-more:$MUG").performClick()
            waitUntil(timeoutMillis = 5_000) { requests.size == 2 }
            assertEquals(listOf("/ui/cart", "/ui/cart"), requests.toList())
            assertEquals(listOf<CartCommand>(CartCommand.ChangeLine(mugUrl, LineChange(quantity = 2))), sent.toList())
        }

    @Test
    fun `in the storefront a cart that did not load says nothing was lost`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val transport = HaulTransport { HaulResponse(500, """{"code":"internal","message":"boom"}""") }
            setContent {
                HaulTheme(FixtureFonts.fonts, compact = false) {
                    Storefront(
                        transport,
                        FakeHistory("/cart"),
                        signIn = {},
                        clock = FixedClock,
                        cartCommands = commands,
                    )
                }
            }
            onNodeWithText("Nothing in it was lost. Try again in a moment.").assertExists()
        }

    private val mugUrl = "$LINES/$MUG"

    private object FixedClock : Clock {
        override fun now(): Instant = CANVAS_NOW
    }

    private companion object {
        const val WIDTH = 1440
        const val HEIGHT = 1400
        const val CONTENT = "cart_content.json"
        const val ITEM_CHANGED = "cart_item_changed.json"
        const val PROMO_APPLIED = "cart_promo_applied.json"
        const val PROMO_ERROR = "cart_promo_error.json"
        const val GUEST = "cart_guest.json"
        const val LINES = "/api/v1/cart/lines"
        const val PROMO = "/api/v1/cart/promo"
        const val HEADPHONES = "p-sony-wh-1000xm6-0"
        const val DUVET = "p-linen-duvet-cover-set-0"
        const val MUG = "p-stoneware-mug-0"
    }
}
