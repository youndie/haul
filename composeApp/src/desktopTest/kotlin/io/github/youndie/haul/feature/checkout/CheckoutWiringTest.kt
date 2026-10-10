package io.github.youndie.haul.feature.checkout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.CANVAS_NOW
import io.github.youndie.haul.FakeHistory
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.decode
import io.github.youndie.haul.read
import io.github.youndie.haul.registry.haulRegistry
import io.github.youndie.haul.shell.HaulResponse
import io.github.youndie.haul.shell.HaulTransport
import io.github.youndie.haul.shell.Storefront
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.CheckoutBody
import io.github.youndie.haul.ui.LocalHaulNow
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotScreen
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.RefreshAction
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The checkout's presses, drawn from the server's own trees (`resources/bodies/checkout_*.json`) through
 * the app's registry: each press is the command endpoint-checkout names, sent to the URL the tree carries,
 * and the server's answer goes to the screen's handler, which draws the checkout again. A press the tree
 * does not allow sends nothing; while the order is being placed the page says so and nothing else is sent.
 */
@OptIn(ExperimentalTestApi::class)
class CheckoutWiringTest {
    private val sent = CopyOnWriteArrayList<CheckoutCommand>()
    private val followed = CopyOnWriteArrayList<KompotAction>()

    /** Placement's answer: held until the test lets it go, so the page can be looked at in between. */
    private var placed =
        CompletableDeferred<KompotAction>().apply {
            complete(
                NavigateAction("/account/orders/HL-48302"),
            )
        }

    private val commands =
        CheckoutCommands { command ->
            sent += command
            if (command is CheckoutCommand.Place) placed.await() else RefreshAction
        }

    private fun ComposeUiTest.checkout(tree: KompotComponent) =
        setContent {
            HaulTheme(FixtureFonts.fonts, compact = false) {
                CompositionLocalProvider(LocalCheckoutCommands provides commands, LocalHaulNow provides CANVAS_NOW) {
                    val forms = remember { FormController(FormSchema(formId = "none", fields = emptyList())) }
                    Box(Modifier.verticalScroll(rememberScrollState())) {
                        KompotScreen(tree, remember { haulRegistry() }, forms, KompotActionHandler { followed += it })
                    }
                }
            }
        }

