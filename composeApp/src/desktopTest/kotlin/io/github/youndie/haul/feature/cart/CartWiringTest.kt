package io.github.youndie.haul.feature.cart

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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
import io.github.youndie.haul.feature.checkout.PLACE_TAG
import io.github.youndie.haul.feature.identity.SignInUnavailable
import io.github.youndie.haul.read
import io.github.youndie.haul.registry.haulJson
import io.github.youndie.haul.registry.haulRegistry
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.MESSAGE_TAG
import io.github.youndie.haul.shell.NO_ANSWER
import io.github.youndie.haul.shell.RETRY
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.shell.assertNoAnswer
import io.github.youndie.haul.shell.told
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.LocalHaulNow
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotScreen
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.RefreshAction
import io.github.youndie.kompot.standard.SequenceAction
import io.github.youndie.kompot.standard.ShowMessageAction
import kotlinx.serialization.PolymorphicSerializer
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

    /** What the server does with a command instead of answering `refresh`: refuses it, or is not there. */
    private var failure: (() -> Throwable)? = null

    /** What the server answers a command it accepts: `refresh`, unless a test says otherwise. */
    private var answer: (CartCommand) -> KompotAction = { RefreshAction }

    private val commands =
        CartCommands { command ->
            sent += command
            failure?.let { throw it() }
            answer(command)
        }

    private fun ComposeUiTest.cart(body: String) = cart(decode(body))

    private fun ComposeUiTest.cart(tree: KompotComponent) =
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                CompositionLocalProvider(LocalCartCommands provides commands, LocalHaulNow provides CANVAS_NOW) {
                    val forms = remember { FormController(FormSchema(formId = "none", fields = emptyList())) }
                    Box(Modifier.verticalScroll(rememberScrollState())) {
                        KompotScreen(
                            tree,
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

    /** B-100: «−» at one has nowhere to go and looks it — muted, disabled — while «+» beside it is live. */
    @Test
    fun `minus at one is idle and sends nothing`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            cart(CONTENT)
            onNodeWithTag("cart-line-less:$MUG").assertIsNotEnabled()
            onNodeWithTag("cart-line-more:$MUG").assertIsEnabled()
            pressInert("cart-line-less:$MUG")
        }

    /** B-100: at the line's limit «+» is idle as «−» is at one; the headphones' line here holds all it can. */
    @Test
    fun `plus at the line's limit is idle and sends nothing`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val atLimit = read(CONTENT).replaceFirst("\"maxQuantity\": 10", "\"maxQuantity\": 1")
            cart(haulJson.decodeFromString(PolymorphicSerializer(KompotComponent::class), atLimit))
            onNodeWithTag("cart-line-more:$HEADPHONES").assertIsNotEnabled()
            onNodeWithTag("cart-line-more:$MUG").assertIsEnabled()
            pressInert("cart-line-more:$HEADPHONES")
        }

    /**
     * B-100, nothing ticked (`cart_nothing_selected.json`): checkout and «Delete selected» have nothing to do and
     * look it — disabled, greyed — where with every line ticked both are live.
     */
    @Test
    fun `with nothing ticked checkout and delete selected are idle`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            cart(NOTHING_SELECTED)
            onNodeWithTag(CHECKOUT_TAG).assertIsNotEnabled()
            onNodeWithTag(DELETE_SELECTED_TAG).assertIsNotEnabled()
            pressInert(CHECKOUT_TAG)
            pressInert(DELETE_SELECTED_TAG)
        }

    @Test
    fun `with lines ticked checkout and delete selected are live`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            cart(CONTENT)
            onNodeWithTag(CHECKOUT_TAG).assertIsEnabled()
            onNodeWithTag(DELETE_SELECTED_TAG).assertIsEnabled()
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

    /**
     * B-76, Cart_Guest: in the browser a keystroke reaches the field at the next animation frame, while
     * a press is handled the moment it arrives, so «Apply» pressed within that frame read an empty field
     * and sent nothing. Here the code lands after the press and before the next frame, as it does there.
     */
    @Test
    fun `apply sends a code typed just before the press`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            cart(GUEST)
            mainClock.autoAdvance = false
            onNodeWithTag(PROMO_APPLY_TAG).performClick()
            onNodeWithTag(PROMO_INPUT_TAG).performTextInput("AUTUMN10")
            mainClock.autoAdvance = true
            waitForIdle()
            assertEquals(listOf<CartCommand>(CartCommand.ApplyPromo(PROMO, PromoEntry("AUTUMN10"))), sent.toList())
            assertEquals(listOf<KompotAction>(RefreshAction), followed.toList())
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

    /**
     * The server keeps a refused code and why (Cart_PromoError), so a refusal redraws the cart too, and the
     * field says why: no message over the page says it twice (B-92).
     */
    @Test
    fun `a refused code still draws the cart again`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            failure = { CartRefused(422, ErrorCode.PromoExpired, "This code has expired") }
            cart(PROMO_ERROR)
            press(PROMO_APPLY_TAG)
            assertEquals(listOf<CartCommand>(CartCommand.ApplyPromo(PROMO, PromoEntry("SUMMER5"))), sent.toList())
            assertEquals(listOf<KompotAction>(RefreshAction), followed.toList())
        }

    /** B-92: a refusal the tree does not draw — a quantity past the stock — draws the cart again and says why. */
    @Test
    fun `a refused quantity draws the cart again and says why`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            failure = { CartRefused(409, ErrorCode.OutOfStock, "Only 1 left in stock") }
            cart(CONTENT)
            press("cart-line-more:$MUG")
            assertEquals(listOf(told("Only 1 left in stock")), followed.toList())
        }

    /** B-92: a press with no answer says so, and its «Retry» sends the same command again. */
    @Test
    fun `a press with no answer says so and Retry sends it again`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            failure = { IOException("connection refused") }
            cart(CONTENT)
            press(DELETE_SELECTED_TAG)
            val again = assertNotNull(assertNoAnswer(followed.single(), retry = true))
            failure = null
            followed.clear()
            runOnIdle { again.press() }
            waitUntil(timeoutMillis = 5_000) { followed.isNotEmpty() }
            assertEquals(2, sent.size)
            assertEquals(sent[0], sent[1], "«Retry» sent another command")
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
            assertEquals(listOf<KompotAction>(NavigateAction("/p/p-stoneware-mug?sku=$MUG")), followed.toList())
        }

    /**
     * B-100 in the storefront: «Save for later» is answered with the cart again and where the line went — the
     * Saved list, with the way there — said over the page.
     */
    @Test
    fun `in the storefront save for later says where the line went`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val requests = CopyOnWriteArrayList<String>()
            val transport =
                HaulTransport { path ->
                    requests += path
                    HaulResponse(200, read(CONTENT))
                }
            val toSaved = NavigateAction(SAVED)
            val moved = ShowMessageAction("Moved to your Saved list", actionLabel = "View saved", action = toSaved)
            answer = { command ->
                if (command is CartCommand.SaveForLater) SequenceAction(listOf(RefreshAction, moved)) else RefreshAction
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
            onNodeWithTag("cart-line-save:$MUG").performClick()
            waitUntil(timeoutMillis = 5_000) { exists(MESSAGE_TAG) }
            assertEquals(listOf<CartCommand>(CartCommand.SaveForLater("$mugUrl/save-for-later")), sent.toList())
            assertEquals(listOf("/ui/cart", "/ui/cart"), requests.toList(), "the cart was not drawn again")
            onNode(hasText("Moved to your Saved list") and hasAnyAncestor(hasTestTag(MESSAGE_TAG))).assertExists()
            onNode(hasText("View saved") and hasAnyAncestor(hasTestTag(MESSAGE_TAG))).assertExists()
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

    /**
     * B-92 in the storefront: a press with no answer leaves the cart as it was under «Couldn't reach the store»,
     * whose «Retry» sends the press again — the cart then fetched again, the message gone — and a refusal draws
     * the cart again under the server's words.
     */
    @Test
    fun `in the storefront no answer says so and Retry presses again and a refusal says why`() =
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
            failure = { IOException("connection refused") }
            onNodeWithTag("cart-line-more:$MUG").performClick()
            waitUntil(timeoutMillis = 5_000) { exists(MESSAGE_TAG) }
            onNode(hasText(NO_ANSWER) and hasAnyAncestor(hasTestTag(MESSAGE_TAG))).assertExists()
            assertEquals(listOf("/ui/cart"), requests.toList(), "a press with no answer redrew the cart")

            failure = null
            onNode(hasText(RETRY) and hasAnyAncestor(hasTestTag(MESSAGE_TAG))).performClick()
            waitUntil(timeoutMillis = 5_000) { requests.size == 2 }
            assertEquals(List(2) { CartCommand.ChangeLine(mugUrl, LineChange(quantity = 2)) }, sent.toList())
            onNodeWithTag(MESSAGE_TAG).assertDoesNotExist()

            failure = { CartRefused(409, ErrorCode.OutOfStock, "Only 1 left in stock") }
            onNodeWithTag("cart-line-more:$MUG").performClick()
            waitUntil(timeoutMillis = 5_000) { requests.size == 3 }
            waitUntil(timeoutMillis = 5_000) { exists(MESSAGE_TAG) }
            onNode(hasText("Only 1 left in stock") and hasAnyAncestor(hasTestTag(MESSAGE_TAG))).assertExists()
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

    /**
     * B-41, Cart_Guest in the storefront: «Sign in to check out» carries `next=%2Fcheckout`, and once
     * the popup has signed the shopper in the storefront lands on the checkout — it used to draw the
     * cart again and leave checkout to be found by hand.
     */
    @Test
    fun `in the storefront a guest's Sign in to check out lands on the checkout`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val history = FakeHistory("/cart")
            val requests = CopyOnWriteArrayList<String>()
            var signIns = 0
            guestStorefront(history, requests, signIn = { signIns += 1 })

            onNodeWithTag(CHECKOUT_TAG).performClick()
            waitUntil(timeoutMillis = 5_000) { exists(PLACE_TAG) }

            assertEquals(1, signIns)
            assertEquals(listOf("/cart", "/checkout"), history.entries)
            assertEquals(listOf("/ui/cart", "/ui/checkout"), requests.toList())
        }

    /** A popup the shopper closed is a guest still on the cart: drawn again, and nothing opened. */
    @Test
    fun `in the storefront a sign-in that does not go through stays on the cart`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val history = FakeHistory("/cart")
            val requests = CopyOnWriteArrayList<String>()
            guestStorefront(history, requests, signIn = { throw SignInUnavailable() })

            onNodeWithTag(CHECKOUT_TAG).performClick()
            waitUntil(timeoutMillis = 5_000) { requests.size == 2 }
            waitForIdle()

            assertEquals(listOf("/cart"), history.entries)
            assertEquals(listOf("/ui/cart", "/ui/cart"), requests.toList())
            onNodeWithTag(PLACE_TAG).assertDoesNotExist()
        }

    /** The storefront at `/cart` for a guest; `/ui/checkout` answers the checkout's tree. */
    private fun ComposeUiTest.guestStorefront(
        history: FakeHistory,
        requests: MutableList<String>,
        signIn: suspend () -> Unit,
    ) {
        val transport =
            HaulTransport { path ->
                requests += path
                when (path) {
                    "/ui/cart" -> HaulResponse(200, read(GUEST))
                    "/ui/checkout" -> HaulResponse(200, read(CHECKOUT))
                    else -> error("nothing answers $path")
                }
            }
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                Storefront(transport, history, signIn = signIn, clock = FixedClock, cartCommands = commands)
            }
        }
    }

    private fun ComposeUiTest.exists(tag: String): Boolean =
        onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

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
        const val NOTHING_SELECTED = "cart_nothing_selected.json"
        const val SAVED = "/account/saved"
        const val CHECKOUT = "checkout_content.json"
        const val LINES = "/api/v1/cart/lines"
        const val PROMO = "/api/v1/cart/promo"
        const val HEADPHONES = "p-sony-wh-1000xm6-0"
        const val DUVET = "p-linen-duvet-cover-set-0"
        const val MUG = "p-stoneware-mug-0"
    }
}
