package io.github.youndie.haul.feature.saved

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.CANVAS_NOW
import io.github.youndie.haul.FixtureFonts
import io.github.youndie.haul.decode
import io.github.youndie.haul.feature.cart.CartCommand
import io.github.youndie.haul.feature.cart.CartCommands
import io.github.youndie.haul.feature.cart.LocalCartCommands
import io.github.youndie.haul.feature.cart.lineTag
import io.github.youndie.haul.registry.haulRegistry
import io.github.youndie.haul.theme.HaulTheme
import io.github.youndie.haul.ui.CartLine
import io.github.youndie.haul.ui.LocalHaulNow
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.SAVE
import io.github.youndie.haul.ui.UNSAVE
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotScreen
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.RefreshAction
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Saved list's presses (B-20), drawn from the server's own trees (`resources/bodies/saved_*.json`,
 * `cart_*.json`) through the app's registry: a heart is the command the tree fixed — `PUT` or `DELETE` on
 * the product, here the state it leaves — and «Save for later» a `POST` on the line, each answered
 * `refresh`, which goes to the screen's handler; a guest's heart and «Save for later» follow the tree to
 * sign-in; the chips, the pages and «Browse deals» follow their addresses.
 */
@OptIn(ExperimentalTestApi::class)
class SavedWiringTest {
    private val sent = CopyOnWriteArrayList<CartCommand>()
    private val followed = CopyOnWriteArrayList<KompotAction>()

    private val commands =
        CartCommands { command ->
            sent += command
            RefreshAction
        }

    private fun ComposeUiTest.draw(
        tree: KompotComponent,
        compact: Boolean = false,
    ) = setContent {
        HaulTheme(FixtureFonts.fonts, compact = compact) {
            CompositionLocalProvider(LocalCartCommands provides commands, LocalHaulNow provides CANVAS_NOW) {
                val forms = remember { FormController(FormSchema(formId = "none", fields = emptyList())) }
                Box(Modifier.verticalScroll(rememberScrollState())) {
                    KompotScreen(tree, remember { haulRegistry() }, forms, KompotActionHandler { followed += it })
                }
            }
        }
    }

    /** A saved card's heart lets its product go — `DELETE`, the state the tree fixed — and follows `refresh`. */
    @Test
    fun `a saved card's heart sends the let-go its tree fixed`() =
        runDesktopComposeUiTest(WIDTH, 2_000) {
            draw(decode("saved_content.json"))
            onAllNodesWithContentDescription(UNSAVE)[1].performClick()
            waitForIdle()
            assertEquals(
                listOf<CartCommand>(CartCommand.Heart("/api/v1/me/saved/p-sony-wh-1000xm6", save = false)),
                sent.toList(),
            )
            assertEquals(listOf<KompotAction>(RefreshAction), followed.toList())
        }

    /** The filter's chips and the page numbers go to their own addresses; nothing is sent. */
    @Test
    fun `the chips and the pages follow their addresses`() =
        runDesktopComposeUiTest(WIDTH, 2_000) {
            draw(decode("saved_content.json"))
            onNodeWithText("Price dropped", substring = true).performClick()
            // The menu's Orders badge reads «2» too; the page number is the last.
            onAllNodesWithText("2").onLast().performClick()
            waitForIdle()
            assertEquals(
                listOf<KompotAction>(
                    NavigateAction("/account/saved?filter=price-dropped"),
                    NavigateAction("/account/saved?page=2"),
                ),
                followed.toList(),
            )
            assertEquals(emptyList(), sent.toList())
        }

    /** Nothing saved: «Browse deals» goes to the deals. */
    @Test
    fun `an empty list sends the shopper to the deals`() =
        runDesktopComposeUiTest(WIDTH, 1_200) {
            draw(decode("saved_empty.json"))
            onNodeWithText("Browse deals").performClick()
            waitForIdle()
            assertEquals(listOf<KompotAction>(NavigateAction("/deals")), followed.toList())
        }

    /**
     * A card not saved keeps its product — `PUT` — and a guest's heart, which carries no command, follows the
     * tree to sign-in instead of sending anything.
     */
    @Test
    fun `an unsaved heart saves and a guest's heart goes to sign-in`() =
        runDesktopComposeUiTest(WIDTH, 600) {
            val keep = card("p-keep").copy(heartCommand = SaveCommand("/api/v1/me/saved/p-keep", true))
            val guest = card("p-guest").copy(heartAction = NavigateAction("/sign-in"))
            draw(ProductGrid("grid", listOf(keep, guest), columns = 4))
            onAllNodesWithContentDescription(SAVE)[0].performClick()
            onAllNodesWithContentDescription(SAVE)[1].performClick()
            waitForIdle()
            assertEquals(listOf<CartCommand>(CartCommand.Heart("/api/v1/me/saved/p-keep", save = true)), sent.toList())
            assertEquals(listOf<KompotAction>(RefreshAction, NavigateAction("/sign-in")), followed.toList())
        }

    /** «Save for later» on a customer's line moves it — the `POST` its tree carries — and a guest's goes to sign-in. */
    @Test
    fun `save for later moves a customer's line and sends a guest to sign-in`() =
        runDesktopComposeUiTest(WIDTH, 1_400) {
            val mug = line("p-stoneware-mug-0")
            draw(decode("cart_content.json"))
            onNodeWithTag(lineTag("save", mug)).performClick()
            waitForIdle()
            assertEquals(
                listOf<CartCommand>(CartCommand.SaveForLater("/api/v1/cart/lines/p-stoneware-mug-0/save-for-later")),
                sent.toList(),
            )
            assertEquals(listOf<KompotAction>(RefreshAction), followed.toList())
        }

    @Test
    fun `a guest's save for later goes to sign-in`() =
        runDesktopComposeUiTest(WIDTH, 1_400) {
            draw(decode("cart_guest.json"))
            onNodeWithTag(lineTag("save", line("p-stoneware-mug-0"))).performClick()
            waitForIdle()
            assertEquals(emptyList(), sent.toList())
            assertEquals(listOf<KompotAction>(NavigateAction("/sign-in")), followed.toList())
        }

    private fun card(productId: String) =
        ProductCard(
            id = "card-$productId",
            productId = productId,
            title = "A product",
            price = "$10",
            rating = "4.5",
            reviews = "10",
            delivery = "Tomorrow",
            tone = "#ECE9E2",
            label = "product",
        )

    /** A line whose tag is the one the cart's view gives its SKU's «Save for later». */
    private fun line(skuId: String) =
        CartLine(
            id = "line-$skuId",
            skuId = skuId,
            productId = skuId.substringBeforeLast('-'),
            title = "",
            options = "",
            tone = "",
            label = "",
            price = "",
            quantity = 1,
            maxQuantity = 1,
            selected = true,
            selectable = true,
            url = "",
        )

    private companion object {
        const val WIDTH = 1_440
    }
}