    private fun ComposeUiTest.checkout(body: String) = checkout(decode(body))

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
    fun `a method is chosen and the answer redraws the checkout`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            checkout(CONTENT)
            press(methodTag(DeliveryMethod.PickupPoint))
            assertEquals(
                listOf<CheckoutCommand>(
                    CheckoutCommand.Choose(CHOICE, CheckoutChoice(method = DeliveryMethod.PickupPoint)),
                ),
                sent.toList(),
            )
            assertEquals(listOf<KompotAction>(RefreshAction), followed.toList())
        }

    @Test
    fun `the method already chosen sends nothing`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            checkout(CONTENT)
            pressInert(methodTag(DeliveryMethod.Courier))
        }

    @Test
    fun `a window is chosen`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            checkout(CONTENT)
            press(slotTag("2025-10-08T09"))
            assertEquals(
                listOf<CheckoutCommand>(CheckoutCommand.Choose(CHOICE, CheckoutChoice(slotId = "2025-10-08T09"))),
                sent.toList(),
            )
        }

    /** Another day's tile shows that day's windows; choosing one of them is the command, not the tile. */
    @Test
    fun `another day shows its windows`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            checkout(CONTENT)
            pressInert(dayTag(1))
            press(slotTag("2025-10-09T18"))
            assertEquals(
                listOf<CheckoutCommand>(CheckoutCommand.Choose(CHOICE, CheckoutChoice(slotId = "2025-10-09T18"))),
                sent.toList(),
            )
        }

    /** Checkout_PlaceError: the window that filled up is drawn and cannot be chosen, and the order cannot be placed. */
    @Test
    fun `a full window and a held button send nothing`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            checkout(PLACE_ERROR)
            pressInert(slotTag("2025-10-08T15"))
            pressInert(PLACE_TAG)
        }

    @Test
    fun `a pickup point is chosen`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            checkout(PICKUP_POINT)
            press(pointTag("point-96-n6th"))
            assertEquals(
                listOf<CheckoutCommand>(CheckoutCommand.Choose(CHOICE, CheckoutChoice(pointId = "point-96-n6th"))),
                sent.toList(),
            )
        }

    @Test
    fun `a way to pay is chosen`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            checkout(CONTENT)
            press(paymentTag("haul_pay"))
            assertEquals(
                listOf<CheckoutCommand>(CheckoutCommand.Choose(CHOICE, CheckoutChoice(payment = "haul_pay"))),
                sent.toList(),
            )
        }

    /** The points toggle turns on from Content and off from PointsApplied (B-23), each answered `refresh`. */
    @Test
    fun `the points toggle turns the points on and off`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            checkout(CONTENT)
            press(POINTS_TAG)
            assertEquals(
                listOf<CheckoutCommand>(CheckoutCommand.Choose(CHOICE, CheckoutChoice(usePoints = true))),
                sent.toList(),
            )
            assertEquals(listOf<KompotAction>(RefreshAction), followed.toList())
        }

    @Test
    fun `the points toggle on turns the points off`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            checkout(POINTS_APPLIED)
            press(POINTS_TAG)
            assertEquals(
                listOf<CheckoutCommand>(CheckoutCommand.Choose(CHOICE, CheckoutChoice(usePoints = false))),
                sent.toList(),
            )
        }

    /** The address form is sent as typed once the shopper leaves it; the server's `refresh` redraws it. */
    @Test
    fun `a changed address is sent when the shopper is done with the form`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            checkout(CONTENT)
            onNodeWithTag(fieldTag("apt")).performTextClearance()
            onNodeWithTag(fieldTag("apt")).performTextInput("5B")
            onNodeWithTag(fieldTag("doorCode")).performClick()
            waitForIdle()
            assertEquals(emptyList(), sent.toList(), "the form was sent while the shopper was still in it")
            onNodeWithTag(fieldTag("doorCode")).performTextInput("1234")
            onNodeWithTag(fieldTag("doorCode")).performImeAction()
            waitUntil(timeoutMillis = 5_000) { followed.isNotEmpty() }
            assertEquals(
                listOf<CheckoutCommand>(
                    CheckoutCommand.SaveAddress(
                        ADDRESSES,
                        AddressEntry("148 Wythe Avenue", "5B", "Brooklyn, NY", "11211", doorCode = "1234"),
                    ),
                ),
                sent.toList(),
            )
            assertEquals(listOf<KompotAction>(RefreshAction), followed.toList())
        }

    @Test
    fun `leaving an unchanged form sends nothing`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            checkout(CONTENT)
            onNodeWithTag(fieldTag("street")).performClick()
            onNodeWithTag(fieldTag("street")).performImeAction()
            waitForIdle()
            assertEquals(emptyList(), sent.toList())
        }

    /** Checkout_Validation: the form drawn again with what was sent, and what to fill in under it. */
    @Test
    fun `a refused form is drawn with its errors`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            checkout(VALIDATION)
            onNodeWithText("Enter the street address").assertExists()
            onNodeWithText("Enter a 5-digit ZIP").assertExists()
            onNodeWithText("Fill in the street address and ZIP").assertExists()
            pressInert(PLACE_TAG)
        }

    /**
     * «Place order» sends the quote under one key per quote and draws Placing while it is answered —
     * the sections inert, the button saying so — then follows where the server says the order is.
     */
    @Test
    fun `place order sends the quote and draws placing until the answer`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            placed = CompletableDeferred()
            val tree = decode(CONTENT)
            checkout(tree)
            onNodeWithTag(PLACE_TAG).performClick()
            waitUntil(timeoutMillis = 5_000) { sent.isNotEmpty() }
            onNodeWithText("Placing order…").assertExists()
            onNodeWithTag(methodTag(DeliveryMethod.PickupPoint)).performClick()
            onNodeWithTag(PLACE_TAG).performClick()
            waitForIdle()
            assertEquals(1, sent.size, "a press while placing was sent")
            val place = sent.single() as CheckoutCommand.Place
            assertEquals(ORDERS, place.url)
            assertEquals(tree.body().summary.quote, place.quote)
            assertTrue(place.idempotencyKey.isNotBlank())

            placed.complete(NavigateAction("/account/orders/HL-48302"))
            waitUntil(timeoutMillis = 5_000) { followed.isNotEmpty() }
            assertEquals(listOf<KompotAction>(NavigateAction("/account/orders/HL-48302")), followed.toList())
        }

    /** A refusal (`409 slot_unavailable`) redraws the checkout, and pressing again is the same order: the same key. */
    @Test
    fun `a refused placement redraws and a retry keeps its key`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            placed = CompletableDeferred<KompotAction>().apply { complete(RefreshAction) }
            checkout(CONTENT)
            press(PLACE_TAG)
            assertEquals(listOf<KompotAction>(RefreshAction), followed.toList())
            onNodeWithText("Place order · $512.00").assertExists()
            followed.clear()
            press(PLACE_TAG)
            val keys = sent.map { (it as CheckoutCommand.Place).idempotencyKey }
            assertEquals(2, keys.size)
            assertEquals(keys[0], keys[1], "a retry of the same quote got a new key")
        }

    /** B-79: a tile in «Your order» opens its product, and «Back to cart» goes to the cart; neither is a command. */
    @Test
    fun `the summary's tiles open their products and back to cart goes to the cart`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val tree = decode(CONTENT)
            checkout(tree)
            val items = tree.body().summary.items
            items.indices.forEach { onNodeWithTag(summaryItemTag(it)).performClick() }
            onNodeWithTag(BACK_TO_CART_TAG).performClick()
            waitForIdle()
            assertEquals(emptyList(), sent.toList())
            assertEquals(
                items.map { it.action } + NavigateAction("/cart"),
                followed.toList(),
            )
            assertEquals(NavigateAction("/p/p-sony-wh-1000xm6"), followed.first())
        }

    /** While the order is on its way the summary's links do nothing: the page leaves only for the order. */
    @Test
    fun `the summary's links do nothing while the order is placed`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            placed = CompletableDeferred()
            checkout(CONTENT)
            onNodeWithTag(PLACE_TAG).performClick()
            waitUntil(timeoutMillis = 5_000) { sent.isNotEmpty() }
            onNodeWithText("Placing order…").assertExists()
            onNodeWithTag(summaryItemTag(0)).performClick()
            onNodeWithTag(BACK_TO_CART_TAG).performClick()
            waitForIdle()
            assertEquals(emptyList(), followed.toList())
            placed.complete(RefreshAction)
        }

    /**
     * B-79: the step indicator is what the tree says — a quote that can be placed (Content) is on «Review»
     * with «Delivery» and «Payment» ticked; one held for its window (PlaceError) is on «Delivery».
     */
    @Test
    fun `the steps follow what is filled in`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            var tree by mutableStateOf(decode(CONTENT))
            setContent {
                HaulTheme(FixtureFonts.fonts, compact = false) {
                    val forms = remember { FormController(FormSchema(formId = "none", fields = emptyList())) }
                    KompotScreen(tree, remember { haulRegistry() }, forms, KompotActionHandler { followed += it })
                }
            }
            assertSteps(current = 2)
            tree = decode(PLACE_ERROR)
            waitForIdle()
            assertSteps(current = 0)
        }

    /** The step [current] is marked selected; the ones before it are ticked, the ones after it numbered. */
    private fun ComposeUiTest.assertSteps(current: Int) {
        (0..2).forEach { index ->
            val step = onNodeWithTag(stepTag(index), useUnmergedTree = true)
            if (index == current) step.assertIsSelected() else step.assertIsNotSelected()
            val numbered =
                onAllNodes(hasText("${index + 1}") and hasParent(hasTestTag(stepTag(index))), useUnmergedTree = true)
            numbered.assertCountEquals(if (index < current) 0 else 1)
        }
    }

    /**
     * In the storefront: `/checkout` loads the checkout's tree, and a command's `refresh` fetches it
     * again — redrawn in place by the shell (B-35).
     */
    @Test
    fun `in the storefront a command's answer fetches the checkout again`() =
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
                        FakeHistory("/checkout"),
                        signIn = {},
                        clock = FixedClock,
                        checkoutCommands = commands,
                    )
                }
            }
            onNodeWithTag(paymentTag("haul_pay")).performClick()
            waitUntil(timeoutMillis = 5_000) { requests.size == 2 }
            assertEquals(listOf("/ui/checkout", "/ui/checkout"), requests.toList())
            assertEquals(
                listOf<CheckoutCommand>(CheckoutCommand.Choose(CHOICE, CheckoutChoice(payment = "haul_pay"))),
                sent.toList(),
            )
        }

    @Test
    fun `in the storefront a checkout that did not load says the cart is unchanged`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val transport = HaulTransport { HaulResponse(500, """{"code":"internal","message":"boom"}""") }
            setContent {
                HaulTheme(FixtureFonts.fonts, compact = false) {
                    Storefront(
                        transport,
                        FakeHistory("/checkout"),
                        signIn = {},
                        clock = FixedClock,
                        checkoutCommands = commands,
                    )
                }
            }
            onNodeWithText("Your cart is unchanged. Try again in a moment.").assertExists()
        }

    private fun KompotComponent.body(): CheckoutBody =
        (this as ColumnComponent).children.filterIsInstance<CheckoutBody>().single()

    private object FixedClock : Clock {
        override fun now(): Instant = CANVAS_NOW
    }

    private companion object {
        const val WIDTH = 1440
        const val HEIGHT = 1700
        const val CONTENT = "checkout_content.json"
        const val POINTS_APPLIED = "checkout_points_applied.json"
        const val PICKUP_POINT = "checkout_pickup_point.json"
        const val VALIDATION = "checkout_validation.json"
        const val PLACE_ERROR = "checkout_place_error.json"
        const val CHOICE = "/api/v1/me/checkout"
        const val ADDRESSES = "/api/v1/me/addresses"
        const val ORDERS = "/api/v1/orders"
    }
}
